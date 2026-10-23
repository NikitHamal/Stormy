package com.oro.app.data.ai.mcp

import com.oro.app.data.ai.AiHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class McpOAuthTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    /** Epoch ms; 0 when the server gave no expiry. */
    val expiresAt: Long = 0,
    val tokenEndpoint: String,
    val clientId: String,
    val resource: String
)

/**
 * MCP authorization (spec 2025-06-18): OAuth 2.1 with PKCE, discovered from
 * the server's protected-resource metadata, a dynamically registered public
 * client, and the system browser for consent. Tokens are refreshed on 401.
 *
 * Flow: [begin] returns the URL to open; the browser redirects to
 * [REDIRECT_URI], which the app's callback activity hands to [complete].
 */
object McpOAuth {
    const val REDIRECT_URI = "oro-mcp://callback"

    private data class Pending(val serverId: String, val verifier: String, val clientId: String, val tokenEndpoint: String, val resource: String)
    private val pending = ConcurrentHashMap<String, Pending>()
    private val random = SecureRandom()

    suspend fun begin(server: McpServerConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val resourceMeta = discoverResourceMetadata(server.url)
            val asUrl = (resourceMeta?.get("authorization_servers") as? JsonArray)?.firstOrNull()?.let { (it as JsonPrimitive).content }
                ?: origin(server.url)
            val asMeta = getJson(wellKnown(asUrl, "oauth-authorization-server")) ?: getJson(wellKnown(asUrl, "openid-configuration"))
                ?: error("${server.name} does not publish OAuth metadata; use a token instead.")
            val authorize = asMeta.str("authorization_endpoint") ?: error("No authorization endpoint")
            val token = asMeta.str("token_endpoint") ?: error("No token endpoint")
            val clientId = register(asMeta.str("registration_endpoint") ?: error("${server.name} does not allow app sign-in (no client registration); use a token."))
            val verifier = randomString(64)
            val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
            )
            val state = randomString(24)
            val scopes = (resourceMeta?.get("scopes_supported") as? JsonArray)?.joinToString(" ") { (it as JsonPrimitive).content }
            pending[state] = Pending(server.id, verifier, clientId, token, server.url)
            authorize.toHttpUrl().newBuilder()
                .addQueryParameter("response_type", "code")
                .addQueryParameter("client_id", clientId)
                .addQueryParameter("redirect_uri", REDIRECT_URI)
                .addQueryParameter("code_challenge", challenge)
                .addQueryParameter("code_challenge_method", "S256")
                .addQueryParameter("state", state)
                .addQueryParameter("resource", server.url)
                .apply { if (!scopes.isNullOrBlank()) addQueryParameter("scope", scopes) }
                .build().toString()
        }
    }

    /** Handle the redirect; returns the server id and its new tokens. */
    suspend fun complete(redirect: String): Result<Pair<String, McpOAuthTokens>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = redirect.replace("oro-mcp://callback", "https://callback.invalid/").toHttpUrl()
            url.queryParameter("error")?.let { error("Sign-in was declined: $it") }
            val state = url.queryParameter("state") ?: error("Missing state")
            val code = url.queryParameter("code") ?: error("Missing code")
            val p = pending.remove(state) ?: error("This sign-in expired. Start again.")
            p.serverId to exchange(
                p.tokenEndpoint, p.clientId, p.resource,
                FormBody.Builder().add("grant_type", "authorization_code").add("code", code)
                    .add("redirect_uri", REDIRECT_URI).add("client_id", p.clientId)
                    .add("code_verifier", p.verifier).add("resource", p.resource).build()
            )
        }
    }

    /** Refresh an expired access token; null when there is no refresh token or it was refused. */
    fun refresh(tokens: McpOAuthTokens): McpOAuthTokens? {
        val rt = tokens.refreshToken ?: return null
        return runCatching {
            exchange(
                tokens.tokenEndpoint, tokens.clientId, tokens.resource,
                FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", rt)
                    .add("client_id", tokens.clientId).add("resource", tokens.resource).build()
            ).let { if (it.refreshToken == null) it.copy(refreshToken = rt) else it }
        }.getOrNull()
    }

    private fun exchange(endpoint: String, clientId: String, resource: String, form: FormBody): McpOAuthTokens {
        val r = AiHttp.client.newCall(Request.Builder().url(endpoint).post(form).header("Accept", "application/json").build()).execute()
        val body = r.use { it.body?.string().orEmpty() }
        if (!r.isSuccessful) error("Token exchange failed (${r.code}): ${body.take(200)}")
        val o = AiHttp.json.parseToJsonElement(body) as JsonObject
        val expires = (o["expires_in"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        return McpOAuthTokens(
            accessToken = o.str("access_token") ?: error("No access token"),
            refreshToken = o.str("refresh_token"),
            expiresAt = expires?.let { System.currentTimeMillis() + it * 1000 } ?: 0,
            tokenEndpoint = endpoint, clientId = clientId, resource = resource
        )
    }

    private fun register(endpoint: String): String {
        val body = buildJsonObject {
            put("client_name", "Oro")
            putJsonArray("redirect_uris") { add(REDIRECT_URI) }
            putJsonArray("grant_types") { add("authorization_code"); add("refresh_token") }
            putJsonArray("response_types") { add("code") }
            put("token_endpoint_auth_method", "none")
        }
        val r = AiHttp.client.newCall(
            Request.Builder().url(endpoint).post(body.toString().toRequestBody(AiHttp.jsonMediaType)).build()
        ).execute()
        val text = r.use { it.body?.string().orEmpty() }
        if (!r.isSuccessful) error("Client registration failed (${r.code})")
        return (AiHttp.json.parseToJsonElement(text) as JsonObject).str("client_id") ?: error("No client id")
    }

    /** 401 → WWW-Authenticate resource_metadata, else the well-known path. */
    private fun discoverResourceMetadata(serverUrl: String): JsonObject? {
        val probe = AiHttp.client.newCall(
            Request.Builder().url(serverUrl)
                .post("{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"ping\"}".toRequestBody(AiHttp.jsonMediaType))
                .header("Accept", "application/json, text/event-stream").build()
        ).execute()
        val header = probe.use { it.header("WWW-Authenticate") }
        val metaUrl = header?.let { Regex("resource_metadata=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
        metaUrl?.let { getJson(it)?.let { m -> return m } }
        val u = serverUrl.toHttpUrl()
        return getJson(origin(serverUrl) + "/.well-known/oauth-protected-resource" + u.encodedPath.trimEnd('/'))
            ?: getJson(origin(serverUrl) + "/.well-known/oauth-protected-resource")
    }

    private fun getJson(url: String): JsonObject? = runCatching {
        AiHttp.client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { r ->
            if (!r.isSuccessful) null else AiHttp.json.parseToJsonElement(r.body?.string().orEmpty()) as? JsonObject
        }
    }.getOrNull()

    private fun origin(url: String): String = url.toHttpUrl().let { "${it.scheme}://${it.host}" + if (it.port != 443 && it.port != 80) ":${it.port}" else "" }

    private fun wellKnown(issuer: String, name: String): String {
        val u = issuer.toHttpUrl()
        val path = u.encodedPath.trimEnd('/')
        return origin(issuer) + "/.well-known/$name" + path
    }

    private fun randomString(n: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        return (1..n).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
}
