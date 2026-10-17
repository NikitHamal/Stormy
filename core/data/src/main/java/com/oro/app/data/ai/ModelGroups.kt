package com.oro.app.data.ai

/**
 * Sections of the model picker, in display order.
 *
 * Grouped by what the user has to do to use a model, because that is the
 * question on a fresh install: "what works right now?" comes first, the
 * unofficial bridges are labelled as such, and keyed providers follow.
 */
enum class ModelGroup(val title: String, val subtitle: String) {
    RECOMMENDED("Recommended", "Fast, free and ready with no setup"),
    ON_DEVICE("On this phone", "Private and offline; download in Settings > On-device models"),
    FREE("Free · no key", "Official free tiers that need no account"),
    COMMUNITY("Community · unofficial", "Public guest chats bridged by Oro; may change or break"),
    YOUR_PROVIDERS("Your providers", "Providers you added a key or URL for");

    companion object {
        fun of(model: AiModel): ModelGroup = when {
            model.provider.worksWithoutKey &&
                AiModels.getRecommendedModels().any { it.id == model.id && it.provider == model.provider } -> RECOMMENDED
            model.provider.protocol == ProviderProtocol.LOCAL -> ON_DEVICE
            model.provider.isCommunity -> COMMUNITY
            model.provider.apiKeyRequirement != ApiKeyRequirement.REQUIRED &&
                model.provider != AiProvider.CUSTOM -> FREE
            else -> YOUR_PROVIDERS
        }

        /** Models grouped and ordered for the picker; empty groups omitted. */
        fun grouped(models: List<AiModel>): List<Pair<ModelGroup, List<AiModel>>> {
            val recommendedOrder = AiModels.getRecommendedModels().map { it.id }
            return models.groupBy(::of).toSortedMap().map { (group, list) ->
                group to if (group == RECOMMENDED) {
                    list.sortedBy { recommendedOrder.indexOf(it.id) }
                } else {
                    list.sortedWith(
                        compareBy<AiModel> { it.provider.ordinal }
                            .thenBy { it.providerLabel().lowercase() }
                            .thenBy { it.name.lowercase() }
                    )
                }
            }
        }

        /** One-line description under a model's name. */
        fun caption(model: AiModel): String = buildList {
            add(model.providerLabel())
            add(formatContext(model.contextLength))
            if (!model.supportsToolCalls) add("text tool calls")
            if (model.provider.isCommunity && model.provider.protocol != ProviderProtocol.LOCAL) add("unofficial")
        }.joinToString(" · ")

        fun formatContext(tokens: Int): String = when {
            tokens >= 1_000_000 -> "${"%.1f".format(tokens / 1_048_576.0).removeSuffix(".0")}M ctx"
            tokens >= 1_000 -> "${tokens / 1_024}K ctx"
            else -> "$tokens ctx"
        }
    }
}

/** The provider's name as the user knows it: a custom provider's own name, not "Custom". */
fun AiModel.providerLabel(): String =
    if (provider == AiProvider.CUSTOM) {
        com.oro.app.data.ai.providers.CustomProviderRegistry.nameFor(id) ?: provider.displayName
    } else {
        provider.displayName.removeSuffix(" (community)")
    }
