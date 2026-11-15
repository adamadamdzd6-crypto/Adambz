package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dataset_reference_images")
data class DatasetReferenceEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val imageUri: String,
    val category: String,
    val addedAt: Long = System.currentTimeMillis()
)
