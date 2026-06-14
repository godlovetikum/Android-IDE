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
 * Performance notes:
 *   - contextmenu disabled (Android long-press is handled natively).
 *   - quickSuggestions disabled (Phase 4: LSP autocomplete).
 *   - folding disabled (saves DOM nodes; re-enable with LSP in Phase 4).
 *   - links disabled (reduces highlight passes on every edit).
 *   - insertText uses editor.executeEdits for atomic paste with correct undo.
 *   - Content-change debounce is 150 ms (was 300 ms) for more responsive dirty-marking.
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
 * Uses window.innerWidth / window.innerHeight (always correct on Android WebView).
 */
function applyLayout() {
  if (!editor) return;
  var w = window.innerWidth  || 0;
  var h = window.innerHeight || 0;
  if (w > 0 && h > 0) {
    editor.layout({ width: w, height: h });
  }
}

window.addEventListener('resize', function () {
  applyLayout();
  requestAnimationFrame(applyLayout);
});

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

var editor = null;          // monaco.editor.IStandaloneCodeEditor
var currentPath = null;     // SAF URI of the currently loaded file
var contentChangeTimer = null;
var CONTENT_CHANGE_DEBOUNCE_MS = 150;   // faster dirty-marking (was 300)

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

    // ── Performance settings for Android WebView ─────────────────────────
    // Context menu disabled: Android handles long-press natively; the WebView
    // context menu is slow and duplicates functionality.
    contextmenu: false,

    // Quick suggestions (autocomplete) disabled until Phase 4 (LSP).
    // Suggestion computation on every keystroke adds latency on low-RAM devices.
    quickSuggestions: false,
    suggestOnTriggerCharacters: false,
    acceptSuggestionOnCommit: 'off',
    snippetSuggestions: 'none',
    parameterHints: { enabled: false },

    // Code folding uses a significant number of DOM nodes. Disabled until Phase 4.
    folding: false,

    // Link detection runs a regex over every visible line on every edit.
    links: false,

    // Render validation decorations only when a language server is present (Phase 4).
    renderValidationDecorations: 'off',

    // Smooth caret animation adds GPU compositing overhead on Android.
    cursorSmoothCaretAnimation: 'off',

    // Bracket pair colorization is purely cosmetic — disable for less render work.
    'bracketPairColorization.enabled': false,

    // ── C015: targeted performance and correctness fixes ─────────────────
    // formatOnPaste: true (default) passes pasted content through the language
    // formatter, which can silently corrupt indentation. Disabled here.
    formatOnPaste: false,

    // autoIndent 'advanced' runs expensive token-based analysis on every Enter
    // key press. 'brackets' is lightweight and handles 95% of real-world cases
    // (matching open/close brackets) without the overhead.
    autoIndent: 'brackets',

    // Glyph margin (left gutter for breakpoints, fold arrows etc.) allocates
    // a DOM element per visible line. Not needed without a debugger in Phase 1.
    glyphMargin: false,

    // Minimise the line-decoration column width; 5px provides visual separation
    // next to line numbers without spending DOM/paint budget.
    lineDecorationsWidth: 5,
  });

  // Initial layout — best-effort.
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

  // --- Tap-to-focus: clicking anywhere in the editor focuses the Monaco
  //     textarea so the soft keyboard appears immediately on Android. ---
  //
  // F024: Three guards protect Android text selection handles:
  //   1. isSelectingText — set via 'selectionchange' as soon as a DOM selection
  //      appears (this fires before 'click' and before the setTimeout below).
  //   2. editor.getSelection() — Monaco's internal selection model.
  //   3. document.getSelection() — the browser/Android DOM selection, which
  //      reflects handle drags before Monaco's model has processed them.
  // All three must be empty before we call editor.focus(), which repositions
  // Monaco's cursor and would dismiss any visible selection handles.
  var isSelectingText = false;
  document.addEventListener('selectionchange', function () {
    var domSel = window.getSelection ? window.getSelection() : null;
    isSelectingText = !!(domSel && domSel.toString().length > 0);
  });

  // Clear the flag when Monaco itself collapses its selection (e.g. cursor tap).
  editor.onDidChangeCursorSelection(function (e) {
    if (e.selection.isEmpty()) {
      isSelectingText = false;
    }
  });

  var editorDom = editor.getDomNode();
  if (editorDom) {
    editorDom.addEventListener('click', function () {
      // F024: click fires AFTER Android selection handles appear (long-press
      // sequence: touchstart → touchend → [handles] → click). Without this
      // guard, editor.focus() collapses the selection on every long-press.
      var monacoSel = editor.getSelection();
      var domSel    = window.getSelection ? window.getSelection() : null;
      if (!isSelectingText && (!monacoSel || monacoSel.isEmpty()) && !(domSel && domSel.toString().length > 0)) {
        editor.focus();
      }
    }, { passive: true });

    editorDom.addEventListener('touchend', function () {
      // C016 + F024: delay allows Android to settle the selection state before
      // we inspect it. 150 ms (up from 50 ms) gives slow devices enough time
      // for Monaco's model to reflect DOM selectionchange events.
      setTimeout(function () {
        var monacoSel = editor.getSelection();
        var domSel    = window.getSelection ? window.getSelection() : null;
        if (!isSelectingText && (!monacoSel || monacoSel.isEmpty()) && !(domSel && domSel.toString().length > 0)) {
          editor.focus();
        }
      }, 150);
    }, { passive: true });
  }

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
          var wrapChanged = false;
          if (msg.tabSize          != null) opts.tabSize          = msg.tabSize;
          if (msg.wordWrap         != null) { opts.wordWrap        = msg.wordWrap; wrapChanged = true; } // "on" / "off"
          if (msg.lineNumbers      != null) opts.lineNumbers       = msg.lineNumbers; // "on" / "off"
          if (msg.fontSize         != null) opts.fontSize          = msg.fontSize;
          // C014: render whitespace — "none" | "selection" | "all" | "boundary"
          if (msg.renderWhitespace != null) opts.renderWhitespace  = msg.renderWhitespace;
          // F017: new Monaco settings surface options
          if (msg.minimapEnabled         != null) opts.minimap              = { enabled: msg.minimapEnabled };
          if (msg.scrollBeyondLastLine   != null) opts.scrollBeyondLastLine = msg.scrollBeyondLastLine;
          if (msg.cursorStyle            != null) opts.cursorStyle          = msg.cursorStyle;
          if (msg.bracketPairColorization!= null) opts.bracketPairColorization = { enabled: msg.bracketPairColorization };
          if (msg.autoClosingBrackets    != null) opts.autoClosingBrackets  = msg.autoClosingBrackets;
          if (Object.keys(opts).length > 0) {
            editor.updateOptions(opts);
            // C015: wordWrap changes alter line-height and column count — must
            // re-layout immediately or Monaco displays broken wrapped lines.
            if (wrapChanged) { applyLayout(); requestAnimationFrame(applyLayout); }
          }
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

      // F019: dispose all Monaco models — sent on project switch so stale models
      // from the previous project cannot leak into the new one.
      case 'closeAllModels':
        monaco.editor.getModels().forEach(function (m) { m.dispose(); });
        if (editor) editor.setModel(null);
        currentPath = null;
        break;

      case 'forceLayout':
        applyLayout();
        requestAnimationFrame(applyLayout);
        break;

      case 'executeCommand':
        if (!editor || !msg.command) break;
        if (msg.command === 'focusEditor') {
          editor.focus();
          break;
        }
        if (msg.command === 'blurEditor') {
          window.androidIDE.blurEditor();
          break;
        }
        // F016: requestCopy — read selection from Monaco and post to Kotlin clipboard.
        if (msg.command === 'requestCopy') {
          var copyModel = editor.getModel();
          var copySel   = editor.getSelection();
          if (copyModel && copySel && !copySel.isEmpty()) {
            var copyText = copyModel.getValueInRange(copySel);
            if (copyText) postToNative({ type: 'textCopied', text: copyText, isCut: false });
          }
          break;
        }
        // F016: requestCut — read selection, post to Kotlin clipboard, then delete selection.
        if (msg.command === 'requestCut') {
          var cutModel = editor.getModel();
          var cutSel   = editor.getSelection();
          if (cutModel && cutSel && !cutSel.isEmpty()) {
            var cutText = cutModel.getValueInRange(cutSel);
            if (cutText) {
              postToNative({ type: 'textCopied', text: cutText, isCut: true });
              editor.executeEdits('cut', [{ range: cutSel, text: '', forceMoveMarkers: true }]);
            }
          }
          break;
        }
        // C017: smart indent — insert tab-width spaces when no selection,
        // indent selected lines when a selection exists.
        if (msg.command === 'smartIndent') {
          var sel = editor.getSelection();
          if (sel && !sel.isEmpty()) {
            var indentAction = editor.getAction('editor.action.indentLines');
            if (indentAction) {
              indentAction.run().catch(function (e) {
                console.warn('[androidIDE] indentLines failed:', e);
              });
            }
          } else {
            var tabSize = editor.getModel()
              ? editor.getModel().getOptions().tabSize
              : 4;
            var spaces = '';
            for (var i = 0; i < tabSize; i++) spaces += ' ';
            var pos = editor.getPosition();
            editor.executeEdits('smartIndent', [{
              range: new monaco.Range(pos.lineNumber, pos.column, pos.lineNumber, pos.column),
              text: spaces,
              forceMoveMarkers: true,
            }]);
          }
          break;
        }
        // C017: smart outdent — remove leading indent from selected lines, or
        // remove tab-width spaces behind cursor when no selection.
        if (msg.command === 'smartOutdent') {
          var sel2 = editor.getSelection();
          if (sel2 && !sel2.isEmpty()) {
            var outdentAction = editor.getAction('editor.action.outdentLines');
            if (outdentAction) {
              outdentAction.run().catch(function (e) {
                console.warn('[androidIDE] outdentLines failed:', e);
              });
            }
          } else {
            editor.trigger('keyboard', 'outdent', null);
          }
          break;
        }
        var action = editor.getAction(msg.command);
        if (action) {
          action.run().catch(function (e) {
            console.warn('[androidIDE] executeCommand action failed:', msg.command, e);
          });
        } else {
          editor.trigger('keyboard', msg.command, null);
        }
        break;

      case 'insertText':
        if (editor && msg.text) {
          // Use executeEdits for correct undo/redo behaviour and proper
          // indentation handling after multi-line pastes. This is more
          // correct than editor.trigger('keyboard', 'type', ...) which
          // bypasses Monaco's paste normalisation.
          var sel = editor.getSelection();
          editor.executeEdits('paste', [{
            range: sel,
            text:  msg.text,
            forceMoveMarkers: true,
          }]);
          // Reveal the cursor after paste so the viewport follows the insertion.
          requestAnimationFrame(function () {
            editor.revealPositionInCenterIfOutsideViewport(editor.getPosition());
          });
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
    // Update content atomically using executeEdits to preserve undo history
    // and avoid visual distortion that setValue() can cause on large files.
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
  editor.setScrollPosition({ scrollTop: 0, scrollLeft: 0 });

  // Apply layout using window.innerWidth/innerHeight, then schedule a
  // follow-up pass after Blink's next paint cycle.
  applyLayout();
  requestAnimationFrame(applyLayout);

  // Focus the editor so the soft keyboard can appear immediately on tap.
  editor.focus();
}
