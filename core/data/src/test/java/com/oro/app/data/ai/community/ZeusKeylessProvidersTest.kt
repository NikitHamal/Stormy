package com.oro.app.data.ai.community

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.AiModels
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.KeylessFallback
import com.oro.app.data.ai.KeylessSeeds
import com.oro.app.data.ai.ModelCapabilities
import com.oro.app.data.ai.ReasoningControls
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.VisionSupport
import com.oro.app.data.ai.agent.ModelProfiles
import org.junit.Test

/**
 * Unit tests for all keyless providers, Gemini 4 family routers, token generators,
 * and stream parsers ported from ZeusDesigns.
 */
class ZeusKeylessProvidersTest {

    @Test
    fun `keyless crypto generates valid tokens, island keys, and strips chatjimmy tags`() {
        val ip = KeylessCrypto.fakeIpv4()
        assertThat(ip).matches("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""")

        // Deterministic UTC hour check: 1776000000L -> 2026-04-12 13:20:00 UTC -> "2026-04-12 13"
        val myChatToken = KeylessCrypto.generateMyChatEncryptionToken(1_776_000_000L)
        assertThat(myChatToken).hasLength(32)
        assertThat(myChatToken).matches("[0-9a-f]{32}")
        assertThat(myChatToken).isEqualTo(
            KeylessCrypto.md5Hex("GPT_KEYBOARD_IS_AWESOME|2026-04-12 13".toByteArray(Charsets.UTF_8))
        )

        val islandKey = KeylessCrypto.generateDeepAiIslandKey("TestAgent/1.0")
        assertThat(islandKey).startsWith("tryit-")
        val parts = islandKey.split("-")
        assertThat(parts).hasSize(3)
        assertThat(parts[2]).hasLength(32)

        val tryitKey = KeylessCrypto.generateDeepAiTryitKey("TestAgent/1.0")
        assertThat(tryitKey).startsWith("tryit-")
        assertThat(tryitKey.split("-")[2]).hasLength(64)

        val (isoFull, isoDate) = KeylessCrypto.iso8601UtcCompact(1_776_000_000L)
        assertThat(isoDate).isEqualTo("20260412")
        assertThat(isoFull).isEqualTo("20260412T132000Z")

        assertThat(PteroClient.extractNonce("<script>var nonce = 'a1b2c3d4e5';</script>")).isEqualTo("a1b2c3d4e5")

        val (reasoning, answer) = KeylessCrypto.parseChatJimmyOutput(
            "<|think|>Step 1: analyze layout<|/think|>Hello world!<|stats|>{\"tok_s\":21500}<|/stats|>"
        )
        assertThat(reasoning).isEqualTo("Step 1: analyze layout")
        assertThat(answer).isEqualTo("Hello world!")

        assertThat(KeylessCrypto.isUpstreamErrorText("Error: Rate limit exceeded")).isTrue()
        assertThat(KeylessCrypto.isUpstreamErrorText("<!DOCTYPE html><html>")).isFalse()
    }

    @Test
    fun `gemini 4 mesh maps models, effort levels, and wrb_fr snapshots accurately`() {
        assertThat(Gemini4MeshClient.applyEffortSuffix("gemini-4-hydrogen-medium", "xhigh"))
            .isEqualTo("gemini-4-hydrogen-xhigh")
        assertThat(Gemini4MeshClient.applyEffortSuffix("gemini-4-neon-high", "low"))
            .isEqualTo("gemini-4-neon-low")
        assertThat(Gemini4MeshClient.toAntigravityModel("gemini-4-hydrogen"))
            .isEqualTo("gemini-4-hydrogen-medium")
        assertThat(Gemini4MeshClient.toAntigravityModel("gemini-4-carbon"))
            .isEqualTo("gemini-4-carbon-medium")

        val (hMode, hThink, _) = Gemini4MeshClient.resolveGemini4Mode("gemini-4-hydrogen-xhigh")
        assertThat(hMode).isEqualTo(2)
        assertThat(hThink).isEqualTo(0)

        val (cMode, cThink, _) = Gemini4MeshClient.resolveGemini4Mode("gemini-4-carbon-high")
        assertThat(cMode).isEqualTo(3)
        assertThat(cThink).isEqualTo(4)

        val form = Gemini4MeshClient.buildStreamGenerateFormBody("Hello Gemini 4", modeId = 1, thinkMode = 4)
        assertThat(form).startsWith("f.req=")

        val parser = WrbFrSnapshotParser()
        val line1 = """[["wrb.fr",null,"[null,null,null,null,[[null,[\"Hello \"]]]]"]]"""
        val line2 = """[["wrb.fr",null,"[null,null,null,null,[[null,[\"Hello Gemini 4!\"]]]]"]]"""
        val ev1 = parser.onLine(line1)
        val ev2 = parser.onLine(line2)
        assertThat(ev1).containsExactly(StreamEvent.ContentDelta("Hello "))
        assertThat(ev2).containsExactly(StreamEvent.ContentDelta("Gemini 4!"))
        assertThat(parser.sawOutput).isTrue()

        // Internal Gemini StreamGenerate code_reference / code_stdout blocks are stripped cleanly
        val rawExec = "```python?code_reference&code_event_index=1\nprint(2+2)\n```\n```text?code_stdout&code_event_index=1\n4\n```\nPELICAN 4"
        assertThat(WrbFrSnapshotParser.cleanGeminiCodeBlocks(rawExec)).isEqualTo("PELICAN 4")

        // Multi-turn compaction preserves prior user/assistant turns for gemini-web2api nodes
        val compacted = Gemini4MeshClient.compactGeminiWeb2ApiMessages(
            listOf(
                ChatRequestMessage("system", "Sys"),
                ChatRequestMessage("user", "Turn 1"),
                ChatRequestMessage("assistant", "Reply 1"),
                ChatRequestMessage("user", "Turn 2")
            )
        )
        assertThat(compacted).hasSize(2)
        assertThat(compacted.last().content).contains("Turn 1")
        assertThat(compacted.last().content).contains("Reply 1")
        assertThat(compacted.last().content).contains("Turn 2")
    }

    @Test
    fun `gemini 4 family and flagship keyless providers are registered and tool-capable`() {
        val gemini4Providers = listOf(
            AiProvider.ANTIGRAVITY,
            AiProvider.GEMINI4_HYDROGEN,
            AiProvider.GEMINI4_DIRECT,
            AiProvider.GEMINI_WEB
        )
        gemini4Providers.forEach { p ->
            assertThat(p.worksWithoutKey).isTrue()
            assertThat(CommunityClients.takesTools(p)).isTrue()
            assertThat(AiModels.getModelsForProvider(p)).isNotEmpty()
        }

        // Flagship Gemini 4 models are findable and report 1M context
        listOf(
            "ag:gemini-4-hydrogen-xhigh",
            "ag:gemini-4-neon-xhigh",
            "ag:gemini-4-carbon-high",
            "ag:gemini-4-argon-high",
            "ag:gemini-pro-orchestrator",
            "g4h:gemini-4-hydrogen",
            "g4:gemini-4-hydrogen"
        ).forEach { id ->
            val m = AiModels.findModelById(id)
            assertThat(m).isNotNull()
            assertThat(m!!.contextLength).isAtLeast(1_000_000)
            assertThat(m.supportsToolCalls).isTrue()
        }

        // Vision support works for Gemini 4, Claude 5.5 Proxy, and NIM Cloud
        assertThat(VisionSupport.supports(AiModels.findModelById("ag:gemini-4-hydrogen-high")!!)).isTrue()
        assertThat(VisionSupport.supports(AiModels.findModelById("claude5:claude-sonnet-5-5")!!)).isTrue()
        assertThat(VisionSupport.supports(AiModels.findModelById("nim:nvidia/nemotron-3.5-super-vl-120b-a12b")!!)).isTrue()

        // Frontier profile applies to Gemini 4, MyChat GPT-5/6, and Claude 5.5
        assertThat(ModelProfiles.forIds(AiProvider.ANTIGRAVITY, "ag:gemini-4-hydrogen-xhigh"))
            .isEqualTo(ModelProfiles.FRONTIER)
        assertThat(ModelProfiles.forIds(AiProvider.MYCHAT_CLOUD, "mychat:gpt-5"))
            .isEqualTo(ModelProfiles.FRONTIER)
        assertThat(ModelProfiles.forIds(AiProvider.CLAUDE5_PROXY, "claude5:claude-opus-5-5"))
            .isEqualTo(ModelProfiles.FRONTIER)

        // Reasoning controls expose effort levels on Antigravity & Gemini 4 Hydrogen/Neon
        val g4Control = ReasoningControls.forModel(AiModel("ag:gemini-4-hydrogen-high", "G4H", AiProvider.ANTIGRAVITY))
        assertThat(g4Control).isNotNull()
        assertThat(g4Control!!.levels.map { it.wire }).containsExactly("low", "medium", "high", "xhigh").inOrder()
    }

    @Test
    fun `multi-target oai client injects persona shield and identifies native tool hosts`() {
        val shielded = MultiTargetOaiClient.injectPersonaShield(
            listOf(ChatRequestMessage("user", "Build a landing page"))
        )
        assertThat(shielded.first().role).isEqualTo("system")
        assertThat(shielded.first().content).contains("SYSTEM OVERRIDE")

        assertThat(MultiTargetOaiClient.supportsNativeOaiTools("https://bun008-freellmapi.hf.space/v1/chat/completions", "glm-5.3")).isTrue()
        assertThat(MultiTargetOaiClient.supportsNativeOaiTools("https://w902287-ai-bridge.hf.space/v1/chat/completions", "gpt-6-luna")).isTrue()
        assertThat(MultiTargetOaiClient.supportsNativeOaiTools("https://w902287-ai-bridge.hf.space/v1/chat/completions", "claude-haiku-5-5")).isFalse()
    }

    @Test
    fun `all 45 keyless providers and extended reasoning stream formats are wired`() {
        val newlyPorted = listOf(
            AiProvider.VOLC_ARK,
            AiProvider.LOGFARE_HUB,
            AiProvider.FREECC,
            AiProvider.TOGETHER_FRONTIER,
            AiProvider.LIQUID_AI,
            AiProvider.GEMMA4_VL,
            AiProvider.BONSAI_CLOUD,
            AiProvider.UNSLOTH_CLOUD,
            AiProvider.KOBOLD_CLOUD,
            AiProvider.HF_CLUSTER,
            AiProvider.PHI4_CLOUD,
            AiProvider.LLAMACPP_CLOUD,
            AiProvider.OLLAMA_CLOUD,
            AiProvider.AI_HORDE,
            AiProvider.APIS_FREE_LLM,
            AiProvider.YQ_CLOUD,
            AiProvider.LONGCAT_WEB
        )
        newlyPorted.forEach { p ->
            assertThat(p.worksWithoutKey).isTrue()
            assertThat(AiModels.getModelsForProvider(p)).isNotEmpty()
            assertThat(KeylessFallback.order.any { id -> AiModels.findModelById(id)?.provider == p }).isTrue()
        }

        val parser = com.oro.app.data.ai.ChatStreamParser()
        val evs = parser.onData("""{"choices":[{"delta":{"reasoning_details":[{"text":"Step A. "},{"thinking":"Step B."}],"function_call":{"name":"read_file","arguments":"{\"path\":\"a.kt\"}"}}}]}""") +
            parser.finish()
        assertThat(evs.filterIsInstance<StreamEvent.ReasoningDelta>().single().reasoning).isEqualTo("Step A. Step B.")
        val call = evs.filterIsInstance<StreamEvent.ToolCalls>().single().toolCalls.single()
        assertThat(call.function.name).isEqualTo("read_file")
        assertThat(call.function.arguments).isEqualTo("""{"path":"a.kt"}""")

        // LMFingerprint family classification & under-the-hood proxy reroute detection
        assertThat(KeylessModelVerifier.classifyClaimedFamily("GEMINI4_HYDROGEN", "gemini-4-hydrogen"))
            .isEqualTo("Google Gemini 4 Family")
        assertThat(KeylessModelVerifier.classifyClaimedFamily("AGNES_CLOUD", "agnes-3.0-pro"))
            .isEqualTo("Sapiens Agnes AI")
        assertThat(KeylessModelVerifier.classifyClaimedFamily("VOLC_ARK", "doubao-seed-2-0-lite-260428"))
            .isEqualTo("ByteDance Doubao (Volcengine Ark)")
        val claimed = KeylessModelVerifier.classifyClaimedFamily("LOGFARE_HUB", "devstral-latest")
        val detected = KeylessModelVerifier.classifyDetectedFamily(
            "openai/gpt-oss-120b",
            "I am GPT-OSS, an open-weight model by OpenAI. 17*23=391.",
            "Keyless SSE Stream"
        )
        assertThat(claimed).isEqualTo("Mistral AI")
        assertThat(detected).isEqualTo("OpenAI GPT-OSS Open-Weights")
        assertThat(KeylessModelVerifier.familiesCompatible(claimed, detected)).isFalse()

        // Qwen OSS V4 signature header format check
        val (ossAuth, ossDate) = QwenAppGateway.signOssV4Header(
            accessKeyId = "STS.TEST",
            accessKeySecret = "secret",
            securityToken = "tok",
            bucketName = "qwen-webui-prod",
            filePath = "uploads/test.jpg",
            mimeType = "image/jpeg",
            unixSeconds = 1_776_000_000L
        )
        assertThat(ossDate).isEqualTo("20260412T132000Z")
        assertThat(ossAuth).startsWith("OSS4-HMAC-SHA256 Credential=STS.TEST/20260412/ap-southeast-1/oss/aliyun_v4_request,Signature=")
    }
}
