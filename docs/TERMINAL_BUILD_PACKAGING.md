# Terminal runtime build and packaging

## Goals

Android IDE must not ship a universal APK containing every Termux bootstrap archive or an arbitrary collection of terminal packages. Each build has one explicit Android ABI and one matching bootstrap archive.

The repository therefore keeps large runtime archives out of Git. The archive is fetched during the build from the pinned Termux release and verified by SHA-256 before Gradle packages it.

## Integrated terminal components

The app vendors the pinned `terminal-emulator` and `terminal-view` source at
`v0.118.3`. Their JNI PTY library is built by the app module's `ndkBuild`
configuration and is constrained by the same `termuxAbi` value as the bootstrap.
The terminal libraries do not add the Termux app, `termux-shared`, or package
archives to the APK. Attribution and license texts are tracked in
`docs/TERMINAL_THIRD_PARTY_NOTICES.md`.

## ABI selection

The supported build values are:

- `arm64-v8a`
- `armeabi-v7a`
- `x86_64`
- `x86`

For a local build:

```bash
cd android-ide/android
./gradlew assembleDebug -PtermuxAbi=arm64-v8a
```

The Gradle property is passed to `scripts/fetch-termux-bootstrap.sh`. The explicit command-line property takes precedence over `TERMUX_ABI`, preventing a stale environment variable from selecting a bootstrap that does not match the APK configuration.

The Android module also applies the selected value to `defaultConfig.ndk.abiFilters` and enables ABI splits with `isUniversalApk = false`. The result is an architecture-specific APK artifact rather than a universal APK. The generated APK name and CI artifact name include the ABI.

To build another architecture, run the same command with another supported value. Do not copy multiple bootstrap archives into `android/assets/termux`; the fetch task removes non-selected generated archives.

## CI behavior

The debug and release workflows expose an ABI choice on manual dispatch. Push and pull-request debug builds use the documented `arm64-v8a` default unless the workflow is changed to a matrix intentionally.

A matrix is not used by default because it would create four separate APKs and download four large archives. If all device architectures are required, run four independent ABI jobs and publish four separately named artifacts:

```text
android-ide-debug-arm64-v8a
android-ide-debug-armeabi-v7a
android-ide-debug-x86_64
android-ide-debug-x86
```

Users install only the artifact matching the target device. A release page must not attach a generically named APK whose ABI is ambiguous.

## Package policy

The bootstrap archive provides the private runtime foundation and package manager. It does **not** silently install an arbitrary development environment.

The terminal surface exposes an explicit package field. A package is installed only when the user names it and activates **Install**. The app then reports the operation result and refreshes the installed package inventory. This keeps package downloads, storage use, and capabilities user-controlled.

The runtime package database remains app-private and separate from:

- user project files;
- project metadata;
- browser state;
- global preferences;
- editor recovery data.

The supported baseline package policy is deliberately explicit. Product defaults may recommend packages such as Git, OpenSSH, certificates, archive tools, process tools, Node.js, or Python, but recommendations must not be treated as automatic installation. A future first-run setup can present those packages with names, versions, download sizes, storage sizes, and a confirmation action.

## Runtime compatibility

At startup, the adapter selects the bootstrap asset using the device's first supported ABI. If the installed APK was built for a different ABI, initialization fails with an unavailable-runtime state rather than silently falling back to Android's shell or a different package set.

The runtime configures its own `HOME`, `PREFIX`, `PATH`, package database, and private directories. Project access is checked separately. A project can remain registered and editable when the runtime cannot establish a working directory for that provider.

## Required release checks

Before publishing an ABI artifact:

1. Confirm the chosen ABI is one of the four supported values.
2. Confirm the bootstrap archive checksum passes.
3. Confirm only the selected archive exists under generated `assets/termux`.
4. Confirm the output APK is the selected ABI artifact and no universal APK was uploaded.
5. Install the artifact on a matching device or emulator.
6. Verify shell commands, package installation, project working directories, Git, child-process termination, session switching, backgrounding, relaunch, and process-loss reporting.
7. Publish the corresponding Termux and installed-package license/source notices.

Build preparation and these checks are intentionally separate from Git history operations. No generated bootstrap archive should be committed.
