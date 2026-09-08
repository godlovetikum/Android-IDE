package dev.androidide.editor

/**
 * Shared file classification used by SAF creation, editor tabs, Monaco, and
 * the file tree. Keep language IDs limited to grammars bundled with Monaco.
 */
enum class FileIconKind {
    IMAGE,
    TEXT,
    CODE,
    GENERIC,
}

object EditorLanguageRegistry {
    const val PLAIN_TEXT = "plaintext"

    private data class Definition(
        val languageId: String,
        val extensions: Set<String> = emptySet(),
        val fileNames: Set<String> = emptySet(),
        val mimeType: String,
        val iconKind: FileIconKind,
    )

    private val definitions = listOf(
        Definition("kotlin", extensions = setOf("kt", "kts"), mimeType = "text/x-kotlin", iconKind = FileIconKind.CODE),
        Definition("java", extensions = setOf("java"), mimeType = "text/x-java", iconKind = FileIconKind.CODE),
        Definition("xml", extensions = setOf("xml", "plist"), mimeType = "text/xml", iconKind = FileIconKind.CODE),
        Definition("json", extensions = setOf("json", "jsonc"), mimeType = "application/json", iconKind = FileIconKind.CODE),
        Definition("markdown", extensions = setOf("md", "mdx"), mimeType = "text/markdown", iconKind = FileIconKind.TEXT),
        Definition("groovy", extensions = setOf("gradle"), mimeType = "text/x-groovy", iconKind = FileIconKind.CODE),
        Definition("python", extensions = setOf("py"), mimeType = "text/x-python", iconKind = FileIconKind.CODE),
        Definition("javascript", extensions = setOf("js", "mjs", "cjs", "jsx"), mimeType = "text/javascript", iconKind = FileIconKind.CODE),
        Definition("typescript", extensions = setOf("ts", "mts", "cts", "tsx"), mimeType = "text/typescript", iconKind = FileIconKind.CODE),
        Definition("html", extensions = setOf("html", "htm"), mimeType = "text/html", iconKind = FileIconKind.CODE),
        Definition("css", extensions = setOf("css", "scss", "less", "sass"), mimeType = "text/css", iconKind = FileIconKind.CODE),
        Definition("shell", extensions = setOf("sh", "bash", "zsh", "fish"), mimeType = "text/x-sh", iconKind = FileIconKind.CODE),
        Definition("bat", extensions = setOf("bat"), mimeType = "text/x-batch", iconKind = FileIconKind.CODE),
        Definition("powershell", extensions = setOf("ps1"), mimeType = "text/x-powershell", iconKind = FileIconKind.CODE),
        Definition("c", extensions = setOf("c", "h"), mimeType = "text/x-csrc", iconKind = FileIconKind.CODE),
        Definition("cpp", extensions = setOf("cpp", "cc", "cxx", "hpp"), mimeType = "text/x-c++src", iconKind = FileIconKind.CODE),
        Definition("rust", extensions = setOf("rs"), mimeType = "text/x-rust", iconKind = FileIconKind.CODE),
        Definition("go", extensions = setOf("go"), mimeType = "text/x-go", iconKind = FileIconKind.CODE),
        Definition("ruby", extensions = setOf("rb"), mimeType = "text/x-ruby", iconKind = FileIconKind.CODE),
        Definition("swift", extensions = setOf("swift"), mimeType = "text/x-swift", iconKind = FileIconKind.CODE),
        Definition("toml", extensions = setOf("toml"), mimeType = "text/x-toml", iconKind = FileIconKind.CODE),
        Definition("yaml", extensions = setOf("yaml", "yml"), mimeType = "text/x-yaml", iconKind = FileIconKind.CODE),
        Definition("sql", extensions = setOf("sql"), mimeType = "text/x-sql", iconKind = FileIconKind.CODE),
        Definition("proto", extensions = setOf("proto"), mimeType = "text/x-protobuf", iconKind = FileIconKind.CODE),
        Definition("ini", extensions = setOf("ini", "properties"), fileNames = setOf(".env"), mimeType = "text/plain", iconKind = FileIconKind.CODE),
        Definition("dockerfile", fileNames = setOf("dockerfile"), mimeType = "text/plain", iconKind = FileIconKind.CODE),
        Definition("make", fileNames = setOf("makefile", "gnumakefile"), mimeType = "text/x-makefile", iconKind = FileIconKind.CODE),
        // Monaco has no bundled CMake grammar in this project.
        Definition(PLAIN_TEXT, fileNames = setOf("cmakelists.txt"), mimeType = "text/plain", iconKind = FileIconKind.CODE),
        Definition(PLAIN_TEXT, extensions = setOf("txt", "rst", "adoc"), mimeType = "text/plain", iconKind = FileIconKind.TEXT),
        Definition("image", extensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico", "tiff", "tif"), mimeType = "application/octet-stream", iconKind = FileIconKind.IMAGE),
    )

    private val byFileName = definitions
        .flatMap { definition -> definition.fileNames.map { it to definition } }
        .toMap()
    private val byExtension = definitions
        .flatMap { definition -> definition.extensions.map { it to definition } }
        .toMap()

    fun languageForFileName(name: String): String {
        val definition = definitionFor(name)
        return if (definition?.languageId == "image") PLAIN_TEXT else definition?.languageId ?: PLAIN_TEXT
    }

    fun mimeTypeForFileName(name: String): String =
        definitionFor(name)?.mimeType ?: "text/plain"

    fun iconKindForFileName(name: String): FileIconKind =
        definitionFor(name)?.iconKind ?: FileIconKind.GENERIC

    fun templateForFileName(name: String): String? =
        if (languageForFileName(name) == "html") HTML_TEMPLATE else null

    private fun definitionFor(name: String): Definition? {
        val fileName = name.substringAfterLast('/').lowercase()
        val extension = fileName.substringAfterLast('.', "")
        return byFileName[fileName] ?: byExtension[extension]
    }

    private const val HTML_TEMPLATE = """<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>New Page</title>
</head>
<body>
</body>
</html>
"""
}