package dev.android.ide

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures fatal exceptions locally without coupling crash handling to Compose,
 * coroutines, network access, or editor/project contents.
 */
class CrashReporter(private val context: Context) : Thread.UncaughtExceptionHandler {
    private val installed = AtomicBoolean(false)
    private val reportDirectory = File(context.filesDir, "crash-reports")
    private val previousHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    fun install() {
        if (installed.compareAndSet(false, true)) {
            reportDirectory.mkdirs()
            Thread.setDefaultUncaughtExceptionHandler(this)
        }
    }

    /** Number of locally persisted crash reports awaiting user review. */
    fun reportCount(): Int = reportDirectory.listFiles { file -> file.extension == "json" }?.size ?: 0

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching { writeReport(thread, throwable) }
        previousHandler?.uncaughtException(thread, throwable)
            ?: android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun writeReport(thread: Thread, throwable: Throwable) {
        reportDirectory.mkdirs()
        val report = JSONObject().apply {
            put("reportVersion", 1)
            put("timestampMs", System.currentTimeMillis())
            put("appVersion", appVersion())
            put("androidVersion", Build.VERSION.RELEASE ?: "unknown")
            put("apiLevel", Build.VERSION.SDK_INT)
            put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
            put("thread", thread.name.take(120))
            put("exception", throwable::class.java.name.take(240))
            put("message", sanitize(throwable.message).take(1000))
            put("stackTrace", stackTrace(throwable).take(MAX_STACK_TRACE_CHARS))
        }
        val target = File(reportDirectory, "crash-${System.currentTimeMillis()}-${thread.id}.json")
        val temporary = File(target.path + ".tmp")
        temporary.outputStream().use { it.write(report.toString().toByteArray(Charsets.UTF_8)) }
        if (!temporary.renameTo(target)) {
            target.delete()
            temporary.renameTo(target)
        }
        trimReports()
    }

    private fun trimReports() {
        reportDirectory.listFiles { file -> file.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_REPORTS)
            ?.forEach { it.delete() }
    }

    private fun stackTrace(throwable: Throwable): String = buildString {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        append(sanitize(writer.toString()))
    }

    private fun sanitize(value: String?): String = value.orEmpty()
        .replace(Regex("(?i)(token|password|secret|authorization)=\\S+"), "${'$'}1=[redacted]")

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }.getOrDefault("unknown")

    private companion object {
        const val MAX_REPORTS = 5
        const val MAX_STACK_TRACE_CHARS = 240_000
    }
}
