package dev.android.ide.runtime

import android.content.Context
import android.os.Build
import dev.android.ide.contracts.ErrorCategory
import dev.android.ide.contracts.OperationOutcome
import dev.android.ide.contracts.OperationReport
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream

/** Installs the pinned Termux bootstrap into Android IDE's private runtime directory. */
class BundledTermuxRuntimeInstaller(private val context: Context) {
    private val prefix = File(context.filesDir, "termux-prefix")
    private val home = File(context.filesDir, "termux-home")

    fun initialize(): OperationReport {
        val assetName = when (Build.SUPPORTED_ABIS.firstOrNull()) {
            "arm64-v8a" -> "aarch64"
            "armeabi-v7a" -> "arm"
            "x86" -> "i686"
            "x86_64" -> "x86_64"
            else -> null
        } ?: return unavailable("This device architecture is not supported by the bundled terminal runtime")
        val marker = File(prefix, ".android-ide-bootstrap-2026.09.13-r1-apt.android-7")
        return if (!marker.exists()) {
            val assetPath = "termux/bootstrap-$assetName.zip"
            val staging = File(context.filesDir, "termux-prefix.staging")
            staging.deleteRecursively()
            staging.mkdirs()
            runCatching {
                context.assets.open(assetPath).use { input ->
                    ZipInputStream(input.buffered()).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            val relative = entry.name.removePrefix("usr/").trimStart('/')
                            if (relative.isBlank() || relative.contains("..") || relative.startsWith("/")) continue
                            val target = File(staging, relative).canonicalFile
                            if (!target.toPath().startsWith(staging.canonicalFile.toPath())) continue
                            if (entry.isDirectory) target.mkdirs()
                            else {
                                target.parentFile?.mkdirs()
                                target.outputStream().use { output -> zip.copyTo(output) }
                                target.setExecutable(true, false)
                            }
                            zip.closeEntry()
                        }
                    }
                }
                val linksFile = File(staging, "SYMLINKS.txt")
                if (linksFile.isFile) {
                    linksFile.readLines().forEach { line ->
                        val parts = line.split("←./", limit = 2)
                        if (parts.size != 2) return@forEach
                        val rawTarget = parts[0]
                        val link = File(staging, parts[1]).canonicalFile
                        if (!link.toPath().startsWith(staging.canonicalFile.toPath())) return@forEach
                        val target = if (rawTarget.startsWith("/data/data/com.termux/files/usr/")) {
                            File(staging, rawTarget.removePrefix("/data/data/com.termux/files/usr/")).canonicalFile
                        } else {
                            File(link.parentFile, rawTarget).canonicalFile
                        }
                        if (!target.toPath().startsWith(staging.canonicalFile.toPath())) return@forEach
                        link.delete()
                        link.parentFile?.mkdirs()
                        Files.createSymbolicLink(link.toPath(), link.parentFile.toPath().relativize(target.toPath()))
                    }
                }
                if (prefix.exists()) prefix.deleteRecursively()
                check(staging.renameTo(prefix)) { "Unable to install the bundled runtime directory" }
                check(repairRuntimeExecutables()) { "The bundled terminal runtime files are not executable" }
                marker.parentFile?.mkdirs()
                marker.writeText(assetName)
                home.mkdirs()
                OperationReport(OperationOutcome.COMPLETE, "Bundled Termux runtime initialized")
            }.getOrElse {
                staging.deleteRecursively()
                unavailable("The bundled terminal runtime could not be initialized: ${it.message ?: "unknown error"}")
            }
        } else {
            home.mkdirs()
            if (!repairRuntimeExecutables()) {
                unavailable("The bundled terminal runtime files are not executable")
            } else {
                OperationReport(OperationOutcome.COMPLETE, "Bundled Termux runtime is available")
            }
        }
    }

    fun prefix(): File = prefix
    fun home(): File = home

    private fun repairRuntimeExecutables(): Boolean {
        val bin = File(prefix, "bin")
        if (!bin.isDirectory) return false
        bin.setExecutable(true, false)
        val required = listOf("sh", "pkg", "npm")
        return required.all { name ->
            val executable = File(bin, name)
            executable.exists() && executable.setExecutable(true, false) && executable.canExecute()
        }
    }

    private fun unavailable(message: String) =
        OperationReport(OperationOutcome.BLOCKED, message, ErrorCategory.UNAVAILABLE_RUNTIME)
}
