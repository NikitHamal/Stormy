package com.oro.app.data.repository.templates

import com.oro.app.data.ai.craft.UiKit
import java.io.File

/**
 * Starter projects built to studio quality on Oro's UI kit, shipped as plain
 * files in `resources/oro/templates/<id>/` (listed in `manifest.txt`) rather
 * than as Kotlin strings: HTML, CSS and JS stay readable, editable and
 * renderable outside the app, and no `$` in a template literal can collide
 * with Kotlin string templates.
 *
 * Every starter also gets the UI kit (`oro-ui/oro.css`, `oro-ui/oro.js`) in
 * its own brand hue, so the project and Orobi share one design vocabulary
 * from the first file.
 */
object StudioTemplates {

    /** A starter: its resource folder and the UI kit hue it is designed in. */
    data class Starter(val id: String, val brandHue: Int)

    val LANDING = Starter("landing", 152)
    val PORTFOLIO = Starter("portfolio", 22)
    val BLOG = Starter("blog", 350)
    val DASHBOARD = Starter("dashboard", 228)
    val STORE = Starter("store", 24)
    val DOCS = Starter("docs", 262)
    val ARCADE = Starter("arcade", 292)

    val all: List<Starter> = listOf(LANDING, PORTFOLIO, BLOG, DASHBOARD, STORE, DOCS, ARCADE)

    private const val ROOT = "/oro/templates"

    /** Relative paths of [starter]'s files, from the manifest. */
    fun files(starter: Starter): List<String> = manifest()
        .filter { it.startsWith(starter.id + "/") }
        .map { it.removePrefix(starter.id + "/") }

    /** The text of one of [starter]'s files. */
    fun read(starter: Starter, path: String): String =
        StudioTemplates::class.java.getResourceAsStream("$ROOT/${starter.id}/$path")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("Template resource missing: ${starter.id}/$path")

    /**
     * Writes [starter] and the UI kit into [root]. Existing files are
     * replaced: this runs only for a project created from the starter.
     */
    fun install(root: File, starter: Starter) {
        files(starter).forEach { path ->
            val file = File(root, path)
            file.parentFile?.mkdirs()
            file.writeText(read(starter, path))
        }
        UiKit.install(root, starter.brandHue)
    }

    private fun manifest(): List<String> =
        StudioTemplates::class.java.getResourceAsStream("$ROOT/manifest.txt")
            ?.bufferedReader()?.use { it.readLines() }
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
}
