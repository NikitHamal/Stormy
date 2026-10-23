package com.oro.app.data.ai.craft

import java.io.File

/**
 * Oro's production UI kit (tokens, components, motion, runtime helpers),
 * installed into a project by the `add_ui_kit` tool.
 *
 * A weak model asked for "a beautiful dashboard" improvises CSS from memory
 * and the result looks like it. Given a vetted foundation of buttons, cards,
 * inputs, skeletons, toasts, dialogs, reveal-on-scroll and dark mode, the
 * same model only has to compose, which it does well.
 */
object UiKit {
    const val DIR = "oro-ui"
    private val FILES = listOf("oro.css", "oro.js")

    fun read(name: String): String =
        UiKit::class.java.getResourceAsStream("/oro/kit/$name")?.bufferedReader()?.use { it.readText() }
            ?: error("UI kit resource missing: $name")

    /** Writes the kit; returns (relative path, previous content or null, new content). */
    fun install(root: File, brandHue: Int?): List<Triple<String, String?, String>> = FILES.map { name ->
        var content = read(name)
        if (name == "oro.css" && brandHue != null) {
            content = content.replaceFirst("--brand-h:248;", "--brand-h:${brandHue.coerceIn(0, 360)};")
        }
        val rel = "$DIR/$name"
        val file = File(root, rel)
        val old = if (file.exists()) file.readText() else null
        file.parentFile?.mkdirs()
        file.writeText(content)
        Triple(rel, old, content)
    }

    /** Usage notes returned to the model after installing. */
    fun usage(): String = """
        Installed the Oro UI kit:
          oro-ui/oro.css  tokens (--brand-h re-themes everything), base typography, layout, components, motion
          oro-ui/oro.js   reveal-on-scroll, sticky/blur nav + mobile menu, button ripples, toasts, confirm dialog,
                          theme toggle, count-up numbers, tilt cards, tabs, app shell; for real apps Oro.api,
                          Oro.auth, Oro.table, Oro.chart
        Link them (paths relative to the HTML file):
          <link rel="stylesheet" href="oro-ui/oro.css">   (before your own stylesheet)
          <script src="oro-ui/oro.js" defer></script>
        Classes: container section section-alt grid stack row center eyebrow lead muted gradient-text hero-glow
          nav nav-inner nav-links nav-toggle | btn btn-primary btn-outline btn-ghost btn-danger btn-sm btn-lg btn-icon is-loading
          card card-hover card-media icon-tile badge badge-success badge-danger glass avatar avatars divider
          field label input select textarea hint error switch | skeleton empty alert alert-error
          dialog.modal modal-body modal-actions | details.acc + summary + .acc-body | tabs tab[aria-selected]
          bottom-bar | reveal reveal-scale float marquee marquee-track
          app-shell (.sidebar + .sidebar-link[aria-current=page] + .app-top with a [data-sidebar-toggle] button + .app-main)
          stats .stat .stat-label .stat-value .stat-delta(.is-down) | table-wrap table (th.num/td.num) pagination
          chart | auth auth-card password (input + toggle button)
        Attributes: data-theme-toggle (button), data-count="1200" data-suffix="+" (count-up), data-tilt (3D hover)
        JS: Oro.toast(msg, 'success'|'error'), await Oro.confirm({title, body, danger:true}), Oro.setBusy(btn, true),
            Oro.skeleton(n, heightPx), Oro.esc(text), Oro.reveal(rootEl) after rendering new .reveal elements
            Backend (Oro's /api, see use_skill "backend"): await Oro.api.get('/todos?_sort=createdAt&_order=desc'),
              Oro.api.post('/todos', {...}), .patch(path, body), .del(path), await Oro.api.upload(file) -> {url};
              errors throw with the server's message (e.status); lists carry .total from X-Total-Count
            Accounts: await Oro.auth.register({email, password, name}), Oro.auth.login({email, password}),
              Oro.auth.logout(), Oro.auth.user, await Oro.auth.require('login.html') at the top of a private page
            Oro.table(el, {columns:[{key, label, num, format}], rows, pageSize, search: inputEl, onRow}).update(rows)
            Oro.chart(el, {type:'bar'|'line', labels, values, format}) into a .chart box
            Tabs: role=tablist > button role=tab aria-controls=panelId (arrow keys work), panels get hidden
        Put project-specific styles in your own CSS using the tokens (var(--brand), var(--s-5), var(--r-lg)...).
    """.trimIndent()
}
