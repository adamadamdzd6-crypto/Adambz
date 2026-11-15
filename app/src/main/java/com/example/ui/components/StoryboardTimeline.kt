package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VideoProject
import com.example.model.VideoScene
import com.example.model.parseColorSafe
import com.example.service.CinemaFrameRenderer
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentPurple
import java.io.File
import java.util.Locale

@Composable
fun StoryboardTimeline(
    project: VideoProject,
    currentTimeSeconds: Float,
    onJumpToScene: (Int) -> Unit,
    onEditScene: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeIdx = CinemaFrameRenderer.resolveCursor(project, currentTimeSeconds).sceneIndex

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Storyboard Timeline (${project.scenes.size} Scenes)",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = project.musicMood,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = AccentPurple
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            itemsIndexed(project.scenes, key = { _, s -> s.id }) { index, scene ->
                SceneTimelineCard(
                    scene = scene,
                    isActive = index == activeIdx,
                    onSelect = { onJumpToScene(index) },
                    onEdit = { onEditScene(index) }
                )
            }
        }
    }
}

@Composable
private fun SceneTimelineCard(
    scene: VideoScene,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit
) {
    val moodColor = parseColorSafe(scene.moodColorHex, Color(0xFF0F172A))
    val accent = parseColorSafe(scene.accentColorHex, AccentCyan)
    val thumbBitmap = remember(scene.imagePath) {
        scene.imagePath?.let { path ->
            val file = File(path)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }
    }

    Card(
        modifier = Modifier
            .width(172.dp)
            .clickable { onSelect() }
            .testTag("scene_card_${scene.sceneNumber}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) AccentCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    ) {
        Column {
            // Thumbnail Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(
                        Brush.linearGradient(listOf(moodColor, accent.copy(alpha = 0.45f)))
                    )
            ) {
                if (thumbBitmap != null) {
                    Image(
                        bitmap = thumbBitmap.asImageBitmap(),
                        contentDescription = scene.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (scene.isGeneratingImage) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x99000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = AccentCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Scene number badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xBB070A12),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = "#${scene.sceneNumber}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Edit icon button (48dp touch target wrapper)
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp)
                        .testTag("btn_edit_scene_${scene.sceneNumber}")
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xCC070A12)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Scene ${scene.sceneNumber}",
                            tint = AccentCyan,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(14.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = scene.title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = scene.narrationSubtitle.ifBlank { scene.visualPrompt },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = String.format(Locale.US, "%.1fs", scene.durationSeconds),
                            style = MaterialTheme.typography.labelSmall.copy(color = AccentCyan)
                        )
                    }
                    Text(
                        text = scene.cameraMotion.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp
                        )
                    )
                }
            }
        }
    }
}
