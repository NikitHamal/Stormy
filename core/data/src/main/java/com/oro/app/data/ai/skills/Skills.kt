package com.oro.app.data.ai.skills

import java.io.File

/**
 * A skill: a named, shareable Markdown playbook ("add dark mode", "SEO
 * pass") that Orobi loads only when a task calls for it.
 *
 * ```
 * ---
 * name: dark-mode
 * description: Add a dark theme with a toggle that remembers the choice
 * ---
 * 1. Define colour custom properties on :root ...
 * ```
 *
 * The system prompt lists names and descriptions only; `use_skill` returns
 * the body. That keeps a library of skills cheap: a hundred skills cost a
 * hundred lines, not a hundred playbooks, until one is needed.
 */
data class Skill(
    val name: String,
    val description: String,
    val body: String,
    val source: Source,
    /** Starter files (path -> content); use_skill hands them to the model to write. */
    val files: Map<String, String> = emptyMap()
) {
    enum class Source { BUILT_IN, MINE, PROJECT }

    fun toMarkdown(): String = "---\nname: $name\ndescription: $description\n---\n" + body.trim() + "\n" +
        files.entries.joinToString("") { (path, content) -> "\n```file:$path\n${content.trimEnd()}\n```\n" }
}

object Skills {

    private val FILE_BLOCK = Regex("(?s)```file:([^\\n]+)\\n(.*?)\\n```")
    private val FRONT = Regex("""(?s)^---\s*\n(.*?)\n---\s*\n?(.*)$""")

    fun parse(text: String, source: Skill.Source, fallbackName: String = "skill"): Skill? {
        val m = FRONT.find(text.trimStart())
        val (meta, body) = if (m != null) m.groupValues[1] to m.groupValues[2] else "" to text
        fun field(k: String) = Regex("""(?m)^$k\s*:\s*(.+)$""").find(meta)?.groupValues?.get(1)?.trim()?.trim('"')
        val name = slug(field("name") ?: fallbackName)
        // Starter files are fenced blocks tagged ```file:path.
        val files = LinkedHashMap<String, String>()
        val prose = FILE_BLOCK.replace(body) { mm -> files[mm.groupValues[1].trim()] = mm.groupValues[2]; "" }.trim()
        if (prose.isBlank() && files.isEmpty()) return null
        val description = field("description") ?: prose.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(120).orEmpty()
        return Skill(name, description, prose, source, files)
    }

    fun slug(s: String) = s.trim().lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-').take(48).ifEmpty { "skill" }

    /** Built-ins, then the user's (app-wide), then the project's; later ones override by name. */
    fun all(userDir: File, projectRoot: File?): List<Skill> {
        val byName = LinkedHashMap<String, Skill>()
        BUILT_IN.forEach { byName[it.name] = it }
        load(userDir, Skill.Source.MINE).forEach { byName[it.name] = it }
        projectRoot?.let { load(File(it, ".oro/skills"), Skill.Source.PROJECT).forEach { s -> byName[s.name] = s } }
        return byName.values.toList()
    }

    fun load(dir: File, source: Skill.Source): List<Skill> =
        dir.listFiles { f -> f.isFile && f.extension == "md" }.orEmpty().sortedBy { it.name }
            .mapNotNull { f -> runCatching { parse(f.readText(), source, f.nameWithoutExtension) }.getOrNull() }

    fun save(dir: File, skill: Skill) {
        dir.mkdirs()
        File(dir, skill.name + ".md").writeText(skill.toMarkdown())
    }

    fun delete(dir: File, name: String) { File(dir, slug(name) + ".md").delete() }

    /** The catalogue line in the system prompt. */
    fun catalogue(skills: List<Skill>): String = if (skills.isEmpty()) "" else buildString {
        append("\n\n# Skills\nPlaybooks you can load with use_skill(name) when a task matches. Load before starting such a task:\n")
        skills.forEach {
            append("- ").append(it.name).append(": ").append(it.description)
            if (it.files.isNotEmpty()) append(" [starter files: ").append(it.files.keys.joinToString(", ")).append(']')
            append('\n')
        }
    }.trimEnd()

    /**
     * Craft playbooks shipped as Markdown resources (`resources/oro/skills`):
     * the design system, motion, states and domain guides that close most of
     * the gap between a weak model's UI and a professional one. Edit the
     * Markdown to raise the bar; add a file and its name here to add one.
     */
    private val CRAFT = listOf(
        "design-system", "motion", "ui-states", "landing-page", "web-app", "backend", "forms", "game", "three-d"
    )

    private fun craftSkills(): List<Skill> = CRAFT.mapNotNull { name ->
        Skills::class.java.getResourceAsStream("/oro/skills/$name.md")?.bufferedReader()?.use {
            parse(it.readText(), Skill.Source.BUILT_IN, name)
        }
    }

    val BUILT_IN: List<Skill> = craftSkills() + listOf(
        Skill(
            "landing-page-starter", "Start a modern landing page: sticky nav with mobile menu, hero, features, CTA, footer, dark mode",
            """
            Use for a new site or an empty project. Apply the starter files, then adapt every text, colour and section
            to what the user asked for (brand name, real copy, their palette). Keep the structure: semantic landmarks,
            mobile-first CSS with custom properties, the accessible menu and the theme toggle.
            """.trimIndent(), Skill.Source.BUILT_IN,
            mapOf("index.html" to StarterFiles.INDEX, "style.css" to StarterFiles.CSS, "app.js" to StarterFiles.JS)
        ),
        Skill(
            "dark-mode", "Add a dark theme with a toggle that follows the system and remembers the choice",
            """
            1. Move every colour into CSS custom properties on :root (background, surface, text, muted, accent, border).
            2. Add a [data-theme="dark"] block overriding them; also @media (prefers-color-scheme: dark) for the default.
            3. Add a toggle button (aria-label, aria-pressed) in the header; on click set document.documentElement.dataset.theme and save it in localStorage.
            4. On load, read localStorage before first paint (inline script in <head>) to avoid a flash.
            5. Check contrast of text on both themes (4.5:1 body, 3:1 large) with audit_page, and screenshot both.
            """.trimIndent(), Skill.Source.BUILT_IN
        ),
        Skill(
            "seo-basics", "Make a site findable: title, description, social cards, headings, sitemap",
            """
            1. Every page: unique <title> (50-60 chars) and <meta name="description"> (120-160 chars), <html lang>, viewport meta.
            2. Open Graph + Twitter card tags (og:title, og:description, og:image, og:url).
            3. One <h1> per page, headings in order; descriptive link text; alt on every image.
            4. Add robots.txt and sitemap.xml listing every page; canonical links.
            5. Run audit_page on each page and fix what it reports.
            """.trimIndent(), Skill.Source.BUILT_IN
        ),
        Skill(
            "accessibility-pass", "Fix accessibility: labels, contrast, focus, keyboard, landmarks",
            """
            1. Run audit_page; fix every accessibility issue it lists.
            2. Landmarks: <header>, <nav>, <main>, <footer>; skip link to #main.
            3. Visible :focus-visible styles on links, buttons and inputs; nothing reachable only by hover.
            4. Buttons are <button>, links are <a href>; icon-only controls get aria-label.
            5. Respect prefers-reduced-motion. Re-run audit_page until clean.
            """.trimIndent(), Skill.Source.BUILT_IN
        ),
        Skill(
            "make-responsive", "Make every page work from 320px phones to wide desktops",
            """
            1. screenshot_preview with device "all" to see the current state; note overflow and wide elements.
            2. Mobile-first CSS: base styles for phones, min-width media queries at 640/1024px.
            3. Replace fixed widths with max-width + width:100%; images max-width:100%; height:auto.
            4. Navigation collapses to a menu button under 640px; tap targets at least 44px.
            5. Fluid type with clamp(); re-run screenshot_preview "all" until no overflow is reported.
            """.trimIndent(), Skill.Source.BUILT_IN
        )
    )
}
