# Android IDE — Provider Stack Research

**Research date:** 2026-09-19  
**Scope:** Select and combine libraries, providers, and runtime components that can be customized to deliver the defined Android IDE product.  
**Status:** Research report only. This document does not modify or replace the product specification.

## 1. Research standard

The target is not merely an Android-compatible application. The target is an integrated mobile development environment with a normal browser, a language-intelligent editor, an embedded terminal/runtime, Git, previews, and developer tools. Each candidate must therefore be evaluated across four dimensions:

1. **Capability coverage:** whether it can provide the required user-facing behavior.
2. **Customization:** whether Android IDE can shape the behavior and UI around the product definition.
3. **Integration fit:** whether it can share projects, processes, sessions, credentials, and state with the other domains.
4. **Lifecycle and maintenance risk:** whether the provider is stable enough to become a foundation and whether its limitations can be handled honestly.

The recommendation below is a coherent stack rather than a list of unrelated compatible packages.

## 2. Recommended high-level stack

| Domain | Recommended foundation | Integration role |
|---|---|---|
| Android shell | Native Android application with the existing Compose direction retained for application UI | Hosts navigation, panels, project management, editor chrome, terminal chrome, browser chrome, and lifecycle-aware state |
| Browser engine | **Mozilla GeckoView Stable channel** | Embeddable browser engine with Gecko networking, JavaScript, HTML, CSS, cookies, permissions, and browser session primitives |
| Browser UI/state | **Mozilla Android Components** browser-state, browser-engine-gecko, browser-toolbar, browser-tabstray, browser-menu, search, downloads, and related feature components | Supplies reusable browser application building blocks rather than forcing Android IDE to reinvent normal browser behavior |
| Browser developer tools | **Eruda 3.4.x**, injected through controlled WebExtension/content-script or page-instrumentation integration | On-device console, DOM/elements, network, resources, source inspection, snippets, and JavaScript execution |
| Editor | **Monaco Editor 0.55.1** | Customizable editor surface with models, commands, providers, themes, workers, and language-service integration points |
| LSP client | **monaco-languageclient 10.7.0 + vscode-ws-jsonrpc 3.5.0** | Released compatibility baseline for connecting Monaco to external language servers |
| Runtime | **Termux-derived runtime fork based on Termux app v0.118.3 and a pinned Termux packages snapshot** | Provides shell, package management, Git, Node.js/Python where installed, local servers, language servers, and normal file commands |
| Git | The runtime’s canonical `git` executable | Ensures terminal commands and Git UI operate on one repository state and one configuration |
| Credentials | Android Keystore-protected app vault, with browser login/password services kept in the browser domain | Protects Git credentials and app secrets while preserving normal browser session behavior |
| Extensions | **Placeholder** | No extension framework is selected in this report or product scope |

## 3. Browser: build a normal browser, not a WebView wrapper

### 3.1 Recommended engine: GeckoView Stable

Mozilla describes GeckoView as an Android library for embedding the Gecko web engine into Android apps. GeckoView powers active Mozilla Android browsers, including Firefox for Android and Firefox Focus. It includes a JavaScript engine, rendering engine, HTML parser, network stack, media support, graphics, and layout engine. [GeckoView architecture](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/contributor/geckoview-architecture.html)

This is a better foundation for Android IDE’s browser than treating Android WebView as a blank page renderer because the product requirement is a **normal browser** with multiple tabs, browser sessions, ordinary web navigation, cookies, local storage, downloads, passwords, permissions, previews, and an inspection console.

Mozilla’s official Android Components documentation identifies the exact building-block model required for this product:

- `browser-state` represents browser state, including which tabs are open and which URLs they point to;
- `browser-engine` provides a generic browser-engine abstraction with GeckoView and Android WebView implementations;
- `browser-toolbar` provides a customizable URL/search toolbar and actions;
- `browser-tabstray` provides a customizable tab-tray component;
- `browser-menu` provides browser menus;
- browser search, domain completion, error pages, downloads, and other features are provided through additional components. [Mozilla Android Components](https://mozac.org/components/)

GeckoView itself deliberately does not define a browser tab. Its model is one `GeckoSession` per web-site instance, which an embedding application can treat as a browser tab. The application owns most browser data storage and session persistence, while GeckoView retains important engine-level state such as cookies. This separation is appropriate: Android IDE can build the browser UI and tab registry without replacing the engine’s web behavior. [GeckoView architecture](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/contributor/geckoview-architecture.html)

### 3.2 Version strategy

Use the **GeckoView Stable channel**, not Beta or Nightly, and pin the exact artifact version from Mozilla’s Maven repository at implementation time. Mozilla documents Stable, Beta, and Nightly channels and requires Java 17 compatibility flags for current GeckoView integration. [GeckoView quick start](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/geckoview-quick-start.html)

The current Firefox for Android evidence provides a useful release-line reference: Firefox for Android 153.0 entered the Release channel on 2026-07-21. The exact GeckoView Maven artifact must still be resolved from Mozilla’s repository rather than inferred from the Firefox application version. [Firefox for Android 153.0 release notes](https://www.firefox.com/en-US/firefox/android/153.0/releasenotes/)

The production rule should be:

- pin one Stable GeckoView artifact;
- pin the matching Android Components release family;
- update them together;
- run browser regression checks before upgrading the pair;
- do not mix Stable GeckoView with unrelated Beta/Nightly Android Components modules.

### 3.3 Normal browser behavior

The browser layer should expose ordinary browser behavior rather than treating browser state as a special IDE preference. The browser domain should own:

- multiple GeckoSessions represented as tabs;
- tab creation, switching, closing, reopening, and restoration;
- navigation history and back/forward behavior;
- URL/search input and search-engine selection;
- cookies and local storage through the browser engine;
- cache and site data controls;
- permissions;
- downloads and default download location;
- password and login handling;
- bookmarks, history, and frequently used domains where included in scope;
- private browsing or equivalent privacy mode if adopted later;
- site settings and per-origin controls;
- project preview URLs and local development-server URLs;
- browser session persistence across app recreation and process death.

The browser should share normal browser state across tabs in the same browser profile. Opening the same website in another tab must not unexpectedly create an unrelated empty session. Private or isolated contexts, if later supported, must be explicit user-visible browser modes.

GeckoView’s API does not automatically provide a complete browser product. Android Components and Android IDE must supply the browser-level state, tab UI, downloads, login/password UX, bookmarks, history, and session restoration. Firefox for Android is the reference implementation to study for these capabilities, not a package to copy wholesale.

### 3.4 Developer tools inside the browser

Eruda is a practical fit for the product’s on-device inspection console. Its documented tools include:

- Console for JavaScript logs;
- Elements for DOM inspection;
- Network for request status;
- Resources for local storage and cookie information;
- Info for URL and user-agent information;
- Snippets;
- Sources for HTML, JavaScript, and CSS source viewing.

Its documented plugins add JavaScript execution, performance monitoring, timing/resource information, feature detection, geolocation testing, orientation testing, touch visualization, and framework-specific tools. [Eruda documentation](https://app.unpkg.com/eruda@3.4.0/files/README.md)

Use Eruda as an **in-app developer-tools surface**, not as the browser engine. It should be loaded only when the user opens developer tools or when the current page is a local project preview. It should be injected through an app-controlled GeckoView WebExtension/content-script or equivalent controlled page-instrumentation path. GeckoView officially supports embedders registering and communicating with WebExtensions. [GeckoView WebExtensions](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html)

The developer-tools surface should provide:

- console output and JavaScript execution;
- DOM/elements inspection;
- HTML/CSS/JavaScript source viewing;
- network request status and timing;
- local storage, cookies, and resource inspection;
- viewport presets and custom viewport dimensions;
- page reload and cache-control actions;
- a clear distinction between inspecting the current page and editing project files.

The browser’s native layer must provide viewport resizing and browser actions that JavaScript alone cannot reliably control. Eruda provides the page-level tools; Android IDE provides the browser-level tools.

`chobitsu` should not be selected as the primary browser-devtools foundation for the recommended GeckoView stack. It implements the Chrome DevTools Protocol in JavaScript and is most naturally aligned with Chromium/Blink instrumentation. It may be evaluated only if the browser engine is changed to Chromium/WebView and the required protocol domains are proven on the target WebView versions. [Chobitsu](https://github.com/liriliri/chobitsu)

### 3.5 Why not make Android WebView the primary browser foundation?

Android WebView is capable and widely available, and AndroidX WebKit provides compatibility APIs. However, WebView is an embeddable web-content component, not a complete normal browser application. The app would need to build and maintain the browser profile, tab/session behavior, downloads, passwords, site settings, and developer-tools integration around it.

WebView remains a valid fallback if GeckoView’s binary size, release process, licensing review, or device compatibility becomes unacceptable. The compatible fallback stack would be AndroidX WebKit Stable plus Android IDE-owned browser state and Eruda. It is not the preferred stack because the product asks for a normal browser rather than a simple in-app web surface.

Chrome Custom Tabs are also not the primary choice. They rely on an external preferred browser and are suitable for external browsing/authentication handoffs, but they do not give Android IDE ownership of the full tab/session/browser/developer-tools surface required here.

## 4. Editor and language intelligence

### 4.1 Monaco is the correct capability direction

The official Monaco repository documents:

- models representing opened files;
- URIs representing file identity;
- editor instances and view state;
- commands and actions;
- language providers for completion and hover information;
- web-worker-backed language services;
- a public API for customization.

Monaco does not itself contain every external language server. Language intelligence is completed by connecting language servers through an LSP client. [Monaco repository](https://github.com/microsoft/monaco-editor)

The released compatibility baseline is:

- `monaco-editor` **0.55.1**;
- `monaco-languageclient` **10.7.0**;
- `vscode-ws-jsonrpc` **3.5.0**.

TypeFox’s compatibility table identifies this as a released set dated 2026-02-04. Monaco 0.56.0 is newer, but its listed pairing with `monaco-languageclient 11.0.0-next.3` is unreleased and should not be the first production baseline. [Monaco Language Client compatibility table](https://github.com/TypeFox/monaco-languageclient/blob/main/docs/versions-and-history.md)

### 4.2 Mobile adaptation plan

Monaco’s official documentation does not claim support for mobile browsers or mobile web frameworks. That is a risk, not an automatic rejection, because Android IDE requires customization that Monaco’s public API can provide.

Android IDE should customize Monaco through:

- editor options and responsive layout;
- touch-sized surrounding controls;
- custom symbol shortcut row;
- custom keyboard-control row;
- press-and-hold cursor and selection actions;
- custom tabs and file-path controls;
- custom search and replace panels;
- custom save, preview, and run controls;
- configurable line numbers, wrapping, minimap, autocomplete, themes, and document information;
- persistent editor display preferences;
- touch/IME-specific validation on real Android devices.

The editor provider should remain behind an Android IDE editor contract, but Monaco is the primary provider candidate because the product requires a language-intelligent editor rather than only a text-editing surface.

### 4.3 Language-server topology

The language-server flow should be:

```text
Monaco in the editor surface
        |
        v
monaco-languageclient 10.7.0
        |
        v
vscode-ws-jsonrpc 3.5.0 or a controlled equivalent transport
        |
        v
Language server process
        |
        v
Termux-derived runtime and project filesystem
```

Language servers are runtime capabilities. They should be installed, started, stopped, and reported through the terminal/runtime management layer. A language server must see the same project path, files, dependencies, and configuration that the terminal and editor use.

Each language server needs:

- executable/runtime detection;
- project-root selection;
- environment and PATH configuration;
- initialization options;
- document synchronization;
- diagnostics;
- completion, hover, definition, references, rename, formatting, and code actions where supported;
- lifecycle cleanup;
- failure reporting;
- resource limits.

## 5. Terminal, runtime, and Git

### 5.1 Termux-derived backend

The official Termux application is an Android terminal application and Linux environment. Its repository separates the app/UI/terminal-emulation code from the installable package ecosystem. The latest official release evidence available for this research is **Termux app v0.118.3**, released 2025-05-22. [Termux application](https://github.com/termux/termux-app) [Termux v0.118.3](https://github.com/termux/termux-app/releases/tag/v0.118.3)

Android IDE should not depend on the installed Termux app or try to communicate with another app for its core terminal. The suitable approach is a deliberate fork or vendored integration based on the Termux source and a pinned package/bootstrap snapshot, with Android IDE controlling the session UI and lifecycle.

The backend adapter should expose:

- shell creation;
- working-directory assignment;
- input/output streams;
- terminal resizing;
- session naming;
- process-tree ownership;
- availability state;
- explicit session termination;
- capability detection;
- environment variables and PATH;
- package installation results.

### 5.2 Git and language servers use the same runtime

Install and use the runtime’s canonical Git executable. The Git UI should be a structured front end over that same Git installation and repository state.

The same runtime should host language servers and developer commands where possible. This avoids a split system in which the editor sees one dependency environment while the terminal sees another.

Git UI and terminal must share:

- repository path;
- working tree;
- Git index;
- Git identity;
- Git configuration;
- credential selection;
- remotes;
- branches;
- hooks where supported;
- command results and errors.

JGit remains an optional library for selected operations, but it should not become a second hidden Git authority. Its official documentation identifies gaps including credential helpers, shallow/partial clone, signing push, multiple worktrees, HTTPS client certificates, and some newer Git features. [JGit](https://github.com/eclipse-jgit/jgit)

## 6. Credentials and browser security

The browser’s cookies, local storage, cache, sessions, and passwords belong to the browser domain because they are normal browser behavior. They should not be treated as arbitrary global IDE preferences or duplicated into project metadata.

Git credentials and IDE secrets belong to the application security domain. Protect them with an Android Keystore-backed vault. The browser may use GeckoView/Android Components browser login facilities and its own browser profile. The two domains should not silently copy passwords or tokens into each other.

Android Keystore provides non-exportable key protection and supports key-use restrictions and user-authentication requirements. [Android Keystore](https://developer.android.com/privacy-and-security/keystore)

## 7. Lifecycle and state

The browser and terminal require different lifecycle treatment:

- browser tabs and browser state are durable application state and must be restorable;
- active terminal processes and language servers are live backend processes and may be terminated by Android;
- a foreground service is appropriate for user-visible long-running terminal/server work;
- process identifiers, session records, and recovery markers must be persisted;
- after process death, Android IDE must restore UI state and accurately report whether a backend session is still available.

Android controls process lifetime. No provider can guarantee an unkillable thread that stops only after an in-app Exit command. The correct guarantee is durable state restoration plus best-effort continuation of active, user-visible work through a foreground service.

## 8. Extensions

Extensions remain an explicit **placeholder**. This report selects no extension package format, extension API, extension host, Marketplace compatibility model, or dynamic-code-loading strategy.

## 9. Final recommendation

Build the browser as a real browser domain using **GeckoView Stable + Mozilla Android Components**, not as a thin WebView wrapper. Use Eruda as the first in-app developer-tools candidate, with GeckoView WebExtension/content-script integration and native browser controls for viewport and page lifecycle actions.

Use **Monaco 0.55.1 + monaco-languageclient 10.7.0 + vscode-ws-jsonrpc 3.5.0** as the initial editor/LSP baseline. Customize Monaco extensively for Android touch interaction, while treating mobile behavior as a validation responsibility because Monaco does not officially support mobile browser frameworks.

Use a **Termux-derived runtime fork based on v0.118.3 and a pinned package snapshot** as the terminal foundation. Run Git, language servers, Node/Python tools, package managers, and local servers through that same runtime so the terminal, editor, preview, and Git domains share one environment.

Do not select extensions yet. Keep that domain explicitly deferred.

## Sources

1. [Mozilla Android Components](https://mozac.org/components/)
2. [GeckoView architecture](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/contributor/geckoview-architecture.html)
3. [GeckoView quick start](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/geckoview-quick-start.html)
4. [GeckoView WebExtensions](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html)
5. [Firefox for Android 153.0 release notes](https://www.firefox.com/en-US/firefox/android/153.0/releasenotes/)
6. [Eruda](https://app.unpkg.com/eruda@3.4.0/files/README.md)
7. [Chobitsu](https://github.com/liriliri/chobitsu)
8. [Monaco repository](https://github.com/microsoft/monaco-editor)
9. [Monaco Language Client compatibility table](https://github.com/TypeFox/monaco-languageclient/blob/main/docs/versions-and-history.md)
10. [Termux application](https://github.com/termux/termux-app)
11. [Termux v0.118.3](https://github.com/termux/termux-app/releases/tag/v0.118.3)
12. [JGit](https://github.com/eclipse-jgit/jgit)
13. [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
14. [Android process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle)
15. [Android Save UI states](https://developer.android.com/topic/libraries/architecture/saving-states)
