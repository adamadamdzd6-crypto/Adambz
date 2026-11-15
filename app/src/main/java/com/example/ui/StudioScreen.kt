package com.example.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.ActorDirective
import com.example.model.LocalAiModelSpec
import com.example.model.LocalModelRole
import com.example.model.PipelineStageState
import com.example.model.VideoAspectRatio
import com.example.model.VideoGenerationMode
import com.example.model.VideoUiState
import com.example.model.VideoVisualStyle
import com.example.service.CharacterBackendUiState
import com.example.service.DevicePerformanceProfile
import com.example.ui.components.DatasetBatchManagerScreen
import com.example.ui.components.ModelImporterScreen
import com.example.ui.components.SavedVideosSheet
import com.example.ui.components.SceneEditorSheet
import com.example.ui.components.StoryboardTimeline
import com.example.ui.components.ThreeDViewportScreen
import com.example.ui.components.VideoExportSheet
import com.example.ui.components.VideoPreviewStage
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentPurple
import java.io.File
import java.util.Locale

private enum class StudioTab {
    VIDEO_AI,
    DATASET_LIBRARY,
    THREE_D_STAGE,
    MODEL_IMPORTER
}

private data class QuickPromptSample(
    val label: String,
    val prompt: String
)

private val QUICK_PROMPT_SAMPLES = listOf(
    QuickPromptSample(
        label = "كلب وحفرة 🐕",
        prompt = "كلب يتمشى في حديقة خضراء ثم يقع في حفرة"
    ),
    QuickPromptSample(
        label = "روبوت والمطر 🤖",
        prompt = "روبوت أزرق يقفز تحت المطر في مدينة نيون ثم يرقص"
    ),
    QuickPromptSample(
        label = "قارب في البحر ⛵",
        prompt = "قارب أبيض يبحر في البحر وقت الغروب وطائر يحلق في السماء"
    ),
    QuickPromptSample(
        label = "وثائقي فضاء 🪐",
        prompt = "صاروخ فضائي يحلق في الفضاء حول قمر بين النجوم"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val promptText by viewModel.promptText.collectAsStateWithLifecycle()
    val selectedStyle by viewModel.selectedStyle.collectAsStateWithLifecycle()
    val selectedAspectRatio by viewModel.selectedAspectRatio.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentTimeSeconds by viewModel.currentTimeSeconds.collectAsStateWithLifecycle()
    val isVoiceoverEnabled by viewModel.isVoiceoverEnabled.collectAsStateWithLifecycle()
    val savedProjects by viewModel.savedProjects.collectAsStateWithLifecycle()
    val showExportSheet by viewModel.showExportSheet.collectAsStateWithLifecycle()
    val showSavedSheet by viewModel.showSavedSheet.collectAsStateWithLifecycle()
    val editingSceneIndex by viewModel.editingSceneIndex.collectAsStateWithLifecycle()
    val mp4ExportState by viewModel.mp4ExportState.collectAsStateWithLifecycle()
    val hardwareProfile by viewModel.hardwareProfile.collectAsStateWithLifecycle()
    val importedModels by viewModel.importedModels.collectAsStateWithLifecycle()
    val pipelineStage by viewModel.pipelineStage.collectAsStateWithLifecycle()
    val orchestratorJson by viewModel.orchestratorJson.collectAsStateWithLifecycle()
    val engineSourceLabel by viewModel.engineSourceLabel.collectAsStateWithLifecycle()
    val generatedActorSprites by viewModel.generatedActorSprites.collectAsStateWithLifecycle()
    val importerStatusMessage by viewModel.importerStatusMessage.collectAsStateWithLifecycle()
    val characterPromptInput by viewModel.characterPromptInput.collectAsStateWithLifecycle()
    val characterBackendState by viewModel.characterBackendState.collectAsStateWithLifecycle()
    val useGeminiCharacterBackend by viewModel.useGeminiCharacterBackend.collectAsStateWithLifecycle()
    val datasetImages by viewModel.datasetImages.collectAsStateWithLifecycle()
    val datasetSearchQuery by viewModel.datasetSearchQuery.collectAsStateWithLifecycle()

    val isGenerating = uiState is VideoUiState.GeneratingScript || uiState is VideoUiState.GeneratingFrames

    var selectedTab by rememberSaveable { mutableStateOf(StudioTab.VIDEO_AI) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AccentCyan.copy(alpha = 0.16f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (selectedTab) {
                                        StudioTab.MODEL_IMPORTER -> Icons.Default.Hub
                                        StudioTab.THREE_D_STAGE -> Icons.Default.ViewInAr
                                        StudioTab.DATASET_LIBRARY -> Icons.Default.PhotoLibrary
                                        StudioTab.VIDEO_AI -> Icons.Default.MovieFilter
                                    },
                                    contentDescription = "Studio Logo",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "CinePrompt Studio",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = when (selectedTab) {
                                    StudioTab.MODEL_IMPORTER -> "Local AI Model Importer"
                                    StudioTab.THREE_D_STAGE -> stringResource(R.string.three_d_topbar_subtitle)
                                    StudioTab.DATASET_LIBRARY -> "Dataset Library & Batch Reference Manager"
                                    StudioTab.VIDEO_AI -> if (selectedMode == VideoGenerationMode.GEMINI_CLOUD_BACKEND) {
                                        "Gemini 3.8 + Character SDK Active"
                                    } else {
                                        "Local Offline + Gemini AI SDK"
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (selectedMode == VideoGenerationMode.GEMINI_CLOUD_BACKEND) AccentPurple else AccentCyan
                                ),
                                maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleTheme() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Color Theme"
                        )
                    }

                    IconButton(
                        onClick = { viewModel.setSavedSheetVisible(true) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_open_library")
                    ) {
                        BadgedBox(
                            badge = {
                                if (savedProjects.isNotEmpty()) {
                                    Badge(containerColor = AccentPurple) {
                                        Text("${savedProjects.size}", color = Color.White)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = "Open Saved Videos Library"
                            )
                        }
                    }

                    if (selectedTab == StudioTab.VIDEO_AI && activeProject != null) {
                        Button(
                            onClick = { viewModel.setExportSheetVisible(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentCyan,
                                contentColor = Color(0xFF021B24)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .height(40.dp)
                                .testTag("btn_top_export_mp4")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("MP4", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                windowInsets = NavigationBarDefaults.windowInsets
            ) {
                NavigationBarItem(
                    selected = selectedTab == StudioTab.VIDEO_AI,
                    onClick = { selectedTab = StudioTab.VIDEO_AI },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.MovieFilter,
                            contentDescription = "Local AI Studio"
                        )
                    },
                    label = { Text(stringResource(R.string.nav_studio)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentPurple,
                        selectedTextColor = AccentPurple,
                        indicatorColor = AccentPurple.copy(alpha = 0.16f)
                    ),
                    modifier = Modifier.testTag("nav_tab_video")
                )
                NavigationBarItem(
                    selected = selectedTab == StudioTab.DATASET_LIBRARY,
                    onClick = { selectedTab = StudioTab.DATASET_LIBRARY },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (datasetImages.isNotEmpty()) {
                                    Badge(containerColor = AccentCyan) {
                                        Text("${datasetImages.size}", color = Color(0xFF021B24))
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Dataset Library"
                            )
                        }
                    },
                    label = { Text("الصور والبيانات") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentCyan,
                        selectedTextColor = AccentCyan,
                        indicatorColor = AccentCyan.copy(alpha = 0.16f)
                    ),
                    modifier = Modifier.testTag("nav_tab_dataset")
                )
                NavigationBarItem(
                    selected = selectedTab == StudioTab.THREE_D_STAGE,
                    onClick = { selectedTab = StudioTab.THREE_D_STAGE },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ViewInAr,
                            contentDescription = stringResource(R.string.nav_3d_stage)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_3d_stage)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentCyan,
                        selectedTextColor = AccentCyan,
                        indicatorColor = AccentCyan.copy(alpha = 0.16f)
                    ),
                    modifier = Modifier.testTag("nav_tab_3d")
                )
                NavigationBarItem(
                    selected = selectedTab == StudioTab.MODEL_IMPORTER,
                    onClick = { selectedTab = StudioTab.MODEL_IMPORTER },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (importedModels.isNotEmpty()) {
                                    Badge(containerColor = AccentEmerald) {
                                        Text("${importedModels.size}", color = Color(0xFF022C22))
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = "Local Model Importer"
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.nav_models)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentEmerald,
                        selectedTextColor = AccentEmerald,
                        indicatorColor = AccentEmerald.copy(alpha = 0.16f)
                    ),
                    modifier = Modifier.testTag("nav_tab_models")
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            StudioTab.DATASET_LIBRARY -> {
                DatasetBatchManagerScreen(
                    datasetImages = datasetImages,
                    searchQuery = datasetSearchQuery,
                    onSearchQueryChange = { viewModel.updateDatasetSearchQuery(it) },
                    onAddBatchImages = { uris, title, desc -> viewModel.addBatchDatasetImages(uris, title, desc) },
                    onDeleteImage = { viewModel.deleteDatasetImage(it) },
                    onClearDataset = { viewModel.clearDataset() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            StudioTab.THREE_D_STAGE -> {
                ThreeDViewportScreen(
                    generatedActors = generatedActorSprites,
                    characterBackendState = characterBackendState,
                    importedModels = importedModels,
                    isDarkTheme = isDarkTheme,
                    onNavigateToStudio = { selectedTab = StudioTab.VIDEO_AI },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            StudioTab.MODEL_IMPORTER -> {
                ModelImporterScreen(
                    importedModels = importedModels,
                    statusMessage = importerStatusMessage,
                    onImportModelUri = { role, uri, endpoint ->
                        viewModel.importModelFromUri(role, uri, endpoint)
                    },
                    onActivateModel = { viewModel.setActiveModel(it) },
                    onUpdateEndpoint = { id, url -> viewModel.updateModelEndpoint(id, url) },
                    onTestEndpoint = { viewModel.testLocalLoopbackEndpoint(it) },
                    onDeleteModel = { viewModel.deleteModel(it) },
                    onDismissStatus = { viewModel.clearImporterStatus() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
            StudioTab.VIDEO_AI -> {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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
                            .weight(1.15f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        VideoPreviewStage(
                            uiState = uiState,
                            activeProject = activeProject,
                            selectedAspectRatio = selectedAspectRatio,
                            currentTimeSeconds = currentTimeSeconds,
                            isPlaying = isPlaying,
                            isVoiceoverEnabled = isVoiceoverEnabled,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onSeek = { viewModel.seekTo(it) },
                            onToggleVoiceover = { viewModel.toggleVoiceover() }
                        )

                        activeProject?.let { proj ->
                            StoryboardTimeline(
                                project = proj,
                                currentTimeSeconds = currentTimeSeconds,
                                onJumpToScene = { viewModel.jumpToScene(it) },
                                onEditScene = { viewModel.openSceneEditor(it) }
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.95f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ErrorBannerIfNeeded(
                            uiState = uiState,
                            onRetry = { viewModel.generateVideo() },
                            onSwitchToOffline = { viewModel.switchToOfflineModeAndGenerate() }
                        )

                        PromptDirectorCard(
                            promptText = promptText,
                            onPromptChange = { viewModel.updatePrompt(it) },
                            selectedMode = selectedMode,
                            onSelectMode = { viewModel.selectGenerationMode(it) },
                            selectedStyle = selectedStyle,
                            onSelectStyle = { viewModel.selectStyle(it) },
                            selectedAspectRatio = selectedAspectRatio,
                            onSelectAspectRatio = { viewModel.selectAspectRatio(it) },
                            hardwareProfile = hardwareProfile,
                            isGenerating = isGenerating,
                            onGenerateClick = { viewModel.generateVideo() }
                        )

                        LocalTeamPipelineStrip(
                            importedModels = importedModels,
                            pipelineStage = pipelineStage,
                            engineSourceLabel = engineSourceLabel,
                            selectedMode = selectedMode
                        )

                        if (generatedActorSprites.isNotEmpty()) {
                            CharacterSpriteHandoffCard(actors = generatedActorSprites)
                        }

                        GeminiCharacterBackendCard(
                            characterPrompt = characterPromptInput,
                            onCharacterPromptChange = { viewModel.updateCharacterPromptInput(it) },
                            useGeminiInVideoPipeline = useGeminiCharacterBackend,
                            onToggleGeminiInPipeline = { viewModel.toggleGeminiCharacterBackend(it) },
                            backendState = characterBackendState,
                            onSendCharacterPrompt = { viewModel.sendCharacterPromptToGemini() },
                            onUseCharacterInStory = { desc ->
                                viewModel.updatePrompt(desc)
                            }
                        )

                        if (orchestratorJson.isNotBlank()) {
                            OrchestratorJsonInspectorCard(
                                jsonText = orchestratorJson,
                                onJsonChange = { viewModel.updateOrchestratorJson(it) },
                                onRunCustomJson = { viewModel.executeCustomJsonPlan() }
                            )
                        }
                    }
                }
            } else {
                val phoneScrollState = rememberScrollState()
                LaunchedEffect(uiState) {
                    if (uiState is VideoUiState.GeneratingScript || uiState is VideoUiState.Error) {
                        phoneScrollState.animateScrollTo(0)
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(phoneScrollState)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        VideoPreviewStage(
                            uiState = uiState,
                            activeProject = activeProject,
                            selectedAspectRatio = selectedAspectRatio,
                            currentTimeSeconds = currentTimeSeconds,
                            isPlaying = isPlaying,
                            isVoiceoverEnabled = isVoiceoverEnabled,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onSeek = { viewModel.seekTo(it) },
                            onToggleVoiceover = { viewModel.toggleVoiceover() }
                        )

                        ErrorBannerIfNeeded(
                            uiState = uiState,
                            onRetry = { viewModel.generateVideo() },
                            onSwitchToOffline = { viewModel.switchToOfflineModeAndGenerate() }
                        )

                        activeProject?.let { proj ->
                            StoryboardTimeline(
                                project = proj,
                                currentTimeSeconds = currentTimeSeconds,
                                onJumpToScene = { viewModel.jumpToScene(it) },
                                onEditScene = { viewModel.openSceneEditor(it) }
                            )
                        }

                        PromptDirectorCard(
                            promptText = promptText,
                            onPromptChange = { viewModel.updatePrompt(it) },
                            selectedMode = selectedMode,
                            onSelectMode = { viewModel.selectGenerationMode(it) },
                            selectedStyle = selectedStyle,
                            onSelectStyle = { viewModel.selectStyle(it) },
                            selectedAspectRatio = selectedAspectRatio,
                            onSelectAspectRatio = { viewModel.selectAspectRatio(it) },
                            hardwareProfile = hardwareProfile,
                            isGenerating = isGenerating,
                            onGenerateClick = { viewModel.generateVideo() }
                        )

                        GeminiCharacterBackendCard(
                            characterPrompt = characterPromptInput,
                            onCharacterPromptChange = { viewModel.updateCharacterPromptInput(it) },
                            useGeminiInVideoPipeline = useGeminiCharacterBackend,
                            onToggleGeminiInPipeline = { viewModel.toggleGeminiCharacterBackend(it) },
                            backendState = characterBackendState,
                            onSendCharacterPrompt = { viewModel.sendCharacterPromptToGemini() },
                            onUseCharacterInStory = { desc ->
                                viewModel.updatePrompt(desc)
                            }
                        )

                        LocalTeamPipelineStrip(
                            importedModels = importedModels,
                            pipelineStage = pipelineStage,
                            engineSourceLabel = engineSourceLabel,
                            selectedMode = selectedMode
                        )

                        if (generatedActorSprites.isNotEmpty()) {
                            CharacterSpriteHandoffCard(actors = generatedActorSprites)
                        }

                        if (orchestratorJson.isNotBlank()) {
                            OrchestratorJsonInspectorCard(
                                jsonText = orchestratorJson,
                                onJsonChange = { viewModel.updateOrchestratorJson(it) },
                                onRunCustomJson = { viewModel.executeCustomJsonPlan() }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
            }
            }
        }
    }

    // Scene Editor Sheet
    val currentProj = activeProject
    val editIdx = editingSceneIndex
    if (currentProj != null && editIdx != null) {
        val scene = currentProj.scenes.getOrNull(editIdx)
        if (scene != null) {
            SceneEditorSheet(
                sceneIndex = editIdx,
                scene = scene,
                onSave = { title, narration, duration, motion, transition ->
                    viewModel.updateSceneDetails(editIdx, title, narration, duration, motion, transition)
                },
                onDismiss = { viewModel.openSceneEditor(null) }
            )
        }
    }

    // Export MP4 Sheet
    if (showExportSheet && currentProj != null) {
        VideoExportSheet(
            project = currentProj,
            exportState = mp4ExportState,
            onStartExport = { viewModel.exportToMp4() },
            onSaveToGallery = { viewModel.saveMp4ToGallery() },
            onDismiss = { viewModel.setExportSheetVisible(false) }
        )
    }

    // Saved Projects Library Sheet
    if (showSavedSheet) {
        SavedVideosSheet(
            savedProjects = savedProjects,
            onLoadProject = { viewModel.loadSavedProject(it) },
            onDeleteProject = { viewModel.deleteSavedProject(it) },
            onDismiss = { viewModel.setSavedSheetVisible(false) }
        )
    }
}

@Composable
private fun ErrorBannerIfNeeded(
    uiState: VideoUiState,
    onRetry: () -> Unit,
    onSwitchToOffline: () -> Unit
) {
    val err = uiState as? VideoUiState.Error ?: return
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("error_banner")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = err.message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Text("Retry")
                }
                Button(
                    onClick = onSwitchToOffline,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentEmerald,
                        contentColor = Color(0xFF022C22)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("btn_switch_offline")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("قوة الهاتف (بدون نت)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun PromptDirectorCard(
    promptText: String,
    onPromptChange: (String) -> Unit,
    selectedMode: VideoGenerationMode,
    onSelectMode: (VideoGenerationMode) -> Unit,
    selectedStyle: VideoVisualStyle,
    onSelectStyle: (VideoVisualStyle) -> Unit,
    selectedAspectRatio: VideoAspectRatio,
    onSelectAspectRatio: (VideoAspectRatio) -> Unit,
    hardwareProfile: DevicePerformanceProfile,
    isGenerating: Boolean,
    onGenerateClick: () -> Unit
) {
    val activeSpecs = remember(hardwareProfile, selectedAspectRatio) {
        hardwareProfile.resolveRenderSpecs(selectedAspectRatio)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "وصف المشهد أو القصة",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                val isGemini = selectedMode == VideoGenerationMode.GEMINI_CLOUD_BACKEND
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = (if (isGemini) AccentPurple else AccentEmerald).copy(alpha = 0.16f)
                ) {
                    Text(
                        text = if (isGemini) "Gemini 3.8 Flash AI ✨" else "ذكاء محلي • أوفلاين 100%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isGemini) AccentPurple else AccentEmerald,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Prominent 50/50 Engine Switcher right above the prompt box
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoGenerationMode.entries.forEach { mode ->
                    val isSelected = selectedMode == mode
                    val chipColor = if (mode == VideoGenerationMode.GEMINI_CLOUD_BACKEND) AccentPurple else AccentEmerald
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectMode(mode) },
                        label = {
                            Text(
                                text = if (mode == VideoGenerationMode.GEMINI_CLOUD_BACKEND) {
                                    "Gemini AI ✨"
                                } else {
                                    "محلي أوفلاين ⚡"
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_chip_${mode.name.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = chipColor.copy(alpha = 0.22f),
                            selectedLabelColor = chipColor
                        )
                    )
                }
            }

            OutlinedTextField(
                value = promptText,
                onValueChange = onPromptChange,
                placeholder = {
                    Text("مثال: رحلة في الصحراء وقت الغروب مع خيول عربية، أو اكتب فكرة الفيديو بالإنجليزية...")
                },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("prompt_input")
            )

            // Quick Sample Prompts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QUICK_PROMPT_SAMPLES.forEach { sample ->
                    FilterChip(
                        selected = promptText == sample.prompt,
                        onClick = { onPromptChange(sample.prompt) },
                        label = { Text(sample.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCyan.copy(alpha = 0.18f),
                            selectedLabelColor = AccentCyan
                        )
                    )
                }
            }

            // Primary Generate Video CTA (immediately visible above settings)
            Button(
                onClick = onGenerateClick,
                enabled = !isGenerating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("btn_generate_video")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isGenerating) {
                                Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF475569)))
                            } else {
                                Brush.horizontalGradient(listOf(AccentCyan, AccentPurple))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text(
                            text = if (isGenerating) "Directing Scenes..." else "Generate Video • إنشاء الفيديو",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // Hardware Power Adaptive Quality Telemetry Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AccentEmerald.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hardware_power_card")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "Hardware Engine Power",
                                tint = AccentEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = hardwareProfile.tier.titleAr,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = AccentEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentEmerald.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = hardwareProfile.tier.badgeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AccentEmerald,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "دقة الإخراج تلقائياً: ${activeSpecs.widthPx}×${activeSpecs.heightPx} • ${activeSpecs.fps} fps • ${
                            String.format(Locale.US, "%.1f Mbps", activeSpecs.bitrate / 1_000_000f)
                        }",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HardwareSpecPill("${hardwareProfile.cpuCores} أنوية CPU")
                        HardwareSpecPill("${String.format(Locale.US, "%.1f", hardwareProfile.totalRamGb)} GB RAM")
                        HardwareSpecPill("تفاصيل ${String.format(Locale.US, "%.1fx", activeSpecs.detailDensity)}")
                    }
                }
            }

            // Character Animation & Offline Phone Engine Active Banner
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AccentCyan.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(AccentEmerald, CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "تحريك الشخصيات والسينما المحلية ⚡",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan
                            )
                        )
                        Text(
                            text = "صناعة الفيديو وتَمشّي الشخصيات بالكامل على الهاتف بدون إنترنت",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }



            // Visual Style Selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(16.dp))
                Text(
                    text = "Visual Style",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoVisualStyle.entries.forEach { style ->
                    FilterChip(
                        selected = selectedStyle == style,
                        onClick = { onSelectStyle(style) },
                        label = { Text(style.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentPurple.copy(alpha = 0.2f),
                            selectedLabelColor = AccentPurple
                        )
                    )
                }
            }

            // Aspect Ratio Selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.AspectRatio, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                Text(
                    text = "Aspect Ratio",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoAspectRatio.entries.forEach { ratio ->
                    val selected = selectedAspectRatio == ratio
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectAspectRatio(ratio) },
                        label = { Text(ratio.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCyan.copy(alpha = 0.2f),
                            selectedLabelColor = AccentCyan
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun HardwareSpecPill(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun LocalTeamPipelineStrip(
    importedModels: List<LocalAiModelSpec>,
    pipelineStage: PipelineStageState,
    engineSourceLabel: String,
    selectedMode: VideoGenerationMode
) {
    val activeLlm = importedModels.firstOrNull { it.role == LocalModelRole.LLM_ORCHESTRATOR && it.isActive }
    val activeImage = importedModels.firstOrNull { it.role == LocalModelRole.IMAGE_GENERATOR && it.isActive }
    val activeVideo = importedModels.firstOrNull { it.role == LocalModelRole.VIDEO_ENGINE && it.isActive }

    val stageLabel = when (pipelineStage) {
        is PipelineStageState.Idle -> if (selectedMode == VideoGenerationMode.GEMINI_CLOUD_BACKEND) {
            "محرك Gemini 3.8 Flash + Character SDK جاهز للتنفيذ ✨"
        } else {
            "فريق النماذج المحلي جاهز للتنفيذ • 0 خوادم خارجية"
        }
        is PipelineStageState.OrchestratingJson -> "العقل المدبر يبني خطة JSON عبر ${pipelineStage.bridgeName}..."
        is PipelineStageState.GeneratingKeyframes -> "تجهيز المشهد ${pipelineStage.sceneIndex}/${pipelineStage.totalScenes} (${pipelineStage.bridgeName})"
        is PipelineStageState.AssemblingTimeline -> "تجميع التحريك والفيديو عبر ${pipelineStage.bridgeName}"
        is PipelineStageState.Completed -> "اكتمل التنفيذ: ${pipelineStage.engineSummary}"
        is PipelineStageState.Failed -> "تنبيه المسار: ${pipelineStage.reason}"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("local_team_pipeline_strip")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = "Local AI Pipeline",
                    tint = AccentPurple,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stageLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HardwareSpecPill("LLM: ${activeLlm?.displayName ?: "المنسّق المدمج"}")
                HardwareSpecPill("JSON: $engineSourceLabel")
                HardwareSpecPill("Img: ${activeImage?.displayName ?: "مولّد الشخصيات المحلي"}")
                HardwareSpecPill("Vid: ${activeVideo?.displayName ?: "MediaCodec GPU"}")
            }
        }
    }
}

@Composable
private fun CharacterSpriteHandoffCard(
    actors: List<ActorDirective>
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_character_sprite_handoff")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = "Character Sprite Team Bridge",
                    tint = AccentEmerald,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = "شخصيات الفريق المصنوعة محلياً",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "طلب العقل -> صورة مقصوصة PNG من نموذج الصور -> تحريك داخل الفيديو",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                actors.forEach { actor ->
                    val spriteBmp = remember(actor.spritePath) {
                        val path = actor.spritePath
                        if (!path.isNullOrBlank() && File(path).exists()) {
                            BitmapFactory.decodeFile(path)
                        } else {
                            null
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.35f)),
                        modifier = Modifier.widthIn(min = 190.dp, max = 240.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0F172A))
                            ) {
                                if (spriteBmp != null) {
                                    Image(
                                        bitmap = spriteBmp.asImageBitmap(),
                                        contentDescription = "${actor.type} Sprite",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().padding(4.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Face,
                                        contentDescription = null,
                                        tint = AccentCyan,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "${actor.label.ifBlank { actor.type }} • ${actor.action}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = AccentEmerald
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = actor.characterImagePrompt.ifBlank { "Isolated sprite for ${actor.type}" },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrchestratorJsonInspectorCard(
    jsonText: String,
    onJsonChange: (String) -> Unit,
    onRunCustomJson: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_orchestrator_json")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Structured JSON Plan",
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "تعليمات العقل المدبر (Structured JSON)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "يمكنك فحص أو تعديل أوامر البيئة والشخصيات والحركة وإعادة تشغيلها",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse JSON" else "Expand JSON",
                    tint = AccentCyan
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = onJsonChange,
                        minLines = 6,
                        maxLines = 14,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_orchestrator_json")
                    )

                    Button(
                        onClick = onRunCustomJson,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentEmerald,
                            contentColor = Color(0xFF022C22)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_run_custom_json")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تشغيل خطة JSON مباشرة في المحرك", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun GeminiCharacterBackendCard(
    characterPrompt: String,
    onCharacterPromptChange: (String) -> Unit,
    useGeminiInVideoPipeline: Boolean,
    onToggleGeminiInPipeline: (Boolean) -> Unit,
    backendState: CharacterBackendUiState,
    onSendCharacterPrompt: () -> Unit,
    onUseCharacterInStory: (String) -> Unit
) {
    val isLoading = backendState is CharacterBackendUiState.Loading

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, AccentPurple.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_gemini_character_backend")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Gemini Character SDK",
                        tint = AccentPurple,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "Gemini Character SDK (Firebase AI)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "توليد الشخصيات عبر Gemini 3.8 + Nano Banana 2.1",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
                Switch(
                    checked = useGeminiInVideoPipeline,
                    onCheckedChange = onToggleGeminiInPipeline,
                    modifier = Modifier.testTag("switch_gemini_character_backend")
                )
            }

            OutlinedTextField(
                value = characterPrompt,
                onValueChange = onCharacterPromptChange,
                placeholder = {
                    Text("وصف الشخصية: مثلاً فارس فضائي بدرع أزرق لامع يحمل مصباحاً")
                },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPurple,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_gemini_character_prompt")
            )

            Button(
                onClick = onSendCharacterPrompt,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPurple,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_send_character_prompt")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generating Character (30-60s)...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إرسال وصف الشخصية إلى Gemini", fontWeight = FontWeight.Bold)
                }
            }

            when (backendState) {
                is CharacterBackendUiState.Idle -> Unit
                is CharacterBackendUiState.Loading -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AccentPurple.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = backendState.stageMessage,
                            style = MaterialTheme.typography.bodySmall.copy(color = AccentPurple),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                is CharacterBackendUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_character_error")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = backendState.message,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.error
                                )
                            )
                        }
                    }
                }
                is CharacterBackendUiState.Success -> {
                    val result = backendState.result
                    val bp = result.blueprint
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AccentEmerald.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_character_result")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0F172A))
                                ) {
                                    Image(
                                        bitmap = result.bitmap.asImageBitmap(),
                                        contentDescription = bp.characterName,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().padding(4.dp)
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = "${bp.characterName} (${bp.actorType} • ${bp.action})",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = AccentEmerald
                                        )
                                    )
                                    Text(
                                        text = bp.visualTraitsSummary,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = bp.characterImagePrompt,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            color = AccentCyan
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    onUseCharacterInStory("${bp.characterName}: ${bp.visualTraitsSummary}")
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("استخدام الشخصية في وصف الفيديو")
                            }
                        }
                    }
                }
            }
        }
    }
}



