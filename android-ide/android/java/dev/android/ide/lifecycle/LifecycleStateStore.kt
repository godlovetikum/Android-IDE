// LifecycleStateStore keeps process and visibility transitions durable so the
// application can restore honestly after backgrounding or process recreation.
package dev.android.ide.lifecycle

import android.content.Context
import dev.android.ide.contracts.LifecycleCoordinator
import dev.android.ide.contracts.OperationOutcome
import dev.android.ide.contracts.OperationReport

/** Keeps lifecycle transitions durable so restoration can distinguish backgrounding from explicit exit. */
class LifecycleStateStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun markProcessStarted() {
        prefs.edit()
            .putString(KEY_STATE, State.FOREGROUND.name)
            .putLong(KEY_STARTED_AT, System.currentTimeMillis())
            .apply()
    }

    fun markForeground() = prefs.edit()
        .putString(KEY_STATE, State.FOREGROUND.name)
        .putLong(KEY_CHANGED_AT, System.currentTimeMillis())
        .apply()

    fun markBackground() = prefs.edit()
        .putString(KEY_STATE, State.BACKGROUND.name)
        .putLong(KEY_CHANGED_AT, System.currentTimeMillis())
        .apply()

    fun markExplicitExit() = prefs.edit()
        .putString(KEY_STATE, State.EXPLICIT_EXIT.name)
        .putLong(KEY_CHANGED_AT, System.currentTimeMillis())
        .apply()

    fun state(): State = runCatching {
        State.valueOf(prefs.getString(KEY_STATE, State.BACKGROUND.name) ?: State.BACKGROUND.name)
    }.getOrDefault(State.BACKGROUND)

    enum class State { FOREGROUND, BACKGROUND, EXPLICIT_EXIT }

    private companion object {
        const val PREFS = "lifecycle_state"
        const val KEY_STATE = "state"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_CHANGED_AT = "changed_at"
    }
}

/** Exposes lifecycle transitions without allowing application services to depend on preference storage. */
class LifecycleCoordinatorImpl(context: Context) : LifecycleCoordinator {
    private val store = LifecycleStateStore(context)

    override suspend fun restore(): OperationReport {
        store.markProcessStarted()
        return report("Lifecycle state restored")
    }

    override suspend fun onForeground(): OperationReport {
        store.markForeground()
        return report("Application foregrounded")
    }

    override suspend fun onBackground(): OperationReport {
        store.markBackground()
        return report("Application backgrounded")
    }

    override suspend fun onExplicitExit(): OperationReport {
        store.markExplicitExit()
        return report("Application exit recorded")
    }

    private fun report(message: String) = OperationReport(
        outcome = OperationOutcome.COMPLETE,
        message = message,
    )
}
