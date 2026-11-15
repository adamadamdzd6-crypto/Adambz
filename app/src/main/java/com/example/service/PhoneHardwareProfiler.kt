package com.example.service

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import com.example.model.VideoAspectRatio
import kotlin.math.roundToInt

enum class HardwareTier(
    val titleAr: String,
    val badgeLabel: String,
    val densityMultiplier: Float
) {
    ULTRA_4K("أداء خارق • دقة 4K / 60fps", "ULTRA 4K", 1.65f),
    HIGH_1080P("أداء قوي • دقة Full HD 1080p", "FULL HD", 1.3f),
    BALANCED_720P("أداء متوازن • دقة HD 720p", "HD 720p", 1.0f),
    ENTRY_540P("وضع موفّر • دقة 540p سريعة", "FAST 540p", 0.75f)
}

data class DevicePerformanceProfile(
    val tier: HardwareTier,
    val cpuCores: Int,
    val totalRamGb: Float,
    val encoderName: String,
    val maxSupportedWidth: Int,
    val maxSupportedHeight: Int,
    val recommendedFps: Int,
    val recommendedBitrate: Int
) {
    fun resolveRenderSpecs(aspectRatio: VideoAspectRatio): RenderSpecs {
        val baseShortEdge = when (tier) {
            HardwareTier.ULTRA_4K -> 1440 // Crisp QHD/4K class without OOM on Canvas
            HardwareTier.HIGH_1080P -> 1080
            HardwareTier.BALANCED_720P -> 720
            HardwareTier.ENTRY_540P -> 540
        }

        val (rawW, rawH) = when (aspectRatio) {
            VideoAspectRatio.LANDSCAPE_16_9 -> (baseShortEdge * 16f / 9f).roundToInt() to baseShortEdge
            VideoAspectRatio.PORTRAIT_9_16 -> baseShortEdge to (baseShortEdge * 16f / 9f).roundToInt()
            VideoAspectRatio.SQUARE_1_1 -> baseShortEdge to baseShortEdge
        }

        val maxCodecLong = maxOf(maxSupportedWidth, maxSupportedHeight)
        val maxCodecShort = minOf(maxSupportedWidth, maxSupportedHeight)
        val (capW, capH) = when (aspectRatio) {
            VideoAspectRatio.LANDSCAPE_16_9 ->
                maxCodecLong.coerceAtLeast(aspectRatio.widthPx) to maxCodecShort.coerceAtLeast(aspectRatio.heightPx)
            VideoAspectRatio.PORTRAIT_9_16 ->
                maxCodecShort.coerceAtLeast(aspectRatio.widthPx) to maxCodecLong.coerceAtLeast(aspectRatio.heightPx)
            VideoAspectRatio.SQUARE_1_1 ->
                maxCodecShort.coerceAtLeast(aspectRatio.widthPx) to maxCodecShort.coerceAtLeast(aspectRatio.heightPx)
        }

        // Scale down proportionally if codec max dimensions are smaller than target
        val scale = minOf(
            1f,
            capW.toFloat() / rawW.coerceAtLeast(1),
            capH.toFloat() / rawH.coerceAtLeast(1)
        )

        val wAligned = align16((rawW * scale).roundToInt()).coerceAtLeast(320)
        val hAligned = align16((rawH * scale).roundToInt()).coerceAtLeast(320)
        return RenderSpecs(
            widthPx = wAligned,
            heightPx = hAligned,
            fps = recommendedFps,
            bitrate = recommendedBitrate,
            detailDensity = tier.densityMultiplier
        )
    }

    private fun align16(v: Int): Int = ((v + 8) / 16) * 16
}

data class RenderSpecs(
    val widthPx: Int,
    val heightPx: Int,
    val fps: Int,
    val bitrate: Int,
    val detailDensity: Float
)

object PhoneHardwareProfiler {

    /**
     * Benchmarks the phone's real CPU core count, total physical RAM, and hardware H.264 MediaCodec
     * capabilities to scale video resolution, FPS, bitrate, and particle/shader richness automatically.
     */
    fun profileDevice(context: Context): DevicePerformanceProfile {
        val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val totalRamBytes = try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            memInfo.totalMem
        } catch (_: Exception) {
            0L
        }
        val totalRamGb = if (totalRamBytes > 0L) {
            (totalRamBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).toFloat()
        } else {
            4.0f
        }

        var encoderName = "Device AVC"
        var maxW = 1920
        var maxH = 1080
        var maxFps = 30

        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val bestCodec = codecList.codecInfos
                .filter { info ->
                    info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) }
                }
                .sortedByDescending { info ->
                    // Prefer hardware-accelerated encoders over software-only codecs
                    val isSw = info.name.contains("c2.android", ignoreCase = true) ||
                        info.name.contains("OMX.google", ignoreCase = true)
                    if (isSw) 0 else 1
                }
                .firstOrNull()

            if (bestCodec != null) {
                encoderName = bestCodec.name
                val videoCaps = bestCodec.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)?.videoCapabilities
                if (videoCaps != null) {
                    maxW = videoCaps.supportedWidths.upper
                    maxH = videoCaps.supportedHeights.upper
                    maxFps = try {
                        videoCaps.getSupportedFrameRatesFor(1280, 720).upper.roundToInt().coerceIn(24, 60)
                    } catch (_: Exception) {
                        videoCaps.supportedFrameRates.upper.coerceIn(24, 60)
                    }
                }
            }
        } catch (_: Exception) {
            // Keep conservative defaults if test JVM environment has no MediaCodecList
        }

        val supports1080p = maxW >= 1920 && maxH >= 1080
        val supports4k = maxW >= 3840 && maxH >= 2160

        val tier = when {
            cpuCores >= 8 && totalRamGb >= 7.5f && supports4k -> HardwareTier.ULTRA_4K
            cpuCores >= 6 && totalRamGb >= 5.0f && supports1080p -> HardwareTier.HIGH_1080P
            cpuCores >= 4 && totalRamGb >= 3.0f -> HardwareTier.BALANCED_720P
            else -> HardwareTier.ENTRY_540P
        }

        val targetFps = when (tier) {
            HardwareTier.ULTRA_4K -> maxFps.coerceAtMost(60).coerceAtLeast(30)
            HardwareTier.HIGH_1080P -> maxFps.coerceAtMost(48).coerceAtLeast(30)
            HardwareTier.BALANCED_720P -> 30
            HardwareTier.ENTRY_540P -> 24
        }

        val bitrate = when (tier) {
            HardwareTier.ULTRA_4K -> 14_000_000
            HardwareTier.HIGH_1080P -> 8_000_000
            HardwareTier.BALANCED_720P -> 5_000_000
            HardwareTier.ENTRY_540P -> 2_500_000
        }

        return DevicePerformanceProfile(
            tier = tier,
            cpuCores = cpuCores,
            totalRamGb = totalRamGb,
            encoderName = encoderName,
            maxSupportedWidth = maxW,
            maxSupportedHeight = maxH,
            recommendedFps = targetFps,
            recommendedBitrate = bitrate
        )
    }
}
