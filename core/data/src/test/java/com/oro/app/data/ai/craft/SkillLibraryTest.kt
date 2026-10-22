package com.oro.app.data.ai.craft

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.skills.Skills
import org.junit.Test
import java.io.File

class SkillLibraryTest {

    private val craft = listOf("design-system", "motion", "ui-states", "landing-page", "web-app", "backend", "forms", "game", "three-d")

    @Test
    fun `every craft playbook ships as a built-in skill with a description and body`() {
        val byName = Skills.BUILT_IN.associateBy { it.name }
        craft.forEach { name ->
            val skill = byName[name]
            assertThat(skill).isNotNull()
            assertThat(skill!!.description.length).isGreaterThan(20)
            assertThat(skill.body.length).isGreaterThan(400)
        }
    }

    @Test
    fun `the catalogue lists the craft skills`() {
        val catalogue = Skills.catalogue(Skills.BUILT_IN)
        craft.forEach { assertThat(catalogue).contains("- $it: ") }
    }

    @Test
    fun `ui kit installs and re-themes`() {
        val dir = kotlin.io.path.createTempDirectory().toFile()
        val written = UiKit.install(dir, 160)
        assertThat(written.map { it.first }).containsExactly("oro-ui/oro.css", "oro-ui/oro.js")
        assertThat(File(dir, "oro-ui/oro.css").readText()).contains("--brand-h:160;")
        assertThat(JsBalance.check(File(dir, "oro-ui/oro.js").readText())).isNull()
        dir.deleteRecursively()
    }
}
