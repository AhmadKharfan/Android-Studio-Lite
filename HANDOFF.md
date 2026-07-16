# Android Studio Lite — Project Report & Handoff Context

> Written 2026-07-15 as a self-contained context document. It explains what the project is, how it
> was built, why there is a second repository, the current architecture and status, how to build /
> run / deploy / operate it, and every open item and gotcha. A new engineer (or a new Claude
> session) should be able to continue from this file alone.
> **No secrets are in this document.** Where credentials are needed, it says so.

---

## 1. What this is

**Android Studio Lite (ASL)** is an Android app that is itself a mobile IDE: you create an Android
project on your phone, edit it, tap **Run**, and it **builds a real APK and installs+launches it on
the device**. It is a Jetpack Compose app, MVVM + Koin DI, package
`com.ahmadkharfan.androidstudiolite`.

The build does **not** happen on the phone. It happens on a **server-side build backend** running
real Gradle + the Android Gradle Plugin. The phone is a thin client that packages the project, sends
it up, streams the live build log into an on-device console, then downloads and installs the
resulting APK.

---

## 2. The two repositories — and why (is the second one "backend" or "just IaC"?)

There are **two sibling git repos**:

| Repo | Path | What it is |
|---|---|---|
| **App** | `~/AndroidStudioProjects/AndroidStudioLite` | The Android IDE app (Kotlin/Compose). |
| **Build backend** | `~/AndroidStudioProjects/asl-build-server` | The server-side build system. |

**`asl-build-server` is a full backend, not just IaC.** It contains three distinct things:

1. **A backend service** — `control-plane/`: a **Kotlin/Ktor HTTP + WebSocket API server**. It
   authenticates devices, accepts build requests, mints presigned upload/download URLs, launches a
   Kubernetes Job per build, and relays the live build-event stream to the app over a WebSocket. It's
   a real stateful service (Postgres + Redis).
2. **A build worker** — `worker/`: a **Docker image + entrypoint** (JDK + Android SDK + Gradle) that
   runs one untrusted user build inside a sandbox, streams structured events, and uploads the APK.
3. **The infrastructure-as-code** — `infra/` (Terraform) + `k8s/` (manifests) that provision and
   configure the DigitalOcean Kubernetes cluster the above run on.

So it's **a backend system *and* its IaC**, kept in a separate repo because it's a separately
deployable system with a different language/runtime/release lifecycle from the Android app. The two
share only a wire contract (see `control-plane/PROTOCOL.md` and the app's `data/remote/`).

---

## 3. How we got here (the history, briefly)

The project went through a deliberate architecture pivot:

1. **On-device build attempt (shelved).** The original plan built APKs *on the phone* — porting a
   Termux-style JDK/SDK/Gradle toolchain into the app. This produced a ~150 MB app and hit a wall:
   upstream Termux binaries hard-code a RUNPATH to `com.termux`, modern Android blocks executing
   binaries from app-data (`targetSdk` W^X), and per-ABI toolchains are huge. Two flavors and an
   in-process build engine were explored, then abandoned. *(That code still exists in git history;
   don't build on it.)*
2. **Pivot to server-side builds (2026-07-13).** Move the build to a server → app drops to **~27 MB**,
   builds run at full speed with **100% real Gradle/AGP fidelity** (XML, Compose, KMP all just work),
   and the entire toolchain-rebuild problem disappears. The app became a thin client.
3. **Built + deployed (2026-07-15).** The backend was implemented (infra, control-plane, worker,
   security), deployed to the user's DigitalOcean account, connected to the app, and **proven
   end-to-end on a real phone**: create project → build on the cluster → install → launch.
4. **Made fast (2026-07-15).** First builds took ~5m44s because every build started from a cold
   cache. Fixed with a shared persistent Gradle cache → **~1 min** typical.

---

## 4. Architecture (current)

```
┌─────────────────────────┐        HTTPS/WS         ┌──────────────────────────────────────────┐
│  ASL app (phone)        │ ─────── /v1/* ────────▶ │  Control-plane API (Ktor)  [asl-system ns] │
│  data/remote/           │ ◀═══ WebSocket events ══ │   - device tokens, quotas, circuit-breaker │
│   RemoteBuildSystem     │                          │   - mints presigned Spaces URLs            │
│   RemoteClient (OkHttp) │                          │   - creates a K8s Job per build            │
│   ProjectPackager (zip) │        presigned         └───────────────┬────────────────────────────┘
│   ArtifactDownloader    │ ──PUT src / GET apk──▶ DO Spaces (S3)     │ creates
│  feature/buildrun UI    │                                          ▼
│  data/buildsystem/      │                          ┌──────────────────────────────────────────┐
│   install/ApkInstaller  │                          │  Build worker Pod (gVisor sandbox)         │
└─────────────────────────┘                          │   [asl-builds ns, warm c-4 build node]     │
                                                     │   real ./gradlew assembleDebug             │
   Postgres (builds/devices/quotas)                  │   shared persistent ~/.gradle cache (PV)   │
   Redis (queue / rate-limit / WS pub-sub)           │   egress allowlist: Maven/Google/Spaces    │
                                                     └──────────────────────────────────────────┘
```

**Build flow (what "Run" does):**
1. App zips the project (minus `build/.gradle/.idea/.git`), `POST /v1/builds` → gets a presigned
   Spaces PUT, uploads the zip.
2. `POST /v1/builds/{id}/start` → control-plane creates a gVisor-sandboxed Kubernetes Job.
3. Worker downloads source, runs `./gradlew`, streams `BuildEvent`s (started/progress/output/
   taskStarted/taskFinished/problem/artifactProduced/finished) to the control plane, which relays
   them over the app's WebSocket into the build console.
4. On success the worker uploads the APK to Spaces; the app fetches a presigned GET via
   `/v1/builds/{id}/artifact`, downloads it, and hands it to `PackageInstaller` → install → launch.

**Security model (it runs untrusted `build.gradle` — this is arbitrary code execution by design):**
every build runs in a **gVisor (runsc) sandbox**, non-root, read-only rootfs, dropped caps, a
Cilium **egress allowlist** (only Maven Central / Google Maven / Gradle plugin portal / DO Spaces),
resource caps + a 20-min `activeDeadlineSeconds`, and the pod holds **no credentials** — only
short-lived presigned URLs. Abuse control: per-token/IP rate limits, daily build-minute quotas, a
cost circuit-breaker, optional Play Integrity attestation.

---

## 5. Current status — what works

**End-to-end proven on a real device** (Huawei `A3SQUT5808000069`) against the live server:
create → build on cluster → stream logs → download → install → launch. ✅

- **App (`main`):** onboarding + permissions, real file/project repos, editor with completion/
  diagnostics, JGit git, terminal, static Gradle parser, **7 templates** (Empty Compose [default],
  Empty/Basic Views, Bottom Nav, Nav Drawer, No Activity, Responsive) that all build on the server,
  create-project wizard (name/package/Save location/language/minSdk, blocks duplicate name+package),
  `RemoteBuildSystem` streaming builds, install/run, release-keystore upload, Play-Integrity hook.
  27 MB APK. Unit tests green (438).
- **Backend (`master`):** control-plane API, gVisor worker, full Terraform/k8s, security fixtures,
  shared Gradle cache. Deployed and live.

---

## 6. Live infrastructure (DigitalOcean, user's account, region nyc3)

- **DOKS cluster:** `asl-build-cluster` (Kubernetes 1.34). Node pools: 1× `s-2vcpu-4gb` control node,
  1× **`c-4` warm build node** (min=1 — see gotcha below).
- **Public API:** `http://129.212.152.5` (LoadBalancer `asl-control-public`). The app's
  `DEFAULT_BUILD_SERVER_URL` points here.
- **Container registry:** `registry.digitalocean.com/asl-build-registry` — images
  `asl-control-plane` and `asl-build-worker` (worker currently `0.2.0`).
- **Object storage:** DO Spaces bucket `asl-build-nyc3-202a96` (source zips, APK artifacts, logs).
- **Managed Postgres:** database `aslbuild` (devices, builds, quotas).
- **Redis:** in-cluster (queue / rate-limit / WebSocket pub-sub).
- **kubectl context:** `do-nyc3-asl-build-cluster` (this host is already configured).

---

## 7. How to work with it

### App
- Build/test: `cd ~/AndroidStudioProjects/AndroidStudioLite && ./gradlew :app:assembleDebug :app:testDebugUnitTest`
- Install on device: `adb install -r -g app/build/outputs/apk/debug/app-debug.apk`
- The **debug** build has a host-scoped cleartext-HTTP exception (`app/src/debug/network_security_config.xml`)
  so it can reach the plain-HTTP server. **Release builds are HTTPS-only** and cannot talk to the
  server until TLS is added.

### Backend (this host has kubectl / doctl / docker / terraform, all authed)
- Deploy guide: `~/AndroidStudioProjects/asl-build-server/DEPLOY.md`.
- Operate: `k8s/README.md` (gVisor verify, egress verify), `docs/runbook.md` (revoke a token, drain
  the pool), `docs/secrets.md`, `docs/attestation.md`.
- Test every template on the real server: `~/AndroidStudioProjects/AndroidStudioLite/tools/template_build_check.py`.
- Rebuild+push worker: bump `worker/VERSION`, `IMAGE=registry.digitalocean.com/asl-build-registry/asl-build-worker ./worker/build.sh && docker push …`, update `WORKER_IMAGE` and roll.

### Wire contract (keep the two repos in sync)
- `asl-build-server/control-plane/PROTOCOL.md` + `WORKER_CONTRACT.md` are authoritative.
- BuildEvent JSON uses **lowerCamelCase** `type` discriminators. REST bodies: `/start` returns
  `{"status":"queued"}` (no buildId); `/artifact` returns `{url, expiresInSeconds}`. The app side is
  in `data/remote/` (RemoteClient, protocol/*).

---

## 8. The build-speed fix (why it was slow, what changed)

Every build re-downloaded the whole Gradle distribution + AGP/Kotlin/Compose/AndroidX graph because
`GRADLE_USER_HOME` was a throwaway `emptyDir` per build (the "Welcome to Gradle…" + "35 tasks
executed" every run). Fix (server `master` + app `main`):
- **Shared persistent `~/.gradle`**: a node-local `local` PV (`k8s/09-gradle-cache.yaml`, 40 GiB,
  RWO, mounted at `/home/worker/.gradle`) reused across builds — like GitHub Actions' `~/.gradle`
  cache. Deps + Gradle dist + local build cache persist.
- Worker turns on `--build-cache --parallel`, `org.gradle.parallel/caching`, `-Xmx4g`, more pod CPU.
- App: skip re-zipping an unchanged project; **toolchain bumped to Gradle 8.11.1 / AGP 8.7.3 /
  Kotlin 2.0.21 (K2)** with the `org.jetbrains.kotlin.plugin.compose` plugin (matches the worker's
  Gradle, fewer/newer deps).
- Result: **~5m44s → ~1 min** typical.

---

## 9. Open items / risks / gotchas (READ before continuing)

**Must-do before any public / real use:**
1. **Rotate the DigitalOcean credentials.** A DO API token and Spaces keys were pasted into a chat
   transcript during deployment. They have full account access — rotate them (DO console → API).
2. **Add TLS to the LoadBalancer.** The API is plain HTTP; the app sends device tokens and (for
   release) keystore material over it. Needs a domain + cert; annotation stubs are in
   `control-plane/k8s/service-public.yaml`. Then set `DEFAULT_BUILD_SERVER_URL` to `https://…` and
   delete the debug cleartext exception.
3. **Run the security fixtures.** `asl-build-server/security/fixtures/` (fork-bomb, disk-fill,
   metadata-probe, network-egress, long-runner) prove the sandbox contains a malicious build. They
   were written but **not yet executed** end-to-end. Mandatory before exposing the service.

**Architectural trades / limitations:**
4. **Shared Gradle cache weakens isolation.** The cache is writable by every (untrusted) build, so a
   malicious build could try to poison it (Gradle checksum-verification limits this). Accepted for
   single-user. The multi-tenant-safe replacement is a **read-through Maven mirror** (builds
   read-only) — do this before public. RWO also **serializes concurrent builds on a node**.
5. **Never scale the build pool to zero.** The warm cache is a `local` PV on the build node's disk;
   scaling to zero deletes the node *and the cache*. `build_pool_min_nodes = 1` is pinned in
   `infra/terraform.tfvars` for this reason.
6. **The worker has no NDK/CMake**, so **native (C++) templates can't build** — that's why ASL ships
   no Native C++ / Game template. Adding them means adding `ndk`/`cmake` to `worker/Dockerfile` and
   redeploying.
7. **Attestation is off** (`PLAY_INTEGRITY_REQUIRED=false`) and **`ADMIN_TOKEN` unset** — correct for
   dev, must change before public.
8. **One manual DB step isn't in Terraform:** `ALTER DATABASE aslbuild OWNER TO aslbuild_app` (PG15+
   `public` schema perms) was applied by hand — codify it before a clean re-provision.
9. **Install needs a fingerprint.** On the test device, Play Protect shows an "install without
   scanning" prompt that requires the user's fingerprint — automation can drive everything up to it.

**Licensing:** ASL is proprietary; the reference projects (android-code-studio, CodeAssist, Termux)
are **GPLv3 — never copy their code**, architecture reference only. Downloaded toolchain binaries run
as separate processes (mere aggregation), which is fine.

---

## 10. Deploy-discovered bug list (hard-won — keep for the next person)

None of these were catchable without real infrastructure; each broke every build until fixed:
`dnsPolicy: Default` (broke egress + Cilium toFQDNs — must be `ClusterFirst` + `ndots:1`); Spaces
missing from the egress allowlist (and Cilium `*` matches one label, so the bucket host needs `*.*.`);
baseline policy blocking the worker→control-plane event POST; RBAC missing `patch/update` on
jobs+secrets (fabric8 applies via PATCH); pull-secret named `asl-build-registry` (no `registry-`
prefix) + `doctl registry add` only patches the *default* SA; AGP needs a writable `$HOME/.android`
under readOnlyRootFilesystem; the artifact key wiped by an unconditional `SET` in `markFinished`
(fixed with COALESCE); a JDBC URL bug passing libpq userinfo into the driver; retired k8s 1.31 pin;
uid/gid-1000 collision in the Ubuntu base image; and the launcher-icon omission that made every
generated project fail aapt2. See git history in both repos for the fixes.
