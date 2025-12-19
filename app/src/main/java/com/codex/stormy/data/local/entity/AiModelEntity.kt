package com.codex.stormy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing an AI model stored locally
 */
@Entity(tableName = "ai_models")
data class AiModelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val alias: String? = null, // User-defined alias
    val provider: String, // DEEPINFRA, OPENAI, ANTHROPIC
    val contextLength: Int = 4096,
    val supportsStreaming: Boolean = true,
    val supportsToolCalls: Boolean = true,
    val isThinkingModel: Boolean = false,
    val isEnabled: Boolean = true,
    val isFavorite: Boolean = false,
    val lastUsed: Long? = null,
    val usageCount: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isCustom: Boolean = false, // User manually added
    val description: String? = null,
    val maxOutputTokens: Int? = null,
    val inputPricePerToken: Double? = null,
    val outputPricePerToken: Double? = null
)
