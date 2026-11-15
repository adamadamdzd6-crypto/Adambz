package com.example.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.model.ActorDirective
import com.example.model.CameraMotion
import com.example.model.SceneBlueprint
import com.example.model.SceneEnvSpec
import com.example.model.SceneTransition
import com.example.model.VideoProject
import com.example.model.VideoScene
import com.example.model.VisualMotif
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object CinemaFrameRenderer {

    data class PlaybackCursor(
        val sceneIndex: Int,
        val scene: VideoScene,
        val localProgress: Float,
        val nextScene: VideoScene?,
        val transitionProgress: Float
    )

    fun resolveCursor(project: VideoProject, timeSeconds: Float): PlaybackCursor {
        val scenes = project.scenes
        if (scenes.isEmpty()) {
            val fallback = VideoScene(
                id = "empty",
                sceneNumber = 1,
                title = project.title,
                visualPrompt = project.prompt,
                narrationSubtitle = project.synopsis
            )
            return PlaybackCursor(0, fallback, 0f, null, 0f)
        }

        var accumulated = 0f
        for (i in scenes.indices) {
            val sc = scenes[i]
            val dur = sc.durationSeconds.coerceAtLeast(1f)
            if (timeSeconds <= accumulated + dur || i == scenes.lastIndex) {
                val localT = (timeSeconds - accumulated).coerceIn(0f, dur)
                val progress = (localT / dur).coerceIn(0f, 1f)
                val transitionWindow = 0.55f.coerceAtMost(dur * 0.35f)
                val remaining = dur - localT
                val nextSc = scenes.getOrNull(i + 1)
                val transProgress = if (nextSc != null && remaining < transitionWindow) {
                    (1f - remaining / transitionWindow).coerceIn(0f, 1f)
                } else {
                    0f
                }
                return PlaybackCursor(i, sc, progress, nextSc, transProgress)
            }
            accumulated += dur
        }
        return PlaybackCursor(0, scenes.first(), 0f, scenes.getOrNull(1), 0f)
    }

    fun extractAllSpritePaths(project: VideoProject): List<String> {
        return project.scenes.flatMap { sc ->
            parseBlueprintOrNull(sc)?.actors.orEmpty().mapNotNull { it.spritePath?.takeIf { p -> p.isNotBlank() } }
        }.distinct()
    }

    fun drawFrameToCanvas(
        canvas: Canvas,
        width: Int,
        height: Int,
        project: VideoProject,
        timeSeconds: Float,
        sceneBitmaps: Map<String, Bitmap>,
        spriteBitmaps: Map<String, Bitmap> = emptyMap()
    ) {
        if (width <= 0 || height <= 0) return
        val cursor = resolveCursor(project, timeSeconds)
        val scene = cursor.scene
        val bmp = sceneBitmaps[scene.id]

        canvas.drawColor(Color.BLACK)

        drawSingleSceneLayer(
            canvas = canvas,
            width = width,
            height = height,
            scene = scene,
            progress = cursor.localProgress,
            globalTime = timeSeconds,
            bitmap = bmp,
            spriteBitmaps = spriteBitmaps,
            alpha = 255,
            detailDensity = project.detailDensity
        )

        val nextScene = cursor.nextScene
        val tp = cursor.transitionProgress
        if (nextScene != null && tp > 0.001f) {
            val nextBmp = sceneBitmaps[nextScene.id]
            when (scene.transition) {
                SceneTransition.CROSSFADE -> {
                    val saveCount = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (tp * 255).toInt())
                    drawSingleSceneLayer(canvas, width, height, nextScene, 0f, timeSeconds, nextBmp, spriteBitmaps, 255, project.detailDensity)
                    canvas.restoreToCount(saveCount)
                }
                SceneTransition.SLIDE_LEFT -> {
                    canvas.save()
                    val offset = width * (1f - tp)
                    canvas.translate(offset, 0f)
                    drawSingleSceneLayer(canvas, width, height, nextScene, 0f, timeSeconds, nextBmp, spriteBitmaps, 255, project.detailDensity)
                    canvas.restore()
                }
                SceneTransition.ZOOM_BURST -> {
                    val saveCount = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (tp * 255).toInt())
                    canvas.save()
                    val scale = 1.25f - 0.25f * tp
                    canvas.scale(scale, scale, width / 2f, height / 2f)
                    drawSingleSceneLayer(canvas, width, height, nextScene, 0f, timeSeconds, nextBmp, spriteBitmaps, 255, project.detailDensity)
                    canvas.restore()
                    canvas.restoreToCount(saveCount)
                }
                SceneTransition.FADE_BLACK -> {
                    val fadeAlpha = if (tp < 0.5f) (tp * 2f * 255).toInt() else ((1f - tp) * 2f * 255).toInt()
                    if (tp >= 0.5f) {
                        drawSingleSceneLayer(canvas, width, height, nextScene, 0f, timeSeconds, nextBmp, spriteBitmaps, 255, project.detailDensity)
                    }
                    val blackPaint = Paint().apply { color = Color.argb(fadeAlpha.coerceIn(0, 255), 0, 0, 0) }
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blackPaint)
                }
            }
        }

        // Subtle cinematic vignette
        val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width / 2f,
                height / 2f,
                max(width, height) * 0.72f,
                intArrayOf(Color.TRANSPARENT, Color.argb(135, 0, 0, 0)),
                floatArrayOf(0.58f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignettePaint)

        drawSubtitleAndHud(
            canvas = canvas,
            width = width,
            height = height,
            cursor = cursor
        )
    }

    private fun drawSingleSceneLayer(
        canvas: Canvas,
        width: Int,
        height: Int,
        scene: VideoScene,
        progress: Float,
        globalTime: Float,
        bitmap: Bitmap?,
        spriteBitmaps: Map<String, Bitmap>,
        alpha: Int,
        detailDensity: Float
    ) {
        if (bitmap != null && !bitmap.isRecycled) {
            drawBitmapWithKenBurns(canvas, width, height, bitmap, scene.cameraMotion, progress, alpha)
            val blueprint = parseBlueprintOrNull(scene)
            if (blueprint != null && blueprint.actors.isNotEmpty()) {
                val accent = parseColorInt(blueprint.environment.accentHex, Color.CYAN)
                val hasPit = blueprint.environment.props.contains("PIT_HOLE") ||
                    scene.characterAction.contains("FALL", ignoreCase = true)
                blueprint.actors.forEach { actor ->
                    drawActorDirective(canvas, width, height, actor, progress, globalTime, accent, hasPit, spriteBitmaps)
                }
            }
        } else {
            drawProceduralCinemaLayer(canvas, width, height, scene, progress, globalTime, detailDensity, spriteBitmaps)
        }
    }

    private fun drawBitmapWithKenBurns(
        canvas: Canvas,
        width: Int,
        height: Int,
        bitmap: Bitmap,
        motion: CameraMotion,
        progress: Float,
        alpha: Int
    ) {
        val baseScale = max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val (zoom, panXFrac, panYFrac) = when (motion) {
            CameraMotion.ZOOM_IN -> Triple(1.0f + 0.16f * progress, 0f, 0f)
            CameraMotion.ZOOM_OUT -> Triple(1.16f - 0.16f * progress, 0f, 0f)
            CameraMotion.PAN_LEFT -> Triple(1.14f, 0.05f - 0.10f * progress, 0f)
            CameraMotion.PAN_RIGHT -> Triple(1.14f, -0.05f + 0.10f * progress, 0f)
            CameraMotion.KEN_BURNS_DRIFT -> Triple(1.08f + 0.10f * progress, -0.03f + 0.06f * progress, 0.03f - 0.06f * progress)
        }

        val finalScale = baseScale * zoom
        val drawW = bitmap.width * finalScale
        val drawH = bitmap.height * finalScale
        val left = (width - drawW) / 2f + panXFrac * width
        val top = (height - drawH) / 2f + panYFrac * height

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.alpha = alpha
        }
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawW, top + drawH), paint)
    }

    private fun drawProceduralCinemaLayer(
        canvas: Canvas,
        width: Int,
        height: Int,
        scene: VideoScene,
        progress: Float,
        globalTime: Float,
        detailDensity: Float,
        spriteBitmaps: Map<String, Bitmap>
    ) {
        val blueprint = parseBlueprintOrNull(scene)
        val env = blueprint?.environment ?: SceneEnvSpec(
            groundType = "GRASS",
            weather = "CLEAR",
            skyTopHex = scene.moodColorHex,
            skyBottomHex = "#1E293B",
            groundHex = "#15803D",
            accentHex = scene.accentColorHex,
            props = if (scene.characterAction.equals("FALL", ignoreCase = true) ||
                scene.characterAction.equals("FALL_INTO_PIT", ignoreCase = true)
            ) listOf("PIT_HOLE") else listOf("TREE", "CLOUD")
        )

        val skyTop = parseColorInt(env.skyTopHex, Color.rgb(12, 18, 34))
        val skyBottom = parseColorInt(env.skyBottomHex, Color.rgb(30, 41, 59))
        val groundColor = parseColorInt(env.groundHex, Color.rgb(21, 128, 61))
        val accentColor = parseColorInt(env.accentHex, Color.rgb(6, 182, 212))

        val horizonY = height * 0.60f

        // 1. Sky Background Gradient
        val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, horizonY,
                skyTop, skyBottom, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), skyPaint)

        // Apply gentle cinematic camera motion
        canvas.save()
        val scale = when (scene.cameraMotion) {
            CameraMotion.ZOOM_IN -> 1.0f + 0.08f * progress
            CameraMotion.ZOOM_OUT -> 1.08f - 0.08f * progress
            else -> 1.03f + 0.03f * sin(progress * PI.toFloat())
        }
        val tx = when (scene.cameraMotion) {
            CameraMotion.PAN_LEFT -> -width * 0.03f * progress
            CameraMotion.PAN_RIGHT -> width * 0.03f * progress
            else -> 0f
        }
        canvas.translate(tx, 0f)
        canvas.scale(scale, scale, width / 2f, height / 2f)

        // 2. Sky Props (SUN / MOON / STARS / CLOUDS / MOUNTAINS / CITY BUILDINGS)
        drawEnvironmentSkyAndBackdrop(canvas, width, height, horizonY, env, progress, globalTime, accentColor, detailDensity)

        // 3. Motif Accent Overlay (subtle atmospheric light rays / particles)
        drawMotifAtmosphere(canvas, width, height, scene.visualMotif, globalTime, accentColor, detailDensity)

        // 4. Terrain Ground Layer (GRASS, SAND, ROAD, WATER, SPACE, SNOW, CITY)
        drawTerrainGround(canvas, width, height, horizonY, env, groundColor, accentColor, progress, globalTime)

        // 5. Foreground Scenery Props (TREE, BRIDGE, PIT_HOLE)
        val hasPit = env.props.contains("PIT_HOLE") ||
            scene.characterAction.contains("FALL", ignoreCase = true)
        drawForegroundProps(canvas, width, height, horizonY, env, accentColor, hasPit)

        // 6. Actors & Kinematic Physics
        val actorsToRender = blueprint?.actors?.takeIf { it.isNotEmpty() } ?: listOf(
            ActorDirective(
                id = "legacy_actor",
                type = scene.characterType,
                action = if (scene.characterAction.equals("FALL", ignoreCase = true)) "FALL_INTO_PIT" else scene.characterAction,
                primaryColorHex = scene.accentColorHex,
                startX = if (hasPit) 0.24f else 0.18f,
                endX = if (hasPit) 0.50f else 0.82f,
                startY = 0.67f,
                endY = 0.67f
            )
        )
        actorsToRender.forEach { actor ->
            drawActorDirective(canvas, width, height, actor, progress, globalTime, accentColor, hasPit, spriteBitmaps)
        }

        // 7. Weather Particles (RAIN, SNOW, STARS, SPARKS)
        drawWeatherLayer(canvas, width, height, env.weather, globalTime, accentColor, detailDensity)

        canvas.restore()

        // 8. Top Scene Title Banner (clean, compact so main animated actors stay unobstructed)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (width * 0.040f).coerceIn(22f, 52f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(10f, 0f, 3f, Color.argb(200, 0, 0, 0))
        }
        val maxTextWidth = (width * 0.80f).toInt().coerceAtLeast(100)
        val titleLayout = StaticLayout.Builder.obtain(
            scene.title, 0, scene.title.length, titlePaint, maxTextWidth
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.05f)
            .build()

        canvas.save()
        val titleAlpha = (sin((progress.coerceIn(0.05f, 0.95f)) * PI).toFloat() * 1.25f).coerceIn(0.65f, 1f)
        val titleY = height * 0.08f
        canvas.translate((width - maxTextWidth) / 2f, titleY)
        titlePaint.alpha = (titleAlpha * 255).toInt()
        titleLayout.draw(canvas)
        canvas.restore()
    }

    private fun drawEnvironmentSkyAndBackdrop(
        canvas: Canvas,
        width: Int,
        height: Int,
        horizonY: Float,
        env: SceneEnvSpec,
        progress: Float,
        globalTime: Float,
        accentColor: Int,
        detailDensity: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = width.toFloat()

        // Sun or Moon
        if (env.props.contains("SUN")) {
            val sunX = w * 0.78f
            val sunY = height * 0.22f
            val rad = max(w, height.toFloat()) * 0.08f
            paint.shader = RadialGradient(
                sunX, sunY, rad * 2.2f,
                intArrayOf(Color.rgb(255, 235, 130), Color.argb(110, 251, 146, 60), Color.TRANSPARENT),
                floatArrayOf(0.25f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(sunX, sunY, rad * 2.2f, paint)
            paint.shader = null
            paint.color = Color.rgb(254, 240, 138)
            canvas.drawCircle(sunX, sunY, rad * 0.65f, paint)
        } else if (env.props.contains("MOON") || env.groundType == "SPACE") {
            val moonX = w * 0.78f
            val moonY = height * 0.21f
            val rad = max(w, height.toFloat()) * 0.065f
            paint.color = Color.rgb(226, 232, 240)
            canvas.drawCircle(moonX, moonY, rad, paint)
            if (env.groundType == "SPACE") {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                paint.color = Color.argb(140, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                canvas.drawOval(RectF(moonX - rad * 1.9f, moonY - rad * 0.35f, moonX + rad * 1.9f, moonY + rad * 0.35f), paint)
                paint.style = Paint.Style.FILL
            }
        }

        // Rainbow arc in sky if requested
        if (env.props.contains("RAINBOW")) {
            val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = max(6f, height * 0.018f)
            }
            val rainbowColors = intArrayOf(
                Color.argb(135, 239, 68, 68),
                Color.argb(135, 249, 115, 22),
                Color.argb(135, 250, 204, 21),
                Color.argb(135, 34, 197, 94),
                Color.argb(135, 59, 130, 246),
                Color.argb(135, 168, 85, 247)
            )
            rainbowColors.forEachIndexed { idx, col ->
                arcPaint.color = col
                val inset = idx * arcPaint.strokeWidth * 0.9f
                val oval = RectF(w * 0.12f + inset, height * 0.12f + inset, w * 0.88f - inset, horizonY + height * 0.25f)
                canvas.drawArc(oval, 180f, 180f, false, arcPaint)
            }
        }

        // Drifting Clouds
        if (env.props.contains("CLOUD")) {
            paint.color = Color.argb(175, 255, 255, 255)
            val drift = (globalTime * 18f) % (w * 0.4f)
            for (c in 0..2) {
                val cx = ((w * (0.2f + c * 0.34f) + drift) % (w * 1.15f)) - w * 0.08f
                val cy = height * (0.17f + (c % 2) * 0.09f)
                val r = w * 0.055f
                canvas.drawCircle(cx, cy, r, paint)
                canvas.drawCircle(cx + r * 0.8f, cy + r * 0.1f, r * 0.78f, paint)
                canvas.drawCircle(cx - r * 0.8f, cy + r * 0.15f, r * 0.72f, paint)
            }
        }

        // Mountains
        if (env.props.contains("MOUNTAIN") || env.groundType == "SNOW" || env.groundType == "SAND") {
            val mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            mPaint.color = if (env.groundType == "SAND") Color.rgb(180, 105, 45) else Color.rgb(45, 58, 84)
            val path = Path().apply {
                moveTo(0f, horizonY)
                lineTo(w * 0.18f, horizonY - height * 0.20f)
                lineTo(w * 0.38f, horizonY)
                lineTo(w * 0.56f, horizonY - height * 0.25f)
                lineTo(w * 0.78f, horizonY)
                lineTo(w * 0.94f, horizonY - height * 0.16f)
                lineTo(w, horizonY)
                close()
            }
            canvas.drawPath(path, mPaint)

            if (env.groundType == "SNOW") {
                val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(241, 245, 249) }
                val capPath = Path().apply {
                    moveTo(w * 0.56f, horizonY - height * 0.25f)
                    lineTo(w * 0.49f, horizonY - height * 0.16f)
                    lineTo(w * 0.63f, horizonY - height * 0.16f)
                    close()
                }
                canvas.drawPath(capPath, capPaint)
            }
        }

        // City Skyline Buildings
        if (env.props.contains("BUILDING") || env.groundType == "CITY") {
            val bPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val winPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(190, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            }
            val buildingCount = 9
            val bWidth = w / buildingCount
            for (i in 0 until buildingCount) {
                val bh = height * (0.14f + ((i * 37) % 5) * 0.045f)
                val bx = i * bWidth + bWidth * 0.08f
                bPaint.color = Color.rgb(18 + (i % 3) * 8, 24 + (i % 2) * 10, 46)
                canvas.drawRect(bx, horizonY - bh, bx + bWidth * 0.84f, horizonY, bPaint)
                // Lit windows
                var wy = horizonY - bh + 12f
                while (wy < horizonY - 14f) {
                    canvas.drawRect(bx + bWidth * 0.2f, wy, bx + bWidth * 0.36f, wy + 8f, winPaint)
                    canvas.drawRect(bx + bWidth * 0.52f, wy, bx + bWidth * 0.68f, wy + 8f, winPaint)
                    wy += 20f
                }
            }
        }
    }

    private fun drawMotifAtmosphere(
        canvas: Canvas,
        width: Int,
        height: Int,
        motif: VisualMotif,
        globalTime: Float,
        accentColor: Int,
        detailDensity: Float
    ) {
        val density = detailDensity.coerceIn(0.6f, 1.8f)
        if (motif == VisualMotif.RAYS || motif == VisualMotif.BOKEH) {
            val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val cx = width * (0.5f + 0.10f * sin(globalTime * 0.7f))
            val cy = height * 0.38f
            orbPaint.shader = RadialGradient(
                cx, cy, max(width, height) * 0.45f,
                intArrayOf(
                    Color.argb(65, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                    Color.TRANSPARENT
                ),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, max(width, height) * 0.45f, orbPaint)
        } else if (motif == VisualMotif.GRID) {
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(40, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                strokeWidth = 2f
            }
            val span = (6 * density).toInt()
            for (i in 0..span) {
                val y = height * 0.6f + (i.toFloat() / span) * height * 0.4f
                canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            }
        }
    }

    private fun drawTerrainGround(
        canvas: Canvas,
        width: Int,
        height: Int,
        horizonY: Float,
        env: SceneEnvSpec,
        groundColor: Int,
        accentColor: Int,
        progress: Float,
        globalTime: Float
    ) {
        val w = width.toFloat()
        val h = height.toFloat()
        val darkerGround = Color.rgb(
            (Color.red(groundColor) * 0.55f).toInt(),
            (Color.green(groundColor) * 0.55f).toInt(),
            (Color.blue(groundColor) * 0.55f).toInt()
        )
        val gPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, horizonY, 0f, h, groundColor, darkerGround, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, horizonY, w, h, gPaint)

        when (env.groundType) {
            "WATER" -> {
                val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 4f * (w / 720f).coerceAtLeast(0.6f)
                    color = Color.argb(140, 224, 242, 254)
                }
                for (row in 0..4) {
                    val path = Path()
                    val baseY = horizonY + (h - horizonY) * (0.18f + row * 0.18f)
                    for (step in 0..28) {
                        val x = (step / 28f) * w
                        val y = baseY + sin(step * 0.45f + globalTime * 3.5f + row) * 9f
                        if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    canvas.drawPath(path, wavePaint)
                }
            }
            "ROAD", "CITY" -> {
                val asphaltTop = horizonY + (h - horizonY) * 0.10f
                val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(31, 41, 55) }
                canvas.drawRect(0f, asphaltTop, w, h, roadPaint)

                // Moving dashed center lane markings
                val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(250, 204, 21)
                    strokeWidth = 6f * (w / 720f).coerceAtLeast(0.7f)
                }
                val laneY = horizonY + (h - horizonY) * 0.42f
                val dashCycle = w * 0.16f
                val shift = (globalTime * 110f) % dashCycle
                var x = -dashCycle + shift
                while (x < w + dashCycle) {
                    canvas.drawLine(x, laneY, x + dashCycle * 0.55f, laneY, dashPaint)
                    x += dashCycle
                }
            }
            "SAND" -> {
                val dunePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(85, 251, 191, 36)
                }
                val path = Path().apply {
                    moveTo(0f, horizonY + 18f)
                    quadTo(w * 0.25f, horizonY - 16f, w * 0.5f, horizonY + 14f)
                    quadTo(w * 0.78f, horizonY + 42f, w, horizonY + 8f)
                    lineTo(w, horizonY + 70f)
                    lineTo(0f, horizonY + 70f)
                    close()
                }
                canvas.drawPath(path, dunePaint)
            }
            "SPACE" -> {
                val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(70, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                    strokeWidth = 2f
                }
                for (i in 0..5) {
                    val y = horizonY + (i / 5f) * (h - horizonY)
                    canvas.drawLine(0f, y, w, y, gridPaint)
                }
            }
            else -> {
                // Grass horizon ridge & subtle tufts
                val ridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(85, 134, 239, 172)
                    strokeWidth = 3f
                }
                canvas.drawLine(0f, horizonY, w, horizonY, ridgePaint)
            }
        }
    }

    private fun drawForegroundProps(
        canvas: Canvas,
        width: Int,
        height: Int,
        horizonY: Float,
        env: SceneEnvSpec,
        accentColor: Int,
        hasPit: Boolean
    ) {
        val w = width.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Trees on edges
        if (env.props.contains("TREE") && env.groundType != "SPACE" && env.groundType != "WATER") {
            val positions = listOf(0.11f, 0.89f)
            positions.forEach { px ->
                val tx = w * px
                val base = horizonY + 10f
                val trunkH = height * 0.11f
                p.color = Color.rgb(120, 72, 36)
                canvas.drawRect(tx - w * 0.015f, base - trunkH, tx + w * 0.015f, base, p)
                p.color = if (env.groundType == "SNOW") Color.rgb(226, 232, 240) else Color.rgb(22, 163, 74)
                canvas.drawCircle(tx, base - trunkH - w * 0.02f, w * 0.055f, p)
                canvas.drawCircle(tx - w * 0.03f, base - trunkH, w * 0.042f, p)
                canvas.drawCircle(tx + w * 0.03f, base - trunkH, w * 0.042f, p)
            }
        }

        // Bridge if requested
        if (env.props.contains("BRIDGE")) {
            p.color = Color.rgb(146, 64, 14)
            canvas.drawRect(w * 0.15f, horizonY + 6f, w * 0.85f, horizonY + 26f, p)
        }

        // House / Cabin prop
        if (env.props.contains("HOUSE")) {
            val hx = w * 0.82f
            val hy = horizonY + 14f
            val hw = w * 0.14f
            val hh = height * 0.14f
            p.color = Color.rgb(226, 232, 240)
            canvas.drawRect(hx - hw * 0.5f, hy - hh, hx + hw * 0.5f, hy, p)
            p.color = Color.rgb(185, 28, 28)
            val roof = Path().apply {
                moveTo(hx - hw * 0.62f, hy - hh)
                lineTo(hx, hy - hh * 1.65f)
                lineTo(hx + hw * 0.62f, hy - hh)
                close()
            }
            canvas.drawPath(roof, p)
            p.color = Color.rgb(120, 53, 15)
            canvas.drawRect(hx - hw * 0.14f, hy - hh * 0.5f, hx + hw * 0.14f, hy, p)
        }

        // Flowers scattered on grass
        if (env.props.contains("FLOWERS")) {
            val flowerColors = intArrayOf(Color.MAGENTA, Color.YELLOW, Color.rgb(244, 63, 94), Color.WHITE)
            for (f in 0..7) {
                val fx = w * (0.08f + f * 0.12f)
                val fy = horizonY + height * (0.06f + (f % 3) * 0.05f)
                p.color = flowerColors[f % flowerColors.size]
                canvas.drawCircle(fx, fy, w * 0.011f, p)
            }
        }

        // Fire / Campfire prop
        if (env.props.contains("FIRE")) {
            val fx = w * 0.72f
            val fy = horizonY + height * 0.09f
            p.color = Color.rgb(249, 115, 22)
            canvas.drawCircle(fx, fy - 12f, w * 0.028f, p)
            p.color = Color.rgb(250, 204, 21)
            canvas.drawCircle(fx, fy - 16f, w * 0.016f, p)
        }

        // Pit / Hole prop (centered at x = 0.50f, y = ground level 0.69f)
        if (hasPit) {
            val groundY = height * 0.69f
            val holeRect = RectF(w * 0.39f, groundY - height * 0.015f, w * 0.61f, groundY + height * 0.065f)
            p.color = Color.rgb(5, 8, 15)
            canvas.drawOval(holeRect, p)

            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f * (w / 720f).coerceAtLeast(0.7f)
                color = accentColor
            }
            canvas.drawOval(holeRect, rimPaint)
        }
    }

    private fun drawActorDirective(
        canvas: Canvas,
        width: Int,
        height: Int,
        actor: ActorDirective,
        progress: Float,
        globalTime: Float,
        sceneAccentColor: Int,
        hasPit: Boolean,
        spriteBitmaps: Map<String, Bitmap> = emptyMap()
    ) {
        val action = actor.action.uppercase()
        val type = actor.type.uppercase()
        val charPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val mainColor = parseColorInt(actor.primaryColorHex, sceneAccentColor)

        val moveProgress = when (action) {
            "IDLE" -> 0.5f
            "FALL_INTO_PIT", "FALL" -> (progress * 1.15f).coerceIn(0f, 1f)
            else -> progress
        }

        val charX = width * (actor.startX + (actor.endX - actor.startX) * moveProgress)
        val groundY = height * (actor.startY + (actor.endY - actor.startY) * moveProgress)
        val walkSpeed = when (action) {
            "RUN", "CHASE" -> 14f
            "DANCE" -> 12f
            else -> 8f
        }
        val walkCycle = sin(globalTime * walkSpeed)
        val unitScale = (minOf(width, height) / 720f).coerceIn(0.45f, 2.2f) * actor.scale

        var fallSinkOffset = 0f
        var actorAlpha = 255
        var rotationDeg = 0f

        when (action) {
            "FALL_INTO_PIT", "FALL" -> {
                if (hasPit && moveProgress > 0.45f) {
                    val dropT = ((moveProgress - 0.45f) / 0.55f).coerceIn(0f, 1f)
                    fallSinkOffset = height * 0.16f * (dropT * dropT)
                    rotationDeg = dropT * 68f
                    actorAlpha = (255 * (1f - dropT * 0.85f)).toInt().coerceIn(30, 255)
                }
            }
            "JUMP" -> {
                val jumpArc = abs(sin(globalTime * 4.5f)) * height * 0.14f
                fallSinkOffset = -jumpArc
            }
            "DANCE" -> {
                fallSinkOffset = -abs(sin(globalTime * 9f)) * height * 0.05f
                rotationDeg = sin(globalTime * 7f) * 10f
            }
            "HOVER", "FLY" -> {
                fallSinkOffset = sin(globalTime * 4f) * height * 0.035f
            }
            "SWIM" -> {
                fallSinkOffset = sin(globalTime * 5f) * height * 0.015f
            }
        }

        val bounce = if (action in listOf("WALK", "RUN", "CHASE")) abs(walkCycle) * 7f * unitScale else 0f
        val charY = groundY - bounce + fallSinkOffset

        // Ground shadow (shrinks while jumping or falling)
        if (fallSinkOffset <= height * 0.05f) {
            charPaint.color = Color.argb(90, 0, 0, 0)
            val shadowW = 46f * unitScale
            canvas.drawOval(charX - shadowW, groundY + 4f * unitScale, charX + shadowW, groundY + 13f * unitScale, charPaint)
        }

        canvas.save()
        // Clip downward when falling into PIT_HOLE so the character sinks convincingly into the hole mouth
        if (hasPit && (action == "FALL_INTO_PIT" || action == "FALL") && moveProgress > 0.45f) {
            canvas.clipRect(0f, 0f, width.toFloat(), height * 0.69f + height * 0.045f)
        }

        // Squash-and-stretch + gait tilt animation for live feel
        val gaitTilt = if (action in listOf("WALK", "RUN", "CHASE")) walkCycle * 5.5f else 0f
        canvas.rotate(rotationDeg + gaitTilt, charX, charY)

        val movingLeft = actor.endX < actor.startX - 0.02f
        val squashX = (if (movingLeft) -1f else 1f) * (1f + 0.045f * walkCycle)
        val squashY = 1f - 0.045f * walkCycle
        canvas.scale(squashX, squashY, charX, charY)

        // If a real AI Image Model (Gemini / Loopback Diffusion) generated a cutout PNG sprite, animate it!
        // For local procedural sprites (`sprite_*.png`), fall through to our articulated multi-limb rig
        // so legs walk, tails wag, wings flap, and wheels spin naturally.
        val spritePath = actor.spritePath
        val isAiRasterSprite = !spritePath.isNullOrBlank() &&
            (spritePath.contains("gemini_") || spritePath.contains("ai_sprite_"))
        val spriteBmp = if (isAiRasterSprite) spriteBitmaps[spritePath] else null
        if (spriteBmp != null && !spriteBmp.isRecycled) {
            val spriteTargetH = 148f * unitScale
            val aspect = spriteBmp.width.toFloat() / spriteBmp.height.coerceAtLeast(1)
            val spriteTargetW = spriteTargetH * aspect
            val drawRect = RectF(
                charX - spriteTargetW * 0.5f,
                charY - spriteTargetH * 0.72f,
                charX + spriteTargetW * 0.5f,
                charY + spriteTargetH * 0.28f
            )
            charPaint.alpha = actorAlpha
            canvas.drawBitmap(spriteBmp, null, drawRect, charPaint)
            canvas.restore()
            return
        }

        charPaint.alpha = actorAlpha

        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * unitScale
            color = Color.argb(actorAlpha, 255, 255, 255)
        }

        when (type) {
            "DOG", "CAT" -> {
                val bodyW = 68f * unitScale
                val bodyH = 40f * unitScale
                charPaint.style = Paint.Style.FILL
                charPaint.color = mainColor
                charPaint.alpha = actorAlpha
                // Body
                canvas.drawRoundRect(
                    RectF(charX - bodyW * 0.55f, charY - bodyH * 0.7f, charX + bodyW * 0.5f, charY + bodyH * 0.15f),
                    16f * unitScale, 16f * unitScale, charPaint
                )
                // Head
                val headX = charX + bodyW * 0.52f
                val headY = charY - bodyH * 0.65f
                canvas.drawCircle(headX, headY, 22f * unitScale, charPaint)
                // Ears (pointy for cat, floppy for dog)
                if (type == "CAT") {
                    val earPath = Path().apply {
                        moveTo(headX - 14f * unitScale, headY - 12f * unitScale)
                        lineTo(headX - 6f * unitScale, headY - 34f * unitScale)
                        lineTo(headX + 2f * unitScale, headY - 14f * unitScale)
                        close()
                    }
                    canvas.drawPath(earPath, charPaint)
                } else {
                    charPaint.color = darkenColor(mainColor, 0.75f)
                    charPaint.alpha = actorAlpha
                    canvas.drawOval(
                        RectF(headX - 18f * unitScale, headY - 8f * unitScale, headX - 2f * unitScale, headY + 18f * unitScale),
                        charPaint
                    )
                }
                // Eye
                charPaint.color = Color.WHITE
                charPaint.alpha = actorAlpha
                canvas.drawCircle(headX + 8f * unitScale, headY - 4f * unitScale, 4f * unitScale, charPaint)
                charPaint.color = Color.BLACK
                charPaint.alpha = actorAlpha
                canvas.drawCircle(headX + 9f * unitScale, headY - 4f * unitScale, 2f * unitScale, charPaint)

                // Tail wagging
                val tailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 7f * unitScale
                    strokeCap = Paint.Cap.ROUND
                    color = mainColor
                    alpha = actorAlpha
                }
                val wag = sin(globalTime * 14f) * 14f * unitScale
                canvas.drawLine(
                    charX - bodyW * 0.52f, charY - bodyH * 0.5f,
                    charX - bodyW * 0.85f, charY - bodyH * 0.85f + wag,
                    tailPaint
                )
                // Four walking legs
                val legSwing = walkCycle * 15f * unitScale
                canvas.drawLine(charX - bodyW * 0.35f, charY, charX - bodyW * 0.35f + legSwing, charY + 26f * unitScale, tailPaint)
                canvas.drawLine(charX - bodyW * 0.15f, charY, charX - bodyW * 0.15f - legSwing, charY + 26f * unitScale, tailPaint)
                canvas.drawLine(charX + bodyW * 0.2f, charY, charX + bodyW * 0.2f + legSwing, charY + 26f * unitScale, tailPaint)
                canvas.drawLine(charX + bodyW * 0.38f, charY, charX + bodyW * 0.38f - legSwing, charY + 26f * unitScale, tailPaint)
            }

            "BIRD" -> {
                val wingFlap = sin(globalTime * 16f) * 26f * unitScale
                charPaint.style = Paint.Style.FILL
                charPaint.color = mainColor
                charPaint.alpha = actorAlpha
                canvas.drawOval(RectF(charX - 28f * unitScale, charY - 14f * unitScale, charX + 28f * unitScale, charY + 14f * unitScale), charPaint)
                // Beak
                charPaint.color = Color.rgb(245, 158, 11)
                charPaint.alpha = actorAlpha
                val beak = Path().apply {
                    moveTo(charX + 26f * unitScale, charY - 5f * unitScale)
                    lineTo(charX + 42f * unitScale, charY)
                    lineTo(charX + 26f * unitScale, charY + 5f * unitScale)
                    close()
                }
                canvas.drawPath(beak, charPaint)
                // Wings
                val wingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 7f * unitScale
                    strokeCap = Paint.Cap.ROUND
                    color = mainColor
                    alpha = actorAlpha
                }
                canvas.drawLine(charX - 4f * unitScale, charY - 4f * unitScale, charX - 36f * unitScale, charY - wingFlap - 16f * unitScale, wingPaint)
                canvas.drawLine(charX + 4f * unitScale, charY - 4f * unitScale, charX + 22f * unitScale, charY - wingFlap - 18f * unitScale, wingPaint)
            }

            "CAR" -> {
                val cw = 110f * unitScale
                val ch = 40f * unitScale
                charPaint.style = Paint.Style.FILL
                charPaint.color = mainColor
                charPaint.alpha = actorAlpha
                canvas.drawRoundRect(RectF(charX - cw * 0.5f, charY - ch * 0.8f, charX + cw * 0.5f, charY + ch * 0.25f), 14f * unitScale, 14f * unitScale, charPaint)
                // Cabin roof
                canvas.drawRoundRect(RectF(charX - cw * 0.28f, charY - ch * 1.45f, charX + cw * 0.24f, charY - ch * 0.55f), 12f * unitScale, 12f * unitScale, charPaint)
                // Window glass
                charPaint.color = Color.rgb(186, 230, 253)
                charPaint.alpha = actorAlpha
                canvas.drawRoundRect(RectF(charX - cw * 0.22f, charY - ch * 1.32f, charX + cw * 0.18f, charY - ch * 0.78f), 6f * unitScale, 6f * unitScale, charPaint)
                // Wheels
                charPaint.color = Color.rgb(15, 23, 42)
                charPaint.alpha = actorAlpha
                canvas.drawCircle(charX - cw * 0.28f, charY + ch * 0.3f, 15f * unitScale, charPaint)
                canvas.drawCircle(charX + cw * 0.28f, charY + ch * 0.3f, 15f * unitScale, charPaint)
                canvas.drawCircle(charX - cw * 0.28f, charY + ch * 0.3f, 6f * unitScale, outlinePaint)
                canvas.drawCircle(charX + cw * 0.28f, charY + ch * 0.3f, 6f * unitScale, outlinePaint)
            }

            "BOAT" -> {
                val bw = 115f * unitScale
                val hull = Path().apply {
                    moveTo(charX - bw * 0.55f, charY - 8f * unitScale)
                    lineTo(charX + bw * 0.55f, charY - 8f * unitScale)
                    lineTo(charX + bw * 0.38f, charY + 24f * unitScale)
                    lineTo(charX - bw * 0.38f, charY + 24f * unitScale)
                    close()
                }
                charPaint.style = Paint.Style.FILL
                charPaint.color = mainColor
                charPaint.alpha = actorAlpha
                canvas.drawPath(hull, charPaint)
                // Sail
                charPaint.color = Color.WHITE
                charPaint.alpha = actorAlpha
                val sail = Path().apply {
                    moveTo(charX - 6f * unitScale, charY - 12f * unitScale)
                    lineTo(charX - 6f * unitScale, charY - 82f * unitScale)
                    lineTo(charX + 42f * unitScale, charY - 18f * unitScale)
                    close()
                }
                canvas.drawPath(sail, charPaint)
            }

            "PLANE" -> {
                val pw = 95f * unitScale
                charPaint.style = Paint.Style.FILL
                charPaint.color = mainColor
                charPaint.alpha = actorAlpha
                canvas.drawOval(RectF(charX - pw * 0.55f, charY - 16f * unitScale, charX + pw * 0.55f, charY + 16f * unitScale), charPaint)
                val wing = Path().apply {
                    moveTo(charX - 10f * unitScale, charY)
                    lineTo(charX - 30f * unitScale, charY - 46f * unitScale)
                    lineTo(charX + 18f * unitScale, charY)
                    close()
                }
                canvas.drawPath(wing, charPaint)
                // Thruster flame
                charPaint.color = Color.rgb(251, 146, 60)
                charPaint.alpha = actorAlpha
                canvas.drawCircle(charX - pw * 0.62f, charY, (8f + abs(sin(globalTime * 20f)) * 8f) * unitScale, charPaint)
            }

            "ROBOT" -> {
                val rw = 52f * unitScale
                val rh = 72f * unitScale
                charPaint.style = Paint.Style.FILL
                charPaint.color = mainColor
                charPaint.alpha = actorAlpha
                canvas.drawRoundRect(RectF(charX - rw * 0.5f, charY - rh * 0.65f, charX + rw * 0.5f, charY + rh * 0.1f), 10f * unitScale, 10f * unitScale, charPaint)
                // Head & visor
                canvas.drawRoundRect(RectF(charX - rw * 0.4f, charY - rh * 1.15f, charX + rw * 0.4f, charY - rh * 0.7f), 8f * unitScale, 8f * unitScale, charPaint)
                charPaint.color = Color.rgb(34, 211, 238)
                charPaint.alpha = actorAlpha
                canvas.drawRect(charX - rw * 0.28f, charY - rh * 1.0f, charX + rw * 0.28f, charY - rh * 0.85f, charPaint)
                // Antenna
                canvas.drawLine(charX, charY - rh * 1.15f, charX, charY - rh * 1.32f, outlinePaint)
                canvas.drawCircle(charX, charY - rh * 1.35f, 5f * unitScale, charPaint)
                // Legs
                val legSwing = walkCycle * 16f * unitScale
                outlinePaint.strokeWidth = 7f * unitScale
                canvas.drawLine(charX - rw * 0.22f, charY + rh * 0.1f, charX - rw * 0.22f + legSwing, charY + rh * 0.45f, outlinePaint)
                canvas.drawLine(charX + rw * 0.22f, charY + rh * 0.1f, charX + rw * 0.22f - legSwing, charY + rh * 0.45f, outlinePaint)
            }

            "HORSE" -> {
                val hw = 80f * unitScale
                val hh = 42f * unitScale
                charPaint.style = Paint.Style.FILL
                charPaint.color = mainColor
                charPaint.alpha = actorAlpha
                canvas.drawRoundRect(RectF(charX - hw * 0.5f, charY - hh * 0.8f, charX + hw * 0.45f, charY - hh * 0.05f), 14f * unitScale, 14f * unitScale, charPaint)
                // Neck + Head
                val neck = Path().apply {
                    moveTo(charX + hw * 0.25f, charY - hh * 0.6f)
                    lineTo(charX + hw * 0.58f, charY - hh * 1.45f)
                    lineTo(charX + hw * 0.82f, charY - hh * 1.32f)
                    lineTo(charX + hw * 0.45f, charY - hh * 0.35f)
                    close()
                }
                canvas.drawPath(neck, charPaint)
                val legPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 7f * unitScale
                    strokeCap = Paint.Cap.ROUND
                    color = mainColor
                    alpha = actorAlpha
                }
                val stride = walkCycle * 18f * unitScale
                canvas.drawLine(charX - hw * 0.35f, charY - hh * 0.1f, charX - hw * 0.35f + stride, charY + 28f * unitScale, legPaint)
                canvas.drawLine(charX + hw * 0.28f, charY - hh * 0.1f, charX + hw * 0.28f - stride, charY + 28f * unitScale, legPaint)
            }

            "FISH" -> {
                val fw = 64f * unitScale
                val fh = 32f * unitScale
                charPaint.style = Paint.Style.FILL
                charPaint.color = mainColor
                charPaint.alpha = actorAlpha
                canvas.drawOval(RectF(charX - fw * 0.5f, charY - fh * 0.5f, charX + fw * 0.5f, charY + fh * 0.5f), charPaint)
                val tail = Path().apply {
                    moveTo(charX - fw * 0.45f, charY)
                    lineTo(charX - fw * 0.82f, charY - fh * 0.55f)
                    lineTo(charX - fw * 0.82f, charY + fh * 0.55f)
                    close()
                }
                canvas.drawPath(tail, charPaint)
                charPaint.color = Color.WHITE
                charPaint.alpha = actorAlpha
                canvas.drawCircle(charX + fw * 0.25f, charY - 4f * unitScale, 4f * unitScale, charPaint)
            }

            "BUTTERFLY" -> {
                val flap = abs(sin(globalTime * 18f)) * 22f * unitScale
                charPaint.style = Paint.Style.FILL
                charPaint.color = mainColor
                charPaint.alpha = actorAlpha
                canvas.drawOval(RectF(charX - 28f * unitScale, charY - flap - 14f * unitScale, charX, charY + 6f * unitScale), charPaint)
                canvas.drawOval(RectF(charX, charY - flap - 14f * unitScale, charX + 28f * unitScale, charY + 6f * unitScale), charPaint)
                charPaint.color = Color.rgb(30, 41, 59)
                charPaint.alpha = actorAlpha
                canvas.drawLine(charX, charY - 12f * unitScale, charX, charY + 14f * unitScale, outlinePaint)
            }

            "BIKE" -> {
                val r = 20f * unitScale
                val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 5f * unitScale
                    color = mainColor
                    alpha = actorAlpha
                }
                canvas.drawCircle(charX - 34f * unitScale, charY + 8f * unitScale, r, framePaint)
                canvas.drawCircle(charX + 34f * unitScale, charY + 8f * unitScale, r, framePaint)
                canvas.drawLine(charX - 34f * unitScale, charY + 8f * unitScale, charX, charY - 14f * unitScale, framePaint)
                canvas.drawLine(charX, charY - 14f * unitScale, charX + 34f * unitScale, charY + 8f * unitScale, framePaint)
            }

            "BALL" -> {
                val r = 28f * unitScale
                charPaint.style = Paint.Style.FILL
                charPaint.color = mainColor
                charPaint.alpha = actorAlpha
                canvas.drawCircle(charX, charY - r, r, charPaint)
                canvas.drawCircle(charX, charY - r, r, outlinePaint)
            }

            "MONSTER" -> {
                val mw = 64f * unitScale
                val mh = 76f * unitScale
                charPaint.style = Paint.Style.FILL
                charPaint.color = mainColor
                charPaint.alpha = actorAlpha
                canvas.drawOval(RectF(charX - mw * 0.5f, charY - mh, charX + mw * 0.5f, charY + 12f * unitScale), charPaint)
                charPaint.color = Color.YELLOW
                charPaint.alpha = actorAlpha
                canvas.drawCircle(charX - 12f * unitScale, charY - mh * 0.65f, 6f * unitScale, charPaint)
                canvas.drawCircle(charX + 12f * unitScale, charY - mh * 0.65f, 6f * unitScale, charPaint)
                val legSwing = walkCycle * 16f * unitScale
                outlinePaint.strokeWidth = 8f * unitScale
                canvas.drawLine(charX - 16f * unitScale, charY + 8f * unitScale, charX - 16f * unitScale + legSwing, charY + 34f * unitScale, outlinePaint)
                canvas.drawLine(charX + 16f * unitScale, charY + 8f * unitScale, charX + 16f * unitScale - legSwing, charY + 34f * unitScale, outlinePaint)
            }

            else -> {
                // Articulated HUMAN Hero
                val bodyW = 40f * unitScale
                val bodyH = 82f * unitScale
                val limbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 9f * unitScale
                    strokeCap = Paint.Cap.ROUND
                    color = Color.argb(actorAlpha, 241, 245, 249)
                }
                val legOffset = walkCycle * 20f * unitScale
                canvas.drawLine(charX - bodyW * 0.2f, charY - bodyH * 0.35f, charX - bodyW * 0.25f + legOffset, charY + 26f * unitScale, limbPaint)
                canvas.drawLine(charX + bodyW * 0.2f, charY - bodyH * 0.35f, charX + bodyW * 0.25f - legOffset, charY + 26f * unitScale, limbPaint)

                // Torso
                charPaint.style = Paint.Style.FILL
                charPaint.color = mainColor
                charPaint.alpha = actorAlpha
                canvas.drawRoundRect(
                    RectF(charX - bodyW * 0.45f, charY - bodyH * 0.9f, charX + bodyW * 0.45f, charY - bodyH * 0.3f),
                    12f * unitScale, 12f * unitScale, charPaint
                )

                // Arms swinging or waving in DANCE
                val armSwing = if (action == "DANCE") -abs(sin(globalTime * 10f)) * 32f * unitScale else walkCycle * 18f * unitScale
                canvas.drawLine(charX - bodyW * 0.45f, charY - bodyH * 0.8f, charX - bodyW * 0.85f, charY - bodyH * 0.5f + armSwing, limbPaint)
                canvas.drawLine(charX + bodyW * 0.45f, charY - bodyH * 0.8f, charX + bodyW * 0.85f, charY - bodyH * 0.5f - armSwing, limbPaint)

                // Head
                charPaint.color = Color.rgb(255, 224, 189)
                charPaint.alpha = actorAlpha
                canvas.drawCircle(charX, charY - bodyH * 1.15f, 19f * unitScale, charPaint)
            }
        }

        canvas.restore()
    }

    private fun drawWeatherLayer(
        canvas: Canvas,
        width: Int,
        height: Int,
        weather: String,
        globalTime: Float,
        accentColor: Int,
        detailDensity: Float
    ) {
        val density = detailDensity.coerceIn(0.6f, 1.8f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (weather) {
            "RAIN" -> {
                p.color = Color.argb(145, 186, 230, 253)
                p.strokeWidth = 2.5f
                val count = (45 * density).toInt()
                for (i in 0 until count) {
                    val rx = ((i * 73 + globalTime * 90f) % width)
                    val ry = ((i * 137 + globalTime * 680f) % height)
                    canvas.drawLine(rx, ry, rx - 8f, ry + 26f, p)
                }
            }
            "SNOW" -> {
                p.color = Color.argb(215, 255, 255, 255)
                val count = (36 * density).toInt()
                for (i in 0 until count) {
                    val sx = ((i * 89 + sin(globalTime * 2f + i) * 20f) % width + width) % width
                    val sy = ((i * 113 + globalTime * 120f) % height)
                    canvas.drawCircle(sx, sy, 3.5f + (i % 3) * 1.5f, p)
                }
            }
            "STARS" -> {
                val count = (40 * density).toInt()
                for (i in 0 until count) {
                    val twinkle = (110 + (sin(globalTime * 3f + i) * 100)).toInt().coerceIn(50, 240)
                    p.color = Color.argb(twinkle, 255, 255, 255)
                    val sx = ((i * 157) % width).toFloat()
                    val sy = ((i * 97) % (height * 0.58f))
                    canvas.drawCircle(sx, sy, 2.5f, p)
                }
            }
            "SPARKS" -> {
                val count = (28 * density).toInt()
                for (i in 0 until count) {
                    p.color = Color.argb(175, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                    val sx = ((i * 103 + sin(globalTime * 1.8f + i) * 35f) % width + width) % width
                    val sy = height - ((i * 89 + globalTime * 160f) % height)
                    canvas.drawCircle(sx, sy, 4f + (i % 3) * 2f, p)
                }
            }
        }
    }

    private fun drawSubtitleAndHud(
        canvas: Canvas,
        width: Int,
        height: Int,
        cursor: PlaybackCursor
    ) {
        val subtitle = cursor.scene.narrationSubtitle.trim()
        if (subtitle.isBlank()) return

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (width * 0.032f).coerceIn(18f, 42f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
        }

        val maxW = (width * 0.84f).toInt().coerceAtLeast(100)
        val layout = StaticLayout.Builder.obtain(subtitle, 0, subtitle.length, textPaint, maxW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.15f)
            .build()

        val padH = width * 0.03f
        val padV = height * 0.016f
        val boxW = maxW + padH * 2
        val boxH = layout.height + padV * 2
        val boxLeft = (width - boxW) / 2f
        val boxTop = height * 0.89f - boxH

        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 8, 12, 24)
        }
        canvas.drawRoundRect(
            RectF(boxLeft, boxTop, boxLeft + boxW, boxTop + boxH),
            18f,
            18f,
            pillPaint
        )

        canvas.save()
        canvas.translate((width - maxW) / 2f, boxTop + padV)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun parseBlueprintOrNull(scene: VideoScene): SceneBlueprint? {
        val raw = scene.sceneBlueprintJson.trim()
        if (raw.isEmpty()) return null
        return try {
            SceneBlueprint.fromJson(JSONObject(raw), scene.sceneNumber - 1)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseColorInt(hex: String, fallback: Int): Int {
        return try {
            val clean = hex.trim().let { if (it.startsWith("#")) it else "#$it" }
            Color.parseColor(clean)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        return Color.rgb(
            (Color.red(color) * factor).toInt().coerceIn(0, 255),
            (Color.green(color) * factor).toInt().coerceIn(0, 255),
            (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        )
    }
}
