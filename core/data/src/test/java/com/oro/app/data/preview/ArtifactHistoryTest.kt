package com.oro.app.data.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArtifactHistoryTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `identical content is not a new version`() {
        val h = ArtifactHistory()
        assertThat(h.record("index.html", "a")).isNotNull()
        assertThat(h.record("index.html", "a")).isNull()
        assertThat(h.hasHistory("index.html")).isFalse()
        h.record("index.html", "b")
        assertThat(h.hasHistory("index.html")).isTrue()
        assertThat(h.revisions("/index.html").map { it.number }).containsExactly(1, 2).inOrder()
    }

    @Test
    fun `old versions fall off but numbers never repeat`() {
        val h = ArtifactHistory(maxPerFile = 3)
        for (i in 1..6) h.record("a.css", "v$i")
        assertThat(h.revisions("a.css").map { it.number }).containsExactly(4, 5, 6).inOrder()
        assertThat(ArtifactHistory.label(0, 3, 4)).isEqualTo("v4")
        assertThat(ArtifactHistory.label(2, 3, 6)).isEqualTo("Latest")
    }

    @Test
    fun `diff marks added and removed lines with numbers`() {
        val d = LineDiff.diff("a\nb\nc\n", "a\nB\nc\nd\n")
        assertThat(d.map { it.kind to it.text }).containsExactly(
            DiffKind.SAME to "a",
            DiffKind.REMOVED to "b",
            DiffKind.ADDED to "B",
            DiffKind.SAME to "c",
            DiffKind.ADDED to "d"
        ).inOrder()
        assertThat(LineDiff.added(d)).isEqualTo(2)
        assertThat(LineDiff.removed(d)).isEqualTo(1)
        assertThat(d.first { it.text == "b" }.oldNumber).isEqualTo(2)
        assertThat(d.first { it.text == "d" }.newNumber).isEqualTo(4)
    }

    @Test
    fun `diff of empty and equal texts`() {
        assertThat(LineDiff.diff("", "")).isEmpty()
        assertThat(LineDiff.diff("x", "x").single().kind).isEqualTo(DiffKind.SAME)
        assertThat(LineDiff.diff("", "x\ny").map { it.kind }).containsExactly(DiffKind.ADDED, DiffKind.ADDED)
        assertThat(LineDiff.diff("x\r\ny", "x\ny").all { it.kind == DiffKind.SAME }).isTrue()
    }

    @Test
    fun `context folds long unchanged runs`() {
        val old = (1..30).joinToString("\n") { "line $it" }
        val new = old.replace("line 15", "changed")
        val folded = LineDiff.withContext(LineDiff.diff(old, new), 2)
        assertThat(folded.count { it.kind == DiffKind.SKIPPED }).isEqualTo(2)
        assertThat(folded.first().kind).isEqualTo(DiffKind.SKIPPED)
        assertThat(folded.map { it.text }).contains("changed")
        assertThat(folded.size).isLessThan(15)
    }

    @Test
    fun `history diff compares two indexes`() {
        val h = ArtifactHistory()
        h.record("p.html", "one\n")
        h.record("p.html", "one\ntwo\n")
        val d = h.diff("p.html", 0, 1)
        assertThat(LineDiff.added(d)).isEqualTo(1)
        assertThat(h.diff("missing.html", 0, 1)).isEmpty()
    }

    @Test
    fun `store captures tracked files inside the project only`() {
        val root = tmp.newFolder("proj")
        File(root, "index.html").writeText("<h1>1</h1>")
        File(root, "notes.bin").writeText("x")
        File(tmp.root, "outside.html").writeText("secret")
        ArtifactHistoryStore.capture("p1", root, "index.html")
        File(root, "index.html").writeText("<h1>2</h1>")
        ArtifactHistoryStore.capture("p1", root, "index.html")
        ArtifactHistoryStore.capture("p1", root, "notes.bin")
        ArtifactHistoryStore.capture("p1", root, "../outside.html")
        val h = ArtifactHistoryStore.forProject("p1")
        assertThat(h.revisions("index.html")).hasSize(2)
        assertThat(h.revisions("notes.bin")).isEmpty()
        assertThat(h.revisions("../outside.html")).isEmpty()
        ArtifactHistoryStore.clear("p1")
        assertThat(ArtifactHistoryStore.forProject("p1").revisions("index.html")).isEmpty()
    }
}
