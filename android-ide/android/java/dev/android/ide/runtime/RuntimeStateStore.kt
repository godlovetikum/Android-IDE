// RuntimeStateStore keeps durable descriptors and recovery markers in app-private
// storage so project files remain authoritative and runtime handles stay out of metadata.
package dev.android.ide.runtime

import android.content.Context
import java.io.File

class RuntimeStateStore(context: Context) {
    private val root = File(context.filesDir, DIRECTORY)

    fun ensureReady(): Boolean = root.exists() || root.mkdirs()

    fun read(scope: String, key: String): ByteArray? = file(scope, key).takeIf(File::exists)?.readBytes()

    fun write(scope: String, key: String, value: ByteArray): Boolean {
        val target = file(scope, key)
        target.parentFile?.mkdirs()
        return runCatching {
            val temporary = File(target.parentFile, ".${target.name}.tmp")
            temporary.writeBytes(value)
            if (target.exists()) target.delete()
            temporary.renameTo(target)
        }.getOrDefault(false)
    }

    fun delete(scope: String, key: String): Boolean = !file(scope, key).exists() || file(scope, key).delete()

    private fun file(scope: String, key: String): File {
        require(scope.matches(SAFE_NAME)) { "Invalid runtime-state scope" }
        require(key.matches(SAFE_NAME)) { "Invalid runtime-state key" }
        return File(File(root, scope), key)
    }

    private companion object {
        const val DIRECTORY = "runtime-state"
        val SAFE_NAME = Regex("[A-Za-z0-9._-]+")
    }
}
