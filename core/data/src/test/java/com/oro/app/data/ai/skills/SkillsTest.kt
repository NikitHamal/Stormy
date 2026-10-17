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
}
