package dev.android.ide.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.FormatIndentDecrease
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.ui.graphics.vector.ImageVector

/** One mobile toolbar shortcut. The command is delegated to Monaco when present. */
data class EditorToolbarAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val commandId: String? = id,
    val requiresSelection: Boolean = false,
    val isPaste: Boolean = false,
    val isKeyboardToggle: Boolean = false,
    val repeatable: Boolean = false,
    val languageServerDependent: Boolean = false,
    val shortcut: String? = null,
)

const val KEYBOARD_TOOLBAR_PAGE_SIZE = 5

/**
 * The complete set of actions intentionally exposed by the mobile toolbar.
 * Search is omitted because Find/Replace already live in the editor top bar.
 * Every command below is a Monaco action or keyboard-handler ID; this catalog
 * does not invent application-side editing commands.
 */
val EDITOR_TOOLBAR_ACTIONS: List<EditorToolbarAction> = listOf(
    // Navigation and cursor movement.
    EditorToolbarAction("cursorLeft", "Move cursor left", Icons.Default.KeyboardArrowLeft, "cursorLeft", shortcut = "←"),
    EditorToolbarAction("cursorRight", "Move cursor right", Icons.Default.KeyboardArrowRight, "cursorRight", shortcut = "→"),
    EditorToolbarAction("cursorUp", "Move cursor up", Icons.Default.KeyboardArrowUp, "cursorUp", shortcut = "↑"),
    EditorToolbarAction("cursorDown", "Move cursor down", Icons.Default.KeyboardArrowDown, "cursorDown", shortcut = "↓"),
    EditorToolbarAction("cursorHome", "Move to line start", Icons.Default.ArrowBack, "cursorHome"),
    EditorToolbarAction("cursorEnd", "Move to line end", Icons.Default.ArrowForward, "cursorEnd"),
    EditorToolbarAction("cursorWordLeft", "Move one word left", Icons.Default.ArrowBack, "cursorWordLeft"),
    EditorToolbarAction("cursorWordRight", "Move one word right", Icons.Default.ArrowForward, "cursorWordRight"),
    EditorToolbarAction("cursorPageUp", "Move page up", Icons.Default.ExpandLess, "cursorPageUp"),
    EditorToolbarAction("cursorPageDown", "Move page down", Icons.Default.ExpandMore, "cursorPageDown"),
    EditorToolbarAction("cursorDocumentStart", "Move to document start", Icons.Default.UnfoldLess, "cursorTop"),
    EditorToolbarAction("cursorDocumentEnd", "Move to document end", Icons.Default.UnfoldMore, "cursorBottom"),

    // Indentation and common editing.
    EditorToolbarAction("indent", "Indent", Icons.Default.FormatIndentIncrease, "smartIndent", shortcut = "Tab"),
    EditorToolbarAction("outdent", "Outdent", Icons.Default.FormatIndentDecrease, "smartOutdent", shortcut = "Shift+Tab"),
    EditorToolbarAction("undo", "Undo", Icons.Default.Undo, "undo", shortcut = "Ctrl+Z"),
    EditorToolbarAction("redo", "Redo", Icons.Default.Redo, "redo", shortcut = "Ctrl+Y"),
    EditorToolbarAction("deleteLeft", "Delete backward", Icons.Default.Delete, "deleteLeft"),
    EditorToolbarAction("deleteRight", "Delete forward", Icons.Default.Delete, "deleteRight"),
    EditorToolbarAction("deleteWordLeft", "Delete previous word", Icons.Default.Delete, "deleteWordLeft"),
    EditorToolbarAction("deleteWordRight", "Delete next word", Icons.Default.Delete, "deleteWordRight"),
    EditorToolbarAction("insertLineAfter", "Insert line after", Icons.Default.Add, "editor.action.insertLineAfter"),
    EditorToolbarAction("insertLineBefore", "Insert line before", Icons.Default.Add, "editor.action.insertLineBefore"),

    // Clipboard and selection.
    EditorToolbarAction("cut", "Cut", Icons.Default.ContentCut, "requestCut", requiresSelection = true, shortcut = "Ctrl+X"),
    EditorToolbarAction("copy", "Copy", Icons.Default.ContentCopy, "requestCopy", requiresSelection = true, shortcut = "Ctrl+C"),
    EditorToolbarAction("paste", "Paste", Icons.Default.ContentPaste, commandId = null, isPaste = true, shortcut = "Ctrl+V"),
    EditorToolbarAction("selectAll", "Select all", Icons.Default.SelectAll, "editor.action.selectAll", shortcut = "Ctrl+A"),
    EditorToolbarAction("selectLine", "Select line", Icons.Default.List, "cursorLineSelect"),
    EditorToolbarAction("selectWord", "Select word", Icons.Default.Code, "cursorWordSelect"),
    EditorToolbarAction("selectLeft", "Extend selection left", Icons.Default.KeyboardArrowLeft, "cursorLeftSelect", repeatable = true),
    EditorToolbarAction("selectRight", "Extend selection right", Icons.Default.KeyboardArrowRight, "cursorRightSelect", repeatable = true),
    EditorToolbarAction("selectUp", "Extend selection up", Icons.Default.KeyboardArrowUp, "cursorUpSelect", repeatable = true),
    EditorToolbarAction("selectDown", "Extend selection down", Icons.Default.KeyboardArrowDown, "cursorDownSelect", repeatable = true),
    EditorToolbarAction("selectWordLeft", "Select previous word", Icons.Default.ArrowBack, "cursorWordLeftSelect", repeatable = true),
    EditorToolbarAction("selectWordRight", "Select next word", Icons.Default.ArrowForward, "cursorWordRightSelect", repeatable = true),
    EditorToolbarAction("selectToStart", "Select to line start", Icons.Default.ArrowBack, "cursorHomeSelect", repeatable = true),
    EditorToolbarAction("selectToEnd", "Select to line end", Icons.Default.ArrowForward, "cursorEndSelect", repeatable = true),
    EditorToolbarAction("expandSelection", "Expand selection", Icons.Default.ExpandMore, "editor.action.smartSelect.expand"),
    EditorToolbarAction("shrinkSelection", "Shrink selection", Icons.Default.ExpandLess, "editor.action.smartSelect.shrink"),
    EditorToolbarAction("duplicateSelection", "Duplicate selection", Icons.Default.ContentCopy, "editor.action.duplicateSelection"),
    EditorToolbarAction("addCursorAbove", "Add cursor above", Icons.Default.Add, "editor.action.insertCursorAbove"),
    EditorToolbarAction("addCursorBelow", "Add cursor below", Icons.Default.Add, "editor.action.insertCursorBelow"),
    EditorToolbarAction("addCursorAtLineEnds", "Add cursors to line ends", Icons.Default.MoreVert, "editor.action.insertCursorAtEndOfEachLineSelected"),
    EditorToolbarAction("addNextOccurrence", "Select next occurrence", Icons.Default.SelectAll, "editor.action.addSelectionToNextFindMatch"),
    EditorToolbarAction("addPreviousOccurrence", "Select previous occurrence", Icons.Default.SelectAll, "editor.action.addSelectionToPreviousFindMatch"),
    EditorToolbarAction("selectAllOccurrences", "Select all occurrences", Icons.Default.SelectAll, "editor.action.selectHighlights"),

    // Formatting, comments, and line structure.
    EditorToolbarAction("formatDocument", "Format document", Icons.Default.Code, "editor.action.formatDocument", shortcut = "Alt+Shift+F"),
    EditorToolbarAction("formatSelection", "Format selection", Icons.Default.Code, "editor.action.formatSelection", requiresSelection = true),
    EditorToolbarAction("commentLine", "Comment or uncomment line", Icons.Default.Block, "editor.action.commentLine", shortcut = "Ctrl+/"),
    EditorToolbarAction("commentSelection", "Comment or uncomment selection", Icons.Default.Block, "editor.action.commentLine", requiresSelection = true),
    EditorToolbarAction("moveLineUp", "Move line up", Icons.Default.KeyboardArrowUp, "editor.action.moveLinesUpAction"),
    EditorToolbarAction("moveLineDown", "Move line down", Icons.Default.KeyboardArrowDown, "editor.action.moveLinesDownAction"),
    EditorToolbarAction("joinLines", "Join lines", Icons.Default.SwapHoriz, "editor.action.joinLines"),
    EditorToolbarAction("sortLinesAscending", "Sort lines ascending", Icons.Default.Sort, "editor.action.sortLinesAscending"),
    EditorToolbarAction("sortLinesDescending", "Sort lines descending", Icons.Default.Sort, "editor.action.sortLinesDescending"),

    // Folding and navigation.
    EditorToolbarAction("fold", "Fold", Icons.Default.UnfoldLess, "editor.action.fold"),
    EditorToolbarAction("unfold", "Unfold", Icons.Default.UnfoldMore, "editor.action.unfold"),
    EditorToolbarAction("foldAll", "Fold all", Icons.Default.UnfoldLess, "editor.action.foldAll"),
    EditorToolbarAction("unfoldAll", "Unfold all", Icons.Default.UnfoldMore, "editor.action.unfoldAll"),
    EditorToolbarAction("foldLevel1", "Fold to level 1", Icons.Default.UnfoldLess, "editor.action.foldLevel1"),
    EditorToolbarAction("foldLevel2", "Fold to level 2", Icons.Default.UnfoldLess, "editor.action.foldLevel2"),
    EditorToolbarAction("goToDefinition", "Go to definition", Icons.Default.Link, "editor.action.revealDefinition", languageServerDependent = true, shortcut = "F12"),
    EditorToolbarAction("goToDeclaration", "Go to declaration", Icons.Default.Link, "editor.action.revealDeclaration", languageServerDependent = true),
    EditorToolbarAction("goToTypeDefinition", "Go to type definition", Icons.Default.Link, "editor.action.revealTypeDefinition", languageServerDependent = true),
    EditorToolbarAction("goToImplementation", "Go to implementation", Icons.Default.Link, "editor.action.goToImplementation", languageServerDependent = true),
    EditorToolbarAction("goBack", "Navigate back", Icons.Default.ArrowBack, "editor.action.navigateBack"),
    EditorToolbarAction("goForward", "Navigate forward", Icons.Default.ArrowForward, "editor.action.navigateForward"),

    // Language-server-backed intelligence. Monaco invokes these actions when available.
    EditorToolbarAction("triggerSuggest", "Show suggestions", Icons.Default.Lightbulb, "editor.action.triggerSuggest", languageServerDependent = true, shortcut = "Ctrl+Space"),
    EditorToolbarAction("triggerParameterHints", "Show parameter hints", Icons.Default.Code, "editor.action.triggerParameterHints", languageServerDependent = true),
    EditorToolbarAction("quickFix", "Quick fix", Icons.Default.Lightbulb, "editor.action.quickFix", languageServerDependent = true),
    EditorToolbarAction("renameSymbol", "Rename symbol", Icons.Default.Edit, "editor.action.rename", languageServerDependent = true, shortcut = "F2"),
    EditorToolbarAction("findReferences", "Find references", Icons.Default.Link, "editor.action.referenceSearch.trigger", languageServerDependent = true),
    EditorToolbarAction("codeAction", "Code actions", Icons.Default.Tune, "editor.action.quickFix", languageServerDependent = true),
    EditorToolbarAction("organizeImports", "Organize imports", Icons.Default.Sort, "editor.action.organizeImports", languageServerDependent = true),

    // View, save, and input controls.
    EditorToolbarAction("toggleKeyboard", "Show or hide keyboard", Icons.Default.Keyboard, commandId = null, isKeyboardToggle = true),
    EditorToolbarAction("toggleWordWrap", "Toggle word wrap", Icons.Default.WrapText, "editor.action.toggleWordWrap"),
    EditorToolbarAction("zoomIn", "Increase editor size", Icons.Default.ZoomIn, "editor.action.fontZoomIn"),
    EditorToolbarAction("zoomOut", "Decrease editor size", Icons.Default.ZoomOut, "editor.action.fontZoomOut"),
)

private val ACTION_BY_ID = EDITOR_TOOLBAR_ACTIONS.associateBy { it.id }

fun editorToolbarAction(id: String): EditorToolbarAction? = ACTION_BY_ID[id]

/** Removes stale IDs, duplicates, and search actions from persisted user order. */
fun normalizeEditorToolbarOrder(order: List<String>): List<String> = order
    .asSequence()
    .mapNotNull(::editorToolbarAction)
    .distinctBy { it.id }
    .map { it.id }
    .toList()
