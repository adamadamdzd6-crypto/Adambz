package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.model.ActorDirective
import com.example.model.OrchestrationPlan
import com.example.model.VideoAspectRatio
import com.example.model.VideoVisualStyle
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

private const val TAG = "GeminiCharacterBackend"

data class CharacterBlueprintPayload(
    val characterName: String,
    val actorType: String,
    val action: String,
    val primaryColorHex: String,
    val secondaryColorHex: String,
    val characterImagePrompt: String,
    val visualTraitsSummary: String
)

data class CharacterBackendResult(
    val blueprint: CharacterBlueprintPayload,
    val bitmap: Bitmap,
    val savedSpritePath: String?
)

sealed interface CharacterBackendUiState {
    data object Idle : CharacterBackendUiState
    data class Loading(val stageMessage: String) : CharacterBackendUiState
    data class Success(val result: CharacterBackendResult) : CharacterBackendUiState
    data class Error(val message: String) : CharacterBackendUiState
}

/**
 * Boilerplate repository/service interfacing with Google AI Studio's Gemini API
 * via Firebase AI Logic (`Firebase.ai`) to plan character specs and generate
 * character images from user prompts.
 */
object GeminiCharacterBackend {

    private const val PLANNER_MODEL_NAME = "gemini-3.8-flash"
    private const val IMAGE_MODEL_NAME = "gemini-nano-banana-2.1"
    private const val INFERENCE_TIMEOUT_MS = 60_000L

    private val characterBlueprintSchema = Schema.obj(
        properties = mapOf(
            "characterName" to Schema.string(description = "Short descriptive name for the character"),
            "actorType" to Schema.enumeration(
                values = listOf(
                    "HUMAN", "DOG", "CAT", "BIRD", "HORSE", "FISH",
                    "BUTTERFLY", "ROBOT", "MONSTER", "BOAT", "CAR", "PLANE"
                ),
                description = "Closest animation rig archetype for motion control"
            ),
            "action" to Schema.enumeration(
                values = listOf(
                    "WALK", "RUN", "JUMP", "FLY", "SWIM",
                    "DANCE", "HOVER", "CHASE", "IDLE"
                ),
                description = "Primary character motion action"
            ),
            "primaryColorHex" to Schema.string(description = "Primary hex color like #38BDF8"),
            "secondaryColorHex" to Schema.string(description = "Secondary accent hex color like #1E293B"),
            "characterImagePrompt" to Schema.string(
                description = "Detailed English prompt for a single isolated full-body character sprite centered on a pure solid white background, crisp studio silhouette, no text"
            ),
            "visualTraitsSummary" to Schema.string(
                description = "Concise summary of outfit, silhouette, and key visual traits"
            )
        )
    )

    private val characterPlannerModel: GenerativeModel by lazy {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = characterBlueprintSchema
        }
        Firebase.ai.generativeModel(
            modelName = PLANNER_MODEL_NAME,
            generationConfig = config,
            systemInstruction = content {
                text(
                    "You are a character design director. Convert the user's character idea " +
                        "into a structured JSON blueprint and a clear characterImagePrompt suitable " +
                        "for generating an isolated full-body 2D/3D animation character sprite on a pure white background."
                )
            }
        )
    }

    private val defaultCharacterImageModel: GenerativeModel by lazy {
        buildCharacterImageModel(AspectRatio.SQUARE_1x1)
    }

    private fun buildCharacterImageModel(aspectRatio: AspectRatio): GenerativeModel {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(aspectRatio)
                .setImageSize(ImageSize.SIZE_1K)
                .build()
        }
        return Firebase.ai.generativeModel(
            modelName = IMAGE_MODEL_NAME,
            generationConfig = config
        )
    }

    private val videoDirectorSchema = Schema.obj(
        properties = mapOf(
            "projectTitle" to Schema.string(description = "Short vivid title matching the user prompt"),
            "synopsis" to Schema.string(description = "One-sentence summary in the same language as the prompt"),
            "musicMood" to Schema.string(description = "Background soundtrack mood and BPM"),
            "scenes" to Schema.array(
                description = "Sequential scenes matching the exact actions/steps described by the user (1 scene if continuous, N scenes if multiple sequential steps; never repeat scenes)",
                items = Schema.obj(
                    properties = mapOf(
                        "sceneNumber" to Schema.integer(description = "1-based index"),
                        "title" to Schema.string(description = "Concise scene title"),
                        "narrationSubtitle" to Schema.string(description = "On-screen subtitle in the user's language"),
                        "imagePrompt" to Schema.string(description = "Detailed visual scene prompt in English"),
                        "durationSeconds" to Schema.double(description = "Duration between 3.0 and 7.0 seconds"),
                        "cameraMotion" to Schema.enumeration(
                            values = listOf("ZOOM_IN", "ZOOM_OUT", "PAN_LEFT", "PAN_RIGHT", "KEN_BURNS_DRIFT")
                        ),
                        "transition" to Schema.enumeration(
                            values = listOf("CROSSFADE", "SLIDE_LEFT", "ZOOM_BURST", "FADE_BLACK")
                        ),
                        "visualMotif" to Schema.enumeration(
                            values = listOf("particles", "rays", "waves", "grid", "bokeh", "horizon")
                        ),
                        "environment" to Schema.obj(
                            properties = mapOf(
                                "groundType" to Schema.enumeration(
                                    values = listOf("GRASS", "SAND", "ROAD", "WATER", "SPACE", "SNOW", "CITY")
                                ),
                                "weather" to Schema.enumeration(
                                    values = listOf("CLEAR", "RAIN", "SNOW", "STARS", "SPARKS")
                                ),
                                "skyTopHex" to Schema.string(description = "Hex color e.g. #0B132B"),
                                "skyBottomHex" to Schema.string(description = "Hex color e.g. #1D3557"),
                                "groundHex" to Schema.string(description = "Hex color e.g. #1B4332"),
                                "accentHex" to Schema.string(description = "Hex color e.g. #06B6D4"),
                                "props" to Schema.array(
                                    items = Schema.enumeration(
                                        values = listOf(
                                            "TREE", "MOUNTAIN", "PIT_HOLE", "CLOUD", "BRIDGE",
                                            "BUILDING", "HOUSE", "FIRE", "RAINBOW", "FLOWERS", "SUN", "MOON"
                                        )
                                    )
                                )
                            )
                        ),
                        "actors" to Schema.array(
                            items = Schema.obj(
                                properties = mapOf(
                                    "id" to Schema.string(),
                                    "type" to Schema.enumeration(
                                        values = listOf(
                                            "HUMAN", "DOG", "CAT", "BIRD", "HORSE", "FISH",
                                            "BUTTERFLY", "BIKE", "CAR", "BOAT", "PLANE",
                                            "ROBOT", "MONSTER", "BALL"
                                        )
                                    ),
                                    "label" to Schema.string(description = "Character label in user's language"),
                                    "action" to Schema.enumeration(
                                        values = listOf(
                                            "WALK", "RUN", "JUMP", "FALL_INTO_PIT", "SWIM",
                                            "FLY", "DRIVE", "DANCE", "HOVER", "CHASE", "IDLE"
                                        )
                                    ),
                                    "primaryColorHex" to Schema.string(description = "Hex color e.g. #F59E0B"),
                                    "characterImagePrompt" to Schema.string(
                                        description = "Full-body isolated sprite prompt on solid pure white background"
                                    ),
                                    "scale" to Schema.double(description = "Scale factor between 0.7 and 1.6"),
                                    "startX" to Schema.double(description = "Normalized X 0.10 to 0.90"),
                                    "endX" to Schema.double(description = "Normalized X 0.10 to 0.90"),
                                    "startY" to Schema.double(description = "Normalized Y 0.25 to 0.80"),
                                    "endY" to Schema.double(description = "Normalized Y 0.25 to 0.80")
                                )
                            )
                        )
                    )
                )
            )
        )
    )

    private val videoDirectorModel: GenerativeModel by lazy {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = videoDirectorSchema
        }
        Firebase.ai.generativeModel(
            modelName = PLANNER_MODEL_NAME,
            generationConfig = config,
            systemInstruction = content {
                text(
                    "You are a precision video scene director. Faithfully execute EVERY event, character, " +
                        "environment, prop, weather condition, and action from the user's description. " +
                        "Do NOT invent repetitive filler scenes: create 1 continuous scene for a single action, " +
                        "or exact sequential scenes when multiple actions happen in sequence, chaining actor endX to next startX."
                )
            }
        )
    }

    /**
     * Plans an entire executable video storyboard via Gemini (`gemini-3.8-flash`)
     * directly from the user's Arabic or English description.
     */
    suspend fun orchestrateVideoPlanViaGemini(
        userPrompt: String,
        style: VideoVisualStyle
    ): OrchestrationPlan {
        val trimmed = userPrompt.trim()
        require(trimmed.isNotEmpty()) { "Video description must not be empty." }

        val fullPrompt = buildString {
            appendLine("Visual Style: ${style.label} (${style.promptDirective})")
            appendLine("User Video Description: $trimmed")
        }

        val response = withTimeout(INFERENCE_TIMEOUT_MS) {
            videoDirectorModel.generateContent(fullPrompt)
        }
        val jsonString = response.text
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Gemini returned an empty video orchestration plan.")

        val parsed = OrchestrationPlan.fromJson(
            rawJson = jsonString,
            fallbackPrompt = trimmed,
            fallbackStyle = style
        )
        if (parsed.scenes.isEmpty()) {
            throw IllegalStateException("Gemini returned 0 scenes for your prompt.")
        }
        return parsed.copy(
            orchestratorSource = "Gemini 3.8 Flash AI Director",
            originalPrompt = trimmed,
            style = style
        )
    }

    /**
     * Step 1: Sends the raw character prompt to Gemini (`gemini-3.8-flash`) with
     * structured JSON output (`Schema.obj`) to obtain an exact character blueprint.
     */
    suspend fun generateCharacterBlueprint(
        userPrompt: String,
        style: VideoVisualStyle
    ): CharacterBlueprintPayload {
        val prompt = buildString {
            appendLine("Visual style target: ${style.label} (${style.promptDirective})")
            appendLine("Character prompt from user: $userPrompt")
        }

        val response = withTimeout(INFERENCE_TIMEOUT_MS) {
            characterPlannerModel.generateContent(prompt)
        }
        val jsonString = response.text
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Gemini returned an empty character blueprint response.")

        val json = JSONObject(jsonString)
        return CharacterBlueprintPayload(
            characterName = json.getString("characterName"),
            actorType = json.getString("actorType"),
            action = json.getString("action"),
            primaryColorHex = json.getString("primaryColorHex"),
            secondaryColorHex = json.getString("secondaryColorHex"),
            characterImagePrompt = json.getString("characterImagePrompt"),
            visualTraitsSummary = json.getString("visualTraitsSummary")
        )
    }

    /**
     * Step 2: Sends the character-generation prompt to Gemini image model (`gemini-nano-banana-2.1`)
     * and extracts the generated [Bitmap] from the [ImagePart].
     */
    suspend fun generateCharacterImage(
        characterImagePrompt: String,
        aspectRatio: VideoAspectRatio = VideoAspectRatio.SQUARE_1_1
    ): Bitmap {
        val model = when (aspectRatio) {
            VideoAspectRatio.SQUARE_1_1 -> defaultCharacterImageModel
            VideoAspectRatio.LANDSCAPE_16_9 -> buildCharacterImageModel(AspectRatio.LANDSCAPE_16x9)
            VideoAspectRatio.PORTRAIT_9_16 -> buildCharacterImageModel(AspectRatio.PORTRAIT_9x16)
        }

        val response = withTimeout(INFERENCE_TIMEOUT_MS) {
            model.generateContent(characterImagePrompt)
        }

        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()

        return imagePart?.image
            ?: throw IllegalStateException("Gemini image model returned no image part for the character prompt.")
    }

    /**
     * Complete two-stage boilerplate pipeline:
     * 1) Structures the prompt via `gemini-3.8-flash`
     * 2) Generates the character asset via `gemini-nano-banana-2.1`
     * 3) Extracts a clean transparent PNG sprite for animation if [context] is provided.
     *
     * Note: Exceptions are never swallowed into mock fallbacks; callers catch and report real errors.
     */
    suspend fun sendCharacterPromptToBackend(
        context: Context?,
        userPrompt: String,
        style: VideoVisualStyle = VideoVisualStyle.CINEMATIC_35MM
    ): CharacterBackendResult {
        val trimmedPrompt = userPrompt.trim()
        require(trimmedPrompt.isNotEmpty()) { "Character prompt must not be empty." }

        val blueprint = generateCharacterBlueprint(trimmedPrompt, style)
        val rawBitmap = generateCharacterImage(blueprint.characterImagePrompt, VideoAspectRatio.SQUARE_1_1)

        val cutoutBitmap = SpriteCutoutProcessor.extractTransparentCharacterSprite(rawBitmap)
        if (cutoutBitmap !== rawBitmap && !rawBitmap.isRecycled) {
            rawBitmap.recycle()
        }

        val savedPath = if (context != null) {
            saveGeminiCharacterSprite(context, blueprint.actorType, cutoutBitmap)
        } else {
            null
        }

        return CharacterBackendResult(
            blueprint = blueprint,
            bitmap = cutoutBitmap,
            savedSpritePath = savedPath
        )
    }

    fun toActorDirective(result: CharacterBackendResult): ActorDirective {
        val bp = result.blueprint
        return ActorDirective(
            id = "gemini_actor_${System.currentTimeMillis()}",
            type = bp.actorType,
            label = bp.characterName,
            action = bp.action,
            primaryColorHex = bp.primaryColorHex,
            characterImagePrompt = bp.characterImagePrompt,
            spritePath = result.savedSpritePath
        )
    }

    fun formatBackendError(error: Throwable): String {
        return when (error) {
            is TimeoutCancellationException ->
                "Gemini backend request timed out after 60 seconds. Please try again."
            is QuotaExceededException ->
                "Gemini image generation quota exceeded or Blaze billing plan required on Firebase project."
            else -> {
                Log.e(TAG, "Gemini character backend call failed", error)
                error.localizedMessage ?: "Gemini character generation request failed."
            }
        }
    }

    private fun saveGeminiCharacterSprite(
        context: Context,
        actorType: String,
        bitmap: Bitmap
    ): String? {
        return try {
            val dir = File(context.filesDir, "sprites").apply { mkdirs() }
            val outFile = File(dir, "gemini_char_${actorType.lowercase()}_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            outFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist generated Gemini character sprite", e)
            null
        }
    }
}
