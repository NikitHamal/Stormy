package com.codex.stormy.data.repository

import com.codex.stormy.data.ai.AiModel
import com.codex.stormy.data.ai.AiProvider
import com.codex.stormy.data.ai.DeepInfraModelService
import com.codex.stormy.data.ai.DeepInfraModels
import com.codex.stormy.data.local.dao.AiModelDao
import com.codex.stormy.data.local.entity.AiModelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository for managing AI models
 * Handles:
 * - Local persistence of model preferences
 * - Dynamic model fetching from providers
 * - Model enable/disable state
 * - Custom model management
 */
class AiModelRepository(
    private val aiModelDao: AiModelDao,
    private val deepInfraModelService: DeepInfraModelService
) {

    /**
     * Observe all models (enabled and disabled)
     */
    fun observeAllModels(): Flow<List<AiModel>> {
        return aiModelDao.observeAllModels().map { entities ->
            entities.map { it.toAiModel() }
        }
    }

    /**
     * Observe only enabled models (for model selector)
     */
    fun observeEnabledModels(): Flow<List<AiModel>> {
        return aiModelDao.observeEnabledModels().map { entities ->
            entities.map { it.toAiModel() }
        }
    }

    /**
     * Get all enabled models synchronously
     */
    suspend fun getEnabledModels(): List<AiModel> {
        return aiModelDao.getEnabledModels().map { it.toAiModel() }
    }

    /**
     * Get model by ID
     */
    suspend fun getModelById(id: String): AiModel? {
        return aiModelDao.getModelById(id)?.toAiModel()
    }

    /**
     * Refresh models from DeepInfra API
     * Merges new models with existing preferences (enabled/disabled state, aliases)
     */
    suspend fun refreshModelsFromProvider(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val result = deepInfraModelService.fetchAvailableModels()

            result.fold(
                onSuccess = { fetchedModels ->
                    // Get existing models to preserve user preferences
                    val existingModels = aiModelDao.getAllModels()
                    val existingMap = existingModels.associateBy { it.id }

                    // Convert fetched models to entities, preserving existing preferences
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

                    // Keep custom models that weren't in the fetch
                    val customModels = existingModels.filter { it.isCustom }

                    // Delete non-custom models and insert all fresh
                    aiModelDao.deleteNonCustomModels()
                    aiModelDao.insertModels(newEntities + customModels)

                    Result.success(newEntities.size)
                },
                onFailure = { error ->
                    // If fetch fails and we have no models, seed with defaults
                    val count = aiModelDao.getModelCount()
                    if (count == 0) {
                        seedDefaultModels()
                    }
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            // Seed defaults if we have nothing
            val count = aiModelDao.getModelCount()
            if (count == 0) {
                seedDefaultModels()
            }
            Result.failure(e)
        }
    }

    /**
     * Seed database with default curated models
     */
    suspend fun seedDefaultModels() {
        val defaults = DeepInfraModels.allModels.map { model ->
            model.toEntity().copy(
                isEnabled = true,
                addedAt = System.currentTimeMillis()
            )
        }
        aiModelDao.insertModels(defaults)
    }

    /**
     * Initialize models if database is empty
     */
    suspend fun initializeModelsIfEmpty() {
        val count = aiModelDao.getModelCount()
        if (count == 0) {
            seedDefaultModels()
        }
    }

    /**
     * Enable or disable a model
     */
    suspend fun setModelEnabled(modelId: String, enabled: Boolean) {
        aiModelDao.setModelEnabled(modelId, enabled)
    }

    /**
     * Set model as favorite
     */
    suspend fun setModelFavorite(modelId: String, favorite: Boolean) {
        aiModelDao.setModelFavorite(modelId, favorite)
    }

    /**
     * Update model alias (user-defined name)
     */
    suspend fun setModelAlias(modelId: String, alias: String?) {
        aiModelDao.setModelAlias(modelId, alias)
    }

    /**
     * Record model usage (for sorting by frequency)
     */
    suspend fun recordModelUsage(modelId: String) {
        aiModelDao.recordModelUsage(modelId)
    }

    /**
     * Add a custom model manually
     */
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

    /**
     * Delete a model (only custom models can be deleted)
     */
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

    /**
     * Get model count
     */
    suspend fun getModelCount(): Int {
        return aiModelDao.getModelCount()
    }

    /**
     * Get enabled model count
     */
    suspend fun getEnabledModelCount(): Int {
        return aiModelDao.getEnabledModelCount()
    }

    // Extension functions for conversion
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
}
