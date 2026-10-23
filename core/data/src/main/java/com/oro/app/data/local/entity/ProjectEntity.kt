package com.oro.app.data.local.entity

import androidx.room.ColumnInfo
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
    val lastUsedModelId: String? = null,
    /**
     * Pinned projects sort above the rest regardless of when they were last
     * opened.
     *
     * The SQL default is declared here as well as in the migration, so a
     * fresh install's CREATE TABLE and an upgraded install's ALTER TABLE
     * produce byte-identical schemas. Without it the two differ by a DEFAULT
     * clause, and whether Room's validator minds is a detail of its version
     * rather than something this code should depend on.
     */
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false
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
    LANDING_PAGE("Landing page", "Product page with pricing, FAQ and sign-up", TemplateCategory.WEB),

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
    PORTFOLIO("Portfolio", "Selected work, filters and a contact form", TemplateCategory.WEB),
    BLOG("Blog", "Editorial journal with search and an article page", TemplateCategory.WEB),
    DASHBOARD("Dashboard", "Live stats, charts and orders on the built-in backend", TemplateCategory.WEB),
    STORE("Store", "Product catalogue with a basket, on the built-in backend", TemplateCategory.WEB),
    DOCS("Docs", "Documentation site with search and code blocks", TemplateCategory.WEB),
    ARCADE("Arcade game", "Canvas game with touch controls and high scores", TemplateCategory.GAME)
}

@Serializable
enum class TemplateCategory(val displayName: String) {
    WEB("Web"),
    FRAMEWORK("Frameworks"),
    MOBILE("Mobile/Native"),
    GAME("Games"),
    BACKEND("Backend")
}
