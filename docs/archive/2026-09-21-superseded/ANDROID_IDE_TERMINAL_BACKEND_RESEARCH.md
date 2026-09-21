# Android IDE — Concrete Terminal Backend Research

**Research date:** 2026-09-19  
**Scope:** Identify an obtainable, versioned terminal backend and package provider for Android IDE.  
**Status:** Standalone research report. It does not modify the product specification.

## 1. Required outcome

The terminal must be a real global command-line environment embedded inside Android IDE. It must support interactive shell sessions, file and directory operations, Git, package installation, Node.js and Python where installed, local servers, language servers, process management, and multiple sessions that remain accessible while the app navigates between domains.

The terminal UI is not the backend. A terminal emulator view only renders and transports PTY input/output. The backend must also provide the shell, filesystem environment, executable packages, package manager, process model, and session lifecycle.

## 2. Research conclusion

There is currently **no mature, production-ready, standalone Android terminal-backend SDK** that can be added to an ordinary third-party Android application as one Maven dependency and provide the complete Termux-level environment.

The research found three meaningful options:

| Option | Obtainable concrete source | Result |
|---|---|---|
| Termux upstream source plus official package/bootstrap distribution | `termux/termux-app` v0.118.3 and `termux/termux-packages` bootstrap `2026.09.13-r1+apt.android-7`, commit `e82d25d` | **Recommended foundation**, but requires integrating the upstream source modules and packaging them into Android IDE |
| `libtermux-android` standalone SDK | JitPack `com.github.libtermux:libtermux-android:1.0.0` | **Rejected as production foundation** because its own README says it is experimental and not production-ready |
| Android Virtualization Framework Linux terminal | AOSP AVF / Terminal App source | **Not available to ordinary third-party apps** because the relevant APIs are `@SystemApi` and require restricted `MANAGE_VIRTUAL_MACHINE` permission |

Therefore, the precise recommendation is not “use a Termux derivative” and not “find a magical terminal SDK.” The precise recommendation is:

> Integrate the official Termux application/runtime source at v0.118.3, use the official Termux package/bootstrap release `bootstrap-2026.09.13-r1+apt.android-7`, pin the package repository snapshot, and place an Android IDE-owned adapter around the backend.

This is a source integration with named upstream artifacts and versions. It is not a dependency on the installed Termux app, and it is not an undefined future fork.

## 3. Recommended provider: Termux upstream source

### 3.1 Exact source locations

Use these upstream locations:

1. **Termux application and terminal process/session source**  
   Repository: [https://github.com/termux/termux-app](https://github.com/termux/termux-app)  
   Baseline: **v0.118.3**  
   Release: 2025-05-22  
   Release page: [Termux v0.118.3](https://github.com/termux/termux-app/releases/tag/v0.118.3)

2. **Termux package build recipes and package distribution**  
   Repository: [https://github.com/termux/termux-packages](https://github.com/termux/termux-packages)

3. **Exact bootstrap/package baseline available on the research date**  
   Tag: **`bootstrap-2026.09.13-r1+apt.android-7`**  
   Commit: **`e82d25d519652c9c120543f77fd5f1eab0749d89`**  
   Release page: [Termux bootstrap 2026.09.13-r1](https://github.com/termux/termux-packages/releases/tag/bootstrap-2026.09.13-r1%2Bapt.android-7)

The bootstrap release contains the architecture-specific bootstrap archives used to initialize the Termux package environment. The package repository then supplies installable packages such as Git, Node.js, Python, OpenSSH, compilers, language tools, and other utilities according to the Termux package catalog.

### 3.2 What is available and what is not

The Termux application repository contains these relevant source areas:

- `terminal-emulator/` — terminal emulation and terminal behavior;
- `terminal-view/` — Android terminal view/UI components;
- `termux-shared/` — shared constants and utility code;
- `app/` — application process, session, service, and lifecycle integration.

The official repository explicitly states that it contains the Android application and terminal emulation, while installable packages are provided by the separate `termux-packages` repository. This distinction matters:

- the Termux source provides the Android-side terminal/session environment;
- the Termux package repository provides the Linux executables and package manager;
- the bootstrap archive initializes the environment on first run;
- Android IDE must connect the two through a controlled application-owned runtime directory.

Termux’s official JitPack publication does **not** turn the whole environment into a stable drop-in backend SDK. Published modules such as terminal-emulator and shared libraries are useful source components, but they do not by themselves deliver the package repository, bootstrap installation, process supervisor, app-owned session lifecycle, and complete runtime environment.

## 4. Required integration shape

Android IDE should integrate the provider in these layers:

```text
Android IDE terminal UI
        |
        v
Android IDE terminal-session adapter
        |
        +--> PTY and terminal-emulator integration
        +--> Termux process/session service integration
        +--> app-owned runtime directory
        +--> bootstrap installer
        +--> package repository configuration
        +--> foreground-service lifecycle
        +--> project/global working-directory policy
        |
        v
Termux runtime environment
        |
        +--> bash/sh
        +--> pkg / apt
        +--> git
        +--> node / npm
        +--> python / pip
        +--> language servers
        +--> local development servers
        +--> user-installed packages
```

The Android IDE adapter must be a deliberate boundary. The UI should not directly depend on internal Termux classes throughout the application. The adapter should expose stable Android IDE concepts:

- `createSession(name, workingDirectory)`;
- `listSessions()`;
- `writeInput(sessionId, bytes)`;
- `resize(sessionId, columns, rows)`;
- `observeOutput(sessionId)`;
- `renameSession(sessionId, name)`;
- `closeSession(sessionId)`;
- `closeAllSessions()`;
- `getAvailability(sessionId)`;
- `getEnvironmentInfo()`;
- `installPackage(packageName)`;
- `getPackageStatus(packageName)`;
- `startBackgroundProcess(command, workingDirectory)`;
- `stopProcess(processId)`;
- `restoreSessionRecords()`.

The UI may offer a project-originated “Open terminal here” action, but the session remains a global terminal session. The project path is only its initial working directory.

## 5. Package provider and package policy

### 5.1 Bootstrap

At first initialization, Android IDE should install an architecture-appropriate bootstrap archive from the exact pinned release:

```text
bootstrap-2026.09.13-r1+apt.android-7
commit e82d25d519652c9c120543f77fd5f1eab0749d89
```

The bootstrap must be stored in the app-private runtime layer, not in project metadata and not in the user’s project directory.

The installer must:

1. identify the device ABI;
2. select the matching bootstrap archive;
3. verify its checksum and expected release identity;
4. extract into an empty or controlled runtime directory;
5. initialize `pkg`/APT configuration;
6. verify shell execution;
7. verify Git availability or install it through the package provider;
8. record the installed runtime version and package snapshot;
9. report failures visibly in the terminal/runtime UI.

### 5.2 Default packages

The first-run runtime should include or install the minimum package set needed for the product:

- shell and core utilities;
- `git`;
- `openssh` or equivalent SSH client support;
- `ca-certificates`;
- `curl` or `wget`;
- `tar`, `unzip`, and archive utilities;
- `procps` or process inspection tools;
- `nodejs` and npm where supported by the selected architecture;
- Python and pip where supported;
- a terminal multiplexer only if needed by the chosen session model;
- language servers only when the user enables the corresponding language capability.

The package catalog must remain user-manageable. Android IDE must not silently install arbitrary large toolchains. It should show package name, version, download size, storage size, and result.

### 5.3 Package repository pinning

The package repository must not be an untracked moving target during initial development. Pin:

- the bootstrap release tag;
- the package recipe commit or release snapshot;
- architecture;
- repository URLs;
- signing-key material and verification rules;
- installed package manifest.

Updates can be offered later through an explicit runtime update flow. Updating the runtime must not silently alter a project’s dependency environment.

## 6. Why `libtermux-android` is not the recommendation

`libtermux-android` is the only discovered project that presents itself as a standalone Termux SDK with a direct Gradle dependency:

```kotlin
implementation("com.github.libtermux:libtermux-android:1.0.0")
implementation("com.github.libtermux:terminal-view:1.0.0")
```

Its repository is useful evidence because it exposes the shape a future SDK would need: bootstrap installation, package management, streaming output, a background service, JNI PTY support, and a terminal view.

However, the project’s own README explicitly states that it is **experimental, may contain bugs, and is not usable in production at the moment**. The repository was created in 2026 and has a small development history compared with Termux upstream. It therefore cannot be used as the foundation for a product that needs reliable terminal sessions, package installation, language servers, and local servers.

It may be used as a disposable proof-of-concept, not as the selected provider.

Source: [libtermux-android](https://github.com/libtermux/libtermux-android)

## 7. Why Android’s AVF Terminal is not the general provider

Android’s Linux development environment demonstrates a strong alternative architecture: a Debian-based Linux image runs in an Android Virtualization Framework VM, and the Terminal App connects to a terminal service in the VM over HTTP. The official AOSP documentation identifies `ttyd`, a guest agent, an OS image, and AVF as the relevant components. [AOSP AVF use cases](https://source.android.com/docs/core/virtualization/usecases)

However, the official AVF API documentation states that its Java APIs are `@SystemApi` and require the restricted `android.permission.MANAGE_VIRTUAL_MACHINE` permission. They are not available to ordinary third-party applications. [AVF API README](https://android.googlesource.com/platform/packages/modules/Virtualization/+/refs/tags/aml_net_351410000/libs/framework-virtualization/README.md)

Therefore AVF is not a viable general Android IDE dependency unless Android IDE becomes a preinstalled/privileged system application or is built for a controlled device image. It may be a future device-specific backend, but it cannot be the baseline provider for an ordinary distributable Android application.

## 8. Platform limitations that remain

Even with the Termux source and package provider integrated, Android remains the host operating system. The product must not promise:

- unrestricted `sudo` or root access;
- Docker or arbitrary Linux kernel features;
- guaranteed survival after force-stop;
- unlimited process counts;
- unrestricted external-storage access;
- every package on every Android ABI;
- desktop Linux compatibility;
- a permanently unkillable process.

Termux’s own documentation warns that Android 12 and later may kill phantom or excessive-CPU processes. A foreground service improves visibility and survival for user-visible work, but Android can still terminate or restrict processes. State persistence and recovery are therefore mandatory.

## 9. Final provider decision

### Selected provider

**Termux upstream integration:**

- Termux application source: **v0.118.3**;
- package/bootstrap source: `termux/termux-packages`;
- bootstrap baseline: **`bootstrap-2026.09.13-r1+apt.android-7`**;
- bootstrap commit: **`e82d25d519652c9c120543f77fd5f1eab0749d89`**;
- runtime: app-private, Android IDE-owned;
- package updates: explicit and versioned;
- terminal sessions: controlled through an Android IDE adapter;
- long-running work: foreground-service-backed where permitted;
- language servers, Git, Node/Python, and local servers: installed into and executed by the same runtime.

### Rejected as primary providers

- **`libtermux-android:1.0.0`:** concrete and easy to obtain, but explicitly experimental and not production-ready.
- **Android AVF Terminal:** technically powerful, but restricted to privileged/preinstalled applications and select devices.
- **Android shell/Toybox alone:** available on devices but does not provide the required package ecosystem or development environment.
- **Termux app IPC:** violates the requirement that Android IDE own and integrate the terminal rather than requiring a separate application.

This is the most defensible concrete provider choice available for the defined product. It identifies exactly where the source and package artifacts come from and exactly which versions are used. It also makes the remaining work explicit: Android IDE must own the integration adapter and runtime packaging rather than pretending that a stable one-line terminal SDK already exists.

## Sources

1. [Termux application repository](https://github.com/termux/termux-app)
2. [Termux v0.118.3 release](https://github.com/termux/termux-app/releases/tag/v0.118.3)
3. [Termux packages repository](https://github.com/termux/termux-packages)
4. [Termux bootstrap releases](https://github.com/termux/termux-packages/releases)
5. [Bootstrap 2026.09.13-r1+apt.android-7](https://github.com/termux/termux-packages/releases/tag/bootstrap-2026.09.13-r1%2Bapt.android-7)
6. [libtermux-android](https://github.com/libtermux/libtermux-android)
7. [AOSP AVF use cases](https://source.android.com/docs/core/virtualization/usecases)
8. [AOSP AVF API README](https://android.googlesource.com/platform/packages/modules/Virtualization/+/refs/tags/aml_net_351410000/libs/framework-virtualization/README.md)
