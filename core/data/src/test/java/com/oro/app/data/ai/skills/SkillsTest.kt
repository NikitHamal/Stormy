package com.oro.app.data.ai.skills

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.file.Files

class SkillsTest {
    @Test
    fun `front matter round-trips and project skills override by name`() {
        val s = Skills.parse("---\nname: Dark Mode\ndescription: Add a dark theme\n---\n1. Do it", Skill.Source.MINE)!!
        assertThat(s.name).isEqualTo("dark-mode")
        assertThat(Skills.parse(s.toMarkdown(), Skill.Source.MINE)).isEqualTo(s)

        val user = Files.createTempDirectory("u").toFile()
        val project = Files.createTempDirectory("p").toFile()
        Skills.save(java.io.File(project, ".oro/skills"), s.copy(description = "Project version", source = Skill.Source.PROJECT))
        val all = Skills.all(user, project)
        assertThat(all.first { it.name == "dark-mode" }.description).isEqualTo("Project version")
        assertThat(Skills.catalogue(all)).contains("- seo-basics:")
    }

    @Test
    fun `starter files are parsed out of the body and round-trip`() {
        val md = "---\nname: starter\ndescription: A page\n---\nApply the files.\n\n```file:index.html\n<h1>Hi</h1>\n<p>x</p>\n```\n"
        val s = Skills.parse(md, Skill.Source.MINE)!!
        assertThat(s.body).isEqualTo("Apply the files.")
        assertThat(s.files).containsExactly("index.html", "<h1>Hi</h1>\n<p>x</p>")
        assertThat(Skills.parse(s.toMarkdown(), Skill.Source.MINE)).isEqualTo(s)
        val builtIn = Skills.BUILT_IN.first { it.name == "landing-page-starter" }
        assertThat(builtIn.files.keys).containsExactly("index.html", "style.css", "app.js")
        assertThat(Skills.catalogue(listOf(builtIn))).contains("starter files: index.html")
    }
}
