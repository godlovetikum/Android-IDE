/**
 * android-ide/android/assets/editor/monaco-init.js
 *
 * Monaco Editor initialisation and bidirectional bridge to the Kotlin layer.
 *
 * Inbound protocol  (Kotlin → JS via WebView.evaluateJavascript):
 *   { type: "loadFile",         path, content, language }
 *   { type: "setTheme",         theme }
 *   { type: "setFontSize",      size }
 *   { type: "requestSave",      path }
 *   { type: "closeTab",         path }
 *   { type: "forceLayout" }
 *   { type: "executeCommand",   command }
 *   { type: "insertText",       text }
 *   { type: "setEditorOptions", tabSize?, wordWrap?, lineNumbers?, fontSize? }
 *   { type: "showFind" }
 *   { type: "showReplace" }
 *
 * Outbound protocol (JS → Kotlin via AndroidBridge.onMessage):
 *   { type: "ready" }
 *   { type: "contentChanged", path, content }
 *   { type: "cursorMoved",    line, column }
 *   { type: "fileSaved",      path }
 *
 * Monaco version: 0.52.0 (bundled — see scripts/fetch-monaco.sh).
 * The require.config paths entry "vs" resolves to the local vs/ directory.
 * No network requests are made at runtime.
 *
 * Model URI scheme:
 *   The 'path' value from Kotlin is a SAF content:// URI. We encode it with
 *   encodeURIComponent:  androidide:///files/<encodeURIComponent(path)>
 *   'currentPath' always stores the original SAF URI for bridge messages.
 *
 * Layout strategy:
 *   automaticLayout is NOT used because Android WebView's ResizeObserver is
 *   unreliable. Instead, editor.layout() is called explicitly:
 *   1. After editor creation (best-effort initial sizing).
 *   2. On the window 'resize' event (orientation changes, IME show/hide).
 *   3. On a 'forceLayout' message from Kotlin (with a requestAnimationFrame
 *      retry to catch any pending Blink synchronization).
 *   4. In loadFile() after setModel(), with a requestAnimationFrame retry.
 *
 * Root cause of Monaco visibility defect on Android WebView (fixed here):
 *   DOM element offsetWidth / offsetHeight are driven by Blink's layout pass.
 *   That pass can complete AFTER Kotlin's evaluateJavascript fires, so those
 *   values may be 0 even though the WebView has its final dimensions.
 *   window.innerWidth / window.innerHeight are set by the Android WebView
 *   engine directly from the View's measured dimensions — they are always
 *   correct once the page has loaded. applyLayout() now uses window.inner*
 *   as the authoritative source instead of #editor-root.offsetWidth/Height.
 */

// ---------------------------------------------------------------------------
// Bridge
// ---------------------------------------------------------------------------

function postToNative(msg) {
  var json = JSON.stringify(msg);
  if (typeof window.AndroidBridge !== 'undefined') {
    window.AndroidBridge.onMessage(json);
  } else {
    window.parent.postMessage(json, '*');
  }
}

// ---------------------------------------------------------------------------
// Layout management
// ---------------------------------------------------------------------------

/**
 * Apply the current viewport dimensions to Monaco.
 *
 * Uses window.innerWidth / window.innerHeight instead of
 * #editor-root.offsetWidth / offsetHeight.  Android WebView sets the
 * window.inner* values from the View's measured dimensions immediately,
 * whereas offsetWidth / offsetHeight depend on a completed Blink layout
 * pass that may not have run yet when evaluateJavascript fires.
 *
 * Safe to call before Monaco is ready — returns early if editor is null.
 */
function applyLayout() {
  if (!editor) return;
  var w = window.innerWidth  || 0;
  var h = window.innerHeight || 0;
  if (w > 0 && h > 0) {
    editor.layout({ width: w, height: h });
  }
}

// Register early so resize events during Monaco loading are not missed.
window.addEventListener('resize', function () {
  applyLayout();
  // One additional pass after the browser finishes reflow.
  requestAnimationFrame(applyLayout);
});

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

var editor = null;          // monaco.editor.IStandaloneCodeEditor
var currentPath = null;     // SAF URI of the currently loaded file
var contentChangeTimer = null;
var CONTENT_CHANGE_DEBOUNCE_MS = 300;

// ---------------------------------------------------------------------------
// Monaco loader
// ---------------------------------------------------------------------------

require.config({ paths: { vs: 'vs' } });

require(['vs/editor/editor.main'], function () {

  // Define AndroidIDE dark theme
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
    // automaticLayout intentionally omitted — ResizeObserver is unreliable
    // on Android WebView. Layout is managed explicitly via applyLayout().
    padding: { top: 8, bottom: 8 },
    scrollbar: {
      vertical: 'auto',
      horizontal: 'auto',
      verticalScrollbarSize: 10,
      horizontalScrollbarSize: 10,
    },
  });

  // Initial layout — best-effort. The authoritative trigger is the forceLayout
  // message from Kotlin, sent after Compose has measured the WebView.
  applyLayout();
  requestAnimationFrame(applyLayout);

  // --- Content change (debounced) ---
  editor.onDidChangeModelContent(function () {
    if (!currentPath) return;
    clearTimeout(contentChangeTimer);
    contentChangeTimer = setTimeout(function () {
      postToNative({
        type: 'contentChanged',
        path: currentPath,
        content: editor.getValue(),
      });
    }, CONTENT_CHANGE_DEBOUNCE_MS);
  });

  // --- Cursor position ---
  editor.onDidChangeCursorPosition(function (e) {
    postToNative({
      type: 'cursorMoved',
      line:   e.position.lineNumber,
      column: e.position.column,
    });
  });

  // --- Keyboard shortcut: Ctrl+S / Cmd+S ---
  editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, function () {
    if (!currentPath) return;
    postToNative({ type: 'fileSaved', path: currentPath });
  });

  // Hide loading indicator and signal readiness to Kotlin
  document.getElementById('loading').classList.add('hidden');
  postToNative({ type: 'ready' });
});

// ---------------------------------------------------------------------------
// Public API — called by Kotlin via evaluateJavascript
// ---------------------------------------------------------------------------

window.androidIDE = {

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
            theme = msg.theme;
          }
          monaco.editor.setTheme(theme);
        }
        break;

      case 'setFontSize':
        if (editor) editor.updateOptions({ fontSize: msg.size });
        break;

      case 'setEditorOptions':
        if (editor) {
          var opts = {};
          if (msg.tabSize     != null) opts.tabSize     = msg.tabSize;
          if (msg.wordWrap    != null) opts.wordWrap    = msg.wordWrap;    // "on" / "off"
          if (msg.lineNumbers != null) opts.lineNumbers = msg.lineNumbers; // "on" / "off"
          if (msg.fontSize    != null) opts.fontSize    = msg.fontSize;
          if (Object.keys(opts).length > 0) editor.updateOptions(opts);
        }
        break;

      case 'requestSave':
        if (editor && currentPath) {
          postToNative({ type: 'fileSaved', path: currentPath });
        }
        break;

      case 'closeTab':
        if (msg.path) {
          var safeSegment = encodeURIComponent(msg.path);
          var modelUri    = monaco.Uri.parse('androidide:///files/' + safeSegment);
          var model       = monaco.editor.getModel(modelUri);
          if (model) {
            if (msg.path === currentPath) {
              editor.setModel(null);
              currentPath = null;
            }
            model.dispose();
          } else if (msg.path === currentPath) {
            editor.setValue('');
            currentPath = null;
          }
        }
        break;

      case 'forceLayout':
        // Apply layout immediately using window.innerWidth/Height (always correct),
        // then schedule a follow-up pass after Blink's next paint cycle.
        applyLayout();
        requestAnimationFrame(applyLayout);
        break;

      case 'executeCommand':
        if (!editor || !msg.command) break;
        // Named special cases handled before Monaco's action/trigger lookup
        if (msg.command === 'focusEditor') {
          editor.focus();
          break;
        }
        if (msg.command === 'blurEditor') {
          window.androidIDE.blurEditor();
          break;
        }
        // Try as an editor action first (async; e.g. 'editor.action.indentLines')
        var action = editor.getAction(msg.command);
        if (action) {
          action.run().catch(function (e) {
            console.warn('[androidIDE] executeCommand action failed:', msg.command, e);
          });
        } else {
          // Fall back to keyboard handler trigger (sync; e.g. 'cursorLeft', 'undo')
          editor.trigger('keyboard', msg.command, null);
        }
        break;

      case 'insertText':
        if (editor && msg.text) {
          editor.trigger('keyboard', 'type', { text: msg.text });
        }
        break;

      case 'showFind':
        if (editor) {
          var findAction = editor.getAction('actions.find');
          if (findAction) findAction.run();
        }
        break;

      case 'showReplace':
        if (editor) {
          var replaceAction = editor.getAction('editor.action.startFindReplaceAction');
          if (replaceAction) replaceAction.run();
        }
        break;

      default:
        console.warn('[androidIDE] Unknown message type:', msg.type);
    }
  },

  /**
   * Blur the Monaco textarea to dismiss the soft keyboard on Android.
   * The Monaco editor's hidden <textarea> is the focused element when typing.
   */
  blurEditor: function () {
    if (editor) {
      var domNode = editor.getDomNode();
      if (domNode) {
        var ta = domNode.querySelector('textarea');
        if (ta) ta.blur();
      }
    }
  },
};

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Load a file into the Monaco editor.
 *
 * @param {string} path     - Original SAF content:// URI.
 * @param {string} content  - Full file text.
 * @param {string} language - Monaco language ID (e.g. "kotlin", "java").
 */
function loadFile(path, content, language) {
  if (!editor) return;

  currentPath = path;

  var safeSegment = encodeURIComponent(path);
  var uri         = monaco.Uri.parse('androidide:///files/' + safeSegment);
  var model       = monaco.editor.getModel(uri);

  if (model) {
    if (model.getValue() !== content) {
      model.pushEditOperations([], [{
        range: model.getFullModelRange(),
        text:  content,
      }], function () { return null; });
    }
    if (model.getLanguageId() !== language) {
      monaco.editor.setModelLanguage(model, language);
    }
  } else {
    model = monaco.editor.createModel(content, language, uri);
  }

  editor.setModel(model);

  // Apply layout using window.innerWidth/innerHeight (always correct on Android
  // WebView), then schedule a follow-up pass after Blink's next paint cycle.
  applyLayout();
  editor.focus();
  editor.setScrollPosition({ scrollTop: 0, scrollLeft: 0 });
  requestAnimationFrame(applyLayout);
}
