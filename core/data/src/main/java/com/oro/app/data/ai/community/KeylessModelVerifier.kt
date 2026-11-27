package com.oro.app.data.ai.community

import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.StreamEvent
import kotlinx.serialization.Serializable

/**
 * LMFingerprint model authenticity and under-the-hood proxy reroute verifier,
 * ported from ZeusDesigns (`src-tauri/src/providers.rs`: `ModelFingerprintReport`,
 * `classify_claimed_family`, `classify_detected_family`, `families_compatible`, `verify_model`).
 */
@Serializable
data class ModelFingerprintReport(
    val provider: String,
    val requestedModel: String,
    val claimedFamily: String,
    val detectedFamily: String,
    val upstreamEndpoint: String,
    val upstreamReportedModel: String,
    val protocolSignature: String,
    val verdict: String,
    val verdictLabel: String,
    val isGenuine: Boolean,
    val routedUnderTheHood: Boolean,
    val hasNativeReasoning: Boolean,
    val canaryPassed: Boolean,
    val latencyMs: Long,
    val tokensPerSec: Long,
    val fingerprintHash: String,
    val selfReport: String,
    val explanation: String
)

object KeylessModelVerifier {

    private const val PROBE_PROMPT =
        "In one sentence, state your exact model family and creator, then compute 17 * 23."

    fun classifyClaimedFamily(provider: String, model: String): String {
        val s = "$provider $model".lowercase()
        return when {
            "mercury" in s -> "Inception Mercury Diffusion"
            "gemini-4" in s || "gemini-pro-orchestrator" in s || "gemini-hydrogen" in s || "gemini-neon" in s ->
                "Google Gemini 4 Family"
            "gemini" in s -> "Google Gemini Family"
            "gemma-4" in s || "gemma4" in s || "diffusiongemma" in s || "gemma" in s ->
                "Google Gemma Open-Weights"
            "claude" in s || "sonnet" in s || "opus" in s || "haiku" in s -> "Anthropic Claude"
            "gpt-oss" in s -> "OpenAI GPT-OSS Open-Weights"
            "gpt-" in s || "o3" in s || "o4" in s || "openai" in s -> "OpenAI GPT / o-Series"
            "deepseek" in s -> "DeepSeek"
            "glm" in s || "zhipu" in s -> "Z.ai / Zhipu GLM"
            "qwen" in s || "qwq" in s -> "Alibaba Qwen"
            "kimi" in s || "moonshot" in s -> "Moonshot Kimi"
            "agnes" in s -> "Sapiens Agnes AI"
            "doubao" in s -> "ByteDance Doubao (Volcengine Ark)"
            "sensenova" in s -> "SenseTime SenseNova"
            "longcat" in s -> "Meituan LongCat MoE"
            "devstral" in s || "magistral" in s || "codestral" in s || "mistral" in s -> "Mistral AI"
            "reka" in s -> "Reka AI"
            "nemotron" in s -> "NVIDIA Nemotron"
            "step-" in s || "stepfun" in s -> "StepFun"
            "minimax" in s -> "MiniMax"
            "grok" in s -> "xAI Grok"
            "k2-horizon" in s || "k2horizon" in s -> "IFM K2-Horizon"
            "motif" in s -> "Motif Technologies"
            "lfm" in s || "liquid" in s -> "Liquid AI LFM"
            "phi-4" in s || "phi4" in s -> "Microsoft Phi"
            "llama" in s || "chatjimmy" in s -> "Meta Llama"
            else -> "Frontier Open / Multi-Model Router"
        }
    }

    fun classifyDetectedFamily(reportedModel: String, selfReport: String, protocolSig: String): String {
        val m = reportedModel.lowercase()
        val r = selfReport.lowercase()
        val p = protocolSig.lowercase()
        return when {
            "gpt-oss" in m || "gpt-oss" in r -> "OpenAI GPT-OSS Open-Weights"
            "gemini-4" in m || "bardfrontendservice" in p || "generativelanguage.googleapis.com" in p ->
                if ("gemini-3" in m || "gemini 3" in r) "Google Gemini Family" else "Google Gemini 4 Family"
            "gemini" in m || "gemini" in r || "built by google" in r || "trained by google" in r ->
                if ("gemma" in r || "gemma" in m) "Google Gemma Open-Weights" else "Google Gemini 4 Family"
            "gemma" in m || "gemma" in r -> "Google Gemma Open-Weights"
            "agnes" in m || "agnes" in r || "sapiens ai" in r -> "Sapiens Agnes AI"
            "doubao" in m || "doubao" in r || "bytedance" in r -> "ByteDance Doubao (Volcengine Ark)"
            "sensenova" in m || "sensenova" in r || "sensetime" in r -> "SenseTime SenseNova"
            "glm" in m || "glm" in r || "zhipu" in r || "chatglm" in r || "z.ai" in r -> "Z.ai / Zhipu GLM"
            "longcat" in m || "longcat" in r || "meituan" in r -> "Meituan LongCat MoE"
            "claude" in m || "claude" in r || "anthropic" in r -> "Anthropic Claude"
            "deepseek" in m || "deepseek" in r -> "DeepSeek"
            "qwen" in m || "qwen" in r || "alibaba" in r || "tongyi" in r -> "Alibaba Qwen"
            "kimi" in m || "moonshot" in m || "kimi" in r || "moonshot" in r -> "Moonshot Kimi"
            "devstral" in m || "magistral" in m || "codestral" in m || "mistral" in m ||
                "mistral" in r || "codestral" in r || "devstral" in r -> "Mistral AI"
            "nemotron" in m || "nemotron" in r || "nvidia" in r -> "NVIDIA Nemotron"
            "mercury" in m || "mercury" in r || "inception" in r -> "Inception Mercury Diffusion"
            "gpt-" in m || "o3" in m || "o4" in m || "openai" in r || "chatgpt" in r ||
                "gpt-4" in r || "gpt-5" in r || "gpt-6" in r -> "OpenAI GPT / o-Series"
            "llama" in m || "llama" in r || "meta" in r -> "Meta Llama"
            else -> "Frontier Open / Multi-Model Router"
        }
    }

    fun familiesCompatible(claimed: String, detected: String): Boolean {
        if (claimed == detected || claimed == "Frontier Open / Multi-Model Router") return true
        return (claimed.startsWith("Google Gemini") && detected.startsWith("Google Gemini")) ||
            (claimed.startsWith("OpenAI") && detected.startsWith("OpenAI"))
    }

    suspend fun verifyModel(model: AiModel): Result<ModelFingerprintReport> = runCatching {
        val t0 = System.currentTimeMillis()
        val upstream = CommunityClients.upstreamId(model)
        val claimedFamily = classifyClaimedFamily(model.provider.name, upstream)
        val content = StringBuilder()
        var hasReasoning = false
        var errorMsg: String? = null
        CommunityClients.stream(model, listOf(ChatRequestMessage("user", PROBE_PROMPT))).collect { ev ->
            when (ev) {
                is StreamEvent.ReasoningDelta -> hasReasoning = true
                is StreamEvent.ContentDelta -> content.append(ev.content)
                is StreamEvent.Error -> errorMsg = ev.message
                else -> Unit
            }
        }
        val elapsedMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
        if (content.isBlank() && errorMsg != null) {
            throw IllegalStateException(errorMsg)
        }
        val selfReport = content.toString().trim().replace('\n', ' ').take(280)
        val protocolSig = if (hasReasoning) "Keyless SSE + Reasoning Trace" else "Keyless SSE Stream"
        val detectedFamily = classifyDetectedFamily(upstream, selfReport, protocolSig)
        val compatible = familiesCompatible(claimedFamily, detectedFamily)
        val canaryPassed = "391" in selfReport
        val outTokens = (content.length / 4).coerceAtLeast(1)
        val tps = ((outTokens * 1000L) / elapsedMs).coerceAtLeast(1L)
        val (verdict, verdictLabel, isGenuine, routed) = if (!compatible) {
            listOf("REROUTED_UNDER_THE_HOOD", "Rerouted Under the Hood -> $detectedFamily", "false", "true")
        } else {
            listOf("GENUINE_DIRECT", "Genuine Direct · $detectedFamily", "true", "false")
        }
        val fpSeed = "${model.provider.name}|$upstream|${model.provider.baseUrl}|$detectedFamily"
        val fpHash = KeylessCrypto.sha256Hex(fpSeed.toByteArray(Charsets.UTF_8)).take(16)
        ModelFingerprintReport(
            provider = model.provider.name,
            requestedModel = upstream,
            claimedFamily = claimedFamily,
            detectedFamily = detectedFamily,
            upstreamEndpoint = model.provider.baseUrl,
            upstreamReportedModel = upstream,
            protocolSignature = protocolSig,
            verdict = verdict,
            verdictLabel = verdictLabel,
            isGenuine = isGenuine == "true",
            routedUnderTheHood = routed == "true",
            hasNativeReasoning = hasReasoning,
            canaryPassed = canaryPassed,
            latencyMs = elapsedMs,
            tokensPerSec = tps,
            fingerprintHash = fpHash,
            selfReport = selfReport,
            explanation = "$verdictLabel on ${model.provider.baseUrl} ($elapsedMs ms, $tps tok/s)."
        )
    }
}
