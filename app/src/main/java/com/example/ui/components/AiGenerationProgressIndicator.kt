package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentPurple
import kotlin.math.cos
import kotlin.math.sin

/**
 * Reusable circular progress indicator composable for AI generation workflows
 * (e.g., 3D mesh reconstruction, AI storyboard scripting, keyframe synthesis, or video encoding).
 *
 * When [progress] is null, displays a fluid orbital indeterminate arc.
 * When [progress] is between 0f and 1f, displays a smooth determinate ring with a live percentage readout.
 */
@Composable
fun AiGenerationProgressIndicator(
    title: String,
    subtitle: String = "",
    progress: Float? = null,
    phaseLabel: String? = null,
    accentColor: Color = AccentCyan,
    secondaryColor: Color = AccentPurple,
    ringSize: Dp = 92.dp,
    modifier: Modifier = Modifier
) {
    val clampedTarget = progress?.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedTarget ?: 0f,
        animationSpec = spring(stiffness = 280f, dampingRatio = 0.85f),
        label = "ai_progress_value"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "ai_progress_orbit")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_rotation"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    val percentText = clampedTarget?.let { "${(it * 100).toInt().coerceIn(0, 100)}%" }
    val accessibilityText = buildString {
        append(title)
        if (percentText != null) append(", $percentText")
        if (subtitle.isNotBlank()) append(". $subtitle")
    }

    Column(
        modifier = modifier
            .semantics { contentDescription = accessibilityText }
            .testTag("ai_generation_progress"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!phaseLabel.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = accentColor.copy(alpha = 0.14f)
            ) {
                Text(
                    text = phaseLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Box(
            modifier = Modifier.size(ringSize),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokePx = 7.dp.toPx()
                val inset = strokePx / 2f + 4.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)

                // Ambient glow halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = pulseAlpha * 0.35f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.minDimension * 0.55f
                    ),
                    radius = size.minDimension * 0.55f,
                    center = center
                )

                // Subtle background track
                drawArc(
                    color = accentColor.copy(alpha = 0.16f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx)
                )

                val activeBrush = Brush.sweepGradient(
                    colors = listOf(secondaryColor, accentColor, secondaryColor)
                )

                if (clampedTarget == null) {
                    // Indeterminate orbital sweep arc
                    drawArc(
                        brush = activeBrush,
                        startAngle = orbitAngle - 90f,
                        sweepAngle = 115f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                    // Counter-orbit inner accent dash
                    val innerInset = inset + strokePx * 1.4f
                    val innerSize = Size(
                        (size.width - innerInset * 2).coerceAtLeast(8f),
                        (size.height - innerInset * 2).coerceAtLeast(8f)
                    )
                    drawArc(
                        color = secondaryColor.copy(alpha = 0.45f),
                        startAngle = -orbitAngle * 1.3f,
                        sweepAngle = 70f,
                        useCenter = false,
                        topLeft = Offset(innerInset, innerInset),
                        size = innerSize,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                } else {
                    // Determinate arc
                    val sweep = (animatedProgress * 360f).coerceAtLeast(6f)
                    drawArc(
                        brush = activeBrush,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                    // Leading orbital node at tip of progress arc
                    val tipRad = Math.toRadians((-90f + sweep).toDouble())
                    val radius = arcSize.width / 2f
                    val nodeX = center.x + (cos(tipRad) * radius).toFloat()
                    val nodeY = center.y + (sin(tipRad) * radius).toFloat()
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = Offset(nodeX, nodeY)
                    )
                }
            }

            if (percentText != null) {
                Text(
                    text = percentText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.testTag("ai_progress_percent")
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("ai_progress_title")
        )

        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}
