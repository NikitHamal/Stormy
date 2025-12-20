package com.codex.stormy.domain.model

import com.codex.stormy.data.local.entity.ProjectEntity
import com.codex.stormy.data.local.entity.ProjectTemplate
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class Project(
    val id: String,
    val name: String,
    val description: String,
    val template: ProjectTemplate,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long,
    val thumbnailPath: String?,
    val rootPath: String,
    val lastUsedModelId: String? = null
) {
    val formattedLastOpened: String
        get() {
            val dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(lastOpenedAt),
                ZoneId.systemDefault()
            )
            val now = LocalDateTime.now()
            val diffDays = java.time.temporal.ChronoUnit.DAYS.between(dateTime.toLocalDate(), now.toLocalDate())

            return when {
                diffDays == 0L -> "Today"
                diffDays == 1L -> "Yesterday"
                diffDays < 7 -> "$diffDays days ago"
                else -> dateTime.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
            }
        }

    val formattedCreatedAt: String
        get() {
            val dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(createdAt),
                ZoneId.systemDefault()
            )
            return dateTime.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        }

    fun toEntity(): ProjectEntity = ProjectEntity(
        id = id,
        name = name,
        description = description,
        templateType = template.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastOpenedAt = lastOpenedAt,
        thumbnailPath = thumbnailPath,
        rootPath = rootPath,
        lastUsedModelId = lastUsedModelId
    )

    companion object {
        fun fromEntity(entity: ProjectEntity): Project = Project(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            template = try {
                ProjectTemplate.valueOf(entity.templateType)
            } catch (e: IllegalArgumentException) {
                ProjectTemplate.BLANK
            },
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            lastOpenedAt = entity.lastOpenedAt,
            thumbnailPath = entity.thumbnailPath,
            rootPath = entity.rootPath,
            lastUsedModelId = entity.lastUsedModelId
        )
    }
}
