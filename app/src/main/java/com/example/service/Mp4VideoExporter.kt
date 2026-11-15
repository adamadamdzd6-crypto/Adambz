package com.example.service

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.model.VideoProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.coroutineContext

private const val TAG = "Mp4VideoExporter"

object Mp4VideoExporter {

    /**
     * Compiles a VideoProject into a real H.264 `.mp4` file on-device using Android's hardware
     * MediaCodec + MediaMuxer surface pipeline.
     */
    suspend fun exportProjectToMp4(
        context: Context,
        project: VideoProject,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.Default) {
        val outDir = File(context.filesDir, "videos").apply { mkdirs() }
        val outFile = File(outDir, "cineprompt_${System.currentTimeMillis()}.mp4")

        var width = project.renderWidthPx.coerceAtLeast(320)
        var height = project.renderHeightPx.coerceAtLeast(320)
        var fps = project.targetFps.coerceIn(15, 60)
        val bitrate = project.targetBitrate.coerceAtLeast(1_500_000)
        val totalDuration = project.totalDurationSeconds.coerceAtLeast(2f)

        // Preload bitmaps for scenes that have saved AI images or user photos
        val sceneBitmaps = mutableMapOf<String, Bitmap>()
        for (scene in project.scenes) {
            val path = scene.imagePath
            if (!path.isNullOrBlank()) {
                val file = File(path)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)?.let { bmp ->
                        sceneBitmaps[scene.id] = bmp
                    }
                }
            }
        }

        // Preload cutout Character Sprites produced by Image Generator AI
        val spriteBitmaps = mutableMapOf<String, Bitmap>()
        for (spritePath in CinemaFrameRenderer.extractAllSpritePaths(project)) {
            val file = File(spritePath)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)?.let { bmp ->
                    spriteBitmaps[spritePath] = bmp
                }
            }
        }

        fun buildFormat(w: Int, h: Int, frameRate: Int, bitRate: Int): MediaFormat {
            return MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
        }

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1

        try {
            encoder = run {
                var candidate: MediaCodec? = null
                try {
                    candidate = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                    candidate.configure(buildFormat(width, height, fps, bitrate), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    candidate
                } catch (highTierError: Exception) {
                    try {
                        candidate?.release()
                    } catch (_: Exception) {}
                    Log.w(TAG, "Hardware codec rejected ${width}x${height}@${fps}fps; falling back to baseline", highTierError)
                    width = project.aspectRatio.widthPx
                    height = project.aspectRatio.heightPx
                    fps = 24
                    MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                        configure(buildFormat(width, height, fps, 4_000_000), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    }
                }
            }

            val totalFrames = (totalDuration * fps).toInt().coerceAtLeast(24)
            val inputSurface = encoder.createInputSurface()
            encoder.start()

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val bufferInfo = MediaCodec.BufferInfo()
            var emittedFrames = 0L

            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) {
                    encoder.signalEndOfInputStream()
                }
                while (true) {
                    val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                    when {
                        outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) break
                        }
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (muxerStarted) {
                                throw RuntimeException("MediaCodec output format changed twice")
                            }
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outputIndex >= 0 -> {
                            val encodedData = encoder.getOutputBuffer(outputIndex)
                                ?: throw RuntimeException("Encoder output buffer $outputIndex was null")

                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size != 0) {
                                if (!muxerStarted) {
                                    throw RuntimeException("Muxer hasn't started yet")
                                }
                                // Enforce deterministic strictly monotonic frame timestamps
                                // matching target FPS so playback duration is exact.
                                bufferInfo.presentationTimeUs = (emittedFrames * 1_000_000L) / fps.toLong()
                                emittedFrames++
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                            }
                            encoder.releaseOutputBuffer(outputIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break
                            }
                        }
                    }
                }
            }

            for (frameIndex in 0 until totalFrames) {
                coroutineContext.ensureActive()

                val timeSec = frameIndex.toFloat() / fps.toFloat()
                val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        inputSurface.lockHardwareCanvas()
                    } catch (_: Exception) {
                        inputSurface.lockCanvas(null)
                    }
                } else {
                    inputSurface.lockCanvas(null)
                }

                try {
                    CinemaFrameRenderer.drawFrameToCanvas(
                        canvas = canvas,
                        width = width,
                        height = height,
                        project = project,
                        timeSeconds = timeSec,
                        sceneBitmaps = sceneBitmaps,
                        spriteBitmaps = spriteBitmaps
                    )
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                drainEncoder(false)
                onProgress(((frameIndex + 1).toFloat() / totalFrames).coerceIn(0f, 0.98f))
            }

            drainEncoder(true)
            onProgress(1.0f)
            outFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compile MP4 video", e)
            if (outFile.exists()) outFile.delete()
            throw e
        } finally {
            try {
                encoder?.stop()
                encoder?.release()
            } catch (_: Exception) {}
            try {
                if (muxerStarted) muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
            sceneBitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
            spriteBitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    /**
     * Saves an exported MP4 into the device's Videos/Movies Gallery via MediaStore (API 29+ zero-permission).
     */
    suspend fun saveMp4ToGallery(
        context: Context,
        mp4File: File,
        videoTitle: String
    ): Uri? = withContext(Dispatchers.IO) {
        val sanitizedTitle = videoTitle.replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF_-]"), "_").take(36)
        val fileName = "${sanitizedTitle}_${System.currentTimeMillis()}.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/CinePrompt")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = resolver.insert(collection, values) ?: return@withContext null
        try {
            resolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(mp4File).use { input ->
                    input.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            }
            itemUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving video to MediaStore", e)
            resolver.delete(itemUri, null, null)
            null
        }
    }

    fun shareMp4File(context: Context, mp4File: File, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            mp4File
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
    }
}
