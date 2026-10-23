package com.oro.app.data.ai.tools

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectOutlineTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `html lists ids and what the page loads, not data attributes`() {
        val html = """
            <!DOCTYPE html><html><head><link rel="stylesheet" href="css/app.css"></head>
            <body><header id="top"></header><input id="search" data-product-id="7">
            <ul id="list"></ul><script src="js/app.js"></script>
            <script>function boot() { document.getElementById('x'); }</script></body></html>
        """.trimIndent()
        val o = ProjectOutline.of("index.html", html)!!
        assertThat(o).contains("ids: #top, #search, #list")
        assertThat(o).contains("loads: js/app.js, css/app.css")
        assertThat(o).contains("inline js: boot")
        assertThat(o).doesNotContain("#7")
    }

    @Test
    fun `css lists selectors, custom properties and at-rules`() {
        val css = """
            /* .commented { } */
            :root { --accent: #f0f; --radius: 12px; }
            .card, .card:hover { color: red; }
            @media (max-width: 600px) { .card { padding: 0; } }
            @keyframes pop { from { opacity: 0 } to { opacity: 1 } }
        """.trimIndent()
        val o = ProjectOutline.of("app.css", css)!!
        assertThat(o).contains("selectors: :root, .card, .card:hover")
        assertThat(o).contains("vars: --accent, --radius")
        assertThat(o).contains("@rules: 2")
        assertThat(o).doesNotContain(".commented")
        assertThat(o).doesNotContain("from")
    }

    @Test
    fun `js lists functions, classes, events and fetched urls`() {
        val js = """
            class Store { load() {} }
            async function loadTodos() { const r = await fetch('/api/todos'); }
            const render = (items) => items.map(i => i);
            const onSave = async e => {};
            document.querySelector('#f').addEventListener('submit', onSave);
            window.addEventListener("scroll", () => {});
        """.trimIndent()
        val o = ProjectOutline.of("app.js", js)!!
        assertThat(o).contains("functions: loadTodos, render, onSave")
        assertThat(o).contains("classes: Store")
        assertThat(o).contains("listens: submit, scroll")
        assertThat(o).contains("fetches: /api/todos")
    }

    @Test
    fun `json shows its keys and lines are bounded`() {
        assertThat(ProjectOutline.of("db.json", """{"todos": [], "users": []}""")).contains("keys: todos, users")
        assertThat(ProjectOutline.of("bad.json", """{"a": """)).contains("not valid JSON")
        val many = (1..200).joinToString("\n") { "function f$it() {}" }
        assertThat(ProjectOutline.of("big.js", many)!!.length).isAtMost(170)
    }

    @Test
    fun `list_files with symbols outlines each file`() {
        File(tmp.root, "index.html").writeText("""<html><body><main id="app"></main><script src="app.js"></script></body></html>""")
        File(tmp.root, "app.js").writeText("function start() { fetch('/api/items') }")
        File(tmp.root, "logo.png").writeBytes(byteArrayOf(1, 2, 3))
        val ws = WorkspaceTools(tmp.root)
        val plain = ws.listFiles(JsonObject(emptyMap())).result.output
        assertThat(plain).doesNotContain("functions")
        val out = ws.listFiles(JsonObject(mapOf("symbols" to JsonPrimitive(true)))).result.output
        assertThat(out).contains("app.js — 1 lines · functions: start · fetches: /api/items")
        assertThat(out).contains("index.html — 1 lines · ids: #app · loads: app.js")
        assertThat(out).contains("logo.png")
        assertThat(out).doesNotContain("logo.png —")
    }
}
