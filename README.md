<div align="center">

# Android Studio Lite

A full Android IDE that runs on your Android device — code editor, file tree, Git,
terminal, an AI agent, and real Gradle builds, all from your phone or tablet.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![minSdk](https://img.shields.io/badge/minSdk-24-3DDC84?logo=android&logoColor=white)](#)

</div>

<div align="center">



https://github.com/user-attachments/assets/713d568b-52a9-46c4-b49d-41494d6b2da5



</div>

## About

Android Studio Lite brings the core of a desktop IDE to Android itself. You get a
syntax-aware code editor with completion, a project file tree, integrated Git, an
embedded terminal, an AI coding agent, and one-tap Gradle builds that install straight
to the device. The idea is simple: you shouldn't need a laptop to write and ship an
Android app.

## Features

- **Code editor** with syntax highlighting, code completion, and a rendered Markdown preview.
- **Project file tree** with type-aware file icons and quick search.
- **Git built in** — stage and commit, push and pull, branches, tags, stashes, diffs, history, blame, and cloning.
- **Embedded terminal** running a real Linux shell.
- **AI coding agent** you can chat with to read and edit files across the project.
- **New projects from templates**, so you can start from a working app instead of an empty folder.
- **One-tap builds** that compile on the server and install the APK to your device.

## How builds work

Gradle is heavy, so builds don't run on your phone. Instead the app sends a task list
(for example `:app:assembleDebug`) to a remote build backend over HTTP/WebSocket, streams
the console output back live, and installs the resulting APK when it's done.

The backend is a sandboxed Kubernetes cluster where each build runs in an isolated pod on
a JDK 21 worker. The project's structure — its modules, variants, and tasks — normally
comes from a model task on the server, but there's also a local parser that reads your
`build.gradle` files directly, so the UI keeps working even when the server can't be
reached.

## Architecture

The project is split into 16 Gradle modules along clear layers:

```
app          entry point, navigation, dependency wiring
feature/*    editor, git, terminal, buildrun, projects, settings, onboarding
data/*       local, git (JGit), build (gradle reader), ai, templates
domain       models and use cases (pure Kotlin, no Android)
core/common  shared utilities and strings
designsystem reusable Compose UI and theming
build-logic  Gradle convention plugins
```

Each feature module owns its own screens and `ViewModel`. `domain` holds the
framework-free models and use cases, and the `data` modules provide the repositories
that back them.

## Stack

- Kotlin 2.2
- Jetpack Compose with Material 3 and Navigation Compose
- Koin for dependency injection
- Coroutines and Flow
- DataStore for preferences
- OkHttp and kotlinx.serialization for networking
- Eclipse JGit for version control
- AGP 9.2 with convention plugins and R8

## Build and run

```bash
git clone <repo-url>
cd AndroidStudioLite
./gradlew :app:assembleDebug
```

Then install the APK, or open the project in Android Studio and run the `app` module.

## Contributing

Contributions are welcome. If you'd like to help, open an issue to talk through the
change first, or send a pull request. Please keep the modules layered as described above,
run `./gradlew compileDebugKotlin` before pushing, and try to match the surrounding code
style.
