package com.oro.app.data.ai.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProjectSearchTest {
    @Test
    fun `finds code by meaning-adjacent words and identifiers`() {
        val root = Files.createTempDirectory("ps").toFile()
        File(root, "index.html").writeText("<header class=\"navbar\">\n<a href=\"/\">Home</a>\n</header>\n<main>\n<section class=\"plans\">\n<h2>Pricing</h2>\n</section>\n</main>")
        File(root, "app.js").writeText("function toggleMobileMenu() {\n  document.body.classList.toggle('menu-open')\n}")
        File(root, ".oro").mkdirs(); File(root, ".oro/secret.md").writeText("navbar navbar navbar")
        val s = ProjectSearch(root)
        assertThat(s.search("pricing table").first().path).isEqualTo("index.html")
        assertThat(s.search("mobile menu").first().path).isEqualTo("app.js")
        assertThat(s.search("nav").map { it.path }).doesNotContain(".oro/secret.md")
        assertThat(ProjectSearch.tokens("toggleMobileMenu nav-bar_item")).containsAtLeast("toggle", "mobile", "menu", "nav", "bar", "item")
    }
}
