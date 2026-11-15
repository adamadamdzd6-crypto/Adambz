package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.CameraMotion
import com.example.model.SceneTransition
import com.example.model.VideoAspectRatio
import com.example.model.VideoGenerationMode
import com.example.model.VideoProject
import com.example.model.VideoScene
import com.example.model.VideoVisualStyle
import com.example.model.VisualMotif
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "saved_video_projects")
data class SavedProject(
    @PrimaryKey val id: String,
    val prompt: String,
    val title: String,
    val synopsis: String,
    val styleName: String,
    val aspectRatioName: String,
    val generationModeName: String,
    val musicMood: String,
    val primaryColorHex: String,
    val secondaryColorHex: String,
    val accentColorHex: String,
    val scenesJson: String,
    val exportedMp4Path: String?,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toVideoProject(): VideoProject {
        val style = VideoVisualStyle.entries.firstOrNull { it.name == styleName }
            ?: VideoVisualStyle.CINEMATIC_35MM
        val aspect = VideoAspectRatio.entries.firstOrNull { it.name == aspectRatioName }
            ?: VideoAspectRatio.LANDSCAPE_16_9
        val mode = VideoGenerationMode.entries.firstOrNull { it.name == generationModeName }
            ?: VideoGenerationMode.ON_DEVICE_OFFLINE

        val scenes = mutableListOf<VideoScene>()
        try {
            val arr = JSONArray(scenesJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                scenes.add(
                    VideoScene(
                        id = obj.optString("id", "sc_$i"),
                        sceneNumber = obj.optInt("sceneNumber", i + 1),
                        title = obj.optString("title", "Scene ${i + 1}"),
                        visualPrompt = obj.optString("visualPrompt", ""),
                        narrationSubtitle = obj.optString("narrationSubtitle", ""),
                        durationSeconds = obj.optDouble("durationSeconds", 4.0).toFloat(),
                        cameraMotion = CameraMotion.fromWire(obj.optString("cameraMotion", "ZOOM_IN")),
                        transition = SceneTransition.fromWire(obj.optString("transition", "CROSSFADE")),
                        moodColorHex = obj.optString("moodColorHex", primaryColorHex),
                        accentColorHex = obj.optString("accentColorHex", accentColorHex),
                        visualMotif = VisualMotif.fromWire(obj.optString("visualMotif", "rays")),
                        characterType = obj.optString("characterType", "HUMAN"),
                        characterAction = obj.optString("characterAction", "WALK"),
                        sceneBlueprintJson = obj.optString("sceneBlueprintJson", ""),
                        imagePath = obj.optString("imagePath", "").takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (_: Exception) {}

        return VideoProject(
            id = id,
            prompt = prompt,
            title = title,
            synopsis = synopsis,
            style = style,
            aspectRatio = aspect,
            generationMode = mode,
            musicMood = musicMood,
            primaryColorHex = primaryColorHex,
            secondaryColorHex = secondaryColorHex,
            accentColorHex = accentColorHex,
            scenes = scenes,
            exportedMp4Path = exportedMp4Path,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromVideoProject(project: VideoProject): SavedProject {
            val arr = JSONArray()
            for (scene in project.scenes) {
                arr.put(
                    JSONObject().apply {
                        put("id", scene.id)
                        put("sceneNumber", scene.sceneNumber)
                        put("title", scene.title)
                        put("visualPrompt", scene.visualPrompt)
                        put("narrationSubtitle", scene.narrationSubtitle)
                        put("durationSeconds", scene.durationSeconds.toDouble())
                        put("cameraMotion", scene.cameraMotion.name)
                        put("transition", scene.transition.name)
                        put("moodColorHex", scene.moodColorHex)
                        put("accentColorHex", scene.accentColorHex)
                        put("visualMotif", scene.visualMotif.wireName)
                        put("characterType", scene.characterType)
                        put("characterAction", scene.characterAction)
                        put("sceneBlueprintJson", scene.sceneBlueprintJson)
                        put("imagePath", scene.imagePath ?: "")
                    }
                )
            }
            return SavedProject(
                id = project.id,
                prompt = project.prompt,
                title = project.title,
                synopsis = project.synopsis,
                styleName = project.style.name,
                aspectRatioName = project.aspectRatio.name,
                generationModeName = project.generationMode.name,
                musicMood = project.musicMood,
                primaryColorHex = project.primaryColorHex,
                secondaryColorHex = project.secondaryColorHex,
                accentColorHex = project.accentColorHex,
                scenesJson = arr.toString(),
                exportedMp4Path = project.exportedMp4Path,
                createdAt = project.createdAt
            )
        }
    }
}
