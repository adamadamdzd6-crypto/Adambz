package com.example.service

import com.example.model.VideoAspectRatio
import com.example.model.VideoProject
import com.example.model.VideoVisualStyle

/**
 * Compatibility facade preserving direct offline project creation via the
 * LocalOrchestratorBridge + MediaPipelineEngine team architecture.
 */
object OfflineVideoDirector {

    fun generateOfflineProject(
        prompt: String,
        style: VideoVisualStyle,
        aspectRatio: VideoAspectRatio,
        hardwareProfile: DevicePerformanceProfile
    ): VideoProject {
        val plan = LocalOrchestratorBridge.orchestratePrompt(
            prompt = prompt,
            style = style,
            activeLlmModel = null
        )
        return kotlinx.coroutines.runBlocking {
            MediaPipelineEngine.executePlanToVideoProject(
                context = null,
                plan = plan,
                aspectRatio = aspectRatio,
                hardwareProfile = hardwareProfile
            )
        }
    }
}
