package com.example.ui.components

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.VideoProject
import com.example.service.Mp4VideoExporter
import com.example.ui.Mp4ExportState
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentPurple
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoExportSheet(
    project: VideoProject,
    exportState: Mp4ExportState,
    onStartExport: () -> Unit,
    onSaveToGallery: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = AccentCyan
                    )
                    Text(
                        text = "Export MP4 Video",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Export Sheet")
                }
            }

            // Video Technical Specs Summary
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${project.renderWidthPx}x${project.renderHeightPx} H.264 MP4 • ${project.targetFps} fps • ${
                            String.format(Locale.US, "%.1f Mbps", project.targetBitrate / 1_000_000f)
                        } • ${
                            String.format(Locale.US, "%.1fs", project.totalDurationSeconds)
                        } • ${project.scenes.size} Scenes",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            when (exportState) {
                is Mp4ExportState.Idle -> {
                    Text(
                        text = "Render all scenes, Ken Burns camera motion, transitions, and subtitles directly into a hardware-encoded .mp4 video file on your device.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Button(
                        onClick = onStartExport,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = Color(0xFF021B24)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_compile_mp4")
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Render .MP4 Video", fontWeight = FontWeight.Bold)
                    }
                }

                is Mp4ExportState.Encoding -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AiGenerationProgressIndicator(
                            title = "Hardware H.264 Encoding...",
                            subtitle = "Rendering frames, camera motion & subtitles to .mp4",
                            progress = exportState.progress,
                            phaseLabel = "MP4 COMPILER",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        )
                    }
                }

                is Mp4ExportState.Ready -> {
                    val sizeKb = (exportState.file.length() / 1024L).coerceAtLeast(1L)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = AccentEmerald.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AccentEmerald
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "MP4 Ready (${sizeKb} KB)",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = AccentEmerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = if (exportState.savedToGalleryUri != null) {
                                        "Saved to Movies/CinePrompt in Gallery"
                                    } else {
                                        exportState.file.name
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    // Native MP4 Preview Player
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(project.aspectRatio.ratio)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    val controller = MediaController(ctx)
                                    controller.setAnchorView(this)
                                    setMediaController(controller)
                                    setVideoURI(Uri.fromFile(exportState.file))
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        start()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onSaveToGallery,
                            enabled = exportState.savedToGalleryUri == null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentCyan,
                                contentColor = Color(0xFF021B24)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_save_gallery")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (exportState.savedToGalleryUri != null) "Saved" else "Save Gallery",
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = {
                                Mp4VideoExporter.shareMp4File(context, exportState.file, project.title)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentPurple,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_share_mp4")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share MP4", maxLines = 1)
                        }
                    }

                    OutlinedButton(
                        onClick = onStartExport,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-render MP4")
                    }
                }

                is Mp4ExportState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = exportState.message,
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)
                            )
                        }
                    }
                    Button(
                        onClick = onStartExport,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry Export")
                    }
                }
            }
        }
    }
}
