package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DatasetDao {
    @Query("SELECT * FROM dataset_reference_images ORDER BY addedAt DESC")
    fun getAllDatasetImages(): Flow<List<DatasetReferenceEntity>>

    @Query("SELECT * FROM dataset_reference_images WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY addedAt DESC")
    fun searchDatasetImages(query: String): Flow<List<DatasetReferenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: DatasetReferenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImagesBatch(images: List<DatasetReferenceEntity>)

    @Update
    suspend fun updateImage(image: DatasetReferenceEntity)

    @Delete
    suspend fun deleteImage(image: DatasetReferenceEntity)

    @Query("DELETE FROM dataset_reference_images")
    suspend fun clearAllDataset()

    @Query("SELECT COUNT(*) FROM dataset_reference_images")
    suspend fun getDatasetCount(): Int
}
