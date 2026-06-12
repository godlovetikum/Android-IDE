/**
 * android-ide/android/assets/editor/monaco-init.js
 *
 * Monaco Editor initialisation and bidirectional bridge to the Rust layer.
 *
 * Inbound protocol  (Rust → JS via evaluateJavascript / wry IPC):
 *   { type: "loadFile",    path, content, language }
 *   { type: "setTheme",    theme }
 *   { type: "setFontSize", size }
 *   { type: "requestSave", path }
 *   { type: "closeTab",    path }
 *
 * Outbound protocol (JS → Rust via AndroidBridge or wry):
 *   { type: "ready" }
 *   { type: "contentChanged", path, content }
 *   { type: "cursorMoved",    line, column }
 *   { type: "fileSaved",      path }
 *
 * Monaco version: 0.52.0 (bundled — see scripts/fetch-monaco.sh).
 * The require.config paths entry "vs" resolves to the local vs/ directory
 * using a relative path. No CDN requests are made at runtime.
 */

// ---------------------------------------------------------------------------
// Platform detection
// ---------------------------------------------------------------------------

const IS_ANDROID = typeof window.AndroidBridge !== 'undefined';

function postToNative(msg) {
  const json = JSON.stringify(msg);
  if (IS_ANDROID) {
    // Java @JavascriptInterface — synchronous call
    window.AndroidBridge.onMessage(json);
  } else if (window.__WRY_IPC__) {
    // wry desktop IPC
    window.__WRY_IPC__.postMessage(json);
  } else {
    // Fallback: postMessage to parent frame (useful for development in a browser)
    window.parent.postMessage(json, '*');
  }
}

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

let editor = null;          // monaco.editor.IStandaloneCodeEditor
let currentPath = null;     // path of the currently loaded file
let contentChangeTimer = null;
const CONTENT_CHANGE_DEBOUNCE_MS = 300;

// ---------------------------------------------------------------------------
// Monaco loader
// ---------------------------------------------------------------------------

// "vs" resolves to the bundled vs/ directory relative to this file.
// On Android: file:///android_asset/editor/vs
// On desktop: file:///path/to/assets/editor/vs
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
      'editor.background':           '#1e1e1e',
      'editor.foreground':           '#d4d4d4',
      'editorLineNumber.foreground': '#858585',
      'editor.lineHighlightBackground': '#2d2d2d',
      'editorCursor.foreground':     '#aeafad',
      'editor.selectionBackground':  '#264f78',
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
    // Touch-friendly settings
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

  // Signal readiness to Rust
  postToNative({ type: 'ready' });
});

// ---------------------------------------------------------------------------
// Public API — called by Rust via evaluateJavascript
// ---------------------------------------------------------------------------

window.androidIDE = {
  /**
   * Receive a message from the Rust layer.
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
          monaco.editor.setTheme(msg.theme === 'vs-dark' ? 'androidide-dark' : msg.theme);
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

function loadFile(path, content, language) {
  if (!editor) return;

  currentPath = path;

  // Reuse or create a model for this path to preserve undo history
  const uri = monaco.Uri.parse('androidide://file' + path);
  let model = monaco.editor.getModel(uri);

  if (model) {
    // File already has a model — update its content if it changed
    if (model.getValue() !== content) {
      model.pushEditOperations([], [{
        range: model.getFullModelRange(),
        text: content,
      }], () => null);
    }
    if (model.getLanguageId() !== language) {
      monaco.editor.setModelLanguage(model, language);
    }
  } else {
    model = monaco.editor.createModel(content, language, uri);
  }

  editor.setModel(model);
  editor.focus();

  // Scroll to top when loading a new file
  editor.setScrollPosition({ scrollTop: 0, scrollLeft: 0 });
}
