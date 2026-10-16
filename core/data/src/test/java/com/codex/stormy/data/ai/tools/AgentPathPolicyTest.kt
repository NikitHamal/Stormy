package com.codex.stormy.data.ai.tools

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AgentPathPolicyTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun root() = tmp.root

    @Test
    fun `ordinary project file is allowed`() {
        assertThat(AgentPathPolicy.isAllowed("index.html", root())).isTrue()
        assertThat(AgentPathPolicy.isAllowed("src/app/main.js", root())).isTrue()
    }

    @Test
    fun `codex directory is refused`() {
        assertThat(AgentPathPolicy.check(".codex", root()))
            .isInstanceOf(AgentPathPolicy.Denial.Reserved::class.java)
        assertThat(AgentPathPolicy.check(".codex/raw_sse.log", root()))
            .isInstanceOf(AgentPathPolicy.Denial.Reserved::class.java)
        assertThat(AgentPathPolicy.check(".codex/memory/notes.json", root()))
            .isInstanceOf(AgentPathPolicy.Denial.Reserved::class.java)
    }

    @Test
    fun `codex cannot be reached by traversal either`() {
        // Checked on the canonical path, so a route that resolves back into
        // .codex is refused however it is spelled.
        assertThat(AgentPathPolicy.isAllowed("src/../.codex/logs.txt", root())).isFalse()
        assertThat(AgentPathPolicy.isAllowed("./.codex/./logs.txt", root())).isFalse()
    }

    @Test
    fun `git directory is refused`() {
        assertThat(AgentPathPolicy.isAllowed(".git/config", root())).isFalse()
    }

    @Test
    fun `a file merely starting with dot-codex is still allowed`() {
        // ".codexrc" is a normal project file; only the directory is reserved.
        assertThat(AgentPathPolicy.isAllowed(".codexrc", root())).isTrue()
    }

    @Test
    fun `paths escaping the project are refused`() {
        assertThat(AgentPathPolicy.check("../secrets.txt", root()))
            .isInstanceOf(AgentPathPolicy.Denial.OutsideProject::class.java)
        assertThat(AgentPathPolicy.isAllowed("/etc/passwd", root())).isFalse()
    }

    @Test
    fun `a sibling directory sharing a name prefix is not treated as inside`() {
        // "/projects/site-backup" must not pass as a child of "/projects/site";
        // a bare startsWith on the root would let it through.
        val site = tmp.newFolder("site")
        tmp.newFolder("site-backup")
        assertThat(AgentPathPolicy.isAllowed("../site-backup/notes.txt", site)).isFalse()
    }

    @Test
    fun `null bytes are refused`() {
        assertThat(AgentPathPolicy.check("index\u0000.html", root()))
            .isInstanceOf(AgentPathPolicy.Denial.Malformed::class.java)
    }

    @Test
    fun `reserved names are hidden from listings`() {
        assertThat(AgentPathPolicy.isVisible(".codex")).isFalse()
        assertThat(AgentPathPolicy.isVisible(".git")).isFalse()
        assertThat(AgentPathPolicy.isVisible("src")).isTrue()
    }

    @Test
    fun `tool arguments are screened by conventional parameter name`() {
        val blocked = buildJsonObject {
            put("content", JsonPrimitive("hello"))
            put("file_path", JsonPrimitive(".codex/logs.txt"))
        }
        assertThat(AgentPathPolicy.denyIfAnyPathBlocked(blocked, root())).isNotNull()

        val allowed = buildJsonObject {
            put("content", JsonPrimitive("hello"))
            put("file_path", JsonPrimitive("index.html"))
        }
        assertThat(AgentPathPolicy.denyIfAnyPathBlocked(allowed, root())).isNull()
    }

    @Test
    fun `rename style arguments are screened on both ends`() {
        val sneaky = buildJsonObject {
            put("source", JsonPrimitive("index.html"))
            put("destination", JsonPrimitive(".codex/index.html"))
        }
        assertThat(AgentPathPolicy.denyIfAnyPathBlocked(sneaky, root())).isNotNull()
    }

    @Test
    fun `non-path arguments are left alone`() {
        val args = buildJsonObject { put("query", JsonPrimitive("../../etc/passwd")) }
        assertThat(AgentPathPolicy.denyIfAnyPathBlocked(args, root())).isNull()
    }
}
