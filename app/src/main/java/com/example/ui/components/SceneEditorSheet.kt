package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.CameraMotion
import com.example.model.SceneTransition
import com.example.model.VideoScene
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentPurple
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneEditorSheet(
    sceneIndex: Int,
    scene: VideoScene,
    onSave: (title: String, narration: String, duration: Float, motion: CameraMotion, transition: SceneTransition) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(scene.id) { mutableStateOf(scene.title) }
    var narration by remember(scene.id) { mutableStateOf(scene.narrationSubtitle) }
    var duration by remember(scene.id) { mutableFloatStateOf(scene.durationSeconds) }
    var motion by remember(scene.id) { mutableStateOf(scene.cameraMotion) }
    var transition by remember(scene.id) { mutableStateOf(scene.transition) }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit Scene #${scene.sceneNumber}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Scene Headline") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = narration,
                onValueChange = { narration = it },
                label = { Text("Subtitle / Voiceover Line") },
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Duration slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Scene Duration",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f sec", duration),
                        style = MaterialTheme.typography.labelSmall.copy(color = AccentCyan)
                    )
                }
                Slider(
                    value = duration,
                    onValueChange = { duration = it },
                    valueRange = 2f..8f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan
                    )
                )
            }

            // Camera Motion Selector
            Text(text = "Camera Motion", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CameraMotion.entries.forEach { m ->
                    FilterChip(
                        selected = motion == m,
                        onClick = { motion = m },
                        label = { Text(m.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCyan.copy(alpha = 0.2f),
                            selectedLabelColor = AccentCyan
                        )
                    )
                }
            }

            // Transition Selector
            Text(text = "Exit Transition", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SceneTransition.entries.forEach { t ->
                    FilterChip(
                        selected = transition == t,
                        onClick = { transition = t },
                        label = { Text(t.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentPurple.copy(alpha = 0.2f),
                            selectedLabelColor = AccentPurple
                        )
                    )
                }
            }


            Button(
                onClick = {
                    onSave(title, narration, duration, motion, transition)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCyan,
                    contentColor = Color(0xFF021B24)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Apply Scene Changes", fontWeight = FontWeight.Bold)
            }
        }
    }
}
