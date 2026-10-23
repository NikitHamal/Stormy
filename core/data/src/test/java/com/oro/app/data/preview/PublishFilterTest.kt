package com.oro.app.data.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PublishFilterTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `db json goes out with its sample data and none of its secrets`() {
        val clean = PublishFilter.sanitize(
            """{"users":[{"id":1,"email":"a@b.c","password":"demo123","passwordHash":"pbkdf2:1:aa:bb"}],""" +
                """"posts":[{"id":1,"title":"Hello","meta":{"confirmPassword":"x"}}],""" +
                """"_sessions":{"t":{"userId":1}},"_resetCodes":[],""" +
                """"uploads":[{"id":1,"url":"/uploads/1-me.png"},{"id":2,"url":"https://cdn.site/a.png"}]}"""
        )!!
        assertThat(clean).contains("a@b.c")
        assertThat(clean).contains("Hello")
        assertThat(clean).doesNotContain("demo123")
        assertThat(clean).doesNotContain("pbkdf2")
        assertThat(clean).doesNotContain("confirmPassword")
        assertThat(clean).doesNotContain("_sessions")
        assertThat(clean).doesNotContain("_resetCodes")
        assertThat(clean).doesNotContain("1-me.png")
        assertThat(clean).contains("cdn.site")
        assertThat(PublishFilter.sanitize("not json")).isNull()
    }

    @Test
    fun `uploads stay home and db json is swapped for the clean copy`() {
        val root = tmp.root
        File(root, "index.html").writeText("<h1>Hi</h1>")
        File(root, "db.json").writeText("""{"users":[{"id":1,"password":"x1"}]}""")
        File(root, "uploads").mkdirs()
        File(root, "uploads/1-cat.jpg").writeText("jpg")
        val files = listOf("index.html", "db.json", "uploads/1-cat.jpg", "db.json.tmp").map { it to File(root, it) }

        val out = PublishFilter.apply(root, files)

        assertThat(out.map { it.first }).containsExactly("index.html", "db.json")
        val db = out.first { it.first == "db.json" }.second
        assertThat(db.canonicalPath).isNotEqualTo(File(root, "db.json").canonicalPath)
        assertThat(db.readText()).doesNotContain("x1")
        // A db.json that is not JSON is not published at all: it could hold anything.
        File(root, "db.json").writeText("{ broken")
        assertThat(PublishFilter.apply(root, files).map { it.first }).containsExactly("index.html")
    }
}
