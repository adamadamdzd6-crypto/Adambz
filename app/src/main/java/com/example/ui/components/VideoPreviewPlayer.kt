package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VideoAspectRatio
import com.example.model.VideoProject
import com.example.model.VideoUiState
import com.example.service.CinemaFrameRenderer
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentPurple
import java.io.File
import java.util.Locale

@Composable
fun VideoPreviewStage(
    uiState: VideoUiState,
    activeProject: VideoProject?,
    selectedAspectRatio: VideoAspectRatio,
    currentTimeSeconds: Float,
    isPlaying: Boolean,
    isVoiceoverEnabled: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleVoiceover: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayRatio = activeProject?.aspectRatio ?: selectedAspectRatio

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val isIdleEmpty = uiState is VideoUiState.Idle && activeProject == null
        // Cinema Frame Container constrained cleanly so controls & Gemini card stay immediately visible
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(
                    min = if (isIdleEmpty) 145.dp else 200.dp,
                    max = if (isIdleEmpty) 185.dp else 320.dp
                )
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF05070E))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            when (uiState) {
                is VideoUiState.Idle -> {
                    EmptyCinemaStage()
                }
                is VideoUiState.GeneratingScript -> {
                    GeneratingStageOverlay(
                        title = "العقل المدبر يحلّل طلبك...",
                        subtitle = "Local LLM Orchestrator is generating Structured JSON Prompts",
                        phaseLabel = "LLM ORCHESTRATOR",
                        progress = null
                    )
                }
                is VideoUiState.GeneratingFrames -> {
                    val frac = if (uiState.total > 0) uiState.completed.toFloat() / uiState.total else 0f
                    GeneratingStageOverlay(
                        title = "تنفيذ المشاهد المحلية ${uiState.completed} / ${uiState.total}",
                        subtitle = "Media Pipeline Engine is assembling scenes & actors locally...",
                        phaseLabel = "MEDIA PIPELINE",
                        progress = frac
                    )
                }
                is VideoUiState.Error -> {
                    if (activeProject != null) {
                        LiveProjectCanvasPlayer(
                            project = activeProject,
                            currentTimeSeconds = currentTimeSeconds,
                            modifier = Modifier
                                .aspectRatio(displayRatio.ratio)
                                .fillMaxSize()
                        )
                    } else {
                        EmptyCinemaStage()
                    }
                }
                is VideoUiState.Ready -> {
                    val proj = uiState.project
                    LiveProjectCanvasPlayer(
                        project = proj,
                        currentTimeSeconds = currentTimeSeconds,
                        modifier = Modifier
                            .aspectRatio(proj.aspectRatio.ratio)
                            .fillMaxSize()
                    )
                }
            }
        }

        // Transport Controls Bar (visible whenever a project is loaded)
        if (activeProject != null && uiState !is VideoUiState.GeneratingScript && uiState !is VideoUiState.GeneratingFrames) {
            Spacer(modifier = Modifier.height(10.dp))
            TransportControlBar(
                project = activeProject,
                currentTimeSeconds = currentTimeSeconds,
                isPlaying = isPlaying,
                isVoiceoverEnabled = isVoiceoverEnabled,
                onTogglePlayPause = onTogglePlayPause,
                onSeek = onSeek,
                onToggleVoiceover = onToggleVoiceover
            )
        }
    }
}

@Composable
private fun EmptyCinemaStage() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0F2138), Color(0xFF060A14))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = AccentCyan.copy(alpha = 0.2f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MovieCreation,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Text(
                text = "استوديو الذكاء الاصطناعي المحلي جاهز",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )
            Text(
                text = "اكتب وصف الفيديو وسيقوم فريق النماذج المحلي بتخطيط المشاهد، إنتاج الشخصيات، وتصدير MP4 بالكامل على هاتفك",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFFCBD5E1)
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GeneratingStageOverlay(
    title: String,
    subtitle: String,
    phaseLabel: String,
    progress: Float?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        AiGenerationProgressIndicator(
            title = title,
            subtitle = subtitle,
            progress = progress,
            phaseLabel = phaseLabel
        )
    }
}

@Composable
private fun LiveProjectCanvasPlayer(
    project: VideoProject,
    currentTimeSeconds: Float,
    modifier: Modifier = Modifier
) {
    val cursor = CinemaFrameRenderer.resolveCursor(project, currentTimeSeconds)
    val scene = cursor.scene

    val sceneBitmaps = remember(project.id, project.scenes.map { it.imagePath }) {
        buildMap {
            project.scenes.forEach { sc ->
                sc.imagePath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        BitmapFactory.decodeFile(file.absolutePath)?.let { bmp ->
                            put(sc.id, bmp)
                        }
                    }
                }
            }
        }
    }

    val spriteBitmaps = remember(project.id, project.scenes.map { it.sceneBlueprintJson }) {
        buildMap {
            CinemaFrameRenderer.extractAllSpritePaths(project).forEach { spritePath ->
                val file = File(spritePath)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)?.let { bmp ->
                        put(spritePath, bmp)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntoCanvas { composeCanvas ->
                CinemaFrameRenderer.drawFrameToCanvas(
                    canvas = composeCanvas.nativeCanvas,
                    width = size.width.toInt().coerceAtLeast(1),
                    height = size.height.toInt().coerceAtLeast(1),
                    project = project,
                    timeSeconds = currentTimeSeconds,
                    sceneBitmaps = sceneBitmaps,
                    spriteBitmaps = spriteBitmaps
                )
            }
        }

        // Vignette overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0x55000000),
                        0.35f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1.0f to Color(0xCC000000)
                    )
                )
        )

        // Top Scene Pill HUD
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xAA070A12)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(AccentEmerald, CircleShape)
                )
                Text(
                    text = "SC ${cursor.sceneIndex + 1}/${project.scenes.size} • ${scene.cameraMotion.label}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontSize = 9.sp
                    )
                )
            }
        }

        // Subtitle Pill Overlay
        if (scene.narrationSubtitle.isNotBlank()) {
            AnimatedContent(
                targetState = scene.narrationSubtitle,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                label = "subtitle_anim"
            ) { sub ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xBB070A12)
                ) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}


@Composable
private fun TransportControlBar(
    project: VideoProject,
    currentTimeSeconds: Float,
    isPlaying: Boolean,
    isVoiceoverEnabled: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleVoiceover: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("btn_play_pause"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = AccentCyan,
                    contentColor = Color(0xFF021B24)
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause Video" else "Play Video"
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Slider(
                    value = currentTimeSeconds.coerceIn(0f, project.totalDurationSeconds),
                    onValueChange = onSeek,
                    valueRange = 0f..project.totalDurationSeconds,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .testTag("video_scrubber")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTimecode(currentTimeSeconds),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = AccentCyan
                        )
                    )
                    Text(
                        text = "${project.renderWidthPx}×${project.renderHeightPx} • ${project.targetFps}fps • ${formatTimecode(project.totalDurationSeconds)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onToggleVoiceover,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("btn_voiceover_toggle")
            ) {
                Icon(
                    imageVector = if (isVoiceoverEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = if (isVoiceoverEnabled) "Mute Voiceover" else "Enable Voiceover",
                    tint = if (isVoiceoverEnabled) AccentPurple else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTimecode(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}
