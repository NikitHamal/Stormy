package com.codex.stormy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val templateType: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long,
    val thumbnailPath: String?,
    val rootPath: String,
    val preferredAiModelId: String? = null  // Per-project AI model preference
)

@Serializable
enum class ProjectTemplate(
    val displayName: String,
    val description: String
) {
    BLANK("Blank", "Start from scratch"),
    HTML_BASIC("Basic HTML", "Simple HTML5 boilerplate"),
    TAILWIND("Tailwind CSS", "HTML with Tailwind CDN"),
    LANDING_PAGE("Landing Page", "Modern landing page template")
}
