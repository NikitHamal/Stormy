# Reasoning controls and context windows (research, 2026-10-22)

Implemented in `core/data/.../ai/ReasoningControls.kt` (effort) and `ModelCapabilities.kt` / `SeedModels` (windows).
Rule: a model gets a control only on a host where its parameter is documented; a wrong value is a 400 on every
request, so "unknown" means no control, never a guess.

## Reasoning effort, per host and model (wire values = labels lowercased; XHigh = `xhigh`)

| Host | Model family | Levels | Default in Oro | Wire |
|---|---|---|---|---|
| Mercury (community, chat.inceptionlabs.ai) | Mercury 3 | Instant, Low, Medium, High | **High** (pinned) | body `reasoningEffort` |
| Inception API | mercury-3 | Instant, Low, Medium, High | **High** (API default Low) | `reasoning_effort` |
| Inception API | mercury-2.5, mercury-2 | Instant, Low, Medium, High | Medium (API default) | `reasoning_effort` |
| Qwen (community, chat.qwen.ai) | Qwen3.7 Plus | Thinking Off / On | Off | `feature_config.thinking_enabled` |
| OpenAI-style (Zen, OpenRouter, Kilo, custom) | gpt-5, -mini, -nano | Minimal, Low, Medium, High | Medium | `reasoning_effort` |
| ″ | gpt-5.1 | None, Low, Medium, High | None | ″ |
| ″ | gpt-5.2 – 5.4 | None, Low, Medium, High, XHigh | None | ″ |
| ″ | gpt-5.5 | None, Low, Medium, High, XHigh | Medium | ″ |
| ″ | gpt-5.x-pro (5.2+) | Medium, High, XHigh | Medium | ″ |
| ″ | gpt-5.x-codex, gpt-5.6+, gpt-6.x, o3/o4-mini/o1 | Low, Medium, High (set every OpenAI reasoning model accepts) | Medium | ″ |
| Gemini API (OpenAI-compat), gateways, custom | gemini-3.x flash / flash-lite | Minimal, Low, Medium, High | High (Gemini 3 default) | ″ (maps to thinking_level) |
| ″ | gemini-3.x pro | Low, Medium, High | High | ″ |
| ″ | gemini-2.5-flash / -flash-lite / -pro | None+Low…High / None+Low…High / Low…High | Medium / None / Medium | ″ (maps to budgets) |
| Gateways, custom (xAI) | grok-4.3 | None, Low, Medium, High | Low | ″ |
| ″ | grok-4.5 | Low, Medium, High | High | ″ |
| ″ | grok-4.6+ | Low, Medium, High, XHigh | High | ″ |
| ″ | grok-3-mini | Low, High | Low | ″ |
| OpenRouter, Kilo | Claude Opus 4.7/4.8, Sonnet/Opus 5.x | Low, Medium, High, XHigh | High (Opus 5.5, Haiku 5.5: Medium) | ″ (→ `output_config.effort`) |
| ″ | Claude Opus/Sonnet 4.6 | Low, Medium, High | High | ″ |
| Custom, Anthropic protocol | Claude 4.6+ | Low, Medium, High | High | `output_config.effort`, no extended thinking (tool turns need no thinking blocks; XHigh/Max need thinking) |
| OpenRouter, Kilo | DeepSeek V4 | None, Low, High | High | `reasoning_effort` |
| Custom (DeepSeek API) | DeepSeek V4 | Low, High, Max | High | `reasoning_effort` (thinking stays on) |
| Custom (Z.ai API) | GLM-5.3 | Low, High, Max | High | `reasoning_effort` |
| Groq | gpt-oss | Low, Medium, High | Medium | `reasoning_effort` |
| Groq | qwen3.8-27b | None, Default, Low, Medium, High | Default | `reasoning_effort` |
| Cerebras | gpt-oss | Low, Medium, High | Medium | `reasoning_effort` |
| OVH, NVIDIA, LLM7, Pollinations, Agnes, Mistral, K2, Ptero, UniKey | — | no control (parameter not documented on these hosts) | | |

Also: OpenAI reasoning models (gpt-5.x, gpt-6, o-series) reject a non-default `temperature` on OpenAI's own API,
so requests through Zen and custom endpoints leave it out (gateways drop it themselves).

Migration: the old app-wide Low/Balanced/Deep becomes a fallback level ("*" in the saved map): Low → low,
Deep → high where a model's scale has it; Balanced (the old default) carries nothing. It never overrides
Mercury's pinned High. Choices are saved per model id.

## Context windows changed

| Model | Before | Now | Source |
|---|---|---|---|
| Mercury 3 (community + API) | 131,072 / 1,000,000 | 1,000,000 | Inception models page, `/v1/models` |
| K2 Horizon 375B | 131,072 | 524,288 | IFM model card (Hugging Face), vLLM recipe |
| Qwen3.8 Max (Qwen web, Zen) | 262,144 | 983,616 (thinking-mode input limit) | Qwen3.8 release; measured limits 991,808 / 983,616 |
| DeepSeek V4 family / V4.1 Flash (NVIDIA) | 163,840 | 1,048,576 | DeepSeek V4 release, NVIDIA NIM model card |
| Nemotron 3 Super / 3.5 Lightning (NVIDIA) | 131,072 | 262,144 (hosted; models reach 1M) | NVIDIA HF cards, NIM, OpenRouter |
| GPT-5.4, GPT-5.5 | 400,000 | 1,050,000 | OpenAI model pages |
| Claude Opus 4.8, Sonnet 5.x | 200,000 | 1,000,000 | Anthropic "What's new in Opus 4.8", Bedrock card |
| Grok 4.3 | 256,000 | 1,000,000 | xAI announcement, Oracle and OpenRouter pages |
| Kimi K3 | 262,144 | 1,048,576 | Moonshot API |
| GLM-5.2, GLM-5.3 (+ Flash) | 200,000 | 1,000,000 | Z.ai docs |
| Mistral Large (Large 3), Mistral Medium (3.5) | 131,072 | 262,144 | Mistral model aliases (promptfoo, Mastra) |

Unchanged on purpose (documented or provider-reported already): Gemini 3 (1,048,576), Kimi K2 (262,144),
gpt-oss (131,072), Llama 3.3 (131,072), Reflection Beam (1,048,576), Laguna S 2.1 free endpoint (262,144),
Codestral (256K), Agnes, LLM7 and Kilo rows (from their `/models`). Kept below the model's own window because
the bridge's limit is unverified: UniKey (128,000; a 40k prompt verified), Ptero (131,072), Qwen web
Qwen3.7 Plus / Omni Flash (262,144).

Catalogue moves: Groq retired `qwen/qwen3-32b` (Aug 2026) → seed `qwen/qwen3.8-27b`; Cerebras deprecated
`qwen-3-coder-480b` (removed from its seeds); the separate "Mercury 3 Instant" seed is gone (Instant is a level).

## Sources
- OpenAI: developers.openai.com/api/docs/guides/reasoning, /models/gpt-5.5, /models/gpt-5.4-pro; zansara.dev (GPT-5.x defaults)
- Inception: docs.inceptionlabs.ai/capabilities/reasoning-efforts, /get-started/models
- Gemini: ai.google.dev/gemini-api/docs/openai, /gemini-3
- xAI Grok 4.3: docs.x.ai (reasoning), AWS Bedrock model card, OpenRouter x-ai/grok-4.3
- Anthropic: platform.claude.com "What's new in Claude Opus 4.8"; effort levels (LiteLLM, DataCamp, Bedrock)
- OpenRouter: openrouter.ai/docs/guides/best-practices/reasoning-tokens, /docs/api_reference/parameters
- DeepSeek: api-docs.deepseek.com/guides/thinking_mode; NVIDIA NIM deepseek-v4.1-flash card
- Groq: console.groq.com/docs/reasoning; Cerebras: inference-docs.cerebras.ai/capabilities/reasoning, change log
- Z.ai: docs.z.ai/guides/llm/glm-5, /glm-5.3, /vlm/glm-5.3-flash
- Qwen: qwen.ai blog (Qwen3.8), docs.qwencloud.com thinking guide
- K2 Horizon: huggingface.co/IFM/K2-Horizon-375B-A23B; Reflection Beam, Laguna S 2.1 model cards; Kimi K2.7 Code page
