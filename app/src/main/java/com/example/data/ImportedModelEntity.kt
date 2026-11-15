package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.LocalAiModelSpec
import com.example.model.LocalModelRole

@Entity(tableName = "imported_local_models")
data class ImportedModelEntity(
    @PrimaryKey val id: String,
    val roleName: String,
    val displayName: String,
    val fileUri: String,
    val fileFormat: String,
    val fileSizeBytes: Long,
    val binaryHeaderSummary: String,
    val isHeaderVerified: Boolean,
    val localEndpointUrl: String,
    val isActive: Boolean,
    val addedAt: Long
) {
    fun toSpec(): LocalAiModelSpec {
        val role = LocalModelRole.entries.firstOrNull { it.name == roleName }
            ?: LocalModelRole.LLM_ORCHESTRATOR
        return LocalAiModelSpec(
            id = id,
            role = role,
            displayName = displayName,
            fileUri = fileUri,
            fileFormat = fileFormat,
            fileSizeBytes = fileSizeBytes,
            binaryHeaderSummary = binaryHeaderSummary,
            isHeaderVerified = isHeaderVerified,
            localEndpointUrl = localEndpointUrl,
            isActive = isActive,
            addedAt = addedAt
        )
    }

    companion object {
        fun fromSpec(spec: LocalAiModelSpec): ImportedModelEntity = ImportedModelEntity(
            id = spec.id,
            roleName = spec.role.name,
            displayName = spec.displayName,
            fileUri = spec.fileUri,
            fileFormat = spec.fileFormat,
            fileSizeBytes = spec.fileSizeBytes,
            binaryHeaderSummary = spec.binaryHeaderSummary,
            isHeaderVerified = spec.isHeaderVerified,
            localEndpointUrl = spec.localEndpointUrl,
            isActive = spec.isActive,
            addedAt = spec.addedAt
        )
    }
}
