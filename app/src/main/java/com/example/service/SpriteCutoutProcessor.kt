package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.example.model.ActorDirective
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * Converts flat-background AI character images into transparent cutout PNG sprites
 * and synthesizes crisp off-device/on-device transparent character sprites ready to be
 * animated across the video timeline by CinemaFrameRenderer.
 */
object SpriteCutoutProcessor {

    /**
     * Samples the 4 corners of an AI-generated character image to strip uniform backdrop pixels
     * into smooth alpha transparency, and tight-crops around the character silhouette.
     */
    fun extractTransparentCharacterSprite(raw: Bitmap, maxDim: Int = 360): Bitmap {
        val scaled = scaleBitmapDown(raw, maxDim)
        val w = scaled.width
        val h = scaled.height
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        val corners = intArrayOf(
            pixels[0],
            pixels[w - 1],
            pixels[(h - 1) * w],
            pixels[w * h - 1]
        )
        var bgR = 0
        var bgG = 0
        var bgB = 0
        for (c in corners) {
            bgR += Color.red(c)
            bgG += Color.green(c)
            bgB += Color.blue(c)
        }
        bgR /= 4
        bgG /= 4
        bgB /= 4

        // Check if corner chroma is bright green (#00FF00 style) or uniform white/studio backdrop
        val isGreenChroma = bgG > bgR + 40 && bgG > bgB + 40

        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val px = pixels[idx]
                val a = Color.alpha(px)
                if (a < 15) {
                    pixels[idx] = Color.TRANSPARENT
                    continue
                }
                val r = Color.red(px)
                val g = Color.green(px)
                val b = Color.blue(px)

                val diff = abs(r - bgR) + abs(g - bgG) + abs(b - bgB)
                val isBackdrop = if (isGreenChroma) {
                    (g > r + 28 && g > b + 28) || diff < 85
                } else {
                    diff < 72
                }

                if (isBackdrop) {
                    pixels[idx] = Color.TRANSPARENT
                } else {
                    val softAlpha = if (!isGreenChroma && diff in 72..105) {
                        ((diff - 72) / 33f * 255f).toInt().coerceIn(40, 255)
                    } else {
                        a
                    }
                    pixels[idx] = Color.argb(softAlpha, r, g, b)
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (scaled !== raw && !scaled.isRecycled) scaled.recycle()

        if (maxX > minX + 10 && maxY > minY + 10) {
            val cropW = (maxX - minX + 1).coerceAtLeast(1)
            val cropH = (maxY - minY + 1).coerceAtLeast(1)
            val cropped = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888).apply {
                setHasAlpha(true)
            }
            cropped.setPixels(pixels, minY * w + minX, w, 0, 0, cropW, cropH)
            return cropped
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setHasAlpha(true)
        }
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Synthesizes a richly shaded 256x256 transparent PNG Character Sprite cleanly on-device
     * when working without a remote/loopback diffusion server, writing the actual PNG asset
     * to `files/sprites/` so the motion animator animates the generated image sprite directly.
     */
    fun createLocalCharacterSpriteFile(
        context: Context?,
        actor: ActorDirective
    ): Pair<Bitmap, String?> {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val mainColor = parseColorInt(actor.primaryColorHex, Color.rgb(59, 130, 246))
        val darkColor = darken(mainColor, 0.65f)
        val lightColor = lighten(mainColor, 1.30f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = Color.argb(215, 15, 23, 42)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val cx = size * 0.5f
        val cy = size * 0.56f

        when (actor.type.uppercase()) {
            "DOG", "CAT" -> {
                val isCat = actor.type.equals("CAT", ignoreCase = true)
                paint.shader = LinearGradient(cx, cy - 60f, cx, cy + 50f, lightColor, darkColor, Shader.TileMode.CLAMP)
                // Body
                val body = RectF(cx - 72f, cy - 28f, cx + 44f, cy + 36f)
                canvas.drawRoundRect(body, 28f, 28f, paint)
                canvas.drawRoundRect(body, 28f, 28f, outline)
                // Tail
                val tail = Path().apply {
                    moveTo(cx - 66f, cy - 10f)
                    quadTo(cx - 105f, cy - 42f, cx - 88f, cy - 68f)
                }
                val tailPaint = Paint(outline).apply {
                    strokeWidth = 14f
                    color = mainColor
                }
                canvas.drawPath(tail, tailPaint)
                // Legs
                canvas.drawRoundRect(RectF(cx - 56f, cy + 20f, cx - 36f, cy + 78f), 8f, 8f, paint)
                canvas.drawRoundRect(RectF(cx + 12f, cy + 20f, cx + 32f, cy + 78f), 8f, 8f, paint)
                // Head
                val hx = cx + 52f
                val hy = cy - 36f
                canvas.drawCircle(hx, hy, 36f, paint)
                canvas.drawCircle(hx, hy, 36f, outline)
                paint.shader = null
                if (isCat) {
                    paint.color = darkColor
                    val ear1 = Path().apply {
                        moveTo(hx - 20f, hy - 22f)
                        lineTo(hx - 10f, hy - 56f)
                        lineTo(hx + 6f, hy - 26f)
                        close()
                    }
                    canvas.drawPath(ear1, paint)
                    canvas.drawPath(ear1, outline)
                } else {
                    paint.color = darkColor
                    canvas.drawOval(RectF(hx - 24f, hy - 8f, hx + 2f, hy + 30f), paint)
                }
                // Eyes + Nose
                paint.color = Color.WHITE
                canvas.drawCircle(hx + 14f, hy - 8f, 7f, paint)
                paint.color = Color.BLACK
                canvas.drawCircle(hx + 16f, hy - 8f, 3.5f, paint)
                canvas.drawCircle(hx + 34f, hy + 2f, 5f, paint)
            }

            "HORSE" -> {
                paint.shader = LinearGradient(cx, cy - 80f, cx, cy + 70f, lightColor, darkColor, Shader.TileMode.CLAMP)
                canvas.drawRoundRect(RectF(cx - 75f, cy - 32f, cx + 45f, cy + 32f), 24f, 24f, paint)
                val neck = Path().apply {
                    moveTo(cx + 15f, cy - 12f)
                    lineTo(cx + 55f, cy - 92f)
                    lineTo(cx + 95f, cy - 72f)
                    lineTo(cx + 78f, cy - 52f)
                    lineTo(cx + 46f, cy + 12f)
                    close()
                }
                canvas.drawPath(neck, paint)
                canvas.drawPath(neck, outline)
                canvas.drawRoundRect(RectF(cx - 58f, cy + 18f, cx - 38f, cy + 86f), 8f, 8f, paint)
                canvas.drawRoundRect(RectF(cx + 18f, cy + 18f, cx + 38f, cy + 86f), 8f, 8f, paint)
                paint.shader = null
                paint.color = Color.BLACK
                canvas.drawCircle(cx + 68f, cy - 74f, 4f, paint)
            }

            "ROBOT" -> {
                paint.shader = LinearGradient(cx, cy - 90f, cx, cy + 70f, lightColor, darkColor, Shader.TileMode.CLAMP)
                val torso = RectF(cx - 42f, cy - 36f, cx + 42f, cy + 40f)
                canvas.drawRoundRect(torso, 16f, 16f, paint)
                canvas.drawRoundRect(torso, 16f, 16f, outline)
                val head = RectF(cx - 34f, cy - 98f, cx + 34f, cy - 42f)
                canvas.drawRoundRect(head, 12f, 12f, paint)
                canvas.drawRoundRect(head, 12f, 12f, outline)
                paint.shader = null
                paint.color = Color.rgb(34, 211, 238)
                canvas.drawRoundRect(RectF(cx - 22f, cy - 80f, cx + 22f, cy - 62f), 6f, 6f, paint)
                // Legs + Arms
                paint.color = darkColor
                canvas.drawRoundRect(RectF(cx - 32f, cy + 42f, cx - 10f, cy + 92f), 8f, 8f, paint)
                canvas.drawRoundRect(RectF(cx + 10f, cy + 42f, cx + 32f, cy + 92f), 8f, 8f, paint)
                canvas.drawRoundRect(RectF(cx - 64f, cy - 28f, cx - 44f, cy + 28f), 8f, 8f, paint)
                canvas.drawRoundRect(RectF(cx + 44f, cy - 28f, cx + 64f, cy + 28f), 8f, 8f, paint)
            }

            "CAR" -> {
                paint.shader = LinearGradient(cx, cy - 50f, cx, cy + 45f, lightColor, darkColor, Shader.TileMode.CLAMP)
                canvas.drawRoundRect(RectF(cx - 95f, cy - 16f, cx + 95f, cy + 36f), 20f, 20f, paint)
                canvas.drawRoundRect(RectF(cx - 56f, cy - 62f, cx + 48f, cy - 6f), 18f, 18f, paint)
                paint.shader = null
                paint.color = Color.rgb(186, 230, 253)
                canvas.drawRoundRect(RectF(cx - 42f, cy - 50f, cx + 34f, cy - 14f), 10f, 10f, paint)
                paint.color = Color.rgb(15, 23, 42)
                canvas.drawCircle(cx - 54f, cy + 38f, 24f, paint)
                canvas.drawCircle(cx + 54f, cy + 38f, 24f, paint)
                paint.color = Color.rgb(203, 213, 225)
                canvas.drawCircle(cx - 54f, cy + 38f, 10f, paint)
                canvas.drawCircle(cx + 54f, cy + 38f, 10f, paint)
            }

            "BOAT" -> {
                paint.shader = LinearGradient(cx, cy - 85f, cx, cy + 45f, lightColor, darkColor, Shader.TileMode.CLAMP)
                val hull = Path().apply {
                    moveTo(cx - 92f, cy)
                    lineTo(cx + 92f, cy)
                    lineTo(cx + 66f, cy + 44f)
                    lineTo(cx - 66f, cy + 44f)
                    close()
                }
                canvas.drawPath(hull, paint)
                canvas.drawPath(hull, outline)
                paint.shader = null
                paint.color = Color.WHITE
                val sail = Path().apply {
                    moveTo(cx - 6f, cy - 6f)
                    lineTo(cx - 6f, cy - 102f)
                    lineTo(cx + 66f, cy - 12f)
                    close()
                }
                canvas.drawPath(sail, paint)
                canvas.drawPath(sail, outline)
            }

            "BIRD", "BUTTERFLY" -> {
                paint.shader = LinearGradient(cx, cy - 60f, cx, cy + 40f, lightColor, darkColor, Shader.TileMode.CLAMP)
                canvas.drawOval(RectF(cx - 54f, cy - 22f, cx + 54f, cy + 22f), paint)
                val wing = Path().apply {
                    moveTo(cx - 10f, cy - 8f)
                    lineTo(cx - 62f, cy - 72f)
                    lineTo(cx + 26f, cy - 14f)
                    close()
                }
                canvas.drawPath(wing, paint)
                canvas.drawPath(wing, outline)
                paint.shader = null
                paint.color = Color.rgb(245, 158, 11)
                val beak = Path().apply {
                    moveTo(cx + 52f, cy - 8f)
                    lineTo(cx + 76f, cy)
                    lineTo(cx + 52f, cy + 8f)
                    close()
                }
                canvas.drawPath(beak, paint)
            }

            "FISH" -> {
                paint.shader = LinearGradient(cx, cy - 45f, cx, cy + 45f, lightColor, darkColor, Shader.TileMode.CLAMP)
                canvas.drawOval(RectF(cx - 62f, cy - 36f, cx + 68f, cy + 36f), paint)
                val tail = Path().apply {
                    moveTo(cx - 56f, cy)
                    lineTo(cx - 98f, cy - 40f)
                    lineTo(cx - 98f, cy + 40f)
                    close()
                }
                canvas.drawPath(tail, paint)
                paint.shader = null
                paint.color = Color.WHITE
                canvas.drawCircle(cx + 38f, cy - 8f, 7f, paint)
                paint.color = Color.BLACK
                canvas.drawCircle(cx + 40f, cy - 8f, 3.5f, paint)
            }

            "PLANE" -> {
                paint.shader = LinearGradient(cx, cy - 70f, cx, cy + 40f, lightColor, darkColor, Shader.TileMode.CLAMP)
                canvas.drawOval(RectF(cx - 92f, cy - 24f, cx + 92f, cy + 24f), paint)
                val wing = Path().apply {
                    moveTo(cx - 12f, cy)
                    lineTo(cx - 48f, cy - 78f)
                    lineTo(cx + 28f, cy)
                    close()
                }
                canvas.drawPath(wing, paint)
                canvas.drawPath(wing, outline)
            }

            else -> {
                // Detailed Character Hero (HUMAN / MONSTER / etc.)
                paint.shader = LinearGradient(cx, cy - 85f, cx, cy + 85f, lightColor, darkColor, Shader.TileMode.CLAMP)
                // Torso
                val torso = RectF(cx - 32f, cy - 34f, cx + 32f, cy + 38f)
                canvas.drawRoundRect(torso, 18f, 18f, paint)
                canvas.drawRoundRect(torso, 18f, 18f, outline)
                // Legs & Arms
                canvas.drawRoundRect(RectF(cx - 26f, cy + 32f, cx - 6f, cy + 94f), 10f, 10f, paint)
                canvas.drawRoundRect(RectF(cx + 6f, cy + 32f, cx + 26f, cy + 94f), 10f, 10f, paint)
                canvas.drawRoundRect(RectF(cx - 52f, cy - 26f, cx - 32f, cy + 26f), 10f, 10f, paint)
                canvas.drawRoundRect(RectF(cx + 32f, cy - 26f, cx + 52f, cy + 26f), 10f, 10f, paint)
                // Head
                paint.shader = null
                paint.color = Color.rgb(255, 224, 189)
                canvas.drawCircle(cx, cy - 66f, 30f, paint)
                canvas.drawCircle(cx, cy - 66f, 30f, outline)
                paint.color = Color.rgb(15, 23, 42)
                canvas.drawCircle(cx - 9f, cy - 68f, 4f, paint)
                canvas.drawCircle(cx + 9f, cy - 68f, 4f, paint)
            }
        }

        val savedPath = context?.let { ctx ->
            try {
                val dir = File(ctx.filesDir, "sprites").apply { mkdirs() }
                val outFile = File(dir, "sprite_${actor.type.lowercase()}_${System.currentTimeMillis()}.png")
                FileOutputStream(outFile).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                outFile.absolutePath
            } catch (_: Exception) {
                null
            }
        }
        return bmp to savedPath
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(
            bitmap,
            (w * ratio).toInt().coerceAtLeast(1),
            (h * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun parseColorInt(hex: String, fallback: Int): Int {
        return try {
            Color.parseColor(hex.trim().let { if (it.startsWith("#")) it else "#$it" })
        } catch (_: Exception) {
            fallback
        }
    }

    private fun darken(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color) * factor).toInt().coerceIn(0, 255)
    )

    private fun lighten(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color) * factor).toInt().coerceIn(0, 255)
    )
}
