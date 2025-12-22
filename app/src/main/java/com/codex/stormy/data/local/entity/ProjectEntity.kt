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
    val lastUsedModelId: String? = null
)

@Serializable
enum class ProjectTemplate(
    val displayName: String,
    val description: String,
    val category: TemplateCategory = TemplateCategory.WEB
) {
    // Web templates
    BLANK("Blank", "Start from scratch", TemplateCategory.WEB),
    HTML_BASIC("Basic HTML", "Simple HTML5 boilerplate", TemplateCategory.WEB),
    TAILWIND("Tailwind CSS", "HTML with Tailwind CDN", TemplateCategory.WEB),
    LANDING_PAGE("Landing Page", "Modern landing page template", TemplateCategory.WEB),

    // Framework templates
    REACT("React", "React app with Vite", TemplateCategory.FRAMEWORK),
    VUE("Vue", "Vue.js 3 with Vite", TemplateCategory.FRAMEWORK),
    SVELTE("Svelte", "Svelte with Vite", TemplateCategory.FRAMEWORK),
    NEXT_JS("Next.js", "Next.js static site", TemplateCategory.FRAMEWORK),

    // Mobile/Native templates
    ANDROID_APP("Android", "Android app with GitHub CI", TemplateCategory.MOBILE),
    PWA("PWA", "Progressive Web App", TemplateCategory.MOBILE),

    // Game templates
    PHASER("Phaser", "2D game with Phaser 3", TemplateCategory.GAME),
    THREE_JS("Three.js", "3D graphics with Three.js", TemplateCategory.GAME),

    // Full-stack templates
    EXPRESS_API("Express API", "Node.js REST API", TemplateCategory.BACKEND),
    PORTFOLIO("Portfolio", "Personal portfolio site", TemplateCategory.WEB),
    BLOG("Blog", "Static blog template", TemplateCategory.WEB),
    DASHBOARD("Dashboard", "Admin dashboard UI", TemplateCategory.WEB)
}

@Serializable
enum class TemplateCategory(val displayName: String) {
    WEB("Web"),
    FRAMEWORK("Frameworks"),
    MOBILE("Mobile/Native"),
    GAME("Games"),
    BACKEND("Backend")
}
