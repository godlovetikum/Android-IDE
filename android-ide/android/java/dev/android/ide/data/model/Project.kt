package dev.android.ide.data.model

import dev.android.ide.contracts.CapabilityState
import dev.android.ide.contracts.ProjectLocationKind

data class Project(
    val name: String,
    val uri: String,
    val lastOpenedMs: Long = System.currentTimeMillis(),
    val createdMs: Long = lastOpenedMs,
    val locationKind: ProjectLocationKind = ProjectLocationKind.USER_VISIBLE_LOCAL,
    val stableLocationId: String = uri,
    val locationLabel: String = uri,
    val capabilityState: CapabilityState = CapabilityState.NOT_YET_CHECKED,
    val capabilityMessage: String? = null,
)
