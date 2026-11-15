package com.example.ui.components

import android.opengl.GLSurfaceView
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.model.ActorDirective
import com.example.model.LocalAiModelSpec
import com.example.service.CharacterBackendUiState
import com.example.service.PlaceholderMeshShape
import com.example.service.ThreeDPlaceholderGlRenderer
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.MonoFontFamily
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class ProcessedModelSlot(
    val id: String,
    val title: String,
    val subtitle: String,
    val formatBadge: String,
    val suggestedShape: PlaceholderMeshShape,
    val accentColor: Color
)

@Composable
fun ThreeDViewportScreen(
    generatedActors: List<ActorDirective>,
    characterBackendState: CharacterBackendUiState,
    importedModels: List<LocalAiModelSpec>,
    isDarkTheme: Boolean,
    onNavigateToStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val processedSlots = remember(generatedActors, characterBackendState, importedModels) {
        buildProcessedSlots(
            generatedActors = generatedActors,
            characterBackendState = characterBackendState,
            importedModels = importedModels
        )
    }

    var selectedSlotId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSlot = remember(processedSlots, selectedSlotId) {
        processedSlots.firstOrNull { it.id == selectedSlotId } ?: processedSlots.firstOrNull()
    }

    var yawDegrees by rememberSaveable { mutableFloatStateOf(34f) }
    var pitchDegrees by rememberSaveable { mutableFloatStateOf(20f) }
    var zoomDistance by rememberSaveable { mutableFloatStateOf(4.6f) }
    var autoRotate by rememberSaveable { mutableStateOf(true) }
    var showGridAndAxes by rememberSaveable { mutableStateOf(true) }
    var wireframeOnly by rememberSaveable { mutableStateOf(false) }
    var meshShape by rememberSaveable { mutableStateOf(PlaceholderMeshShape.CHARACTER_RIG) }

    LaunchedEffect(selectedSlot?.id) {
        selectedSlot?.let { slot ->
            meshShape = slot.suggestedShape
        }
    }

    val renderer = remember { ThreeDPlaceholderGlRenderer() }
    var glSurfaceRef by remember { mutableStateOf<GLSurfaceView?>(null) }

    LaunchedEffect(
        yawDegrees,
        pitchDegrees,
        zoomDistance,
        autoRotate,
        showGridAndAxes,
        wireframeOnly,
        meshShape,
        isDarkTheme
    ) {
        renderer.yawDegrees = yawDegrees
        renderer.pitchDegrees = pitchDegrees
        renderer.zoomDistance = zoomDistance
        renderer.autoRotate = autoRotate
        renderer.showGridAndAxes = showGridAndAxes
        renderer.wireframeOnly = wireframeOnly
        renderer.meshShape = meshShape
        renderer.isDarkSurface = isDarkTheme
        glSurfaceRef?.renderMode = if (autoRotate) {
            GLSurfaceView.RENDERMODE_CONTINUOUSLY
        } else {
            GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
        glSurfaceRef?.requestRender()
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val isWideScreen = maxWidth >= 840.dp

        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.45f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ThreeDRenderingSurfaceCard(
                        renderer = renderer,
                        onGlSurfaceReady = { glSurfaceRef = it },
                        yawDegrees = yawDegrees,
                        pitchDegrees = pitchDegrees,
                        zoomDistance = zoomDistance,
                        autoRotate = autoRotate,
                        showGridAndAxes = showGridAndAxes,
                        wireframeOnly = wireframeOnly,
                        meshShape = meshShape,
                        selectedSlot = selectedSlot,
                        isDarkTheme = isDarkTheme,
                        onOrbitDelta = { dyaw, dpitch ->
                            autoRotate = false
                            yawDegrees = (yawDegrees + dyaw) % 360f
                            pitchDegrees = (pitchDegrees + dpitch).coerceIn(-75f, 75f)
                        },
                        onSelectShape = { meshShape = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    ThreeDViewportControlsBar(
                        autoRotate = autoRotate,
                        showGridAndAxes = showGridAndAxes,
                        wireframeOnly = wireframeOnly,
                        onToggleAutoRotate = { autoRotate = !autoRotate },
                        onToggleGrid = { showGridAndAxes = !showGridAndAxes },
                        onToggleWireframe = { wireframeOnly = !wireframeOnly },
                        onZoomIn = { zoomDistance = (zoomDistance - 0.6f).coerceIn(2.2f, 10.0f) },
                        onZoomOut = { zoomDistance = (zoomDistance + 0.6f).coerceIn(2.2f, 10.0f) },
                        onResetCamera = {
                            yawDegrees = 34f
                            pitchDegrees = 20f
                            zoomDistance = 4.6f
                            autoRotate = true
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.85f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProcessedModelsQueuePanel(
                        slots = processedSlots,
                        selectedSlotId = selectedSlot?.id,
                        onSelectSlot = { slot ->
                            selectedSlotId = slot.id
                            meshShape = slot.suggestedShape
                        },
                        onNavigateToStudio = onNavigateToStudio
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ThreeDRenderingSurfaceCard(
                        renderer = renderer,
                        onGlSurfaceReady = { glSurfaceRef = it },
                        yawDegrees = yawDegrees,
                        pitchDegrees = pitchDegrees,
                        zoomDistance = zoomDistance,
                        autoRotate = autoRotate,
                        showGridAndAxes = showGridAndAxes,
                        wireframeOnly = wireframeOnly,
                        meshShape = meshShape,
                        selectedSlot = selectedSlot,
                        isDarkTheme = isDarkTheme,
                        onOrbitDelta = { dyaw, dpitch ->
                            autoRotate = false
                            yawDegrees = (yawDegrees + dyaw) % 360f
                            pitchDegrees = (pitchDegrees + dpitch).coerceIn(-75f, 75f)
                        },
                        onSelectShape = { meshShape = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 250.dp, max = 330.dp)
                    )

                    ThreeDViewportControlsBar(
                        autoRotate = autoRotate,
                        showGridAndAxes = showGridAndAxes,
                        wireframeOnly = wireframeOnly,
                        onToggleAutoRotate = { autoRotate = !autoRotate },
                        onToggleGrid = { showGridAndAxes = !showGridAndAxes },
                        onToggleWireframe = { wireframeOnly = !wireframeOnly },
                        onZoomIn = { zoomDistance = (zoomDistance - 0.6f).coerceIn(2.2f, 10.0f) },
                        onZoomOut = { zoomDistance = (zoomDistance + 0.6f).coerceIn(2.2f, 10.0f) },
                        onResetCamera = {
                            yawDegrees = 34f
                            pitchDegrees = 20f
                            zoomDistance = 4.6f
                            autoRotate = true
                        }
                    )

                    ProcessedModelsQueuePanel(
                        slots = processedSlots,
                        selectedSlotId = selectedSlot?.id,
                        onSelectSlot = { slot ->
                            selectedSlotId = slot.id
                            meshShape = slot.suggestedShape
                        },
                        onNavigateToStudio = onNavigateToStudio
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ThreeDRenderingSurfaceCard(
    renderer: ThreeDPlaceholderGlRenderer,
    onGlSurfaceReady: (GLSurfaceView) -> Unit,
    yawDegrees: Float,
    pitchDegrees: Float,
    zoomDistance: Float,
    autoRotate: Boolean,
    showGridAndAxes: Boolean,
    wireframeOnly: Boolean,
    meshShape: PlaceholderMeshShape,
    selectedSlot: ProcessedModelSlot?,
    isDarkTheme: Boolean,
    onOrbitDelta: (Float, Float) -> Unit,
    onSelectShape: (PlaceholderMeshShape) -> Unit,
    modifier: Modifier = Modifier
) {
    val isPreviewOrRobolectric = LocalInspectionMode.current ||
        Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

    val lifecycleOwner = LocalLifecycleOwner.current
    var glViewHolder by remember { mutableStateOf<GLSurfaceView?>(null) }

    DisposableEffect(lifecycleOwner, glViewHolder) {
        val view = glViewHolder
        if (view == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> view.onResume()
                    Lifecycle.Event.ON_PAUSE -> view.onPause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                view.onPause()
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (isDarkTheme) Color(0xFF060A14) else Color(0xFFEAF0F8))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                shape = RoundedCornerShape(22.dp)
            )
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onOrbitDelta(-dragAmount.x * 0.45f, dragAmount.y * 0.35f)
                }
            }
            .testTag("three_d_rendering_surface")
    ) {
        if (!isPreviewOrRobolectric) {
            AndroidView(
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        setEGLContextClientVersion(2)
                        preserveEGLContextOnPause = true
                        setRenderer(renderer)
                        renderMode = if (autoRotate) {
                            GLSurfaceView.RENDERMODE_CONTINUOUSLY
                        } else {
                            GLSurfaceView.RENDERMODE_WHEN_DIRTY
                        }
                        glViewHolder = this
                        onGlSurfaceReady(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Robolectric3DFallbackCanvas(
                yawDegrees = yawDegrees,
                pitchDegrees = pitchDegrees,
                zoomDistance = zoomDistance,
                showGridAndAxes = showGridAndAxes,
                wireframeOnly = wireframeOnly,
                meshShape = meshShape,
                isDarkTheme = isDarkTheme,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top HUD Strip: OpenGL surface status + camera telemetry
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0B1220).copy(alpha = 0.80f),
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.45f)),
                modifier = Modifier.testTag("three_d_surface_status")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AccentEmerald)
                    )
                    Text(
                        text = stringResource(R.string.three_d_surface_gl_ready),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "• %.0f°/%.0f° • %.1fx",
                            yawDegrees,
                            pitchDegrees,
                            6.5f / zoomDistance
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = AccentCyan,
                            fontFamily = MonoFontFamily,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0B1220).copy(alpha = 0.80f),
                border = BorderStroke(
                    1.dp,
                    (selectedSlot?.accentColor ?: AccentPurple).copy(alpha = 0.55f)
                )
            ) {
                Text(
                    text = selectedSlot?.formatBadge ?: stringResource(R.string.three_d_placeholder_badge),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = selectedSlot?.accentColor ?: AccentPurple,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Bottom Overlay: Placeholder Notice + Geometry Preset Switcher
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0B1220).copy(alpha = 0.82f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(12.dp)
                .testTag("three_d_placeholder_banner")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewInAr,
                        contentDescription = null,
                        tint = selectedSlot?.accentColor ?: AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedSlot?.title ?: stringResource(R.string.three_d_surface_title),
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = selectedSlot?.subtitle ?: stringResource(R.string.three_d_surface_subtitle),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlaceholderShapeChip(
                        label = stringResource(R.string.three_d_shape_rig),
                        selected = meshShape == PlaceholderMeshShape.CHARACTER_RIG,
                        onClick = { onSelectShape(PlaceholderMeshShape.CHARACTER_RIG) },
                        testTag = "chip_3d_shape_rig"
                    )
                    PlaceholderShapeChip(
                        label = stringResource(R.string.three_d_shape_crystal),
                        selected = meshShape == PlaceholderMeshShape.CRYSTAL,
                        onClick = { onSelectShape(PlaceholderMeshShape.CRYSTAL) },
                        testTag = "chip_3d_shape_crystal"
                    )
                    PlaceholderShapeChip(
                        label = stringResource(R.string.three_d_shape_cube),
                        selected = meshShape == PlaceholderMeshShape.CUBE,
                        onClick = { onSelectShape(PlaceholderMeshShape.CUBE) },
                        testTag = "chip_3d_shape_cube"
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceholderShapeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentCyan.copy(alpha = 0.24f),
            selectedLabelColor = AccentCyan,
            labelColor = Color(0xFFE2E8F0)
        ),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    )
}

@Composable
private fun ThreeDViewportControlsBar(
    autoRotate: Boolean,
    showGridAndAxes: Boolean,
    wireframeOnly: Boolean,
    onToggleAutoRotate: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleWireframe: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetCamera: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("three_d_controls_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onToggleAutoRotate,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (autoRotate) AccentCyan.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (autoRotate) AccentCyan else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_3d_auto_rotate")
            ) {
                Icon(
                    imageVector = Icons.Default.Autorenew,
                    contentDescription = stringResource(R.string.three_d_cd_auto_rotate)
                )
            }

            FilledTonalIconButton(
                onClick = onToggleGrid,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (showGridAndAxes) AccentEmerald.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (showGridAndAxes) AccentEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_3d_toggle_grid")
            ) {
                Icon(
                    imageVector = Icons.Default.GridOn,
                    contentDescription = stringResource(R.string.three_d_cd_grid)
                )
            }

            FilledTonalIconButton(
                onClick = onToggleWireframe,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (wireframeOnly) AccentPurple.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (wireframeOnly) AccentPurple else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_3d_toggle_wireframe")
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = stringResource(R.string.three_d_cd_wireframe)
                )
            }

            FilledTonalIconButton(
                onClick = onZoomOut,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_3d_zoom_out")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = stringResource(R.string.three_d_cd_zoom_out)
                )
            }

            FilledTonalIconButton(
                onClick = onZoomIn,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_3d_zoom_in")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.three_d_cd_zoom_in)
                )
            }

            FilledTonalIconButton(
                onClick = onResetCamera,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_3d_reset_camera")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.three_d_cd_reset_camera)
                )
            }
        }
    }
}

@Composable
private fun ProcessedModelsQueuePanel(
    slots: List<ProcessedModelSlot>,
    selectedSlotId: String?,
    onSelectSlot: (ProcessedModelSlot) -> Unit,
    onNavigateToStudio: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("three_d_processed_models_panel")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.three_d_processed_models_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentCyan.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "${slots.size}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (slots.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("three_d_empty_models_state")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.three_d_empty_models_title),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = stringResource(R.string.three_d_empty_models_tip),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Button(
                            onClick = onNavigateToStudio,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentCyan,
                                contentColor = Color(0xFF021B24)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_3d_open_studio")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MovieFilter,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.three_d_go_to_studio),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    slots.forEach { slot ->
                        val isSelected = slot.id == selectedSlotId
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                slot.accentColor.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) {
                                    slot.accentColor
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectSlot(slot) }
                                .testTag("processed_model_slot_${slot.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = slot.accentColor.copy(alpha = 0.18f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (slot.id.startsWith("gemini_")) {
                                                Icons.Default.AutoAwesome
                                            } else {
                                                Icons.Default.Hub
                                            },
                                            contentDescription = null,
                                            tint = slot.accentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = slot.title,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = slot.subtitle,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = slot.accentColor.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = slot.formatBadge,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = slot.accentColor,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Software perspective fallback used strictly in Compose Previews and Robolectric JVM tests
 * where hardware OpenGL contexts are unavailable.
 */
@Composable
private fun Robolectric3DFallbackCanvas(
    yawDegrees: Float,
    pitchDegrees: Float,
    zoomDistance: Float,
    showGridAndAxes: Boolean,
    wireframeOnly: Boolean,
    meshShape: PlaceholderMeshShape,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.background(
            Brush.radialGradient(
                colors = if (isDarkTheme) {
                    listOf(Color(0xFF10203A), Color(0xFF060A14))
                } else {
                    listOf(Color(0xFFF8FAFC), Color(0xFFE2E8F0))
                }
            )
        )
    ) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.44f
        val scale = (size.minDimension * 0.24f) * (4.6f / zoomDistance.coerceIn(2.2f, 10f))
        val yawRad = Math.toRadians(yawDegrees.toDouble())
        val pitchRad = Math.toRadians(pitchDegrees.toDouble())
        val cosY = cos(yawRad).toFloat()
        val sinY = sin(yawRad).toFloat()
        val cosP = cos(pitchRad).toFloat()
        val sinP = sin(pitchRad).toFloat()

        fun project(x: Float, y: Float, z: Float): Offset {
            val rx = x * cosY - z * sinY
            val rz = x * sinY + z * cosY
            val ry = y * cosP - rz * sinP
            return Offset(cx + rx * scale, cy - ry * scale)
        }

        if (showGridAndAxes) {
            for (i in -3..3) {
                val v = i * 0.5f
                drawLine(
                    color = Color(0xFF334155).copy(alpha = 0.55f),
                    start = project(v, -0.8f, -1.5f),
                    end = project(v, -0.8f, 1.5f),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = Color(0xFF334155).copy(alpha = 0.55f),
                    start = project(-1.5f, -0.8f, v),
                    end = project(1.5f, -0.8f, v),
                    strokeWidth = 1.5f
                )
            }
        }

        val half = if (meshShape == PlaceholderMeshShape.CRYSTAL) 0.7f else 0.65f
        val corners = listOf(
            project(-half, -half, -half),
            project(half, -half, -half),
            project(half, half, -half),
            project(-half, half, -half),
            project(-half, -half, half),
            project(half, -half, half),
            project(half, half, half),
            project(-half, half, half)
        )
        val wireColor = if (wireframeOnly) AccentPurple else AccentCyan
        val edges = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 0,
            4 to 5, 5 to 6, 6 to 7, 7 to 4,
            0 to 4, 1 to 5, 2 to 6, 3 to 7
        )
        edges.forEach { (a, b) ->
            drawLine(color = wireColor, start = corners[a], end = corners[b], strokeWidth = 2.5f)
        }
    }
}

private fun buildProcessedSlots(
    generatedActors: List<ActorDirective>,
    characterBackendState: CharacterBackendUiState,
    importedModels: List<LocalAiModelSpec>
): List<ProcessedModelSlot> {
    val result = mutableListOf<ProcessedModelSlot>()

    if (characterBackendState is CharacterBackendUiState.Success) {
        val bp = characterBackendState.result.blueprint
        result += ProcessedModelSlot(
            id = "gemini_${bp.characterName.hashCode()}",
            title = bp.characterName,
            subtitle = bp.visualTraitsSummary.ifBlank { bp.characterImagePrompt },
            formatBadge = "Gemini Character",
            suggestedShape = PlaceholderMeshShape.CHARACTER_RIG,
            accentColor = AccentPurple
        )
    }

    generatedActors.forEachIndexed { idx, actor ->
        result += ProcessedModelSlot(
            id = "actor_${actor.id}_$idx",
            title = actor.label.ifBlank { actor.type },
            subtitle = actor.characterImagePrompt.ifBlank { "${actor.type} • ${actor.action}" },
            formatBadge = actor.action,
            suggestedShape = if (actor.type.uppercase(Locale.US) in setOf("CAR", "BOAT", "ROCKET")) {
                PlaceholderMeshShape.CUBE
            } else {
                PlaceholderMeshShape.CHARACTER_RIG
            },
            accentColor = AccentCyan
        )
    }

    importedModels.forEach { model ->
        result += ProcessedModelSlot(
            id = "local_${model.id}",
            title = model.displayName,
            subtitle = model.binaryHeaderSummary.ifBlank { "${model.fileFormat} • ${model.role.name}" },
            formatBadge = model.fileFormat,
            suggestedShape = PlaceholderMeshShape.CRYSTAL,
            accentColor = AccentEmerald
        )
    }

    return result
}
