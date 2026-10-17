package com.oro.app.data.repository

import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.AiModels
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.providers.ModelIds
import com.oro.app.data.ai.ReasoningEffortSupport
import com.oro.app.data.ai.ModelCapabilities
import com.oro.app.data.ai.GeminiModelService
import com.oro.app.data.ai.OpenAiCompatibleModelService
import com.oro.app.data.local.dao.AiModelDao
import com.oro.app.data.local.entity.AiModelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository for managing AI models and provider model discovery.
 */
class AiModelRepository(
    private val aiModelDao: AiModelDao,
    private val geminiModelService: GeminiModelService,
    private val openAiCompatibleModelService: OpenAiCompatibleModelService
) {

    fun observeAllModels(): Flow<List<AiModel>> {
        return aiModelDao.observeAllModels().map { entities ->
            entities.map { it.toAiModel() }
        }
    }

    fun observeEnabledModels(): Flow<List<AiModel>> {
        return aiModelDao.observeEnabledModels().map { entities ->
            entities.map { it.toAiModel() }
        }
    }

    suspend fun getEnabledModels(): List<AiModel> {
        return aiModelDao.getEnabledModels().map { it.toAiModel() }
    }

    suspend fun getModelById(id: String): AiModel? {
        return aiModelDao.getModelById(id)?.toAiModel()
    }

    /** Refresh from the provider that works without configuration. */
    suspend fun refreshModelsFromProvider(): Result<Int> =
        refreshModelsFromProvider(AiProvider.DEFAULT)

    suspend fun refreshModelsFromProvider(
        provider: AiProvider,
        apiKey: String = ""
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val result = fetchModelsForProvider(provider, apiKey)
            mergeModelsIntoDatabase(result, provider)
        } catch (e: Exception) {
            handleRefreshError(e)
        }
    }

    suspend fun refreshModelsFromAllConfiguredProviders(
        apiKeys: Map<AiProvider, String>
    ): Result<Int> = withContext(Dispatchers.IO) {
        var totalRefreshed = 0
        var attemptedProviders = 0
        val failures = mutableListOf<String>()

        for (provider in AiProvider.entries) {
            // Custom providers refresh one by one from their own screen
            // (refreshCustomProvider); a blanket delete-by-provider here would
            // wipe every one of their catalogues.
            if (provider == AiProvider.CUSTOM) continue
            val key = apiKeys[provider].orEmpty()
            if (provider.requiresApiKey && key.isBlank()) {
                continue
            }
            attemptedProviders += 1

            refreshModelsFromProvider(provider, key)
                .onSuccess { count -> totalRefreshed += count }
                .onFailure { error ->
                    failures.add("${provider.displayName}: ${error.message ?: "unknown error"}")
                }
        }

        when {
            totalRefreshed > 0 -> Result.success(totalRefreshed)
            failures.isEmpty() && attemptedProviders > 0 -> Result.success(0)
            failures.isNotEmpty() -> Result.failure(Exception(failures.joinToString("; ")))
            else -> Result.failure(Exception("No providers configured for model refresh"))
        }
    }

    /**
     * Fetch a provider's live catalogue.
     *
     * Previously one `when` branch per provider, all of which did the same two
     * things (check the key, call the OpenAI-compatible /models endpoint) with
     * slightly different wording. Only Gemini genuinely differs: its
     * OpenAI-compatibility layer does not implement /models, so it needs the
     * native endpoint as a fallback.
     */
    private suspend fun fetchModelsForProvider(
        provider: AiProvider,
        apiKey: String
    ): Result<List<AiModel>> {
        if (provider.requiresApiKey && apiKey.isBlank()) {
            return Result.failure(
                IllegalArgumentException("${provider.displayName} API key is required")
            )
        }

        // Community bridges have no model listing; their catalogue is the seed.
        if (provider.isCommunity) return Result.success(AiModels.getModelsForProvider(provider))

        val result = openAiCompatibleModelService.fetchAvailableModels(
            provider = provider,
            apiKey = apiKey
        )
        if (result.getOrNull()?.isNotEmpty() == true) return result

        return if (provider == AiProvider.GEMINI) {
            geminiModelService.fetchAvailableModels(apiKey)
        } else {
            result
        }
    }
    private suspend fun mergeModelsIntoDatabase(
        result: Result<List<AiModel>>,
        provider: AiProvider
    ): Result<Int> {
        return result.fold(
            onSuccess = { fetchedModels ->
                val existingModels = aiModelDao.getAllModels()
                val existingMap = existingModels.associateBy { it.id }
                val seedIds = AiModels.getAllModels().map { it.id }.toSet()

                val newEntities = fetchedModels.map { model ->
                    val existing = existingMap[model.id]
                    model.toEntity().copy(
                        // New models arrive switched off; only the shipped
                        // seeds (the ones that work out of the box) start on.
                        isEnabled = existing?.isEnabled ?: seedIds.contains(model.id),
                        isFavorite = existing?.isFavorite ?: false,
                        alias = existing?.alias,
                        lastUsed = existing?.lastUsed,
                        usageCount = existing?.usageCount ?: 0,
                        addedAt = existing?.addedAt ?: System.currentTimeMillis()
                    )
                }

                aiModelDao.deleteModelsByProvider(provider.name)
                aiModelDao.insertModels(newEntities)

                Result.success(newEntities.size)
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    private suspend fun handleRefreshError(e: Exception): Result<Int> {
        val count = aiModelDao.getModelCount()
        if (count == 0) {
            seedDefaultModels()
        }
        return Result.failure(e)
    }

    suspend fun seedDefaultModels() {
        val defaults = AiModels.getAllModels().map { model ->
            model.toEntity().copy(
                isEnabled = true,
                addedAt = System.currentTimeMillis()
            )
        }
        aiModelDao.insertModels(defaults)
    }

    suspend fun initializeModelsIfEmpty() {
        val count = aiModelDao.getModelCount()
        if (count == 0) {
            seedDefaultModels()
        } else {
            addMissingKeylessSeeds()
        }
    }

    /**
     * Existing installs only got the seed list once, so providers added since
     * (OVHcloud, Kilo's keyless pool, K2 Horizon) would stay invisible until a
     * manual refresh. Insert keyless seeds that are missing; never touch rows
     * the user already has (enabled state, aliases, favourites).
     */
    private suspend fun addMissingKeylessSeeds() {
        val existing = aiModelDao.getAllModels().map { it.id }.toSet()
        val missing = AiModels.getAllModels()
            .filter { it.provider.worksWithoutKey && it.id !in existing }
            .map { it.toEntity().copy(isEnabled = true, addedAt = System.currentTimeMillis()) }
        if (missing.isNotEmpty()) aiModelDao.insertModels(missing)
    }

    /** Enable or disable every model from one provider. */
    suspend fun setProviderEnabled(provider: AiProvider, enabled: Boolean) {
        aiModelDao.setProviderEnabled(provider.name, enabled)
    }

    /** Enable or disable every model. */
    suspend fun setAllModelsEnabled(enabled: Boolean) {
        aiModelDao.setAllEnabled(enabled)
    }

    suspend fun setModelEnabled(modelId: String, enabled: Boolean) {
        aiModelDao.setModelEnabled(modelId, enabled)
    }

    suspend fun setModelFavorite(modelId: String, favorite: Boolean) {
        aiModelDao.setModelFavorite(modelId, favorite)
    }

    suspend fun setModelAlias(modelId: String, alias: String?) {
        aiModelDao.setModelAlias(modelId, alias)
    }

    suspend fun recordModelUsage(modelId: String) {
        aiModelDao.recordModelUsage(modelId)
    }

    suspend fun addCustomModel(
        id: String,
        name: String,
        provider: AiProvider = AiProvider.DEFAULT,
        contextLength: Int = 8192,
        supportsStreaming: Boolean = true,
        supportsToolCalls: Boolean = true,
        isThinkingModel: Boolean = false
    ): Result<Unit> {
        return try {
            val entity = AiModelEntity(
                id = id,
                name = name,
                provider = provider.name,
                contextLength = contextLength,
                reportedContextLength = contextLength,
                supportsStreaming = supportsStreaming,
                supportsToolCalls = supportsToolCalls,
                isThinkingModel = isThinkingModel,
                isEnabled = true,
                isCustom = true,
                addedAt = System.currentTimeMillis()
            )
            aiModelDao.insertModel(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Models of one custom provider, hand-added first. */
    fun observeCustomProviderModels(providerId: String): Flow<List<AiModel>> =
        aiModelDao.observeModelsWithPrefix(ModelIds.prefixFor(providerId))
            .map { rows -> rows.map { it.toAiModel() } }

    /**
     * Replace a custom provider's fetched catalogue, keeping per-model state
     * (enabled, favourite, alias, usage) and every hand-added model.
     */
    suspend fun replaceCustomProviderModels(providerId: String, fetched: List<AiModel>): Int =
        withContext(Dispatchers.IO) {
            val existing = aiModelDao.getAllModels().associateBy { it.id }
            val manual = existing.values.filter { it.isCustom && it.id.startsWith(ModelIds.prefixFor(providerId)) }
                .map { it.id }.toSet()
            val rows = fetched.filter { it.id !in manual }.map { model ->
                val old = existing[model.id]
                model.toEntity().copy(
                    // Off until the user picks it: a big catalogue must not flood the picker.
                    isEnabled = old?.isEnabled ?: false,
                    isFavorite = old?.isFavorite ?: false,
                    alias = old?.alias,
                    lastUsed = old?.lastUsed,
                    usageCount = old?.usageCount ?: 0,
                    addedAt = old?.addedAt ?: System.currentTimeMillis()
                )
            }
            aiModelDao.deleteFetchedModelsWithPrefix(ModelIds.prefixFor(providerId))
            aiModelDao.insertModels(rows)
            rows.size
        }

    suspend fun addCustomProviderModel(
        providerId: String,
        upstreamId: String,
        name: String,
        contextLength: Int,
        supportsToolCalls: Boolean,
        isThinkingModel: Boolean
    ): Result<Unit> = addCustomModel(
        id = ModelIds.of(providerId, upstreamId.trim()),
        name = name.ifBlank { upstreamId }.trim(),
        provider = AiProvider.CUSTOM,
        contextLength = contextLength,
        supportsToolCalls = supportsToolCalls,
        isThinkingModel = isThinkingModel
    )

    suspend fun deleteCustomProviderModels(providerId: String) = withContext(Dispatchers.IO) {
        aiModelDao.deleteAllModelsWithPrefix(ModelIds.prefixFor(providerId))
    }

    suspend fun deleteModel(modelId: String): Result<Unit> {
        return try {
            val model = aiModelDao.getModelById(modelId)
            if (model?.isCustom == true) {
                aiModelDao.deleteModelById(modelId)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Only custom models can be deleted"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getModelCount(): Int {
        return aiModelDao.getModelCount()
    }

    suspend fun getEnabledModelCount(): Int {
        return aiModelDao.getEnabledModelCount()
    }

    private fun AiModelEntity.toAiModel(): AiModel {
        return AiModel(
            id = id,
            name = alias ?: name,
            provider = AiProvider.fromString(provider),
            // Resolved on read rather than trusted from the row: a stored
            // figure that came from an older capability table (Agnes 3.0 Flash
            // was metered at 128k this way) would otherwise never be corrected.
            // Custom models keep exactly what the user typed.
            contextLength = if (isCustom) {
                contextLength
            } else {
                ModelCapabilities.resolveContextLength(id, reportedContextLength)
            },
            reportedContextLength = reportedContextLength,
            supportsStreaming = supportsStreaming,
            supportsToolCalls = supportsToolCalls,
            isThinkingModel = isThinkingModel,
            // Derived rather than stored: capability comes from the model id,
            // so persisting it would just be a column that can go stale
            // against the detection table.
            supportsReasoningEffort = ReasoningEffortSupport.supports(id),
            isEnabled = isEnabled,
            inputPricePerToken = inputPricePerToken,
            outputPricePerToken = outputPricePerToken
        )
    }

    private fun AiModel.toEntity(): AiModelEntity {
        return AiModelEntity(
            id = id,
            name = name,
            provider = provider.name,
            contextLength = contextLength,
            reportedContextLength = reportedContextLength,
            supportsStreaming = supportsStreaming,
            supportsToolCalls = supportsToolCalls,
            isThinkingModel = isThinkingModel,
            isEnabled = true,
            isCustom = false,
            inputPricePerToken = inputPricePerToken,
            outputPricePerToken = outputPricePerToken
        )
    }

    companion object {
        private val OPENROUTER_HEADERS = mapOf(
            "HTTP-Referer" to "https://github.com/oro/app",
            "X-Title" to "Oro"
        )
    }
}
