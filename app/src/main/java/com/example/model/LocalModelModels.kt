package com.example.model

import org.json.JSONArray
import org.json.JSONObject

enum class LocalModelRole(
    val titleAr: String,
    val titleEn: String,
    val subtitleAr: String,
    val supportedExtensions: Set<String>
) {
    LLM_ORCHESTRATOR(
        titleAr = "العقل المدبر (LLM)",
        titleEn = "LLM Orchestrator",
        subtitleAr = "ملفات .gguf أو .bin لتحليل الأوامر وتوليد خطة JSON",
        supportedExtensions = setOf("gguf", "bin", "tflite", "onnx")
    ),
    IMAGE_GENERATOR(
        titleAr = "نموذج الصور (Image AI)",
        titleEn = "Local Image Model",
        subtitleAr = "نماذج توليد اللوحات والصور المحلية (.safetensors / .onnx / .ckpt)",
        supportedExtensions = setOf("safetensors", "onnx", "ckpt", "pt", "tflite", "bin")
    ),
    VIDEO_ENGINE(
        titleAr = "محرك الفيديو والحركة (Video)",
        titleEn = "Video & Motion Engine",
        subtitleAr = "نماذج أو محركات التحريك وتوليد الفيديو على الهاتف (.onnx / .tflite)",
        supportedExtensions = setOf("onnx", "tflite", "safetensors", "bin", "gguf")
    )
}

data class LocalAiModelSpec(
    val id: String,
    val role: LocalModelRole,
    val displayName: String,
    val fileUri: String,
    val fileFormat: String,
    val fileSizeBytes: Long,
    val binaryHeaderSummary: String,
    val isHeaderVerified: Boolean,
    val localEndpointUrl: String = "",
    val isActive: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

sealed interface PipelineStageState {
    data object Idle : PipelineStageState
    data class OrchestratingJson(val bridgeName: String) : PipelineStageState
    data class GeneratingKeyframes(val sceneIndex: Int, val totalScenes: Int, val bridgeName: String) : PipelineStageState
    data class AssemblingTimeline(val bridgeName: String) : PipelineStageState
    data class Completed(val engineSummary: String) : PipelineStageState
    data class Failed(val reason: String) : PipelineStageState
}

/**
 * Structured JSON command contract emitted by the LLM Orchestrator
 * and consumed by the Media Pipeline Engine.
 */
data class SceneEnvSpec(
    val groundType: String = "GRASS",      // GRASS, SAND, ROAD, WATER, SPACE, SNOW, CITY
    val weather: String = "CLEAR",         // CLEAR, RAIN, SNOW, STARS, SPARKS
    val skyTopHex: String = "#0B132B",
    val skyBottomHex: String = "#1D3557",
    val groundHex: String = "#1B4332",
    val accentHex: String = "#06B6D4",
    val props: List<String> = emptyList()  // PIT_HOLE, TREE, MOUNTAIN, CLOUD, BRIDGE, BUILDING, SUN, MOON
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("groundType", groundType)
        put("weather", weather)
        put("skyTopHex", skyTopHex)
        put("skyBottomHex", skyBottomHex)
        put("groundHex", groundHex)
        put("accentHex", accentHex)
        put("props", JSONArray(props))
    }

    companion object {
        fun fromJson(obj: JSONObject?): SceneEnvSpec {
            if (obj == null) return SceneEnvSpec()
            val propsArr = obj.optJSONArray("props")
            val props = buildList {
                if (propsArr != null) {
                    for (i in 0 until propsArr.length()) {
                        val item = propsArr.optString(i, "").trim()
                        if (item.isNotEmpty()) add(item.uppercase())
                    }
                }
            }
            return SceneEnvSpec(
                groundType = obj.optString("groundType", "GRASS").uppercase(),
                weather = obj.optString("weather", "CLEAR").uppercase(),
                skyTopHex = obj.optString("skyTopHex", "#0B132B"),
                skyBottomHex = obj.optString("skyBottomHex", "#1D3557"),
                groundHex = obj.optString("groundHex", "#1B4332"),
                accentHex = obj.optString("accentHex", "#06B6D4"),
                props = props
            )
        }
    }
}

data class ActorDirective(
    val id: String = "actor_1",
    val type: String = "HUMAN",            // HUMAN, DOG, CAT, BIRD, HORSE, FISH, BUTTERFLY, BIKE, CAR, BOAT, PLANE, ROBOT, MONSTER, BALL
    val label: String = "",
    val action: String = "WALK",           // WALK, RUN, JUMP, FALL_INTO_PIT, SWIM, FLY, DRIVE, DANCE, HOVER, CHASE, IDLE
    val primaryColorHex: String = "#F59E0B",
    val characterImagePrompt: String = "", // Prompt sent to Image Generator AI to create this character sprite
    val spritePath: String? = null,        // Cutout transparent PNG path produced by Image Model & animated by Engine
    val scale: Float = 1.0f,
    val startX: Float = 0.2f,
    val endX: Float = 0.8f,
    val startY: Float = 0.68f,
    val endY: Float = 0.68f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("label", label)
        put("action", action)
        put("primaryColorHex", primaryColorHex)
        put("characterImagePrompt", characterImagePrompt)
        put("spritePath", spritePath ?: "")
        put("scale", scale.toDouble())
        put("startX", startX.toDouble())
        put("endX", endX.toDouble())
        put("startY", startY.toDouble())
        put("endY", endY.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject): ActorDirective {
            return ActorDirective(
                id = obj.optString("id", "actor_1"),
                type = obj.optString("type", "HUMAN").uppercase(),
                label = obj.optString("label", ""),
                action = obj.optString("action", "WALK").uppercase(),
                primaryColorHex = obj.optString("primaryColorHex", "#F59E0B"),
                characterImagePrompt = obj.optString("characterImagePrompt", ""),
                spritePath = obj.optString("spritePath", "").takeIf { it.isNotBlank() },
                scale = obj.optDouble("scale", 1.0).toFloat().coerceIn(0.4f, 2.2f),
                startX = obj.optDouble("startX", 0.2).toFloat().coerceIn(0.05f, 0.95f),
                endX = obj.optDouble("endX", 0.8).toFloat().coerceIn(0.05f, 0.95f),
                startY = obj.optDouble("startY", 0.68).toFloat().coerceIn(0.15f, 0.9f),
                endY = obj.optDouble("endY", 0.68).toFloat().coerceIn(0.15f, 0.9f)
            )
        }
    }
}

data class SceneBlueprint(
    val sceneNumber: Int,
    val title: String,
    val narrationSubtitle: String,
    val imagePrompt: String,
    val durationSeconds: Float = 3.5f,
    val cameraMotion: CameraMotion = CameraMotion.ZOOM_IN,
    val transition: SceneTransition = SceneTransition.CROSSFADE,
    val visualMotif: VisualMotif = VisualMotif.RAYS,
    val environment: SceneEnvSpec = SceneEnvSpec(),
    val actors: List<ActorDirective> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sceneNumber", sceneNumber)
        put("title", title)
        put("narrationSubtitle", narrationSubtitle)
        put("imagePrompt", imagePrompt)
        put("durationSeconds", durationSeconds.toDouble())
        put("cameraMotion", cameraMotion.name)
        put("transition", transition.name)
        put("visualMotif", visualMotif.wireName)
        put("environment", environment.toJson())
        put("actors", JSONArray().apply { actors.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(obj: JSONObject, index: Int = 0): SceneBlueprint {
            val actorsArr = obj.optJSONArray("actors")
            val actors = buildList {
                if (actorsArr != null) {
                    for (i in 0 until actorsArr.length()) {
                        actorsArr.optJSONObject(i)?.let { add(ActorDirective.fromJson(it)) }
                    }
                }
            }
            return SceneBlueprint(
                sceneNumber = obj.optInt("sceneNumber", index + 1),
                title = obj.optString("title", "Scene ${index + 1}"),
                narrationSubtitle = obj.optString("narrationSubtitle", ""),
                imagePrompt = obj.optString("imagePrompt", ""),
                durationSeconds = obj.optDouble("durationSeconds", 3.5).toFloat().coerceIn(2f, 10f),
                cameraMotion = CameraMotion.fromWire(obj.optString("cameraMotion", "ZOOM_IN")),
                transition = SceneTransition.fromWire(obj.optString("transition", "CROSSFADE")),
                visualMotif = VisualMotif.fromWire(obj.optString("visualMotif", "rays")),
                environment = SceneEnvSpec.fromJson(obj.optJSONObject("environment")),
                actors = actors
            )
        }
    }
}

data class OrchestrationPlan(
    val planId: String,
    val orchestratorSource: String,
    val originalPrompt: String,
    val projectTitle: String,
    val synopsis: String,
    val musicMood: String,
    val style: VideoVisualStyle,
    val scenes: List<SceneBlueprint>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("planId", planId)
        put("orchestratorSource", orchestratorSource)
        put("originalPrompt", originalPrompt)
        put("projectTitle", projectTitle)
        put("synopsis", synopsis)
        put("musicMood", musicMood)
        put("style", style.name)
        put("scenes", JSONArray().apply { scenes.forEach { put(it.toJson()) } })
    }

    fun toPrettyJson(): String = toJson().toString(2)

    companion object {
        fun fromJson(rawJson: String, fallbackPrompt: String = "", fallbackStyle: VideoVisualStyle = VideoVisualStyle.CINEMATIC_35MM): OrchestrationPlan {
            val root = JSONObject(rawJson)
            val styleName = root.optString("style", fallbackStyle.name)
            val resolvedStyle = VideoVisualStyle.entries.firstOrNull { it.name.equals(styleName, ignoreCase = true) }
                ?: fallbackStyle
            val scenesArr = root.optJSONArray("scenes") ?: JSONArray()
            val scenes = buildList {
                for (i in 0 until scenesArr.length()) {
                    scenesArr.optJSONObject(i)?.let { add(SceneBlueprint.fromJson(it, i)) }
                }
            }
            return OrchestrationPlan(
                planId = root.optString("planId", "plan_${System.currentTimeMillis()}"),
                orchestratorSource = root.optString("orchestratorSource", "Custom JSON Plan"),
                originalPrompt = root.optString("originalPrompt", fallbackPrompt),
                projectTitle = root.optString("projectTitle", fallbackPrompt.take(36).ifBlank { "Local AI Production" }),
                synopsis = root.optString("synopsis", "Structured Multi-Model Local AI Production"),
                musicMood = root.optString("musicMood", "Cinematic Ambient 105 BPM"),
                style = resolvedStyle,
                scenes = scenes
            )
        }
    }
}
