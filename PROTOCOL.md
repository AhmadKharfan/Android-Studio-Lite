# AndroidStudioLite build API — wire protocol

Canonical JSON wire schema for the **server-side build backend**. The Android app is a thin client:
it packages a project (or points at a Git repo), asks the control plane to build it, streams
`BuildEvent`s into the existing build console, then downloads and installs the resulting APK/AAB.

This document is the contract shared by the app (`data/remote/RemoteBuildSystem`, added in A2) and
the server (control plane S2, worker S3). It is derived directly from the existing Kotlin domain
models so the two sides can share them:

- `domain/buildsystem/BuildRequest.kt` — `BuildRequest`, `BuildKind`
- `domain/buildsystem/BuildEvent.kt` — the `BuildEvent` sealed stream
- `domain/buildsystem/ProjectModel.kt` — `ProjectModel` and friends (sync result)

**Conventions**
- Transport: HTTPS for REST, WSS for the event stream. `Content-Type: application/json; charset=utf-8`.
- All request/response bodies are JSON objects. Field names are `lowerCamelCase`.
- Timestamps are epoch milliseconds (`Long`). Durations are milliseconds (`Long`).
- Enums serialize as their Kotlin constant name (e.g. `"ASSEMBLE"`, `"ERROR"`, `"APK"`).
- File paths in `BuildEvent`/`ProjectModel` are **project-relative POSIX paths** on the wire. The
  server never sees the device's absolute paths; the client maps them back to local `File`s (the
  Kotlin models hold `java.io.File`, but only the relative path string crosses the wire).
- Unknown fields MUST be ignored by both sides (forward-compatible).
- Auth: every endpoint except `POST /v1/devices` requires `Authorization: Bearer <deviceToken>`.
- Errors: non-2xx responses return `{ "error": { "code": string, "message": string } }` (see
  [Errors](#errors)).

---

## REST endpoints (control plane)

### `POST /v1/devices`
Register an anonymous device and mint a token. No auth. Attestation optional (Phase 4).

Request:
```json
{ "attestation": "<optional Play Integrity token>" }
```
Response `201`:
```json
{ "deviceToken": "dev_9f3c…", "createdAt": 1732492800000 }
```

### `POST /v1/builds`
Create a build. Returns a `buildId`; for `zip` source, also a presigned `uploadUrl` to PUT the
project archive to before starting.

Request (mirrors `BuildRequest`; `projectRoot` is not sent — the source is uploaded/cloned instead):
```json
{
  "sourceType": "zip",            // "zip" | "git"
  "gitUrl": null,                  // required when sourceType == "git"
  "ref": null,                     // optional git ref/branch/sha
  "modulePath": ":app",            // Gradle path of the module to build
  "variantName": "debug",          // e.g. "debug"
  "kind": "ASSEMBLE",              // BuildKind: "ASSEMBLE" | "BUNDLE" | "CLEAN"
  "tasks": ["assembleDebug"]       // optional explicit task override
}
```
Response `201`:
```json
{
  "buildId": "bld_1a2b3c",
  "uploadUrl": "https://spaces…/sources/bld_1a2b3c.zip?X-Amz-Signature=…",  // null for git
  "uploadMethod": "PUT",
  "uploadExpiresAt": 1732492860000
}
```

### `POST /v1/builds/{id}/start`
Enqueue the build. Call after the zip has been uploaded (no-op body). For `git` builds, may be called
immediately after create.

Response `202`:
```json
{ "buildId": "bld_1a2b3c", "status": "QUEUED" }
```

### `GET /v1/builds/{id}`
Poll build status/timings/problems and the artifact URL once available.

Response `200`:
```json
{
  "buildId": "bld_1a2b3c",
  "status": "SUCCEEDED",           // QUEUED | RUNNING | SUCCEEDED | FAILED | CANCELLED | TIMED_OUT
  "queuedAt": 1732492800000,
  "startedAt": 1732492803000,
  "finishedAt": 1732492861000,
  "durationMillis": 58000,
  "problems": [ /* Problem event payloads, see below */ ],
  "artifact": {
    "kind": "APK",                 // ArtifactKind: "APK" | "AAB" | "OTHER"
    "name": "app-debug.apk",
    "sizeBytes": 6291456,
    "downloadUrl": null            // use GET /artifact for a fresh presigned URL
  },
  "error": null                    // { "code", "message" } when status == FAILED
}
```

### `GET /v1/builds/{id}/artifact`
Returns a fresh presigned GET URL for the built artifact.

Response `200`:
```json
{ "kind": "APK", "name": "app-debug.apk", "sizeBytes": 6291456,
  "downloadUrl": "https://spaces…/artifacts/bld_1a2b3c/app-debug.apk?X-Amz…",
  "expiresAt": 1732496400000 }
```

### `POST /v1/builds/{id}/cancel`
Cancel a queued or running build (maps to `BuildSystem.cancel()`).

Response `200`:
```json
{ "buildId": "bld_1a2b3c", "status": "CANCELLED" }
```

### `POST /v1/sync` (Phase 3)
Run the Gradle tooling model server-side and return a `ProjectModel`. Same source-transfer options as
`POST /v1/builds` (`sourceType`, upload/clone). Response body is a [`ProjectModel`](#projectmodel).

---

## WebSocket: `WS /v1/builds/{id}/stream`

Upgrade with `Authorization: Bearer <token>` (or `?token=` query param where headers can't be set).
The server pushes one JSON **`BuildEvent`** object per message, in order, until a `Finished` event, then
closes. Clients reconnecting mid-build receive a replay from the log archive followed by live events.

Every message is a tagged union discriminated by `type`, matching the `BuildEvent` sealed interface.
The `type` values are **lowerCamelCase** — the server control plane / worker are the source of truth
(see `asl-build-server/control-plane/PROTOCOL.md`); the app parser
(`data/remote/protocol/BuildEventParser`) decodes exactly these tags.

### `started`
```json
{ "type": "started", "request": {
    "buildId": "bld_1a2b3c", "modulePath": ":app", "variantName": "debug", "kind": "ASSEMBLE" } }
```

### `progress`
```json
{ "type": "progress", "message": "Configuring project ':app'" }
```

### `taskStarted`
```json
{ "type": "taskStarted", "taskPath": ":app:compileDebugKotlin" }
```

### `taskFinished`
`result` is a `TaskResult`: `SUCCESS | UP_TO_DATE | SKIPPED | FAILED`.
```json
{ "type": "taskFinished", "taskPath": ":app:compileDebugKotlin", "result": "SUCCESS" }
```

### `output`
A raw log line. `stream` is an `OutputStream`: `STDOUT | STDERR`.
```json
{ "type": "output", "line": "> Task :app:preBuild", "stream": "STDOUT" }
```

### `problem`
A structured, navigable problem. `severity` is a `ProblemSeverity`: `ERROR | WARNING | INFO`.
`file` is a project-relative path (or null); `line`/`column` are 1-based (or null).
```json
{ "type": "problem", "severity": "WARNING",
  "message": "variable 'unused' is never used",
  "file": "app/src/main/java/MainActivity.kt", "line": 24, "column": 13 }
```

### `artifactProduced`
`kind` is an `ArtifactKind`: `APK | AAB | OTHER`. `name` is the artifact's object name; the client
fetches the actual bytes via `GET /v1/builds/{id}/artifact` and re-attaches a local `File`.
```json
{ "type": "artifactProduced", "name": "app-debug.apk", "kind": "APK" }
```

### `finished`
Terminal event; the server closes the socket after sending it.
```json
{ "type": "finished", "success": true, "durationMillis": 58000 }
```

**Log archive:** the same NDJSON stream is persisted to `logs/{buildId}.ndjson` in object storage (one
`BuildEvent` JSON per line) for replay and post-mortem.

---

## ProjectModel

Result of `POST /v1/sync`, mirroring `domain/buildsystem/ProjectModel.kt`. `rootDir`/`moduleDir`/dir
lists/`manifestFile`/`resolvedArtifact` serialize as project-relative POSIX path strings (or null).

```json
{
  "name": "MyApp",
  "rootDir": ".",
  "modules": [
    {
      "path": ":app",
      "name": "app",
      "type": "ANDROID_APP",             // ModuleType: ANDROID_APP | ANDROID_LIBRARY | JVM | UNKNOWN
      "moduleDir": "app",
      "variants": [
        { "name": "debug", "buildType": "debug", "flavors": [] },
        { "name": "release", "buildType": "release", "flavors": [] }
      ],
      "sourceSets": [
        {
          "name": "main",
          "javaDirs": ["app/src/main/java"],
          "kotlinDirs": [],
          "resDirs": ["app/src/main/res"],
          "assetsDirs": [],
          "manifestFile": "app/src/main/AndroidManifest.xml"
        }
      ],
      "dependencies": [
        {
          "coordinate": "androidx.core:core-ktx:1.13.1",
          "scope": "IMPLEMENTATION",     // DependencyScope: IMPLEMENTATION | API | COMPILE_ONLY |
                                          //   RUNTIME_ONLY | TEST | ANDROID_TEST | UNKNOWN
          "resolvedArtifact": null        // path to jar/aar once resolved, else null
        }
      ]
    }
  ]
}
```

---

## Errors

Non-2xx responses:
```json
{ "error": { "code": "QUOTA_EXCEEDED", "message": "Daily build-minute quota reached." } }
```

| HTTP | `code` (examples)                          | Meaning                                    |
|------|--------------------------------------------|--------------------------------------------|
| 400  | `INVALID_REQUEST`                          | Malformed body / missing field.            |
| 401  | `INVALID_TOKEN`                            | Missing/expired/revoked device token.      |
| 404  | `BUILD_NOT_FOUND`                          | Unknown `buildId`.                         |
| 409  | `INVALID_STATE`                            | e.g. `start` before upload, cancel a done build. |
| 413  | `SOURCE_TOO_LARGE`                         | Uploaded zip exceeds the limit.            |
| 429  | `RATE_LIMITED`, `QUOTA_EXCEEDED`           | Per-token/IP rate limit or daily quota.    |
| 503  | `CAPACITY`, `CIRCUIT_OPEN`                 | Build pool saturated / cost circuit-breaker open. |

---

## Build lifecycle (zip path)

```
POST /v1/devices                         → deviceToken (once, cached in DataStore)
POST /v1/builds {sourceType:"zip", …}    → buildId + presigned uploadUrl
PUT  <uploadUrl>  (project zip)          → 200
POST /v1/builds/{id}/start               → QUEUED
WS   /v1/builds/{id}/stream              → started → progress/task*/output/problem* → artifactProduced → finished
GET  /v1/builds/{id}/artifact            → presigned downloadUrl
GET  <downloadUrl>  (APK bytes)          → install + launch via PackageInstaller
```
Cancel at any point: `POST /v1/builds/{id}/cancel`. Git path is identical minus the upload step
(`sourceType:"git"`, `gitUrl`/`ref` in the create body).
