# Android IDE — Provider and Dependency Research Report

**Status:** Approved provider and dependency research baseline.
**Research date:** 2026-09-20  
**Purpose:** Record the researched provider choices that support the Android IDE product definition.  
**Document responsibility:** This report identifies what the selected technologies provide, how their capabilities relate to the defined product, and the facts that give confidence in using them. It does not redefine the product, describe the current codebase, prescribe a build order, or act as an architecture specification.

## 1. Research conclusion

The Android IDE product should use a group of specialized providers. No single library supplies the complete product because the product combines project storage, code editing, language intelligence, a Linux-style terminal, browser sessions, previews, developer tools, Git, credentials, and Android lifecycle behavior.

The selected provider direction is:

| Product capability | Selected provider or technology | What it provides |
|---|---|---|
| Project locations and user-selected folders | Android Storage Access Framework | System document and folder selection with persistent provider access |
| Code editor | Monaco Editor 0.55.1 | Browser-based code editor models, commands, themes, language hooks, and editor customization |
| Language intelligence | Language Server Protocol with `monaco-languageclient` 10.7.0 and `vscode-ws-jsonrpc` 3.5.0 | Standard communication between Monaco and language-server processes |
| Terminal runtime | Termux application source v0.118.3 | Android terminal/session implementation and runtime integration source |
| Terminal packages | Termux bootstrap `2026.09.13-r1+apt.android-7`, commit `e82d25d` | Initial package environment and package-management foundation |
| Browser | Mozilla GeckoView Stable with Mozilla Android Components | Embeddable web engine and browser product components |
| In-app web developer tools | Eruda 3.4.0, integrated through GeckoView WebExtensions/content scripts | Console, elements, network, resources, sources, snippets, and related page inspection |
| Git operations | Canonical Git executable supplied by the terminal runtime | Repository state, commands, remotes, branches, history, and synchronization |
| Secret protection | Android Keystore with app-private encrypted storage | Protection for encryption keys and application-owned credentials |
| Long-running visible work | Android foreground services with durable recovery state | User-visible background execution where Android permits it |
| Extensions | No provider selected | Explicit placeholder in the product definition |

### Accessibility status of the selected providers

The providers listed above are not merely factual technologies that exist somewhere. The named source repositories, package registries, release pages, and platform APIs were checked for obtainability on 2026-09-20.

| Provider | Access status | Form in which it is obtainable |
|---|---|---|
| Monaco Editor 0.55.1 | **Directly obtainable** | npm package and public source repository |
| `monaco-languageclient` 10.7.0 | **Directly obtainable** | npm package and public source repository |
| `vscode-ws-jsonrpc` 3.5.0 | **Directly obtainable** | npm package and public source repository |
| Termux application v0.118.3 | **Directly obtainable** | public GitHub source and release tag; source integration is required rather than a complete drop-in SDK |
| Termux bootstrap `2026.09.13-r1+apt.android-7` | **Directly obtainable** | public GitHub release assets and exact commit |
| GeckoView | **Directly obtainable** | Mozilla Maven repository; use the Stable channel |
| Mozilla Android Components | **Directly obtainable** | Mozilla Maven repository and public source repositories |
| Eruda 3.4.0 | **Directly obtainable** | npm package, unpkg distribution, and public source repository |
| Git | **Obtainable through the selected runtime** | Termux package repository; it is not a separate IDE-only provider in this selection |
| Android Storage Access Framework | **Built into Android** | Android platform API; no third-party download is required |
| Android Keystore | **Built into Android** | Android platform security API; no third-party download is required |
| Extensions | **Not selected** | No provider is being claimed or recommended |

The distinction is important. “Directly obtainable” means the named release or package can be retrieved from the stated public distribution channel. It does not mean that the provider is a complete Android IDE feature without integration work. Termux is directly obtainable as source and bootstrap artifacts, but it requires source integration and packaging. GeckoView is directly obtainable as a Maven dependency, but its exact Stable version must be pinned together with the matching Android Components release. Android platform services are available through the device SDK rather than through an external package.

These selections are not claims that the providers solve every product requirement automatically. They are the most suitable researched foundations for the specified capabilities. Android IDE must supply the product surfaces, user workflows, mobile interaction patterns, and coordination around them.

## 2. Project locations and storage access

### Selected technology: Android Storage Access Framework

Android’s Storage Access Framework (SAF) provides a standard system interface for choosing files and directories from local, cloud, removable, and other registered document providers. The relevant actions include `ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`, and `ACTION_OPEN_DOCUMENT_TREE`. `ACTION_OPEN_DOCUMENT_TREE` is the appropriate foundation for selecting an existing project directory or a project destination.

SAF can grant long-term access to a selected document tree. A provider exposes documents through stable document identifiers and reports supported operations such as writing, deleting, and creating children. This is important because a project location may not be an ordinary filesystem path. It may be a document-tree URI backed by a local provider, cloud storage, removable storage, or another document service.

The selected project-storage approach should therefore treat the provider URI and document identity as authoritative. A human-readable path can be displayed where available, but it must not be assumed to exist for every provider.

SAF supports the project definition’s acquisition and file-management features:

- selecting an existing project folder;
- selecting an import source;
- selecting a destination for a new project;
- creating, reading, updating, renaming, moving, copying, and deleting documents when the provider exposes those capabilities;
- exporting a project or folder through a user-selected destination;
- retaining access across application restarts when the user grants persistable access.

The application must inspect the selected destination before creating a file or folder. SAF’s provider model makes this preflight necessary because providers can differ in supported flags and behavior. The application should verify the returned document identity after a mutation rather than assuming that a requested name was created exactly as entered.

SAF is therefore the Android acquisition and permission mechanism, not automatically the terminal’s POSIX filesystem. Android IDE may register a local project for in-place editing only when the integrated editor and Termux-based runtime can operate on that same user-visible location for the required operations. A document-tree URI that can be selected but cannot support the runtime’s required reads, writes, renames, deletions, execution, or file-change observation is not a supported live project location and must be rejected rather than copied silently into app-private storage.

Cloud-backed or remote document providers are not supported as live editable project locations. Their contents must be imported or downloaded into a user-selected local location first. That local copy is then the authoritative project source for Monaco, the Termux runtime, Git, language servers, previews, and project metadata. Exporting or uploading the local project back to the cloud is an explicit user operation; automatic two-way synchronization is outside the selected design.

**Source:** Android’s official [Storage Access Framework documentation](https://developer.android.com/guide/topics/providers/document-provider) [1]

## 3. Code editor

### Selected technology: Monaco Editor 0.55.1

Monaco Editor is a browser-based code editor component derived from the editor technology used by Visual Studio Code. It provides the core editing surface and public APIs for:

- text models;
- document URIs;
- multiple editor instances;
- syntax highlighting;
- language configuration;
- themes;
- editor options;
- commands and actions;
- keybindings;
- decorations and markers;
- search and replacement within a document;
- completion, hover, definition, reference, formatting, and other language-provider hooks;
- view state and cursor-related behavior;
- programmatic document updates.

These capabilities support the product definition’s editor requirements. Monaco can be surrounded by native Android controls for the file path, tabs, search and replace, save, preview, context menus, editor preferences, keyboard shortcuts, symbol shortcuts, and mobile feedback.

Monaco should be treated as the editor surface, not as the complete editor product. Android IDE must provide the surrounding file tree, project identity, tab-state rules, recovery behavior, save workflow, file mutation actions, Android keyboard controls, and mobile interaction design.

### Mobile customization

Monaco exposes editor options and commands rather than requiring the application to reproduce its internal editor implementation. This makes it possible to configure:

- line numbers;
- word wrapping;
- themes;
- minimap visibility;
- autocomplete behavior;
- cursor and selection behavior;
- font and layout settings;
- read-only or editable states;
- keyboard commands and custom actions.

Android IDE can add touch-sized native controls around the editor and map press-and-hold gestures to repeated editor commands. The product’s symbol row and keyboard-control row can invoke Monaco commands for indentation, cursor movement, selection, undo, redo, copy, paste, and related operations.

Monaco is designed primarily for browser and desktop-style editing. Its APIs make mobile customization possible, but reliable Android behavior still depends on testing text input, selection handles, the software keyboard, large files, memory pressure, and touch gestures on real devices.

### Editor and file ownership

Monaco models should identify documents using stable project-relative URIs. The document contents remain owned by the project storage provider. Monaco holds an editing representation and view state; it must not silently become a second permanent copy of the project.

This supports the product definition’s tab rules:

- temporary tabs can be represented as open models without persisted edits;
- permanent tabs can persist their identity and view state;
- dirty tabs can retain unsaved recovery content separately from the project file;
- pinned tabs can remain open until explicitly closed;
- recovered content can be offered to the user without silently overwriting the project file.

**Source:** [Monaco Editor repository and API](https://github.com/microsoft/monaco-editor) [2]

## 4. Language intelligence

### Selected technology: Language Server Protocol

The Language Server Protocol (LSP) standardizes communication between an editor and a language server. It exists so that language intelligence can be implemented once in a language server and reused by multiple editors and tools.

The protocol supports the product’s language-intelligence requirements, including:

- completion;
- hover documentation;
- diagnostics;
- go to definition;
- go to declaration;
- find references;
- document symbols;
- workspace symbols;
- rename;
- formatting;
- code actions;
- code lenses where supported;
- document synchronization;
- workspace initialization and shutdown.

The current LSP specification is version 3.18. LSP messages use JSON-RPC, which can be carried over standard input/output, sockets, or another controlled transport.

### Selected client compatibility baseline

The researched released compatibility baseline is:

```text
monaco-editor:       0.55.1
monaco-languageclient: 10.7.0
vscode-ws-jsonrpc:   3.5.0
```

This combination gives Monaco a client-side connection to language servers. It does not include the language servers themselves. Each language requires an appropriate language-server executable and its runtime dependencies.

A language server must use the same project files, working directory, dependencies, and environment that the terminal and Git workflows use. Installing a dependency through the terminal may therefore require the language server to refresh or restart before its completion and diagnostics become accurate.

The language-server process should be started, stopped, and monitored by the selected terminal/runtime provider. The editor client should receive explicit unavailable or initialization-failed states rather than displaying stale intelligence as if it were current.

**Sources:** [Language Server Protocol](https://microsoft.github.io/language-server-protocol/) [3]; [Monaco Language Client compatibility history](https://github.com/TypeFox/monaco-languageclient/blob/main/docs/versions-and-history.md) [4]

## 5. Terminal, runtime, packages, and background processes

### Selected runtime foundation: Termux upstream source

The selected terminal foundation is the official Termux application source at **v0.118.3**. Termux is an Android terminal application and Linux environment. Its source contains Android-side terminal and session components, including the terminal emulator, terminal view, shared utilities, and application process integration.

The source is not the same thing as an installed Termux application. The intended product direction is to incorporate the relevant upstream source into Android IDE so that Android IDE controls the user experience and the runtime lifecycle.

### Selected package foundation

The initial package environment should use the official Termux bootstrap release:

```text
Release: bootstrap-2026.09.13-r1+apt.android-7
Commit:  e82d25d519652c9c120543f77fd5f1eab0749d89
```

The bootstrap initializes the architecture-specific Termux package environment. The separate `termux-packages` project supplies installable packages and package metadata. This is the basis for obtaining development tools such as:

- shell utilities;
- Git;
- OpenSSH;
- certificates;
- `curl` or `wget`;
- archive tools;
- process tools;
- Node.js and npm where supported;
- Python and pip where supported;
- language servers and their dependencies;
- local development servers;
- compilers and other user-selected packages.

The package environment is therefore capable of supporting the terminal requirements in the product definition, subject to Android ABI, device, package, storage, and process limitations.

### Terminal capability model

The runtime provides the pieces that a terminal UI alone cannot provide:

- interactive shell processes;
- PTY input and output;
- session working directories;
- executable programs;
- package installation;
- environment variables and PATH;
- child processes;
- process output and exit status;
- user-installed development tools;
- local servers and language servers.

This supports the definition of a global terminal. A session may start in a project directory, but it is not project-owned and is not permanently restricted to the project root.

The terminal provider should support session names, switching between sessions, explicit closing, closing all sessions, session availability, and opening a new session from a project or folder. Closing a session terminates its backend session and its child processes.

### Runtime state and package state

The runtime environment belongs to app-private runtime storage. It must remain separate from project metadata, project files, browser profiles, and global application preferences.

Package operations should expose the package name, version, installation result, and errors. The runtime should retain enough package and environment information to explain why a command or language server is unavailable.

### Termux private filesystem: what is and is not provided

Termux’s development-friendly filesystem is not a separate Android storage-provider SDK. It is the application-private data directory assigned by Android to the installed package, populated with the Termux rootfs layout, including a Home directory and a Prefix directory containing shell utilities, shared libraries, the package database, and installed executables. Because this area normally resides on an internal Linux filesystem such as ext4 or F2FS and is controlled by the application UID, it provides materially better Unix/POSIX behavior than ordinary shared or emulated external storage: executable files, symlinks, package-managed paths, permissions, native libraries, and process working directories can be managed within Android’s sandbox. It does not remove Android’s sandbox, SELinux restrictions, ABI constraints, or the lack of root access.

The official Termux project publishes reusable `termux-shared`, `terminal-view`, and `terminal-emulator` libraries for third-party applications under their applicable licenses. `terminal-view` and its emulator dependency provide the terminal UI and terminal-emulation layer; they do not, by themselves, provide a complete private Linux filesystem or package distribution. Android IDE must integrate the runtime components, install the ABI-specific bootstrap, establish package-name-specific private paths, and manage the resulting lifecycle. A fork or integrated build with a different package name also requires corresponding bootstrap and package-path changes.

Android IDE should provide two user-selectable project-location classes:

1. **Private development workspace:** an Android IDE-managed directory inside the application’s private runtime filesystem. This is the recommended location for projects that need reliable package installation, native binaries, symlinks, executable scripts, language servers, file watching, and local development servers. The project remains logically user-owned, but the location is not ordinarily browsable by unrelated Android applications; Android IDE must provide explicit export, sharing, backup, and relocation workflows.
2. **User-visible local location:** an accessible device-storage, removable-storage, or other local provider location. This keeps files directly visible outside Android IDE, but it may impose Android storage, permission, execution, symlink, file-watching, or provider-specific limitations. Android IDE must validate the required operations before registering the location and must report or reject unsupported development behavior rather than implying full Termux semantics.

The user may create, import, move, or copy a project between these location classes. Moving or copying into the private development workspace is explicit and must not happen silently. Moving or copying out of it uses an explicit export or relocation workflow because unrelated applications cannot generally access the private directory by ordinary filesystem path. The selected location must remain visible in project details, and project size calculations must account for it.

The technical conclusion is that the Termux private filesystem is necessary as a supported **development-workspace option**, especially for package installation and execution-sensitive projects, but it is not a universal replacement for user-visible storage and it is not obtained as a single filesystem library. Android IDE needs the Termux runtime integration plus a workspace manager that exposes the choice and provides explicit import, export, copy, move, and backup operations.

### Android lifecycle facts

Android foreground services are designed for user-visible work that continues while the user is not directly interacting with the application. They show a notification and are subject to Android’s service-start, permission, type, timeout, and user-stop rules.

A foreground service improves the runtime’s ability to continue visible terminal and server work, but it does not create an unkillable process. The product must preserve session and process records, verify availability when reopened, and distinguish a recoverable record from a process that is still alive.

**Sources:** [Termux application v0.118.3](https://github.com/termux/termux-app/releases/tag/v0.118.3) [5]; [Termux package releases](https://github.com/termux/termux-packages/releases) [6]; [Termux bootstrap release](https://github.com/termux/termux-packages/releases/tag/bootstrap-2026.09.13-r1%2Bapt.android-7) [7]; [Android foreground services](https://developer.android.com/develop/background-work/services/foreground-services) [8]

## 6. Browser and web previews

### Selected browser foundation: GeckoView Stable

GeckoView is Mozilla’s embeddable Gecko web engine for Android applications. It provides web-page loading, navigation, JavaScript execution, web storage, cookies, permissions, downloads, and other browser-engine capabilities through `GeckoSession` and related APIs.

GeckoView is an engine rather than a finished browser application. Mozilla Android Components supplies reusable browser components for assembling a browser product, including browser engine integration, toolbar behavior, tabs and tab trays, menus, search, error pages, downloads, and browser state.

The selected browser direction supports the product definition’s requirement for a normal internet browser inside Android IDE:

- URL and search input;
- navigation history;
- multiple tabs;
- browser session restoration;
- cookies and site storage;
- downloads;
- browser permissions;
- browser settings;
- local project previews;
- browser-specific controls and customization.

The browser should own browser tabs, browser sessions, cookies, cache, local storage, downloads, passwords, history, and bookmarks where those features are enabled. These are browser-domain data, not project metadata or global Git credentials.

### Preview behavior

A project preview can open an HTML, Markdown, image, PDF, video, or web-based resource in the browser when the resource is suitable for browser presentation. A local development server started by the terminal runtime can be opened as a normal browser tab.

The preview does not require a separate preview application. It uses the same browser tab and navigation model as ordinary browsing. If the local server stops or becomes unavailable, the browser must show the resulting error rather than treating the preview as active.

### Version selection

GeckoView and Android Components should be selected from the same Mozilla Stable release family and pinned together during implementation. The exact artifact versions must be recorded at dependency-selection time because Mozilla’s release channel advances over time. Stable, Beta, and Nightly components must not be mixed casually.

**Sources:** [GeckoView architecture](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/contributor/geckoview-architecture.html) [9]; [Mozilla Android Components](https://mozac.org/components/) [10]

## 7. Browser developer tools

### Selected page-inspection provider: Eruda 3.4.0

Eruda is a mobile browser developer-tools library designed to run inside a web page. Its documented panels include:

- Console;
- Elements;
- Network;
- Resources;
- Info;
- Sources;
- Snippets.

These capabilities support the product requirement for a browser console or developer tool that is accessible while browsing or previewing a web resource.

Eruda can inspect page-level information such as JavaScript output, DOM structure, network activity, page resources, and page storage that the web page is allowed to access. It does not automatically provide every native browser capability. Native browser controls remain responsible for tab management, browser permissions, viewport selection, cache controls, downloads, and browser profile data.

### GeckoView integration

GeckoView supports embedders registering and communicating with WebExtensions. A controlled extension/content-script integration can inject the developer-tools script into the current page when the user opens developer tools or when a local preview is active.

This gives the Browser domain a separation between:

- native browser controls and browser state;
- in-page inspection and console behavior.

Developer tools should be disabled or unavailable when the current page cannot safely support the selected injection method. The browser must not claim to provide native Chrome DevTools Protocol coverage merely because it provides an in-page console.

**Sources:** [Eruda 3.4.0 documentation](https://app.unpkg.com/eruda@3.4.0/files/README.md) [11]; [GeckoView WebExtensions](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html) [12]

## 8. Git integration

### Selected Git provider: canonical Git executable in the terminal runtime

Git should be available as a package in the selected Termux runtime. The canonical Git executable should remain the source of truth for repository state because it provides the broadest compatibility with repositories, configuration, remotes, credential helpers, hooks, branches, history, and user workflows.

The Git screens are a user-facing way to operate on the same Git package and repository that the terminal uses. The product therefore supports:

- repository status;
- changed-file lists;
- staged and unstaged changes;
- diffs;
- stage and unstage;
- commit creation;
- commit history;
- branch creation, checkout, and switching;
- tags and remotes;
- fetch, pull, and push;
- restore, reset, revert, merge, rebase, and conflict workflows;
- repository and global Git configuration.

A Git screen must refresh repository state after terminal commands. Terminal Git commands must see changes made through the Git screen. The UI should parse command output into structured information, but it must not establish a competing repository state.

### Git libraries and scope

JGit is a pure-Java Git implementation that can be useful for selected in-process operations. Its documented scope and compatibility gaps mean it should not silently replace the canonical Git executable for every operation. A single repository workflow should have a clear source of truth.

The selected direction therefore uses the runtime Git executable for broad repository compatibility. Other Git libraries may be evaluated for a specific operation only when their behavior is verified against the product’s repository requirements.

**Source:** [Eclipse JGit](https://github.com/eclipse-jgit/jgit) [13]

## 9. Credentials and security

### Selected secret-protection technology: Android Keystore

Android Keystore stores cryptographic keys in a way that makes key material more difficult to extract. Key material remains non-exportable through normal application operations. Keystore can also restrict key use by algorithm, operation, validity period, and recent user authentication. Some devices can bind keys to secure hardware such as a Trusted Execution Environment or StrongBox.

The application should use a Keystore-protected vault key to encrypt application-owned credential records in app-private storage. The encrypted record may contain:

- provider type;
- account or display name;
- username or email where relevant;
- token or private-key ciphertext;
- remote or host scope;
- creation and last-use metadata.

The actual token or private key must not be placed in project metadata, source files, terminal history, Git configuration files, browser storage, or ordinary logs.

The Git credential workflow can provide a short-lived credential to the Git process through an askpass or credential-helper mechanism. The exact secret should not be printed into the terminal output.

Browser credentials are browser-domain data. They should not automatically be reused as Git credentials. SSH keys, HTTPS access tokens, Git provider accounts, browser logins, and application secrets require separate ownership and disclosure rules.

**Source:** [Android Keystore system](https://developer.android.com/privacy-and-security/keystore) [14]

## 10. Extensions

Extensions remain an explicit placeholder in the product definition. No extension provider is selected.

This means the current provider research does not assume:

- VS Code Marketplace compatibility;
- arbitrary extension package installation;
- dynamic native-code loading;
- unrestricted JavaScript plugin execution;
- extension permissions;
- extension storage;
- extension updates or revocation.

The placeholder preserves the product domain without allowing an unresearched extension architecture to influence the editor, terminal, browser, or security design prematurely.

## 11. Provider facts that affect more than one domain

The selected technologies have several important relationships.

### Editor and language intelligence

Monaco supplies the editor surface. LSP supplies the communication protocol. A language server supplies language-specific intelligence. The terminal/runtime supplies the process and dependencies required by that language server. None of these three roles should be confused with the others.

### Terminal and browser preview

The terminal/runtime can start a local server. The browser can open the server URL as a normal browser tab. The browser does not need to own the server process, and the terminal does not need to become a browser.

### Terminal and Git

The runtime supplies Git. The Git domain presents structured operations over the same Git installation and repository. This is what prevents terminal Git state and Git-screen state from diverging.

### Project storage and editor

SAF or the selected storage provider owns the project files. Monaco holds the active editing representation. Save, external-change detection, recovery, and file-tree refresh must preserve that distinction.

### Project storage and terminal

A terminal started from a project receives a project directory as its initial location. It remains a global terminal session. If the selected document provider cannot expose ordinary POSIX paths, the runtime must provide a defined way to work with the project rather than silently creating an untracked second copy.

### Security and all domains

Credentials must be stored according to their owner. Git credentials belong to Git security. Browser passwords and cookies belong to the browser profile. Project metadata must not become a general secret store. Android Keystore protects application encryption keys; it does not remove the need for access control, redaction, safe logging, and explicit user consent.

## 12. Confidence and remaining provider-specific work

The researched provider direction is sufficiently concrete to support product planning:

- Monaco has the editor APIs required for the specified editing surface.
- LSP has the protocol required for language intelligence, with a released Monaco client compatibility baseline.
- Termux provides the most concrete researched foundation for an embedded Android Linux-style runtime and package environment.
- GeckoView and Android Components provide the foundation for a normal embedded browser rather than only a web preview.
- Eruda provides a practical page-level mobile developer-tools surface.
- Canonical Git provides broad repository compatibility through the same runtime used by the terminal.
- SAF provides the Android-native project-location model.
- Keystore provides the platform mechanism for protecting application-owned encryption keys.

The remaining work is provider-specific verification, not a reason to change the product definition:

- confirm the exact GeckoView and Android Components Stable artifact pair when dependencies are selected;
- verify the selected Termux source modules and bootstrap packaging for the supported Android ABIs;
- verify Monaco input, selection, performance, and recovery behavior on target Android devices;
- select and test language servers individually because LSP does not provide the servers themselves;
- verify which SAF providers support each required file mutation;
- verify the selected Git credential-helper mechanism without exposing secrets;
- verify the Eruda injection path for GeckoView pages and local previews.

## References

[1]: https://developer.android.com/guide/topics/providers/document-provider "Android Storage Access Framework"
[2]: https://github.com/microsoft/monaco-editor "Monaco Editor repository"
[3]: https://microsoft.github.io/language-server-protocol/ "Language Server Protocol"
[4]: https://github.com/TypeFox/monaco-languageclient/blob/main/docs/versions-and-history.md "Monaco Language Client compatibility history"
[5]: https://github.com/termux/termux-app/releases/tag/v0.118.3 "Termux application v0.118.3"
[6]: https://github.com/termux/termux-packages/releases "Termux package releases"
[7]: https://github.com/termux/termux-packages/releases/tag/bootstrap-2026.09.13-r1%2Bapt.android-7 "Termux bootstrap 2026.09.13-r1+apt.android-7"
[8]: https://developer.android.com/develop/background-work/services/foreground-services "Android foreground services"
[9]: https://firefox-source-docs.mozilla.org/mobile/android/geckoview/contributor/geckoview-architecture.html "GeckoView architecture"
[10]: https://mozac.org/components/ "Mozilla Android Components"
[11]: https://app.unpkg.com/eruda@3.4.0/files/README.md "Eruda 3.4.0 documentation"
[12]: https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html "GeckoView WebExtensions"
[13]: https://github.com/eclipse-jgit/jgit "Eclipse JGit"
[14]: https://developer.android.com/privacy-and-security/keystore "Android Keystore system"
[15]: https://github.com/termux/termux-app/blob/master/LICENSE.md "Termux application license"
[16]: https://github.com/mozilla-mobile/android-components/blob/main/LICENSE "Mozilla Android Components license"
[17]: https://github.com/liriliri/eruda/blob/master/LICENSE "Eruda license"
[18]: https://github.com/git/git/blob/master/COPYING "Git license"
[19]: https://github.com/microsoft/monaco-editor/blob/main/LICENSE "Monaco Editor license"
[20]: https://source.android.com/license "Android platform license information"

### Licensing and acquisition routes

The selected providers are free to obtain and are open-source or platform-provided, but “free” does not mean that every component has the same redistribution obligations. A release build must preserve notices, license texts, source-availability obligations where applicable, and the license information of every package installed into the runtime.

| Provider | Main license or status | How it enters Android IDE |
|---|---|---|
| Monaco Editor | MIT | Add the pinned npm package to the editor web bundle and retain the MIT notice |
| `monaco-languageclient` and `vscode-ws-jsonrpc` | MIT | Add the pinned npm packages to the editor web bundle and retain notices |
| Termux application source | GPLv3-only, with documented component exceptions | Retrieve the tagged source, integrate the required modules, preserve GPL notices, and publish corresponding source for distributed covered modifications as required by GPLv3 |
| Termux bootstrap and packages | Package and component licenses vary | Retrieve the pinned ABI-specific bootstrap assets and package metadata; maintain a complete third-party license manifest for installed packages |
| GeckoView | Mozilla Public License 2.0 | Add the pinned Stable artifact from Mozilla’s Maven repository and ship the required notices/source information for covered modifications |
| Mozilla Android Components | Mozilla Public License 2.0 | Add the matching Maven artifacts and preserve MPL notices and source-availability information for covered modifications |
| Eruda | MIT | Add the pinned npm package or bundled distribution and retain the MIT notice |
| Git | GPLv2 | Install the package into the selected runtime and comply with GPLv2 distribution and source obligations if the runtime is distributed |
| Storage Access Framework and Android Keystore | Android platform APIs; Android platform components are principally Apache 2.0, with component-specific notices | Use the APIs supplied by the Android SDK/device; no separate provider download is required |

The acquisition process is therefore concrete. JavaScript providers are downloaded from npm during the web-bundle dependency installation. Android browser components are downloaded from Mozilla’s Maven repository through Gradle. Termux source is retrieved from the public GitHub tag, and its bootstrap archives are retrieved from the exact public release. Git and other runtime packages are installed through the pinned Termux package environment. SAF and Keystore are called through Android SDK APIs.

The main licensing item requiring deliberate release handling is Termux. Termux application source is GPLv3-only, and the runtime package collection contains packages with their own licenses. Android IDE must include a third-party notices area, retain license texts, track source revisions, and make corresponding source available where a license requires it. This is a distribution obligation, not a reason to treat the provider as inaccessible.

Monaco does not provide Android filesystem access by itself. Android IDE must provide a project-file adapter that reads and writes the authoritative local project files, converts them into stable project-relative Monaco model URIs, and observes external changes. The adapter must use the same local project location that the Termux runtime and Git use; Monaco’s in-memory models and recovery state must not become a hidden second project copy. SAF or another Android storage API may be used by the adapter for permission and file operations only where the selected local provider also satisfies the runtime eligibility rule.

The runtime’s own installation, package database, PTYs, logs, caches, and session state may be app-private, but user project files must not be relocated there by default. The Termux filesystem capability is used as the command and process environment for the same user-visible local project location. The integration must establish the project working directory and expose it to shell processes, Git, language servers, and local servers without creating a hidden authoritative copy. The exact Android storage bridge is an implementation concern, but the compatibility gate is behavioral: if the bridge cannot provide the required filesystem operations on the selected local location, Android IDE rejects that location.
