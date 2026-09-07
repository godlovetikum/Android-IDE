package dev.androidide.viewmodel.model

/** A pending project change that is waiting for a dirty-tab decision. */
data class ProjectSwitchRequest(
    val projectUri: String,
    val projectName: String,
)