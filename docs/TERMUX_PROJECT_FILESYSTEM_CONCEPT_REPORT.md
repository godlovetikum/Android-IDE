# Termux-Backed Project Filesystem Concept Report

**Status:** Concept clarification for product and architecture discussion  
**Date:** 2026-09-30

## The central understanding

Android IDE should not treat the Termux-derived private filesystem only as a hidden place for bootstrap files, package databases, PTYs, logs, and caches.

It should expose that filesystem as a **first-class, user-selectable project-location provider**.

The user should be able to choose between:

1. a user-visible local location, such as shared device storage or an accessible SD card; and
2. an Android IDE private development filesystem backed by the integrated Termux runtime.

The second option is not an automatic migration target, a hidden cache, or merely a terminal working directory. When the user chooses it, it becomes the authoritative location of that project. The project remains a normal project in the product model, but its files live in a private POSIX-capable filesystem that Android gives to the application.

This is consistent with the approved architecture contracts, which already define the two supported project-location classes: a user-visible local location and an explicitly selected private development workspace. The important clarification is that the private development workspace must be treated as a real user-facing storage choice, not as an internal runtime detail.

## Why the Termux filesystem matters

Termux is not just a terminal screen. Its package environment is built to execute native Android-compatible programs inside the app’s private filesystem. Termux programs run on the Android host kernel rather than inside a general-purpose virtual machine or emulator. Its executables and libraries are compiled and arranged for the Termux rootfs and prefix layout. [1] [2]

That private filesystem is materially different from Android shared storage. Android’s shared and removable storage mounts commonly use emulated filesystems and `noexec` behavior. Termux documents that files in those locations can be unsuitable for executing programs and for special filesystem features. This explains why projects involving package managers, native binaries, executable scripts, symlinks, file watching, or local development servers can behave differently when placed in shared storage. [1]

The benefit is therefore not that Termux grants a new general Android permission. The benefit is that the project is placed on a filesystem whose ownership, permissions, executable behavior, symlink support, and process working-directory semantics are compatible with the Termux package environment.

## The three-layer model

The product should distinguish three related but different things.

### 1. Runtime installation area

This is the application-managed Termux installation:

- bootstrap files;
- `HOME` and `PREFIX` infrastructure;
- shell and dynamic libraries;
- package database;
- installed packages;
- runtime configuration;
- PTYs, logs, caches, and process/session state.

This area is controlled by Android IDE. It is not automatically presented as a project folder, and user projects should not be mixed into the runtime’s package directories.

### 2. Private development workspace

This is a user-selectable project filesystem exposed by the Termux-backed provider. It may be located under an Android IDE-managed private root, but it is conceptually a **project storage provider**, not just runtime state.

When selected for a project:

- the project files in that workspace are authoritative;
- the editor reads and writes them there;
- the terminal starts in that project directory;
- Git operates on that same project directory;
- language servers use that same directory and dependencies;
- previews and local servers use that same project;
- project details show that the project is in the private development workspace;
- export, copy, move, backup, and sharing are explicit user operations.

The workspace must not be silently substituted for a user-selected external location. It is an additional option that gives the user stronger development behavior when they want it.

### 3. User-visible local providers

These include shared device storage, removable SD cards, and other local providers accessible through Android’s storage APIs.

They remain important because users may need to:

- see projects in ordinary file managers;
- share projects with other applications;
- keep projects on removable media;
- preserve files independently of Android IDE;
- use an existing folder without importing it into a private area.

These locations are not automatically equivalent to the private workspace. Android IDE must inspect their capabilities and report limitations honestly.

## User choice is the ownership boundary

The user chooses the project location during creation, import, copy, relocation, or another explicit workflow.

If the user chooses the private development workspace, Android IDE may create the project there. If the user chooses shared storage or an SD card, Android IDE must operate on that selected location or reject it with a clear explanation. It must not silently:

- migrate the project into private storage;
- create a second private copy and call it the active project;
- redirect the terminal to another directory;
- replace a SAF location with the terminal home directory;
- move files because a package or command failed on the selected provider.

If a selected provider cannot support a required capability, the product should offer an explicit alternative such as **Copy to Private Development Workspace**. That is a new, user-approved project operation, not a recovery trick hidden inside terminal startup.

## Termux shared storage is an integration, not the private workspace

Termux’s shared-storage setup makes user-visible Android storage reachable from the Termux environment through configured links. That is useful, but it does not turn shared storage into the same filesystem as the Termux private area. The user-visible location remains subject to Android storage-provider behavior and execution restrictions.

The practical model is therefore:

- Termux private filesystem: strong development workspace;
- shared storage or SAF: user-visible project storage with provider-specific capabilities;
- bridge or adapter: the controlled connection between the Android project model and the selected location.

A `content://` URI is an Android document identity, not a POSIX working directory. If a provider cannot expose the operations required by a terminal or editor, the product must either use a defined bridge or require an explicit copy/import into a compatible local location.

## Why “in-memory workspace” is the wrong product concept

An in-memory representation can be useful for editor buffers, recovery content, or temporary session state. It cannot be the authoritative project filesystem for this product.

The terminal, Git, package managers, native tools, language servers, file watchers, and local servers all need durable files and ordinary filesystem paths. A memory-only project would also make persistence, process recreation, relaunch, sharing, backup, and cross-domain consistency difficult or impossible.

The right distinction is not “memory versus storage.” It is:

- **runtime-private storage** for Android IDE’s managed runtime state;
- **private development storage** for user projects that explicitly choose it;
- **user-visible storage** for projects that must remain accessible outside the app;
- **in-memory state** only for temporary UI/editor/session concerns.

## What this changes in the product definition

The product definition should describe the private development workspace as a complete storage choice with a visible identity and lifecycle, not as a technical fallback.

A project-location choice should communicate:

- where the project will live;
- whether other Android applications can browse it directly;
- whether execution-sensitive development behavior is supported;
- how the project can later be exported, copied, moved, or backed up;
- what happens if the app is uninstalled or its private data is cleared;
- whether the selected location is currently available.

This matters because Android app-specific internal files are private to the application and are removed on uninstall. Android’s documentation also distinguishes app-specific storage from shared storage and warns that app-specific external files are not independent user storage. [3] [4]

Therefore, a private development workspace should be powerful but should never be presented as automatically safer, more portable, or more permanent than user-visible storage. The product must make the tradeoff visible.

## What this changes in the architecture

The logical `ProjectLocation` contract should have two real implementations, not one real implementation plus an implicit fallback:

- `UserVisibleLocalProjectLocation`
- `PrivateDevelopmentWorkspaceProjectLocation`

Both should satisfy the same logical project operations where supported. Their capability differences should be explicit and observable.

The Termux runtime adapter should own runtime installation and process execution. The private workspace adapter should own project roots inside the Termux-backed filesystem. The project storage adapter should route editor, project-management, Git, language-server, and preview operations to the selected authoritative location.

The key invariant is:

> The selected project location is the same location used by the editor, terminal, Git, language servers, previews, and project-management operations.

That invariant is stronger than merely making the shell start successfully. It is what gives the user a genuinely useful development environment.

## Conclusion

Yes, the understanding is correct.

The permanent answer is not simply “repair the terminal’s permissions.” The stronger product is an Android IDE with **two deliberate project storage choices**:

- ordinary user-visible local storage for portability and external access; and
- a Termux-backed private development filesystem for reliable execution, packages, symlinks, native tooling, and local development workflows.

The private filesystem should be installed and maintained by Android IDE, but projects should enter it only through an explicit user choice. It should be treated as a first-class project provider with visible ownership, capability reporting, and explicit export/relocation paths.

The previous runtime-focused interpretation was incomplete because it treated the private filesystem mainly as infrastructure. Your clarification is that it is infrastructure **and**, when selected by the user, a legitimate authoritative project filesystem.

## References

[1]: https://github.com/termux/termux-packages/wiki/Termux-execution-environment "Termux execution environment"

[2]: https://github.com/termux/termux-packages/wiki/For-maintainers "Termux package and bootstrap maintenance"

[3]: https://developer.android.com/training/data-storage "Android data and file storage overview"

[4]: https://developer.android.com/training/data-storage/app-specific "Android app-specific storage"

[5]: https://developer.android.com/about/versions/11/privacy/storage "Android 11 storage changes"


## 2026-09-30 correction: provider, not private workspace

The Android IDE filesystem must be described as a normal persistent storage provider, not as a private project workspace. The user-files root is exposed through Android's `DocumentsProvider`/SAF model and is selectable alongside shared storage, removable media, and other providers. Other applications may browse or mutate it through user-granted SAF URI permissions.

The Termux runtime/package root remains separate and protected. It contains the bootstrap, package database, shell libraries, PTYs, processes, and runtime state. It is not a general project root and is not exposed through the document provider.

The filesystem provider's value is capability: the user-files root is backed by a filesystem suitable for executable files, symlinks, packages, language servers, and development servers. In-memory buffers remain transient application state and are not a storage model.

The implementation direction is therefore:

- `AndroidIdeDocumentsProvider` exposes only the durable user-files root;
- project URIs remain SAF-authoritative for editor and file mutations;
- the terminal and language servers resolve Android IDE provider URIs to the same underlying directory and use it as their working directory;
- the package manager is the only package-management interface;
- first-launch runtime setup installs a baseline toolchain through Termux commands, including curl, Git, Node.js/npm, Python, zip/unzip, live-server, and initial LSP packages;
- Git UI/acquisition remains deferred; Git itself remains available as a terminal package;
- Monaco language intelligence is connected conceptually through stdio JSON-RPC servers launched from the same runtime environment and project directory.

Android's official SAF references:

- https://developer.android.com/guide/topics/providers/document-provider
- https://developer.android.com/guide/topics/providers/create-document-provider
- https://developer.android.com/reference/android/provider/DocumentsProvider
