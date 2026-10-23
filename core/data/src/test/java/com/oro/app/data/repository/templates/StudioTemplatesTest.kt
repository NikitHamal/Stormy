package com.oro.app.data.repository.templates

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.oro.app.data.ai.AiHttp
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StudioTemplatesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `every starter lists files and has an index page`() {
        StudioTemplates.all.forEach { starter ->
            val files = StudioTemplates.files(starter)
            assertThat(files).contains("index.html")
            files.forEach { assertThat(StudioTemplates.read(starter, it)).isNotEmpty() }
        }
    }

    @Test
    fun `install writes the starter and the UI kit in its hue`() {
        StudioTemplates.all.forEach { starter ->
            val root = tmp.newFolder(starter.id)
            StudioTemplates.install(root, starter)
            StudioTemplates.files(starter).forEach { assertThat(File(root, it).isFile).isTrue() }
            assertThat(File(root, "oro-ui/oro.js").isFile).isTrue()
            assertThat(File(root, "oro-ui/oro.css").readText()).contains("--brand-h:${starter.brandHue};")
        }
    }

    @Test
    fun `every local link and script in a starter resolves`() {
        val ref = Regex("""(?:href|src)="([^"#:?]+)"""")
        StudioTemplates.all.forEach { starter ->
            val root = tmp.newFolder("links-" + starter.id)
            StudioTemplates.install(root, starter)
            StudioTemplates.files(starter).filter { it.endsWith(".html") }.forEach { page ->
                val dir = File(root, page).parentFile!!
                ref.findAll(File(root, page).readText()).map { it.groupValues[1] }
                    .filter { it.isNotBlank() && !it.startsWith("//") }
                    .forEach { path -> assertWithMessage("${starter.id}/$page -> $path").that(File(dir, path).exists()).isTrue() }
            }
        }
    }

    @Test
    fun `seed data is valid JSON with the collections the pages ask for`() {
        val dashboard = AiHttp.json.parseToJsonElement(StudioTemplates.read(StudioTemplates.DASHBOARD, "db.json")).toString()
        assertThat(dashboard).contains("\"orders\"")
        val store = AiHttp.json.parseToJsonElement(StudioTemplates.read(StudioTemplates.STORE, "db.json")).toString()
        assertThat(store).contains("\"products\"")
    }

    @Test
    fun `no starter uses dollar-brace templates the server could misread`() {
        // Pages are served as-is; this guards the resources against a stray Kotlin-style template.
        StudioTemplates.all.forEach { starter ->
            StudioTemplates.files(starter).filter { it.endsWith(".html") }.forEach { page ->
                assertThat(StudioTemplates.read(starter, page)).doesNotContain("\${")
            }
        }
    }
}
