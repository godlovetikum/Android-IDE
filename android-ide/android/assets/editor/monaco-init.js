/**
 * android-ide/android/assets/editor/monaco-init.js
 *
 * Monaco Editor initialisation and bidirectional bridge to the Kotlin layer.
 *
 * Inbound protocol  (Kotlin → JS via WebView.evaluateJavascript):
 *   { type: "loadFile",    path, content, language }
 *   { type: "setTheme",    theme }
 *   { type: "setFontSize", size }
 *   { type: "requestSave", path }
 *   { type: "closeTab",    path }
 *
 * Outbound protocol (JS → Kotlin via AndroidBridge.onMessage):
 *   { type: "ready" }
 *   { type: "contentChanged", path, content }
 *   { type: "cursorMoved",    line, column }
 *   { type: "fileSaved",      path }
 *
 * Monaco version: 0.52.0 (bundled — see scripts/fetch-monaco.sh).
 * The require.config paths entry "vs" resolves to the local vs/ directory
 * using a relative path. No CDN requests are made at runtime.
 *
 * Model URI scheme:
 *   Monaco requires a URI to identify each model. The 'path' value coming from
 *   Kotlin is a SAF content:// URI, which cannot be concatenated raw into another
 *   URI (the result would be malformed and monaco.Uri.parse would throw).
 *   We encode the path with encodeURIComponent so it becomes a safe URI segment:
 *     androidide:///files/<encodeURIComponent(path)>
 *   The 'currentPath' variable always stores the original SAF URI string, which
 *   is what Kotlin expects in contentChanged / fileSaved bridge messages.
 */

// ---------------------------------------------------------------------------
// Bridge
// ---------------------------------------------------------------------------

function postToNative(msg) {
  const json = JSON.stringify(msg);
  if (typeof window.AndroidBridge !== 'undefined') {
    // Kotlin @JavascriptInterface — synchronous call from the WebView JS thread
    window.AndroidBridge.onMessage(json);
  } else {
    // Fallback for browser-based development
    window.parent.postMessage(json, '*');
  }
}

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

let editor = null;          // monaco.editor.IStandaloneCodeEditor
let currentPath = null;     // SAF URI of the currently loaded file (original, un-encoded)
let contentChangeTimer = null;
const CONTENT_CHANGE_DEBOUNCE_MS = 300;

// ---------------------------------------------------------------------------
// Monaco loader
// ---------------------------------------------------------------------------

// "vs" resolves to the bundled vs/ directory relative to this file.
// On Android: file:///android_asset/editor/vs
// No network requests are made — all files are served from APK assets.
require.config({
  paths: { vs: 'vs' }
});

require(['vs/editor/editor.main'], function () {
  // Define AndroidIDE dark theme (extends vs-dark with custom colours)
  monaco.editor.defineTheme('androidide-dark', {
    base: 'vs-dark',
    inherit: true,
    rules: [
      { token: 'comment',  foreground: '6a9955' },
      { token: 'keyword',  foreground: '569cd6', fontStyle: 'bold' },
      { token: 'string',   foreground: 'ce9178' },
      { token: 'number',   foreground: 'b5cea8' },
      { token: 'type',     foreground: '4ec9b0' },
    ],
    colors: {
      'editor.background':              '#1e1e1e',
      'editor.foreground':              '#d4d4d4',
      'editorLineNumber.foreground':    '#858585',
      'editor.lineHighlightBackground': '#2d2d2d',
      'editorCursor.foreground':        '#aeafad',
      'editor.selectionBackground':     '#264f78',
    }
  });

  // Define AndroidIDE light theme
  monaco.editor.defineTheme('androidide-light', {
    base: 'vs',
    inherit: true,
    rules: [
      { token: 'comment', foreground: '008000' },
      { token: 'keyword', foreground: '0000ff', fontStyle: 'bold' },
      { token: 'string',  foreground: 'a31515' },
      { token: 'number',  foreground: '098658' },
      { token: 'type',    foreground: '267f99' },
    ],
    colors: {
      'editor.background':              '#ffffff',
      'editor.foreground':              '#000000',
      'editorLineNumber.foreground':    '#237893',
      'editor.lineHighlightBackground': '#f0f0f0',
      'editorCursor.foreground':        '#000000',
      'editor.selectionBackground':     '#add6ff',
    }
  });

  editor = monaco.editor.create(document.getElementById('editor-root'), {
    value: '',
    language: 'plaintext',
    theme: 'androidide-dark',
    fontSize: 14,
    lineNumbers: 'on',
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    wordWrap: 'off',
    renderWhitespace: 'selection',
    automaticLayout: true,
    padding: { top: 8, bottom: 8 },
    // Touch-friendly scrollbars
    scrollbar: {
      vertical: 'auto',
      horizontal: 'auto',
      verticalScrollbarSize: 10,
      horizontalScrollbarSize: 10,
    },
  });

  // --- Content change ---
  editor.onDidChangeModelContent(() => {
    if (!currentPath) return;
    clearTimeout(contentChangeTimer);
    contentChangeTimer = setTimeout(() => {
      postToNative({
        type: 'contentChanged',
        path: currentPath,
        content: editor.getValue(),
      });
    }, CONTENT_CHANGE_DEBOUNCE_MS);
  });

  // --- Cursor position ---
  editor.onDidChangeCursorPosition((e) => {
    postToNative({
      type: 'cursorMoved',
      line:   e.position.lineNumber,
      column: e.position.column,
    });
  });

  // --- Keyboard shortcut: Ctrl+S / Cmd+S → explicit save ---
  editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
    if (!currentPath) return;
    postToNative({ type: 'fileSaved', path: currentPath });
  });

  // Hide loading indicator
  document.getElementById('loading').classList.add('hidden');

  // Signal readiness to Kotlin
  postToNative({ type: 'ready' });
});

// ---------------------------------------------------------------------------
// Public API — called by Kotlin via evaluateJavascript
// ---------------------------------------------------------------------------

window.androidIDE = {
  /**
   * Receive a message from the Kotlin layer.
   * Called as: window.androidIDE.receiveMessage({type, ...})
   */
  receiveMessage: function (msg) {
    if (typeof msg === 'string') {
      try { msg = JSON.parse(msg); } catch (e) { return; }
    }

    switch (msg.type) {
      case 'loadFile':
        loadFile(msg.path, msg.content, msg.language);
        break;

      case 'setTheme':
        if (editor) {
          var theme = 'androidide-dark';
          if (msg.theme === 'light' || msg.theme === 'vs') {
            theme = 'androidide-light';
          } else if (msg.theme !== 'dark' && msg.theme !== 'vs-dark') {
            theme = msg.theme; // pass through custom theme names unchanged
          }
          monaco.editor.setTheme(theme);
        }
        break;

      case 'setFontSize':
        if (editor) {
          editor.updateOptions({ fontSize: msg.size });
        }
        break;

      case 'requestSave':
        if (editor && currentPath) {
          postToNative({ type: 'fileSaved', path: currentPath });
        }
        break;

      case 'closeTab':
        if (msg.path === currentPath) {
          editor.setValue('');
          currentPath = null;
        }
        break;

      default:
        console.warn('[androidIDE] Unknown message type:', msg.type);
    }
  }
};

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Load a file into the Monaco editor.
 *
 * @param {string} path     - Original SAF content:// URI. Stored as currentPath
 *                            and echoed back in contentChanged/fileSaved messages.
 * @param {string} content  - Full file text.
 * @param {string} language - Monaco language ID (e.g. "kotlin", "java").
 */
function loadFile(path, content, language) {
  if (!editor) return;

  // Keep the original SAF URI as currentPath — Kotlin identifies files by this URI.
  currentPath = path;

  // Build a well-formed Monaco model URI.
  // path is a SAF content:// URI and cannot be concatenated raw into another URI
  // (e.g. 'androidide://file' + 'content://...' produces 'androidide://filecontent://...'
  // which monaco.Uri.parse rejects and throws, aborting the load silently).
  // encodeURIComponent makes the SAF URI safe as a URI path segment.
  var safeSegment = encodeURIComponent(path);
  var uri = monaco.Uri.parse('androidide:///files/' + safeSegment);

  // Reuse or create a model for this path to preserve undo history.
  var model = monaco.editor.getModel(uri);

  if (model) {
    // File already has a model — update content if it changed.
    if (model.getValue() !== content) {
      model.pushEditOperations([], [{
        range: model.getFullModelRange(),
        text: content,
      }], function () { return null; });
    }
    if (model.getLanguageId() !== language) {
      monaco.editor.setModelLanguage(model, language);
    }
  } else {
    model = monaco.editor.createModel(content, language, uri);
  }

  editor.setModel(model);
  editor.focus();

  // Scroll to top when loading a new file.
  editor.setScrollPosition({ scrollTop: 0, scrollLeft: 0 });
}
