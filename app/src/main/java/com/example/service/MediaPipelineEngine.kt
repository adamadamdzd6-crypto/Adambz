package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.model.ActorDirective
import com.example.model.LocalAiModelSpec
import com.example.model.OrchestrationPlan
import com.example.model.VideoAspectRatio
import com.example.model.VideoGenerationMode
import com.example.model.VideoProject
import com.example.model.VideoScene
import com.example.model.VideoVisualStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Media Pipeline Engine ("محرك الوسائط والتنفيذ"):
 * 100% Offline Local Multi-Model Collaboration Bridge:
 * 1. Receives Structured JSON (`OrchestrationPlan`) from the Brain/Mediator (`LocalOrchestratorBridge`).
 * 2. Explicitly asks the Local Image Model (`IMAGE_GENERATOR`: Loopback 127.0.0.1:7860 or
 *    On-Device Sprite Synthesizer) for each character's isolated PNG sprite
 *    (`actor.characterImagePrompt` -> `actor.spritePath`), strips backdrops cleanly.
 * 3. Binds the generated Character Sprite images + Scene Motion JSON into `VideoProject` so
 *    `CinemaFrameRenderer` & `Mp4VideoExporter` physically animate the generated Character Images.
 */
object MediaPipelineEngine {

    suspend fun executePlanToVideoProject(
        context: Context?,
        plan: OrchestrationPlan,
        aspectRatio: VideoAspectRatio,
        hardwareProfile: DevicePerformanceProfile,
        activeImageModel: LocalAiModelSpec? = null,
        activeVideoModel: LocalAiModelSpec? = null,
        onSceneImageProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): VideoProject = withContext(Dispatchers.IO) {
        val specs = hardwareProfile.resolveRenderSpecs(aspectRatio)
        val projectId = UUID.randomUUID().toString()
        val scenes = plan.scenes
        val total = scenes.size

        val imageBridgeEndpoint = activeImageModel?.localEndpointUrl.orEmpty()
        val hasLocalImageEndpoint = imageBridgeEndpoint.isNotBlank() &&
            LocalModelInspector.isLocalLoopbackUrl(imageBridgeEndpoint)

        // Cache generated character sprites across scenes so the same character keeps visual identity!
        val actorSpriteCache = mutableMapOf<String, String>()

        val videoScenes = scenes.mapIndexed { idx, blueprint ->
            // Step A: Ask the Local Image Model for each actor's cutout Character Sprite
            val actorsWithSprites = blueprint.actors.map { actor ->
                val cacheKey = "${actor.type}_${actor.primaryColorHex}_${actor.characterImagePrompt.hashCode()}"
                val existingValidPath = actor.spritePath?.takeIf { File(it).exists() }
                val spritePath = existingValidPath
                    ?: actorSpriteCache[cacheKey]
                    ?: resolveCharacterSprite(
                        context = context,
                        actor = actor,
                        localImageEndpoint = if (hasLocalImageEndpoint) imageBridgeEndpoint else null
                    )?.also { path ->
                        actorSpriteCache[cacheKey] = path
                    }

                actor.copy(spritePath = spritePath)
            }

            // Step B: Optionally fetch backdrop keyframe if a local loopback diffusion server (127.0.0.1) is running
            val resolvedBgPath = if (hasLocalImageEndpoint && context != null) {
                tryGenerateLocalLoopbackImage(
                    context = context,
                    endpointUrl = imageBridgeEndpoint,
                    imagePrompt = "${blueprint.imagePrompt}, empty scenery background without characters",
                    width = 512,
                    height = when (aspectRatio) {
                        VideoAspectRatio.LANDSCAPE_16_9 -> 320
                        VideoAspectRatio.PORTRAIT_9_16 -> 768
                        VideoAspectRatio.SQUARE_1_1 -> 512
                    },
                    projectId = projectId,
                    sceneNumber = blueprint.sceneNumber
                )
            } else {
                null
            }

            val updatedBlueprint = blueprint.copy(actors = actorsWithSprites)
            onSceneImageProgress(idx + 1, total)

            val primaryActor = actorsWithSprites.firstOrNull()
            VideoScene(
                id = "${projectId}_sc_${updatedBlueprint.sceneNumber}",
                sceneNumber = updatedBlueprint.sceneNumber,
                title = updatedBlueprint.title,
                visualPrompt = updatedBlueprint.imagePrompt,
                narrationSubtitle = updatedBlueprint.narrationSubtitle,
                durationSeconds = updatedBlueprint.durationSeconds,
                cameraMotion = updatedBlueprint.cameraMotion,
                transition = updatedBlueprint.transition,
                moodColorHex = updatedBlueprint.environment.skyTopHex,
                accentColorHex = updatedBlueprint.environment.accentHex,
                visualMotif = updatedBlueprint.visualMotif,
                characterType = primaryActor?.type ?: "HUMAN",
                characterAction = primaryActor?.action ?: "WALK",
                sceneBlueprintJson = updatedBlueprint.toJson().toString(),
                imagePath = resolvedBgPath
            )
        }

        val firstEnv = scenes.firstOrNull()?.environment
        val primaryHex = firstEnv?.skyTopHex ?: "#0F172A"
        val secondaryHex = firstEnv?.skyBottomHex ?: "#1E1B4B"
        val accentHex = firstEnv?.accentHex ?: "#06B6D4"

        val spriteSourceTag = when {
            hasLocalImageEndpoint -> activeImageModel?.displayName ?: "Local Image Bridge"
            activeImageModel != null -> "On-Device Sprite (${activeImageModel.displayName})"
            else -> "On-Device Local Sprite AI"
        }

        val engineNote = buildString {
            append("${specs.widthPx}x${specs.heightPx} • ${specs.fps}fps • $spriteSourceTag")
            if (activeVideoModel != null) {
                append(" -> ${activeVideoModel.displayName}")
            }
        }

        VideoProject(
            id = projectId,
            prompt = plan.originalPrompt.ifBlank { plan.projectTitle },
            title = plan.projectTitle,
            synopsis = "${plan.synopsis} [$engineNote]",
            style = plan.style,
            aspectRatio = aspectRatio,
            generationMode = VideoGenerationMode.ON_DEVICE_OFFLINE,
            musicMood = plan.musicMood,
            primaryColorHex = primaryHex,
            secondaryColorHex = secondaryHex,
            accentColorHex = accentHex,
            scenes = videoScenes,
            renderWidthPx = specs.widthPx,
            renderHeightPx = specs.heightPx,
            targetFps = specs.fps,
            targetBitrate = specs.bitrate,
            detailDensity = specs.detailDensity
        )
    }

    /**
     * Explicitly asks the Local Image Model for this character's transparent cutout PNG:
     * 1. Local Loopback Diffusion Server (127.0.0.1:7860) if configured & reachable
     * 2. On-Device Multi-Layer Character Sprite Synthesizer (always works 100% offline!)
     */
    private fun resolveCharacterSprite(
        context: Context?,
        actor: ActorDirective,
        localImageEndpoint: String?
    ): String? {
        val prompt = actor.characterImagePrompt.ifBlank {
            "Full-body isolated sprite of ${actor.type.lowercase()} (${actor.primaryColorHex}) on pure white background"
        }

        // 1. Try Local Loopback Diffusion Bridge (127.0.0.1:7860)
        if (!localImageEndpoint.isNullOrBlank() && context != null) {
            val rawBmp = fetchLocalLoopbackBitmap(localImageEndpoint, prompt, 512, 512)
            if (rawBmp != null) {
                val cutout = SpriteCutoutProcessor.extractTransparentCharacterSprite(rawBmp)
                if (cutout !== rawBmp && !rawBmp.isRecycled) rawBmp.recycle()
                val saved = saveSpritePng(context, actor.type, cutout)
                if (!cutout.isRecycled) cutout.recycle()
                if (saved != null) return saved
            }
        }

        // 2. On-Device Character Sprite Synthesizer (transparent PNG asset written locally)
        val (localBmp, savedPath) = SpriteCutoutProcessor.createLocalCharacterSpriteFile(context, actor)
        if (!localBmp.isRecycled) localBmp.recycle()
        return savedPath
    }

    private fun saveSpritePng(context: Context, actorType: String, bitmap: Bitmap): String? {
        return try {
            val dir = File(context.filesDir, "sprites").apply { mkdirs() }
            val outFile = File(dir, "ai_sprite_${actorType.lowercase()}_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            outFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchLocalLoopbackBitmap(
        endpointUrl: String,
        imagePrompt: String,
        width: Int,
        height: Int
    ): Bitmap? {
        if (!LocalModelInspector.isLocalLoopbackUrl(endpointUrl)) return null
        val targetUrl = endpointUrl.trim().trimEnd('/') + "/sdapi/v1/txt2img"
        var conn: HttpURLConnection? = null
        return try {
            val payload = JSONObject().apply {
                put("prompt", imagePrompt)
                put("steps", 12)
                put("width", width)
                put("height", height)
            }
            conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 900
                readTimeout = 15000
                setRequestProperty("Content-Type", "application/json")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }
            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val b64 = JSONObject(body).optJSONArray("images")?.optString(0, "")?.substringAfter(",") ?: return null
            if (b64.isBlank()) return null

            val rawBytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun tryGenerateLocalLoopbackImage(
        context: Context,
        endpointUrl: String,
        imagePrompt: String,
        width: Int,
        height: Int,
        projectId: String,
        sceneNumber: Int
    ): String? {
        val bmp = fetchLocalLoopbackBitmap(endpointUrl, imagePrompt, width, height) ?: return null
        return try {
            val framesDir = File(context.filesDir, "frames").apply { mkdirs() }
            val outFile = File(framesDir, "sd_${projectId}_${sceneNumber}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            outFile.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            if (!bmp.isRecycled) bmp.recycle()
        }
    }
}
