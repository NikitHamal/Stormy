package com.codex.stormy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.codex.stormy.data.local.entity.AiModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiModelDao {

    @Query("SELECT * FROM ai_models ORDER BY isFavorite DESC, usageCount DESC, name ASC")
    fun observeAllModels(): Flow<List<AiModelEntity>>

    @Query("SELECT * FROM ai_models WHERE isEnabled = 1 ORDER BY isFavorite DESC, usageCount DESC, name ASC")
    fun observeEnabledModels(): Flow<List<AiModelEntity>>

    @Query("SELECT * FROM ai_models WHERE provider = :provider ORDER BY name ASC")
    fun observeModelsByProvider(provider: String): Flow<List<AiModelEntity>>

    @Query("SELECT * FROM ai_models WHERE id = :id")
    suspend fun getModelById(id: String): AiModelEntity?

    @Query("SELECT * FROM ai_models")
    suspend fun getAllModels(): List<AiModelEntity>

    @Query("SELECT * FROM ai_models WHERE isEnabled = 1")
    suspend fun getEnabledModels(): List<AiModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: AiModelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<AiModelEntity>)

    @Update
    suspend fun updateModel(model: AiModelEntity)

    @Delete
    suspend fun deleteModel(model: AiModelEntity)

    @Query("DELETE FROM ai_models WHERE id = :id")
    suspend fun deleteModelById(id: String)

    @Query("DELETE FROM ai_models WHERE isCustom = 0")
    suspend fun deleteNonCustomModels()

    @Query("UPDATE ai_models SET isEnabled = :isEnabled, updatedAt = :timestamp WHERE id = :id")
    suspend fun setModelEnabled(id: String, isEnabled: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE ai_models SET isFavorite = :isFavorite, updatedAt = :timestamp WHERE id = :id")
    suspend fun setModelFavorite(id: String, isFavorite: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE ai_models SET alias = :alias, updatedAt = :timestamp WHERE id = :id")
    suspend fun setModelAlias(id: String, alias: String?, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE ai_models SET lastUsed = :timestamp, usageCount = usageCount + 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun recordModelUsage(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM ai_models")
    suspend fun getModelCount(): Int

    @Query("SELECT COUNT(*) FROM ai_models WHERE isEnabled = 1")
    suspend fun getEnabledModelCount(): Int
}
