package com.example.model

import androidx.compose.ui.graphics.Color

enum class VideoAspectRatio(
    val label: String,
    val shortLabel: String,
    val ratio: Float,
    val widthPx: Int,
    val heightPx: Int
) {
    LANDSCAPE_16_9(
        label = "16:9 Cinema",
        shortLabel = "16:9",
        ratio = 16f / 9f,
        widthPx = 1280,
        heightPx = 720
    ),
    PORTRAIT_9_16(
        label = "9:16 Reels / Shorts",
        shortLabel = "9:16",
        ratio = 9f / 16f,
        widthPx = 720,
        heightPx = 1280
    ),
    SQUARE_1_1(
        label = "1:1 Square",
        shortLabel = "1:1",
        ratio = 1f,
        widthPx = 960,
        heightPx = 960
    )
}

enum class VideoVisualStyle(
    val label: String,
    val arabicLabel: String,
    val promptDirective: String
) {
    CINEMATIC_35MM(
        label = "Cinematic 35mm",
        arabicLabel = "سينمائي 35mm",
        promptDirective = "anamorphic 35mm cinema film, dramatic volumetric lighting, shallow depth of field, rich color grading"
    ),
    ANIME_STUDIO(
        label = "Anime Studio",
        arabicLabel = "أنمي احترافي",
        promptDirective = "high-budget Japanese anime feature film aesthetic, luminous sky, painterly backgrounds, expressive lighting"
    ),
    CYBERPUNK_NEON(
        label = "Cyberpunk Neon",
        arabicLabel = "نيون مستقبلي",
        promptDirective = "futuristic cyberpunk cityscape, glowing cyan and magenta neon reflections, wet asphalt, atmospheric mist"
    ),
    DOCUMENTARY_REAL(
        label = "Documentary",
        arabicLabel = "وثائقي واقعي",
        promptDirective = "ultra-realistic National Geographic 8K documentary photography, natural golden hour lighting, crisp detail"
    ),
    PIXAR_3D(
        label = "3D Animation",
        arabicLabel = "ثلاثي الأبعاد 3D",
        promptDirective = "stylized 3D animated cinema still, subsurface scattering, soft studio rim light, vibrant stylized textures"
    ),
    MINIMAL_MOTION(
        label = "Minimalist Editorial",
        arabicLabel = "تصميم حديث",
        promptDirective = "ultra-clean architectural minimalism, geometric shadows, calm luxury editorial color palette"
    )
}

enum class VideoGenerationMode(
    val title: String,
    val subtitle: String
) {
    ON_DEVICE_OFFLINE(
        title = "قوة الهاتف (بدون إنترنت ⚡)",
        subtitle = "Offline Hardware Engine • تحريك الشخصيات وتوليد السينما محلياً"
    ),
    GEMINI_CLOUD_BACKEND(
        title = "Gemini AI Backend (Firebase AI)",
        subtitle = "Google AI Studio SDK • توليد الشخصيات عبر Gemini"
    )
}

enum class CameraMotion(val label: String) {
    ZOOM_IN("Zoom In"),
    ZOOM_OUT("Zoom Out"),
    PAN_LEFT("Pan Left"),
    PAN_RIGHT("Pan Right"),
    KEN_BURNS_DRIFT("Ken Burns Drift");

    companion object {
        fun fromWire(value: String): CameraMotion =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: ZOOM_IN
    }
}

enum class SceneTransition(val label: String) {
    CROSSFADE("Crossfade"),
    SLIDE_LEFT("Slide Wipe"),
    ZOOM_BURST("Zoom Cut"),
    FADE_BLACK("Dip to Black");

    companion object {
        fun fromWire(value: String): SceneTransition =
            entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: CROSSFADE
    }
}

enum class VisualMotif(val wireName: String) {
    PARTICLES("particles"),
    RAYS("rays"),
    WAVES("waves"),
    GRID("grid"),
    BOKEH("bokeh"),
    HORIZON("horizon");

    companion object {
        fun fromWire(value: String): VisualMotif =
            entries.firstOrNull { it.wireName.equals(value.trim(), ignoreCase = true) } ?: RAYS
    }
}

data class VideoScene(
    val id: String,
    val sceneNumber: Int,
    val title: String,
    val visualPrompt: String,
    val narrationSubtitle: String,
    val durationSeconds: Float = 4f,
    val cameraMotion: CameraMotion = CameraMotion.ZOOM_IN,
    val transition: SceneTransition = SceneTransition.CROSSFADE,
    val moodColorHex: String = "#0F172A",
    val accentColorHex: String = "#06B6D4",
    val visualMotif: VisualMotif = VisualMotif.RAYS,
    val characterType: String = "HUMAN",
    val characterAction: String = "WALK",
    val sceneBlueprintJson: String = "",
    val imagePath: String? = null,
    val isGeneratingImage: Boolean = false,
    val imageError: String? = null
)

data class VideoProject(
    val id: String,
    val prompt: String,
    val title: String,
    val synopsis: String,
    val style: VideoVisualStyle,
    val aspectRatio: VideoAspectRatio,
    val generationMode: VideoGenerationMode,
    val musicMood: String,
    val primaryColorHex: String,
    val secondaryColorHex: String,
    val accentColorHex: String,
    val scenes: List<VideoScene>,
    val renderWidthPx: Int = aspectRatio.widthPx,
    val renderHeightPx: Int = aspectRatio.heightPx,
    val targetFps: Int = 24,
    val targetBitrate: Int = 4_000_000,
    val detailDensity: Float = 1.0f,
    val exportedMp4Path: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val totalDurationSeconds: Float
        get() = scenes.sumOf { it.durationSeconds.toDouble() }.toFloat().coerceAtLeast(1f)
}

sealed interface VideoUiState {
    data object Idle : VideoUiState
    data object GeneratingScript : VideoUiState
    data class GeneratingFrames(val completed: Int, val total: Int) : VideoUiState
    data class Ready(val project: VideoProject) : VideoUiState
    data class Error(val message: String, val isImageBillingOrQuotaIssue: Boolean = false) : VideoUiState
}

fun parseColorSafe(hex: String, fallback: Color = Color(0xFF06B6D4)): Color {
    val clean = hex.removePrefix("#").trim()
    return try {
        when (clean.length) {
            6 -> Color(android.graphics.Color.parseColor("#$clean"))
            8 -> Color(android.graphics.Color.parseColor("#$clean"))
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}
