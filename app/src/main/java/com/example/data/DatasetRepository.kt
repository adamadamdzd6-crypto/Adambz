package com.example.data

import kotlinx.coroutines.flow.Flow

class DatasetRepository(private val datasetDao: DatasetDao) {
    val allDatasetImages: Flow<List<DatasetReferenceEntity>> = datasetDao.getAllDatasetImages()

    fun searchImages(query: String): Flow<List<DatasetReferenceEntity>> {
        return if (query.isBlank()) allDatasetImages else datasetDao.searchDatasetImages(query)
    }

    suspend fun addImage(image: DatasetReferenceEntity) {
        datasetDao.insertImage(image)
    }

    suspend fun addImagesBatch(images: List<DatasetReferenceEntity>) {
        datasetDao.insertImagesBatch(images)
    }

    suspend fun updateImage(image: DatasetReferenceEntity) {
        datasetDao.updateImage(image)
    }

    suspend fun deleteImage(image: DatasetReferenceEntity) {
        datasetDao.deleteImage(image)
    }

    suspend fun clearDataset() {
        datasetDao.clearAllDataset()
    }

    suspend fun getCount(): Int {
        return datasetDao.getDatasetCount()
    }
}
