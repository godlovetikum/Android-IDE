# Android IDE — Provider Compatibility and Lifecycle Architecture Research

**Research date:** 2026-09-19  
**Purpose:** Evaluate browser, terminal, editor, Git, credential/security, extension, and Android lifecycle choices as a coherent stack for Android IDE.

## Executive conclusion

Android IDE should not be built around a single UI process, an unkillable background thread, or a collection of independently selected packages. The reliable design is a layered system with explicit ownership:

1. A native Android shell and navigation layer.
2. A persistent application-data layer for project registry, preferences, session records, and recovery information.
3. A native foreground-service boundary for user-visible long-running terminal and server work.
4. A terminal/runtime backend that supplies the shell, package manager, Git executable, and process tree.
5. An embedded WebView browser using AndroidX WebKit, with an in-app JavaScript console such as Eruda for pages the application controls.
6. A mobile-oriented editor engine, preferably CodeMirror 6 for the primary mobile editor, with Monaco retained only as an experimental or non-mobile-compatible option unless a device-specific validation program proves otherwise.
7. A credential vault based on Android Keystore-protected encryption, with Credential Manager used for supported account sign-in flows rather than as a generic Git-token database.
8. A constrained, signed extension model rather than unrestricted Android dynamic code loading or assuming VS Code extension compatibility.

The central architectural conclusion is that **state persistence and process persistence are different problems**. Persistent storage can restore the user’s state after process death. A foreground service can improve survival of active, user-visible work. Neither can guarantee that Android or an OEM will keep a process alive forever or preserve a session after force-stop or resource termination.

## 1. Browser and developer tools

### Evidence

AndroidX WebKit provides compatibility APIs for modern Android WebView behavior and supports Android 5.0 and above. The AndroidX release page lists `androidx.webkit:webkit:1.17.0` as the stable release on 2026-09-09; `1.18.0-alpha01` is not an appropriate production baseline. [AndroidX WebKit releases](https://developer.android.com/jetpack/androidx/releases/webkit)

Android’s WebView debugging documentation describes Chrome DevTools inspection by enabling `WebView.setWebContentsDebuggingEnabled`. This is a development/debugging connection from a desktop Chrome instance, not an in-app mobile DevTools surface. Android recommends enabling it only for development builds. [Android WebView debugging](https://developer.android.com/develop/ui/views/layout/webapps/debug-chrome-devtools)

Chrome Custom Tabs use the user’s preferred browser engine and share browser state such as cookies, saved passwords, permissions, and browser features. They are useful for ordinary external browsing and authentication, but the host application does not own the full rendering surface or browser state in the same way as an embedded WebView. [Chrome Custom Tabs](https://developer.chrome.com/docs/android/custom-tabs)

Eruda is an MIT-licensed JavaScript console for mobile browsers. It can be bundled into controlled pages and initialized inside an application-owned WebView. Its official repository describes a mobile console and related projects including `eruda-android` and `chobitsu`. [Eruda](https://github.com/liriliri/eruda)

### Recommendation

Use **Android WebView plus AndroidX WebKit 1.17.0** as the application-owned browser and preview substrate. Use WebView for project previews, local development servers, local files, and the in-app browser surface where Android IDE needs to control tabs, downloads, storage, JavaScript bridges, and developer tools.

Bundle Eruda or a comparable in-app console only for pages and contexts where Android IDE has a justified right to inject the console. Do not assume that Chrome DevTools remote debugging is the product’s on-device developer-tools implementation.

Use Custom Tabs selectively for external authentication or sites where shared browser identity is more important than full application control. Custom Tabs should not be the primary browser substrate if Android IDE must provide its own tab management, download routing, preview integration, or embedded console.

### Compatibility warning

WebView and Custom Tabs are not interchangeable. WebView provides control but has a separate browser profile and requires the application to manage security, cookies, downloads, and storage. Custom Tabs provides browser integration and shared browser state but reduces control over the internal browser surface. The product should define these as two deliberate modes, not hide the difference behind one provider abstraction.

## 2. Terminal backend and runtime

### Evidence

The official Termux application repository describes Termux as an Android terminal application and Linux environment. Its repository contains the application UI and terminal emulation; installable packages are maintained separately in `termux-packages`. The project also warns that Android 12 and newer can kill phantom or excessive-CPU processes, demonstrating that the Termux model itself cannot promise unlimited process survival. [Termux application](https://github.com/termux/termux-app)

Termux is therefore a substantial runtime and packaging ecosystem, but the official application repository is not presented as a stable embeddable SDK. Integrating the complete Termux application into Android IDE would mean owning a fork or vendored subset of its app, terminal-emulator, shared, bootstrap, and package components. A third-party project claiming to be an embeddable `libtermux` provider must be independently audited for provenance, maintenance, licensing, ABI coverage, and Android-policy compatibility before being treated as a foundation.

### Recommendation

Treat the terminal backend as a **vendored or deliberately forked Termux-derived runtime**, not as an assumed drop-in library. Keep the UI/session model in Android IDE, but isolate the backend behind a narrow adapter:

- create shell session;
- assign working directory;
- send input;
- receive output;
- resize terminal;
- enumerate capabilities;
- terminate session;
- report availability;
- expose process ownership and exit results.

The terminal package set should be installed and managed inside the backend’s own runtime environment. Git should be installed there as the canonical command-line Git implementation. Node.js, Python, package managers, compilers, and other tools should be capability-detected rather than assumed.

This preserves the user’s requirement that terminal commands and user-interface Git reports operate on the same underlying Git and filesystem state. A separate UI-only Git implementation should not become the source of truth for ordinary repository operations.

### Default terminal capability baseline

The product can reasonably define a baseline of:

- shell and core file utilities;
- Git;
- archive utilities;
- text-processing utilities;
- a package manager appropriate to the selected backend;
- Node.js only when the chosen runtime package is installed and supported;
- Python only when installed and supported;
- local HTTP server capability through available runtime commands.

The product must report unavailable commands honestly. It must not promise Docker, `sudo`, `apt`, `pkg`, a compiler, or a language runtime on every device.

## 3. Editor engine

### Evidence

The official Monaco site lists Monaco Editor 0.55.1, while the official GitHub releases page lists **v0.56.0** as the latest Monaco release on 2026-07-20. Monaco’s repository documents models, URIs, editors, providers, commands, language features, and web-worker-backed language services. It also states that Monaco itself is not supported in mobile browsers or mobile web frameworks. [Monaco Editor](https://microsoft.github.io/monaco-editor/) [Monaco repository](https://github.com/microsoft/monaco-editor) [Monaco releases](https://github.com/microsoft/monaco-editor/releases)

Monaco does not automatically provide every language server. It provides editor APIs and built-in or registerable language providers. A separate LSP client is required to connect external language servers. TypeFox’s official compatibility table identifies **monaco-languageclient 10.7.0**, `vscode-ws-jsonrpc 3.5.0`, and `monaco-editor 0.55.1` as a released compatible set dated 2026-02-04. Its row for Monaco 0.56.0 uses `monaco-languageclient 11.0.0-next.3`, which is unreleased and therefore not a production baseline. [Monaco Language Client](https://github.com/TypeFox/monaco-languageclient) [Compatibility table](https://github.com/TypeFox/monaco-languageclient/blob/main/docs/versions-and-history.md)

CodeMirror’s official site explicitly lists mobile support using the platform’s native selection and editing features. It provides syntax highlighting, autocomplete, folding, search/replace, linting, multiple selections, undo history, extensibility, and language packages under a permissive MIT license. However, CodeMirror is an editor component, not a complete language-server platform; external LSP integration would still need a separate client, transport, and language-server process. [CodeMirror](https://codemirror.net/)

### Recommendation

For this product, **Monaco is the preferred editor direction because the required capability set includes code intelligence and external language servers**, but it must be treated as a customized, validated mobile adaptation rather than assumed mobile support.

Use the released compatibility set **Monaco 0.55.1 + monaco-languageclient 10.7.0 + vscode-ws-jsonrpc 3.5.0** as the initial research baseline. Do not move to Monaco 0.56.0 with `monaco-languageclient 11.0.0-next.3` until that combination has a deliberate compatibility and stability review, because the documented pairing is unreleased.

Monaco is customizable enough to implement the requested mobile surface: editor options, themes, language providers, commands, actions, keybindings, models, URIs, view state, workers, and custom UI around the editor can be controlled by the host application. Android IDE must supply the mobile interaction layer around Monaco: symbol row, keyboard-control row, press-and-hold commands, touch-friendly hit targets, file path bar, tabs, search panels, and responsive toolbar placement. This is a product-level feasibility recommendation, not a claim that Monaco is officially mobile-supported.

Language intelligence requires a separate architecture: language servers run in the terminal/runtime layer or another managed process, and the WebView-hosted Monaco client connects to them through a controlled transport such as WebSocket or another bridge. Each language server must be treated as an independently installed and capability-detected runtime.

CodeMirror 6 remains a credible fallback or alternate provider for mobile editing, especially where native touch editing is more important than a full LSP-first experience. It should not be described as providing the required language-server capability by itself.

## 4. Git engine and terminal interoperability

### Evidence

JGit is a pure-Java implementation of Git under the Eclipse Distribution License 1.0, described by its official repository as BSD-3-Clause-compatible in licensing terms. It can read and write repositories, perform transport, merge, rebase, archive, and other operations, but its official documentation lists missing features including signing push, shallow and partial cloning, credential helpers, multiple worktrees, HTTPS client certificates, SHA-256 object IDs, and protocol-v2 client features. [JGit](https://github.com/eclipse-jgit/jgit)

The official Termux model provides an Android Linux environment in which the ordinary Git package can be installed and used from the shell. [Termux application](https://github.com/termux/termux-app)

### Recommendation

Use the terminal backend’s **canonical Git executable** as the source of truth for repository operations. Build the Git UI as a structured front end over the same repository and working tree that terminal commands use.

Use a library such as JGit only for carefully selected operations where it materially improves integration and its feature gaps are acceptable. Do not silently mix JGit and command-line Git for the same workflow without defining conflict, configuration, credential, hook, and repository-state behavior.

The Git UI and terminal must share:

- repository path;
- Git configuration and identity;
- credential selection;
- remotes;
- index and working tree;
- branch and checkout state;
- operation results and errors.

## 5. Android lifecycle and persistence

### Verified platform boundary

Android’s official process-lifecycle documentation states that the application process lifetime is controlled by the system, not directly by the application. Android may kill a process to reclaim memory, and when it does, threads in that process terminate. Cached processes may be killed at any time. [Android process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle)

Android’s state-saving guidance distinguishes in-memory ViewModel state, saved state, and persistent storage. ViewModel state does not survive process death; saved state can restore small UI state after system-initiated process death; persistent storage survives process death and is the correct place for larger or durable application data. [Android Save UI states](https://developer.android.com/topic/libraries/architecture/saving-states)

### Architectural conclusion

A “permanent thread that can only be terminated by an in-app exit command” is not a guarantee Android allows an application to make. A thread is subordinate to its process. A process is subordinate to Android and device policy. A foreground service can raise process importance for user-visible work, but it is still not an unkillable process and does not defeat force-stop, OEM policies, crashes, resource exhaustion, or system termination.

Recent-apps clearing and process death must therefore be treated as lifecycle events that may occur. The correct goal is:

- keep active user-visible terminal/server work in a foreground service where policy permits;
- show a persistent notification and truthful service state;
- write session metadata, command history, working directory, process identifiers, logs, and recovery markers to durable storage;
- restore the application UI from persistent state after process death;
- reconnect or mark sessions Unavailable according to backend evidence;
- never claim that a previously running process continued when it was actually terminated;
- provide an explicit in-app Exit command that intentionally closes sessions and stops the service.

Compose is not the cause of the fundamental limitation. Compose can host the UI, but no UI toolkit can guarantee permanent process life. The architectural correction is to move long-lived work out of screen-scoped coroutines and ViewModels into a service/backend boundary, while keeping UI state and durable application data separate.

### Data ownership for lifecycle recovery

Project data and project metadata remain separate from global application data:

- project files and project metadata stay in the project’s storage model;
- terminal runtime files, PTYs, logs, and caches belong to app-private runtime storage;
- session records, project registry, preferences, credentials, and recovery markers belong to global application storage;
- UI reconstruction state belongs to saved state or persistent session records, not to the editor composable alone.

## 6. Credentials and security

Android Keystore stores cryptographic key material in a way intended to make extraction difficult and can enforce key-use restrictions and user-authentication requirements. Android recommends the app-scoped Keystore provider for credentials an individual app owns, while the system KeyChain is for credentials shared through system credential selection. [Android Keystore](https://developer.android.com/privacy-and-security/keystore)

Credential Manager is appropriate for supported identity and authentication flows, but it is not a replacement for an app-owned encrypted Git credential vault. Git provider tokens, SSH private keys, and related secrets need a separate app-owned storage policy.

Recommended design:

- encrypt secret records with a data-encryption key protected by Android Keystore;
- require explicit user authentication for high-risk secret use where appropriate;
- keep tokens and private keys out of project metadata, logs, terminal output, URLs, and crash reports;
- pass secrets to Git through memory-safe or provider-supported mechanisms rather than command-line arguments;
- separate global credentials from project-specific credential selection references;
- make revocation, replacement, and deletion explicit;
- never copy credentials into the project storage layers.

## 7. Extensions — placeholder

Extensions are intentionally a product-definition placeholder at this stage. No extension package format, installation workflow, extension API, or execution model is adopted by this research.

Android’s official dynamic-code-loading guidance warns that loading remote or external code can enable tampering and may violate Google Play policies. It recommends avoiding dynamic code loading where possible, using trusted locations, integrity checks, and signing. [Android dynamic code loading](https://developer.android.com/privacy-and-security/risks/dynamic-code-loading)

VS Code’s extension-host model is not automatically portable to Android. VS Code extensions commonly assume a Node-based extension host, desktop APIs, filesystem semantics, process access, and a specific VS Code API surface. Treating the VS Code Marketplace as a drop-in Android extension system would create compatibility and security risks.

Any future extension design must be treated as a later architecture decision. Until that decision is made, the following are research constraints rather than an adopted product model:

- signed or hash-verified Web/JavaScript extensions;
- explicit capability manifests;
- a restricted extension API for editor commands, themes, language packages, snippets, and UI contributions;
- no arbitrary native Android code loading from project or external storage;
- no unrestricted access to credentials, terminal processes, or all project files;
- isolated execution where practical;
- clear install, enable, disable, update, and removal workflows.

Native extensions should be compiled and shipped as trusted application modules until a separate signing, review, and update system exists.

## 8. Coherent provider recommendation

| Domain | Recommended baseline | Reason | Important limitation |
|---|---|---|---|
| Browser | Android WebView + AndroidX WebKit 1.17.0 | Application-owned tabs, previews, downloads, bridges, and storage | Separate browser profile; security and browser state are app responsibilities |
| Mobile console | Eruda bundled into controlled pages | Practical in-app console for mobile WebView content | Not a complete Chrome DevTools replacement; injection must be controlled |
| External browser/auth | Custom Tabs as a secondary route | Shared browser cookies and provider browser state | Less control over internal UI and developer tools |
| Terminal | Vendored/forked Termux-derived backend behind an adapter | Strong Android Linux/package ecosystem and shell compatibility | Not a guaranteed embeddable SDK; Android process limits remain |
| Editor | Monaco 0.55.1 + monaco-languageclient 10.7.0 + vscode-ws-jsonrpc 3.5.0 baseline | Customizable editor APIs plus a released LSP-client compatibility set | Official Monaco mobile support is absent; mobile behavior must be validated and supported by Android IDE UI layers |
| Editor fallback | CodeMirror 6 | Official mobile support, native selection, modular editing features | Does not provide the full language-server architecture by itself |
| Git | Backend Git executable as source of truth; optional JGit for isolated operations | Keeps terminal and Git UI consistent | JGit feature gaps make mixed operation semantics risky |
| Secrets | Android Keystore-protected encrypted vault | App-scoped, non-exportable key protection and optional authentication | Device compromise and active app compromise remain threat considerations |
| Extensions | Signed capability-limited Web/JS model | Safer and more portable than arbitrary native dynamic loading | Not VS Code Marketplace compatibility by default |
| Long-running work | Foreground service + durable recovery state | Best Android-supported model for visible terminal/server work | No guarantee against force-stop, OEM killing, crashes, or resource limits |

## 9. Recommended next documentation step

The product definition is sufficiently settled for ordinary terminal shortcuts, browser conventions, Git command parity, and the core editor command surface. The next architecture document should define the boundaries between:

- native shell and Compose UI;
- application process and foreground service;
- terminal backend and session adapter;
- project storage and app-private runtime storage;
- editor provider and editor command abstraction;
- WebView browser and browser console injection;
- Git UI and canonical Git executable;
- global encrypted credential vault and project credential references;
- extension host and capability/security model.

These boundaries should be documented before implementation because they determine whether state survives navigation, Activity recreation, recent-apps clearing, process death, or an explicit in-app exit.

## Sources

1. [Android process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle)
2. [Android Save UI states](https://developer.android.com/topic/libraries/architecture/saving-states)
3. [AndroidX WebKit releases](https://developer.android.com/jetpack/androidx/releases/webkit)
4. [Android WebView debugging](https://developer.android.com/develop/ui/views/layout/webapps/debug-chrome-devtools)
5. [Chrome Custom Tabs](https://developer.chrome.com/docs/android/custom-tabs)
6. [Termux application](https://github.com/termux/termux-app)
7. [Monaco Editor](https://microsoft.github.io/monaco-editor/)
8. [Monaco releases](https://github.com/microsoft/monaco-editor/releases)
9. [CodeMirror](https://codemirror.net/)
10. [Eruda](https://github.com/liriliri/eruda)
11. [JGit](https://github.com/eclipse-jgit/jgit)
12. [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
13. [Android dynamic code loading](https://developer.android.com/privacy-and-security/risks/dynamic-code-loading)
