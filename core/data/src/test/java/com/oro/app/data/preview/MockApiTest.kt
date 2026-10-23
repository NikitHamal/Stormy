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

    private fun token(body: String) = Regex(""""token"\s*:\s*"([0-9a-f]+)"""").find(body)!!.groupValues[1]

    @Test
    fun `register, log in, read the session and log out`() {
        val a = api("""{"users":[]}""")
        val reg = a.handle("POST", "/api/auth/register", """{"email":"Ana@Example.com","password":"secret1","name":"Ana"}""")
        assertThat(reg.status).isEqualTo(201)
        assertThat(reg.body).contains("\"email\": \"ana@example.com\"")
        assertThat(reg.body).doesNotContain("password")
        // The hash is stored but never served.
        assertThat(File(tmp.root, "db.json").readText()).contains("passwordHash")
        assertThat(a.handle("GET", "/api/users", null).body).doesNotContain("passwordHash")

        assertThat(a.handle("POST", "/api/auth/register", """{"email":"ana@example.com","password":"other1"}""").status).isEqualTo(409)
        assertThat(a.handle("POST", "/api/auth/login", """{"email":"ana@example.com","password":"wrong"}""").status).isEqualTo(401)

        val login = a.handle("POST", "/api/auth/login", """{"email":"ANA@example.com","password":"secret1"}""")
        assertThat(login.status).isEqualTo(200)
        val t = token(login.body)
        val me = a.handle("GET", "/api/auth/me", null, mapOf("Authorization" to "Bearer $t"))
        assertThat(me.status).isEqualTo(200)
        assertThat(me.body).contains("Ana")
        assertThat(a.handle("GET", "/api/auth/me", null).status).isEqualTo(401)

        assertThat(a.handle("POST", "/api/auth/logout", null, mapOf("authorization" to "Bearer $t")).status).isEqualTo(204)
        assertThat(a.handle("GET", "/api/auth/me", null, mapOf("Authorization" to "Bearer $t")).status).isEqualTo(401)
        // Sessions are internal.
        assertThat(a.handle("GET", "/api/_sessions", null).status).isEqualTo(404)
        assertThat(a.handle("GET", "/api", null).body).doesNotContain("_sessions")
    }

    @Test
    fun `a patch from the page keeps the password hash`() {
        val a = api("""{"users":[]}""")
        a.handle("POST", "/api/auth/register", """{"username":"neo","password":"matrix"}""")
        a.handle("PATCH", "/api/users/1", """{"name":"Thomas"}""")
        assertThat(a.handle("POST", "/api/auth/login", """{"username":"neo","password":"matrix"}""").status).isEqualTo(200)
    }

    @Test
    fun `expand and embed relations like json-server`() {
        val a = api(
            """{"users":[{"id":1,"name":"Ana"}],"posts":[{"id":1,"title":"Hi","userId":1}],""" +
                """"comments":[{"id":1,"postId":1,"body":"First"},{"id":2,"postId":2,"body":"Other"}]}"""
        )
        val post = a.handle("GET", "/api/posts/1?_expand=user&_embed=comments", null).body
        assertThat(post).contains("\"user\"")
        assertThat(post).contains("Ana")
        assertThat(post).contains("First")
        assertThat(post).doesNotContain("Other")
        val list = a.handle("GET", "/api/posts?_embed=comments", null).body
        assertThat(list).contains("First")
    }

    @Test
    fun `uploads save a data url into the project and delete removes the file`() {
        val a = api("""{}""")
        val png = java.util.Base64.getEncoder().encodeToString(byteArrayOf(-119, 80, 78, 71, 1, 2, 3))
        val r = a.handle("POST", "/api/uploads", """{"name":"my photo.png","dataUrl":"data:image/png;base64,$png"}""")
        assertThat(r.status).isEqualTo(201)
        val url = Regex(""""url"\s*:\s*"([^"]+)"""").find(r.body)!!.groupValues[1]
        assertThat(url).startsWith("/uploads/1-my-photo.png")
        val file = File(tmp.root, url.trimStart('/'))
        assertThat(file.readBytes().size).isEqualTo(7)
        assertThat(a.handle("GET", "/api/uploads", null).body).contains("image/png")
        assertThat(a.handle("DELETE", "/api/uploads/1", null).status).isEqualTo(204)
        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `timestamps follow the collection's own fields`() {
        val a = api("""{"notes":[{"id":1,"text":"a","createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z"}],"todos":[]}""")
        assertThat(a.handle("POST", "/api/notes", """{"text":"b"}""").body).contains("createdAt")
        val patched = a.handle("PATCH", "/api/notes/1", """{"text":"c"}""").body
        val updatedAt = Regex(""""updatedAt"\s*:\s*"([^"]+)"""").find(patched)!!.groupValues[1]
        assertThat(updatedAt).isNotEqualTo("2026-01-01T00:00:00Z")
        assertThat(a.handle("POST", "/api/todos", """{"title":"x"}""").body).doesNotContain("createdAt")
    }
}
