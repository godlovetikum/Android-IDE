package dev.android.ide.data.model

import dev.android.ide.contracts.CapabilityState

data class Project(
    val name: String,
    val description: String = "",
    val uri: String,
    val lastOpenedMs: Long = System.currentTimeMillis(),
    val createdMs: Long = lastOpenedMs,
    val stableLocationId: String = uri,
    val locationLabel: String = uri,
    val capabilityState: CapabilityState = CapabilityState.NOT_YET_CHECKED,
    val capabilityMessage: String? = null,
)
