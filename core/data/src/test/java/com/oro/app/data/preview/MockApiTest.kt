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
        // The hash is stored outside db.json (a deploy publishes db.json) and never served.
        assertThat(File(tmp.root, "db.json").readText()).doesNotContain("password")
        assertThat(File(tmp.root, ".oro/mock-auth.json").readText()).contains("pbkdf2:")
        assertThat(a.handle("GET", "/api/users", null).body).doesNotContain("password")

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
        // Sessions are internal, and not in the project's data at all.
        assertThat(a.handle("GET", "/api/_sessions", null).status).isEqualTo(404)
        assertThat(a.handle("GET", "/api", null).body).doesNotContain("_sessions")
        assertThat(File(tmp.root, "db.json").readText()).doesNotContain("_sessions")
    }

    @Test
    fun `hashes and sessions an older version wrote into db json move out and keep working`() {
        val salt = "abc123"
        val hex = java.security.MessageDigest.getInstance("SHA-256").digest((salt + "matrix").toByteArray())
            .joinToString("") { "%02x".format(it) }
        val a = api(
            """{"users":[{"id":1,"username":"neo","passwordHash":"sha256:$salt:$hex"}],""" +
                """"_sessions":{"tok1":{"userId":1,"createdAt":"2026-01-01T00:00:00Z"}}}"""
        )
        assertThat(a.handle("GET", "/api/auth/me", null, mapOf("Authorization" to "Bearer tok1")).status).isEqualTo(200)
        val db = File(tmp.root, "db.json").readText()
        assertThat(db).doesNotContain("passwordHash")
        assertThat(db).doesNotContain("_sessions")
        // The old SHA-256 hash still logs in, and is upgraded to PBKDF2.
        assertThat(a.handle("POST", "/api/auth/login", """{"username":"neo","password":"matrix"}""").status).isEqualTo(200)
        assertThat(File(tmp.root, ".oro/mock-auth.json").readText()).doesNotContain("sha256:")
        assertThat(a.handle("POST", "/api/auth/login", """{"username":"neo","password":"wrong"}""").status).isEqualTo(401)
    }

    @Test
    fun `a seeded plain password logs in once, then lives on only as a hash`() {
        val a = api("""{"users":[{"id":1,"email":"demo@site.com","name":"Demo","password":"demo123"}]}""")
        assertThat(a.handle("GET", "/api/users", null).body).doesNotContain("demo123")
        assertThat(a.handle("POST", "/api/auth/login", """{"email":"demo@site.com","password":"nope"}""").status).isEqualTo(401)
        assertThat(a.handle("POST", "/api/auth/login", """{"email":"demo@site.com","password":"demo123"}""").status).isEqualTo(200)
        assertThat(File(tmp.root, "db.json").readText()).doesNotContain("demo123")
        assertThat(a.handle("POST", "/api/auth/login", """{"email":"demo@site.com","password":"demo123"}""").status).isEqualTo(200)
    }

    @Test
    fun `a password sent in a patch is hashed, never stored as text`() {
        val a = api("""{"users":[{"id":1,"email":"ana@site.com","password":"old1"}]}""")
        val r = a.handle("PATCH", "/api/users/1", """{"name":"Ana","password":"new22","confirmPassword":"new22"}""")
        assertThat(r.body).doesNotContain("new22")
        assertThat(File(tmp.root, "db.json").readText()).doesNotContain("new22")
        assertThat(File(tmp.root, "db.json").readText()).doesNotContain("old1")
        assertThat(a.handle("POST", "/api/auth/login", """{"email":"ana@site.com","password":"new22"}""").status).isEqualTo(200)
        assertThat(a.handle("POST", "/api/auth/login", """{"email":"ana@site.com","password":"old1"}""").status).isEqualTo(401)
        // A patch without a password keeps the one there is.
        a.handle("PATCH", "/api/users/1", """{"name":"Ana B"}""")
        assertThat(a.handle("POST", "/api/auth/login", """{"email":"ana@site.com","password":"new22"}""").status).isEqualTo(200)
    }

    @Test
    fun `secrets cannot be searched, filtered, sorted or embedded`() {
        val a = api(
            """{"users":[{"id":1,"email":"a@b.c","password":"hunter2"},{"id":2,"email":"d@e.f","password":"pass99"}],""" +
                """"posts":[{"id":1,"userId":1,"title":"Hi"}],"_resetCodes":[{"id":1,"postId":1,"code":"9911"}]}"""
        )
        assertThat(a.handle("GET", "/api/users?q=hunter2", null).body.trim()).isEqualTo("[]")
        assertThat(a.handle("GET", "/api/users?password_like=hun", null).status).isEqualTo(400)
        assertThat(a.handle("GET", "/api/users?passwordHash_like=sha", null).status).isEqualTo(400)
        assertThat(a.handle("GET", "/api/users?password_ne=x", null).status).isEqualTo(400)
        assertThat(a.handle("GET", "/api/users?_sort=password", null).status).isEqualTo(400)
        assertThat(a.handle("GET", "/api/posts/1?_embed=_resetCodes", null).body).doesNotContain("9911")
        assertThat(a.handle("GET", "/api/posts/1?_expand=user", null).body).doesNotContain("hunter2")
        assertThat(a.handle("GET", "/api/users?email=a@b.c", null).body).contains("a@b.c")
    }

    @Test
    fun `a deleted user's password and sessions go with it`() {
        val a = api("""{"users":[]}""")
        val t = token(a.handle("POST", "/api/auth/register", """{"username":"neo","password":"matrix"}""").body)
        assertThat(a.handle("DELETE", "/api/users/1", null).status).isEqualTo(204)
        assertThat(a.handle("GET", "/api/auth/me", null, mapOf("Authorization" to "Bearer $t")).status).isEqualTo(401)
        // A new user that gets the same id inherits nothing.
        a.handle("POST", "/api/users", """{"username":"trinity"}""")
        assertThat(a.handle("POST", "/api/auth/login", """{"username":"trinity","password":"matrix"}""").status).isEqualTo(401)
    }

    @Test
    fun `the upload limit fits a phone photo sent as a data url`() {
        assertThat(MockApi.MAX_UPLOAD_BYTES).isAtLeast(8_000_000)
        assertThat(MockApi.MAX_BODY_BYTES.toLong()).isAtLeast(MockApi.MAX_UPLOAD_BYTES.toLong() * 4 / 3)
    }

    @Test
    fun `a patch from the page keeps the password`() {
        val a = api("""{"users":[]}""")
        a.handle("POST", "/api/auth/register", """{"username":"neo","password":"matrix"}""")
        a.handle("PATCH", "/api/users/1", """{"name":"Thomas"}""")
        a.handle("PUT", "/api/users/1", """{"username":"neo","name":"Thomas A."}""")
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
