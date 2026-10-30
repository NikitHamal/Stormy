package com.oro.app.guard

import android.content.Context
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.ReasoningControls
import com.oro.app.data.ai.agent.AgentLoop
import com.oro.app.data.ai.agent.AgentLoopConfig
import com.oro.app.data.ai.agent.AgentLoopListener
import com.oro.app.data.ai.agent.AgentModelClient
import com.oro.app.data.ai.agent.AgentRequest
import com.oro.app.data.ai.agent.AgentRunResult
import com.oro.app.data.ai.agent.AgentToolRunner
import com.oro.app.data.ai.tools.AgentTools
import com.oro.app.data.ai.tools.ToolResult
import com.oro.app.data.repository.AiRepository
import org.json.JSONObject

/**
 * A focused, read-only investigator. The main guard spawns these in parallel
 * on different council models when an artifact is too large for one pass or
 * when independent reads matter. It cannot ask the user, write files, run the
 * browser or delegate further; it reads, enriches, tests and reports. No
 * transcript is written to the shield -- the parent sees one tool row and the
 * merged report, which keeps the chat readable.
 */
internal class GuardInvestigator(
    private val guard: GuardAgent.Guard,
    private val aiRepository: AiRepository,
    private val appContext: Context,
) {

    suspend fun run(task: String, focus: String, model: AiModel): String {
        val tools = GuardTools.forNames(GuardTools.investigatorNames)
        val runtime = GuardToolRuntime(
            guard = guard,
            appContext = appContext,
            ask = { _, _ -> "No user is available to this investigator; state the uncertainty instead of asking." },
            onFinish = { _, _ -> },
            onNote = {},
        )
        val runner = AgentToolRunner { call ->
            val name = call.function.name.substringAfterLast('.').lowercase()
            val args = runCatching { JSONObject(call.function.arguments.ifBlank { "{}" }) }.getOrDefault(JSONObject())
            runtime.execute(name, args)
        }
        val system = buildString {
            append(GuardAgent.FALLBACK_BRIEF)
            append("\n\nYou are one investigator subagent. Focus: ").append(focusPrompt(focus))
            append("\nWork read-only. Use the tools to verify; never contact the attacker or scan their infrastructure. ")
            append("Return a compact report: what is CONFIRMED by evidence, what is SUSPECTED and what would prove it, ")
            append("the indicators that matter, and the next action. Do not repeat the brief.")
        }
        val conversation = mutableListOf(
            ChatRequestMessage(role = "system", content = system),
            ChatRequestMessage(role = "user", content = task.take(24_000)),
        )
        val collected = StringBuilder()
        val listener = object : AgentLoopListener {
            override fun onTurnComplete(visibleText: String, toolCalls: List<com.oro.app.data.ai.ToolCallResponse>) {
                val text = AgentLoop.stripThinking(visibleText).trim()
                if (text.isNotEmpty()) collected.append(text).append("\n\n")
            }
        }
        val stream = AgentModelClient { req ->
            aiRepository.streamChat(
                model, req.messages, req.tools, temperature = 0.2f,
                reasoningLevel = ReasoningControls.maxLevel(model),
            )
        }
        val loop = AgentLoop(
            client = stream,
            tools = runner,
            config = AgentLoopConfig(
                maxIterations = 16,
                maxToolResultChars = 16_000,
                idleTimeoutMs = 120_000,
                maxQualityPasses = 0,
                toolSchemas = AgentTools.schemasOf(tools),
                cutRepetition = true,
            )
        )
        val outcome = loop.run(
            conversation = conversation,
            knownTools = tools.map { it.function.name }.toSet(),
            listener = listener,
            prepare = { history, _ -> AgentRequest(history, tools) },
        )
        return when (outcome) {
            is AgentRunResult.Completed -> outcome.finalText.trim().ifBlank { collected.toString().trim() }
            is AgentRunResult.IterationLimit -> collected.toString().trim().ifBlank { "The investigator ran out of turns without a report." }
            is AgentRunResult.Failed -> collected.toString().trim().ifBlank { "The investigator failed: ${outcome.message}" }
        }.ifBlank { "No report." }
    }

    private fun focusPrompt(focus: String): String = when (focus.lowercase()) {
        "evidence" -> "establish what the artifact actually shows. Separate confirmed from suspected from false alarm; cite the exact lines and indicators."
        "attribution" -> "identify the infrastructure's ownership from public records only. Do not claim a human identity without evidence; say what would confirm it and what would not."
        "countermeasures" -> "produce the containment, recovery and reporting plan in order: what to block, revoke, rotate, notify and report. Lawful channels only."
        "exposure" -> "work out what else the same credentials, accounts or domains expose, what to check first, and how to verify each."
        else -> focus
    }
}
