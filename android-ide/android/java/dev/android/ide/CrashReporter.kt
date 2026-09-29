package dev.android.ide

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean

data class CrashReportSummary(
    val id: String,
    val exception: String,
    val message: String,
    val stackTrace: String,
    val timestampMs: Long,
    val appVersion: String = "unknown",
    val androidVersion: String = "unknown",
    val apiLevel: Int = 0,
    val abi: String = "unknown",
    val thread: String = "unknown",
    val rawJson: String = "{}",
)

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
    fun reportCount(): Int = reports().size

    /** Reports are newest first and include the complete original JSON payload. */
    fun reports(): List<CrashReportSummary> {
        val persisted = reportDirectory
            .listFiles { file -> file.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { file ->
                runCatching {
                    JSONObject(file.readText(Charsets.UTF_8)).toSummary(file.name)
                }.getOrNull()
            }
            .orEmpty()
        if (persisted.isNotEmpty()) return persisted
        return emergencyMarker().takeIf { it.exists() }?.let { marker ->
            val lines = runCatching { marker.readLines() }.getOrDefault(emptyList())
            val json = JSONObject()
                .put("reportVersion", 1)
                .put("timestampMs", marker.lastModified())
                .put("exception", lines.getOrNull(1) ?: "Unknown exception")
                .put("message", lines.getOrNull(2).orEmpty().ifBlank { "The crash report could not be fully persisted." })
                .put("stackTrace", "Emergency crash marker; full stack trace was unavailable.")
                .toString(2)
            listOf(
                CrashReportSummary(
                    id = marker.name,
                    exception = lines.getOrNull(1) ?: "Unknown exception",
                    message = lines.getOrNull(2).orEmpty().ifBlank { "The crash report could not be fully persisted." },
                    stackTrace = "Emergency crash marker; full stack trace was unavailable.",
                    timestampMs = marker.lastModified(),
                    rawJson = json,
                ),
            )
        }.orEmpty()
    }

    fun latestReport(): CrashReportSummary? = reports().firstOrNull()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runCatching { writeReport(thread, throwable) }
            .onFailure { writeEmergencyMarker(thread, throwable) }
        previousHandler?.uncaughtException(thread, throwable)
            ?: android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun writeReport(thread: Thread, throwable: Throwable) {
        check(reportDirectory.exists() || reportDirectory.mkdirs()) { "Unable to create crash-report directory" }
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
        emergencyMarker().delete()
    }

    private fun writeEmergencyMarker(thread: Thread, throwable: Throwable) {
        runCatching {
            File(context.filesDir, "crash-pending.marker").writeText(
                "${thread.name}\n${throwable::class.java.name}\n${sanitize(throwable.message)}",
                Charsets.UTF_8,
            )
        }
    }

    private fun emergencyMarker(): File = File(context.filesDir, "crash-pending.marker")

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

    private fun JSONObject.toSummary(id: String): CrashReportSummary = CrashReportSummary(
        id = id,
        exception = optString("exception", "Unknown exception"),
        message = optString("message", "No message"),
        stackTrace = optString("stackTrace", "No stack trace available"),
        timestampMs = optLong("timestampMs", 0L),
        appVersion = optString("appVersion", "unknown"),
        androidVersion = optString("androidVersion", "unknown"),
        apiLevel = optInt("apiLevel", 0),
        abi = optString("abi", "unknown"),
        thread = optString("thread", "unknown"),
        rawJson = toString(2),
    )

    private companion object {
        const val MAX_REPORTS = 5
        const val MAX_STACK_TRACE_CHARS = 240_000
    }
}
