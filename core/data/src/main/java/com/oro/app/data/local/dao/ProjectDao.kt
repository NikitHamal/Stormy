package com.oro.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.oro.app.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    // Pinned first, then most-recently-opened. Ordering lives in the query
    // rather than in the UI so every consumer -- list, search, sync read --
    // agrees, and so SQLite does the sort on an already-indexed column.
    @Query("SELECT * FROM projects ORDER BY isPinned DESC, lastOpenedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :projectId")
    fun observeProjectById(projectId: String): Flow<ProjectEntity?>

    @Query(
        "SELECT * FROM projects WHERE name LIKE '%' || :query || '%' " +
            "ORDER BY isPinned DESC, lastOpenedAt DESC"
    )
    fun searchProjects(query: String): Flow<List<ProjectEntity>>

    @Query("SELECT COUNT(*) FROM projects WHERE name = :name")
    suspend fun countProjectsByName(name: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: String)

    @Query("UPDATE projects SET lastOpenedAt = :timestamp WHERE id = :projectId")
    suspend fun updateLastOpenedAt(projectId: String, timestamp: Long)

    @Query("UPDATE projects SET updatedAt = :timestamp WHERE id = :projectId")
    suspend fun updateUpdatedAt(projectId: String, timestamp: Long)

    @Query("UPDATE projects SET thumbnailPath = :path WHERE id = :projectId")
    suspend fun updateThumbnailPath(projectId: String, path: String?)

    @Query("UPDATE projects SET lastUsedModelId = :modelId WHERE id = :projectId")
    suspend fun updateLastUsedModelId(projectId: String, modelId: String?)

    /** Move every project on model [from] to [to]; returns how many changed. */
    @Query("UPDATE projects SET lastUsedModelId = :to WHERE lastUsedModelId = :from")
    suspend fun replaceLastUsedModelId(from: String, to: String): Int

    @Query("SELECT * FROM projects ORDER BY isPinned DESC, lastOpenedAt DESC")
    suspend fun getAllProjectsSync(): List<ProjectEntity>

    @Query("UPDATE projects SET rootPath = :newPath WHERE id = :projectId")
    suspend fun updateRootPath(projectId: String, newPath: String)

    // A targeted UPDATE, not a read-modify-write of the whole row: pinning
    // must not bump updatedAt or race a rename happening at the same time.
    @Query("UPDATE projects SET isPinned = :pinned WHERE id = :projectId")
    suspend fun setPinned(projectId: String, pinned: Boolean)
}
