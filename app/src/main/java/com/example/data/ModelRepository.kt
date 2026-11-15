package com.example.data

import com.example.model.LocalAiModelSpec
import com.example.model.LocalModelRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ModelRepository(private val modelDao: ModelDao) {

    val allModels: Flow<List<LocalAiModelSpec>> = modelDao.observeAllModels()
        .map { entities -> entities.map { it.toSpec() } }

    suspend fun saveAndActivate(spec: LocalAiModelSpec) {
        modelDao.clearActiveForRole(spec.role.name)
        modelDao.upsertModel(ImportedModelEntity.fromSpec(spec.copy(isActive = true)))
    }

    suspend fun setActive(spec: LocalAiModelSpec) {
        modelDao.clearActiveForRole(spec.role.name)
        modelDao.markActive(spec.id)
    }

    suspend fun getActiveForRole(role: LocalModelRole): LocalAiModelSpec? {
        return modelDao.getActiveForRole(role.name)?.toSpec()
    }

    suspend fun updateEndpoint(id: String, endpointUrl: String) {
        modelDao.updateEndpoint(id, endpointUrl.trim())
    }

    suspend fun deleteModel(spec: LocalAiModelSpec) {
        modelDao.deleteModel(ImportedModelEntity.fromSpec(spec))
    }
}
