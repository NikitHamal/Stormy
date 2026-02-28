package com.codex.stormy.data.repository

import com.codex.stormy.data.ai.AiModel
import com.codex.stormy.data.ai.AiModels
import com.codex.stormy.data.ai.AiProvider
import com.codex.stormy.data.ai.DeepInfraModelService
import com.codex.stormy.data.ai.GeminiModelService
import com.codex.stormy.data.ai.OpenAiCompatibleModelService
import com.codex.stormy.data.local.dao.AiModelDao
import com.codex.stormy.data.local.entity.AiModelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository for managing AI models and provider model discovery.
 */
class AiModelRepository(
    private val aiModelDao: AiModelDao,
    private val deepInfraModelService: DeepInfraModelService,
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

    suspend fun refreshModelsFromProvider(): Result<Int> = withContext(Dispatchers.IO) {
        refreshModelsFromDeepInfra()
    }

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

    suspend fun refreshModelsFromDeepInfra(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val result = deepInfraModelService.fetchAvailableModels()
            mergeModelsIntoDatabase(result, AiProvider.DEEPINFRA)
        } catch (e: Exception) {
            handleRefreshError(e)
        }
    }

    private suspend fun fetchModelsForProvider(
        provider: AiProvider,
        apiKey: String
    ): Result<List<AiModel>> {
        return when (provider) {
            AiProvider.DEEPINFRA -> deepInfraModelService.fetchAvailableModels()
            AiProvider.GEMINI -> {
                if (apiKey.isBlank()) {
                    Result.failure(IllegalArgumentException("Gemini API key is required"))
                } else {
                    val openAiCompat = openAiCompatibleModelService.fetchAvailableModels(
                        provider = AiProvider.GEMINI,
                        apiKey = apiKey
                    )
                    openAiCompat.getOrNull()?.takeIf { it.isNotEmpty() }?.let { models ->
                        Result.success(models)
                    } ?: geminiModelService.fetchAvailableModels(apiKey)
                }
            }

            AiProvider.NVIDIA -> {
                if (apiKey.isBlank()) {
                    Result.failure(IllegalArgumentException("NVIDIA API key is required"))
                } else {
                    openAiCompatibleModelService.fetchAvailableModels(
                        provider = AiProvider.NVIDIA,
                        apiKey = apiKey
                    )
                }
            }

            AiProvider.OPENROUTER -> {
                if (apiKey.isBlank()) {
                    Result.failure(IllegalArgumentException("OpenRouter API key is required"))
                } else {
                    openAiCompatibleModelService.fetchAvailableModels(
                        provider = AiProvider.OPENROUTER,
                        apiKey = apiKey,
                        extraHeaders = OPENROUTER_HEADERS
                    )
                }
            }
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

                val newEntities = fetchedModels.map { model ->
                    val existing = existingMap[model.id]
                    model.toEntity().copy(
                        isEnabled = existing?.isEnabled ?: true,
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
        }
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
        provider: AiProvider = AiProvider.DEEPINFRA,
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
            contextLength = contextLength,
            supportsStreaming = supportsStreaming,
            supportsToolCalls = supportsToolCalls,
            isThinkingModel = isThinkingModel
        )
    }

    private fun AiModel.toEntity(): AiModelEntity {
        return AiModelEntity(
            id = id,
            name = name,
            provider = provider.name,
            contextLength = contextLength,
            supportsStreaming = supportsStreaming,
            supportsToolCalls = supportsToolCalls,
            isThinkingModel = isThinkingModel,
            isEnabled = true,
            isCustom = false
        )
    }

    companion object {
        private val OPENROUTER_HEADERS = mapOf(
            "HTTP-Referer" to "https://github.com/codex/stormy",
            "X-Title" to "Stormy"
        )
    }
}
