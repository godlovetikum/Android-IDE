package dev.androidide.data.model

data class Project(
    val name: String,
    val uri: String,
    val lastOpenedMs: Long = System.currentTimeMillis(),
)
