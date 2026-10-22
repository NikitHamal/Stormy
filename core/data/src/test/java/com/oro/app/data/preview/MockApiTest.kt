package com.oro.app.data.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MockApiTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun api(db: String? = """{"todos":[{"id":1,"title":"Ship","done":false},{"id":2,"title":"Test","done":true}],"profile":{"name":"Ana"}}"""): MockApi {
        if (db != null) File(tmp.root, "db.json").writeText(db)
        return MockApi(tmp.root)
    }

    @Test
    fun `lists, filters, sorts and paginates`() {
        val a = api()
        assertThat(a.handle("GET", "/api/todos", null).body).contains("Ship")
        val done = a.handle("GET", "/api/todos?done=true", null)
        assertThat(done.body).contains("Test")
        assertThat(done.body).doesNotContain("Ship")
        assertThat(done.headers["X-Total-Count"]).isEqualTo("1")
        val sorted = a.handle("GET", "/api/todos?_sort=title&_order=desc", null).body
        assertThat(sorted.indexOf("Test")).isLessThan(sorted.indexOf("Ship"))
        val page = a.handle("GET", "/api/todos?_page=2&_limit=1", null)
        assertThat(page.body).contains("Test")
        assertThat(page.headers["X-Total-Count"]).isEqualTo("2")
        assertThat(a.handle("GET", "/api/todos?q=shi", null).body).contains("Ship")
    }

    @Test
    fun `create assigns the next id and persists`() {
        val a = api()
        val r = a.handle("POST", "/api/todos", """{"title":"Café ☕","done":false}""")
        assertThat(r.status).isEqualTo(201)
        assertThat(r.body).contains("\"id\": 3")
        assertThat(File(tmp.root, "db.json").readText()).contains("Café ☕")
        assertThat(MockApi(tmp.root).handle("GET", "/api/todos/3", null).body).contains("Café")
    }

    @Test
    fun `patch merges, put replaces, delete removes`() {
        val a = api()
        assertThat(a.handle("PATCH", "/api/todos/1", """{"done":true}""").body).contains("Ship")
        val put = a.handle("PUT", "/api/todos/1", """{"title":"New"}""").body
        assertThat(put).contains("New")
        assertThat(put).doesNotContain("done")
        assertThat(a.handle("DELETE", "/api/todos/1", null).status).isEqualTo(204)
        assertThat(a.handle("GET", "/api/todos/1", null).status).isEqualTo(404)
    }

    @Test
    fun `singletons, new collections and errors`() {
        val a = api()
        assertThat(a.handle("PATCH", "/api/profile", """{"city":"Pokhara"}""").body).contains("Pokhara")
        assertThat(a.handle("POST", "/api/notes", """{"text":"hi"}""").status).isEqualTo(201)
        assertThat(a.handle("GET", "/api", null).body).contains("notes")
        assertThat(a.handle("GET", "/api/missing", null).status).isEqualTo(404)
        assertThat(a.handle("POST", "/api/todos", "not json").status).isEqualTo(400)
    }

    @Test
    fun `works with no db file`() {
        val a = api(db = null)
        assertThat(a.handle("GET", "/api", null).status).isEqualTo(200)
        assertThat(a.handle("POST", "/api/items", """{"name":"x"}""").status).isEqualTo(201)
        assertThat(File(tmp.root, "db.json").exists()).isTrue()
    }
}
