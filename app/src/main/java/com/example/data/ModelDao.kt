package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM imported_local_models ORDER BY addedAt DESC")
    fun observeAllModels(): Flow<List<ImportedModelEntity>>

    @Query("SELECT * FROM imported_local_models WHERE roleName = :roleName AND isActive = 1 LIMIT 1")
    suspend fun getActiveForRole(roleName: String): ImportedModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModel(model: ImportedModelEntity)

    @Query("UPDATE imported_local_models SET isActive = 0 WHERE roleName = :roleName")
    suspend fun clearActiveForRole(roleName: String)

    @Query("UPDATE imported_local_models SET isActive = 1 WHERE id = :id")
    suspend fun markActive(id: String)

    @Query("UPDATE imported_local_models SET localEndpointUrl = :endpointUrl WHERE id = :id")
    suspend fun updateEndpoint(id: String, endpointUrl: String)

    @Delete
    suspend fun deleteModel(model: ImportedModelEntity)
}
