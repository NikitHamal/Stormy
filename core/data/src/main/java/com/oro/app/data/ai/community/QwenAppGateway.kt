package com.oro.app.data.ai.community

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.StreamEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Qwen Android App Gateway (`https://chat.qwen.ai/api/v2` with `Dalvik/2.1.0 ... AliApp(QWENCHAT/2.5.1)`
 * and `api.0mail.tech/v1` automatic account provisioning & cooldown rotation), ported from
 * ZeusDesigns (`src-tauri/src/providers.rs`: `provision_qwen_account`, `acquire_qwen_account`, `stream_qwen_app`).
 */
object QwenAppGateway {

    private const val QWEN_APP_BASE = "https://chat.qwen.ai/api/v2"
    private const val QWEN_MAIL_BASE = "https://api.0mail.tech/v1"
    private const val QWEN_APP_UA =
        "Dalvik/2.1.0 (Linux; U; Android 15; 25028RN03A Build/AP3A.240905.015.A2) " +
            "AliApp(QWENCHAT/2.5.1) AppType/Release AplusBridgeLite," +
            "Dalvik/2.1.0 (Linux; U; Android 15; 25028RN03A Build/AP3A.240905.015.A2)"
    private const val QWEN_APP_WAF = "Z9Tr56YmQpXcO2K_d_3nAbJvRqMLFW8HTNjvRguWHEowM1xY"
    private val JSON = "application/json".toMediaType()

    data class Account(
        val email: String,
        val passwordHash: String,
        val device: String,
        val token: String,
        val createdSec: Long,
        @Volatile var coolUntilSec: Long = 0L
    )

    private val accounts = CopyOnWriteArrayList<Account>()
    @Volatile private var provisionFailedUntilSec: Long = 0L

    private val noRedirectClient: OkHttpClient by lazy {
        AiHttp.client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    }

    fun extractActivateLink(body: String): String? {
        val idx = body.indexOf("auths/activate")
        if (idx < 0) return null
        val before = body.substring(0, idx)
        val httpStart = before.lastIndexOf("http")
        if (httpStart < 0) return null
        val rest = body.substring(httpStart)
        val end = rest.indexOfFirst { it in charArrayOf('"', '\'', '<', '>', ' ', '\r', '\n') }
            .let { if (it < 0) rest.length else it }
        return rest.substring(0, end).replace("&amp;", "&")
    }

    /**
     * Generates Alibaba Cloud OSS V4 `Authorization` header (`OSS4-HMAC-SHA256`) for
     * Qwen Android App Gateway multimodal attachment uploads.
     */
    fun signOssV4Header(
        accessKeyId: String,
        accessKeySecret: String,
        securityToken: String,
        bucketName: String,
        filePath: String,
        mimeType: String,
        unixSeconds: Long = System.currentTimeMillis() / 1000L
    ): Pair<String, String> {
        val (dateStr, datePart) = KeylessCrypto.iso8601UtcCompact(unixSeconds)
        val ossUa = "aliyun-sdk-js/6.23.0 Chrome 132.0.0.0 on Windows 10 64-bit"
        val canonicalHeaders =
            "content-type:$mimeType\n" +
                "x-oss-content-sha256:UNSIGNED-PAYLOAD\n" +
                "x-oss-date:$dateStr\n" +
                "x-oss-security-token:$securityToken\n" +
                "x-oss-user-agent:$ossUa\n"
        val encodedPath = filePath.split('/').joinToString("/") { seg ->
            java.net.URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
        }
        val canonicalUri = "/$bucketName/$encodedPath"
        val canonicalRequest = "PUT\n$canonicalUri\n\n$canonicalHeaders\n\nUNSIGNED-PAYLOAD"
        val dateScope = "$datePart/ap-southeast-1/oss/aliyun_v4_request"
        val stringToSign =
            "OSS4-HMAC-SHA256\n$dateStr\n$dateScope\n${KeylessCrypto.sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))}"
        val kDate = KeylessCrypto.hmacSha256Bytes(
            "aliyun_v4$accessKeySecret".toByteArray(Charsets.UTF_8),
            datePart.toByteArray(Charsets.UTF_8)
        )
        val kRegion = KeylessCrypto.hmacSha256Bytes(kDate, "ap-southeast-1".toByteArray(Charsets.UTF_8))
        val kService = KeylessCrypto.hmacSha256Bytes(kRegion, "oss".toByteArray(Charsets.UTF_8))
        val kSigning = KeylessCrypto.hmacSha256Bytes(kService, "aliyun_v4_request".toByteArray(Charsets.UTF_8))
        val signature = KeylessCrypto.hmacSha256Hex(kSigning, stringToSign.toByteArray(Charsets.UTF_8))
        return "OSS4-HMAC-SHA256 Credential=$accessKeyId/$dateScope,Signature=$signature" to dateStr
    }

    private suspend fun uploadImageAttachment(acct: Account, rawBase64: String, index: Int): JsonObject? = runCatching {
        val cleanB64 = rawBase64.substringAfter("base64,", rawBase64).trim()
        val bytes = java.util.Base64.getDecoder().decode(cleanB64)
        val filename = "image_$index.jpg"
        val mime = "image/jpeg"
        val stsReq = appRequest("$QWEN_APP_BASE/files/getstsToken", acct)
            .post(
                buildJsonObject {
                    put("filename", filename)
                    put("filesize", bytes.size)
                    put("filetype", "image")
                }.toString().toRequestBody(JSON)
            )
            .build()
        val stsData = CommunityHttp.execute(stsReq, AiHttp.client) { resp ->
            if (!resp.isSuccessful) return@execute null
            val o = AiHttp.json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject
            if ((o?.get("success") as? JsonPrimitive)?.booleanOrNull != true) return@execute null
            o["data"] as? JsonObject
        } ?: return null

        val fileUrl = (stsData["file_url"] as? JsonPrimitive)?.contentOrNull ?: return null
        val fileId = (stsData["file_id"] as? JsonPrimitive)?.contentOrNull ?: return null
        val bucket = (stsData["bucketname"] as? JsonPrimitive)?.contentOrNull ?: "qwen-webui-prod"
        val filePath = (stsData["file_path"] as? JsonPrimitive)?.contentOrNull ?: return null
        val akId = (stsData["access_key_id"] as? JsonPrimitive)?.contentOrNull ?: return null
        val akSecret = (stsData["access_key_secret"] as? JsonPrimitive)?.contentOrNull ?: return null
        val secToken = (stsData["security_token"] as? JsonPrimitive)?.contentOrNull ?: return null

        val (authHeader, dateStr) = signOssV4Header(akId, akSecret, secToken, bucket, filePath, mime)
        val putUrl = fileUrl.substringBefore('?')
        val putReq = Request.Builder()
            .url(putUrl)
            .put(bytes.toRequestBody(mime.toMediaType()))
            .header("Content-Type", mime)
            .header("x-oss-content-sha256", "UNSIGNED-PAYLOAD")
            .header("x-oss-date", dateStr)
            .header("x-oss-security-token", secToken)
            .header("x-oss-user-agent", "aliyun-sdk-js/6.23.0 Chrome 132.0.0.0 on Windows 10 64-bit")
            .header("authorization", authHeader)
            .build()
        val uploaded = CommunityHttp.execute(putReq, AiHttp.client) { it.isSuccessful }
        if (!uploaded) return null
        val nowMs = System.currentTimeMillis()
        buildJsonObject {
            put("type", "image")
            putJsonObject("file") {
                put("created_at", nowMs)
                putJsonObject("data") {}
                put("filename", filename)
                put("hash", JsonNull)
                put("id", fileId)
                putJsonObject("meta") {
                    put("name", filename)
                    put("size", bytes.size)
                    put("content_type", mime)
                }
                put("update_at", nowMs)
                put("type", "image")
            }
            put("id", fileId)
            put("url", fileUrl)
            put("name", filename)
            put("collection_name", "")
            put("progress", 0)
            put("status", "uploaded")
            put("greenNet", "success")
            put("size", bytes.size)
            put("error", "")
            put("itemId", UUID.randomUUID().toString())
            put("file_type", mime)
            put("showType", "image")
            put("file_class", "vision")
            put("uploadTaskId", UUID.randomUUID().toString())
        }
    }.getOrNull()

    suspend fun FlowCollector<StreamEvent>.tryStream(
        upstreamModel: String,
        prompt: String,
        thinking: Boolean,
        images: List<String> = emptyList()
    ): Boolean {
        val acct = acquireAccount() ?: return false
        val qwenModel = when (upstreamModel) {
            "qwen4-max", "qwen3.8-max", "qwen3.7-max", "qwen3.8-omni-flash", "qwen3.5-omni-plus", "qwen3.7-plus" -> upstreamModel
            else -> "qwen4-max"
        }
        val uploadedFiles = images.mapIndexedNotNull { idx, img -> uploadImageAttachment(acct, img, idx) }
        val nowMs = System.currentTimeMillis()
        val newChatReq = appRequest("$QWEN_APP_BASE/chats/new", acct)
            .post(
                buildJsonObject {
                    put("title", "New Chat")
                    putJsonArray("models") { add(JsonPrimitive(qwenModel)) }
                    put("chat_mode", "normal")
                    put("chat_type", "t2t")
                    put("timestamp", nowMs)
                    put("project_id", "")
                }.toString().toRequestBody(JSON)
            )
            .build()

        val chatId = runCatching {
            CommunityHttp.execute(newChatReq, AiHttp.client) { resp ->
                if (!resp.isSuccessful) return@execute null
                val obj = AiHttp.json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject
                ((obj?.get("data") as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull
            }
        }.getOrNull() ?: run {
            acct.coolUntilSec = (System.currentTimeMillis() / 1000L) + 300L
            return false
        }

        val payload = buildJsonObject {
            put("stream", true)
            put("incremental_output", true)
            put("chat_id", chatId)
            put("chat_mode", "normal")
            put("model", qwenModel)
            put("parent_id", JsonNull)
            put("version", "2.1")
            put("timestamp", nowMs)
            put("share_id", "")
            put("origin_branch_message_id", "")
            putJsonArray("messages") {
                addJsonObject {
                    put("fid", UUID.randomUUID().toString())
                    put("parentId", JsonNull)
                    putJsonArray("childrenIds") {}
                    put("chat_type", "t2t")
                    put("content", prompt)
                    put("role", "user")
                    put("timestamp", nowMs)
                    put("sub_chat_type", "t2t")
                    putJsonArray("models") { add(JsonPrimitive(qwenModel)) }
                    put("user_action", "chat")
                    putJsonArray("files") {
                        uploadedFiles.forEach { add(it) }
                    }
                    putJsonObject("safety") { put("enabled", false) }
                    putJsonObject("extra") {
                        putJsonObject("meta") { put("subChatType", "t2t") }
                        put("disable_recitation_policy", true)
                        put("skip_safety_check", true)
                    }
                    putJsonObject("feature_config") {
                        put("output_schema", "phase")
                        put("thinking_enabled", thinking)
                        put("thinking_mode", "Auto")
                        put("auto_thinking", thinking)
                        put("research_mode", "normal")
                        put("auto_search", false)
                    }
                }
            }
        }

        val compReq = appRequest("$QWEN_APP_BASE/chat/completions?chat_id=$chatId", acct)
            .header("Accept", "*/*,text/event-stream")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        val ok = runCatching {
            CommunityHttp.execute(compReq, AiHttp.streaming) { resp ->
                if (!resp.isSuccessful) return@execute false
                val source = resp.body?.source() ?: return@execute false
                val parser = QwenStreamParser()
                var started = false
                while (!parser.finished) {
                    val line = source.readUtf8Line() ?: break
                    val events = parser.onLine(line)
                    for (ev in events) {
                        if (!started) {
                            started = true
                            emit(StreamEvent.Started)
                        }
                        emit(ev)
                    }
                }
                if (parser.quota) acct.coolUntilSec = (System.currentTimeMillis() / 1000L) + 600L
                if (!parser.sawOutput) return@execute false
                parser.finish().forEach { emit(it) }
                true
            }
        }.getOrDefault(false)

        // Best-effort cleanup of the server-side chat
        runCatching {
            val delReq = appRequest("$QWEN_APP_BASE/chats/$chatId", acct).delete().build()
            CommunityHttp.execute(delReq, AiHttp.client) {}
        }
        return ok
    }

    private suspend fun acquireAccount(): Account? {
        val nowSec = System.currentTimeMillis() / 1000L
        accounts.firstOrNull { it.token.isNotBlank() && it.coolUntilSec <= nowSec }?.let { return it }
        if (provisionFailedUntilSec > nowSec) return null
        val fresh = provisionAccount()
        if (fresh == null) {
            provisionFailedUntilSec = nowSec + 300L
            return null
        }
        accounts += fresh
        while (accounts.size > 6) accounts.removeAt(0)
        return fresh
    }

    private suspend fun provisionAccount(): Account? = runCatching {
        val sess = UUID.randomUUID().toString()
        val device = "ai" + KeylessCrypto.randomHex(32)
        val domReq = Request.Builder()
            .url("$QWEN_MAIL_BASE/domains")
            .header("X-Session-Id", sess)
            .get()
            .build()
        val domain = CommunityHttp.execute(domReq, AiHttp.client) { resp ->
            if (!resp.isSuccessful) return@execute null
            val arr = AiHttp.json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonArray ?: return@execute null
            arr.firstNotNullOfOrNull { el ->
                val o = el as? JsonObject ?: return@firstNotNullOfOrNull null
                val avail = (o["available"] as? JsonPrimitive)?.booleanOrNull == true
                val prem = (o["premium"] as? JsonPrimitive)?.booleanOrNull == true
                val name = (o["domain"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                if (avail && !prem && name.isNotBlank() && name != "cfdevelopment.online") name else null
            }
        } ?: return null

        val inboxReq = Request.Builder()
            .url("$QWEN_MAIL_BASE/inboxes")
            .header("X-Session-Id", sess)
            .post(buildJsonObject { put("domain", domain) }.toString().toRequestBody(JSON))
            .build()
        val (email, inboxId) = CommunityHttp.execute(inboxReq, AiHttp.client) { resp ->
            if (!resp.isSuccessful) return@execute null
            val o = AiHttp.json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject ?: return@execute null
            val addr = (o["address"] as? JsonPrimitive)?.contentOrNull ?: return@execute null
            val id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: return@execute null
            addr to id
        } ?: return null

        val password = "Aa1!" + KeylessCrypto.randomHex(14)
        val pwHash = KeylessCrypto.sha256Hex(password.toByteArray(Charsets.UTF_8))
        val signupReq = Request.Builder()
            .url("$QWEN_APP_BASE/auths/signup")
            .header("User-Agent", QWEN_APP_UA)
            .header("Accept", "application/json")
            .header("X-Platform", "android")
            .header("source", "app")
            .header("Accept-Language", "en-US")
            .header("Cache-Control", "no-store")
            .header("app_waf", QWEN_APP_WAF)
            .header("x-request-id", UUID.randomUUID().toString())
            .header("x-device-id", device)
            .post(
                buildJsonObject {
                    put("email", email)
                    put("password", pwHash)
                    put("name", "Oro ${email.take(5)}")
                }.toString().toRequestBody(JSON)
            )
            .build()
        val signedUp = CommunityHttp.execute(signupReq, AiHttp.client) { resp ->
            val o = runCatching { AiHttp.json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject }.getOrNull()
            (o?.get("success") as? JsonPrimitive)?.booleanOrNull == true
        }
        if (!signedUp) return null

        var activateLink: String? = null
        for (poll in 0 until 12) {
            delay(1_100L)
            val msgReq = Request.Builder()
                .url("$QWEN_MAIL_BASE/inboxes/$inboxId/messages")
                .header("X-Session-Id", sess)
                .get()
                .build()
            activateLink = CommunityHttp.execute(msgReq, AiHttp.client) { resp ->
                val arr = runCatching { AiHttp.json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonArray }.getOrNull()
                arr?.firstNotNullOfOrNull { el ->
                    val body = ((el as? JsonObject)?.get("body") as? JsonPrimitive)?.contentOrNull.orEmpty()
                    extractActivateLink(body)
                }
            }
            if (activateLink != null) break
        }
        val link = activateLink ?: return null
        runCatching {
            val actReq = Request.Builder()
                .url(link)
                .header("User-Agent", QWEN_APP_UA)
                .header("X-Platform", "android")
                .header("source", "app")
                .header("app_waf", QWEN_APP_WAF)
                .header("x-device-id", device)
                .get()
                .build()
            CommunityHttp.execute(actReq, noRedirectClient) {}
        }

        val signinReq = Request.Builder()
            .url("$QWEN_APP_BASE/auths/signin")
            .header("User-Agent", QWEN_APP_UA)
            .header("Accept", "application/json")
            .header("X-Platform", "android")
            .header("source", "app")
            .header("app_waf", QWEN_APP_WAF)
            .header("x-request-id", UUID.randomUUID().toString())
            .header("x-device-id", device)
            .post(
                buildJsonObject {
                    put("email", email)
                    put("password", pwHash)
                }.toString().toRequestBody(JSON)
            )
            .build()
        val token = CommunityHttp.execute(signinReq, AiHttp.client) { resp ->
            val o = runCatching { AiHttp.json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject }.getOrNull()
            ((o?.get("data") as? JsonObject)?.get("token") as? JsonPrimitive)?.contentOrNull
        }?.takeIf { it.isNotBlank() } ?: return null

        Account(
            email = email,
            passwordHash = pwHash,
            device = device,
            token = token,
            createdSec = System.currentTimeMillis() / 1000L
        )
    }.getOrNull()

    private fun appRequest(url: String, acct: Account): Request.Builder =
        Request.Builder()
            .url(url)
            .header("User-Agent", QWEN_APP_UA)
            .header("Accept", "application/json")
            .header("X-Platform", "android")
            .header("source", "app")
            .header("app_waf", QWEN_APP_WAF)
            .header("x-request-id", UUID.randomUUID().toString())
            .header("x-device-id", acct.device)
            .header("Authorization", "Bearer ${acct.token}")
}
