package com.example.data

import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val dao: ProjectDao) {
    val allProjects: Flow<List<SavedProject>> = dao.getAllProjects()

    suspend fun saveProject(project: SavedProject) = dao.insertProject(project)

    suspend fun deleteProject(project: SavedProject) = dao.deleteProject(project)

    suspend fun deleteProjectById(id: String) = dao.deleteProjectById(id)

    suspend fun getProjectById(id: String): SavedProject? = dao.getProjectById(id)
}
