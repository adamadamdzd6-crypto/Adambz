package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DatasetReferenceEntity
import com.example.data.DatasetRepository
import com.example.data.ModelRepository
import com.example.data.ProjectRepository
import com.example.data.SavedProject
import com.example.model.ActorDirective
import com.example.model.CameraMotion
import com.example.model.LocalAiModelSpec
import com.example.model.LocalModelRole
import com.example.model.PipelineStageState
import com.example.model.SceneBlueprint
import com.example.model.SceneTransition
import com.example.model.VideoAspectRatio
import com.example.model.VideoGenerationMode
import com.example.model.VideoProject
import com.example.model.VideoScene
import com.example.model.VideoUiState
import com.example.model.VideoVisualStyle
import com.example.service.CharacterBackendUiState
import com.example.service.CinemaFrameRenderer
import com.example.service.DevicePerformanceProfile
import com.example.service.GeminiCharacterBackend
import com.example.service.HardwareTier
import com.example.service.LocalModelInspector
import com.example.service.LocalOrchestratorBridge
import com.example.service.MediaPipelineEngine
import com.example.service.Mp4VideoExporter
import com.example.service.PhoneHardwareProfiler
import com.example.service.SpriteCutoutProcessor
import com.example.service.VoiceoverManager
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "StudioViewModel"

sealed interface Mp4ExportState {
    data object Idle : Mp4ExportState
    data class Encoding(val progress: Float) : Mp4ExportState
    data class Ready(val file: File, val savedToGalleryUri: Uri? = null) : Mp4ExportState
    data class Error(val message: String) : Mp4ExportState
}

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProjectRepository
    private val modelRepository: ModelRepository
    private val datasetRepository: DatasetRepository
    private val voiceoverManager = VoiceoverManager(application)

    private val _hardwareProfile = MutableStateFlow(
        DevicePerformanceProfile(
            tier = HardwareTier.BALANCED_720P,
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            totalRamGb = 4.0f,
            encoderName = "Device AVC",
            maxSupportedWidth = 1920,
            maxSupportedHeight = 1080,
            recommendedFps = 30,
            recommendedBitrate = 5_000_000
        )
    )
    val hardwareProfile: StateFlow<DevicePerformanceProfile> = _hardwareProfile.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ProjectRepository(db.projectDao())
        modelRepository = ModelRepository(db.modelDao())
        datasetRepository = DatasetRepository(db.datasetDao())
        viewModelScope.launch(Dispatchers.Default) {
            _hardwareProfile.value = PhoneHardwareProfiler.profileDevice(application)
        }
    }

    val savedProjects: StateFlow<List<SavedProject>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val importedModels: StateFlow<List<LocalAiModelSpec>> = modelRepository.allModels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _datasetSearchQuery = MutableStateFlow("")
    val datasetSearchQuery: StateFlow<String> = _datasetSearchQuery.asStateFlow()

    val datasetImages: StateFlow<List<DatasetReferenceEntity>> = datasetRepository.allDatasetImages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _pipelineStage = MutableStateFlow<PipelineStageState>(PipelineStageState.Idle)
    val pipelineStage: StateFlow<PipelineStageState> = _pipelineStage.asStateFlow()

    private val _orchestratorJson = MutableStateFlow("")
    val orchestratorJson: StateFlow<String> = _orchestratorJson.asStateFlow()

    private val _engineSourceLabel = MutableStateFlow("On-Device Semantic JSON Orchestrator")
    val engineSourceLabel: StateFlow<String> = _engineSourceLabel.asStateFlow()

    private val _generatedActorSprites = MutableStateFlow<List<ActorDirective>>(emptyList())
    val generatedActorSprites: StateFlow<List<ActorDirective>> = _generatedActorSprites.asStateFlow()

    private val _importerStatusMessage = MutableStateFlow<String?>(null)
    val importerStatusMessage: StateFlow<String?> = _importerStatusMessage.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    // Prompt & Configuration
    private val _promptText = MutableStateFlow("")
    val promptText: StateFlow<String> = _promptText.asStateFlow()

    private val _selectedStyle = MutableStateFlow(VideoVisualStyle.CINEMATIC_35MM)
    val selectedStyle: StateFlow<VideoVisualStyle> = _selectedStyle.asStateFlow()

    private val _selectedAspectRatio = MutableStateFlow(VideoAspectRatio.LANDSCAPE_16_9)
    val selectedAspectRatio: StateFlow<VideoAspectRatio> = _selectedAspectRatio.asStateFlow()

    private val _selectedMode = MutableStateFlow(VideoGenerationMode.ON_DEVICE_OFFLINE)
    val selectedMode: StateFlow<VideoGenerationMode> = _selectedMode.asStateFlow()

    // Gemini AI Character Backend State (Firebase AI Logic SDK)
    private val _characterPromptInput = MutableStateFlow("")
    val characterPromptInput: StateFlow<String> = _characterPromptInput.asStateFlow()

    private val _characterBackendState =
        MutableStateFlow<CharacterBackendUiState>(CharacterBackendUiState.Idle)
    val characterBackendState: StateFlow<CharacterBackendUiState> =
        _characterBackendState.asStateFlow()

    private val _useGeminiCharacterBackend = MutableStateFlow(false)
    val useGeminiCharacterBackend: StateFlow<Boolean> = _useGeminiCharacterBackend.asStateFlow()

    // Main AI & Project State
    private val _uiState = MutableStateFlow<VideoUiState>(VideoUiState.Idle)
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    private val _activeProject = MutableStateFlow<VideoProject?>(null)
    val activeProject: StateFlow<VideoProject?> = _activeProject.asStateFlow()

    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTimeSeconds = MutableStateFlow(0f)
    val currentTimeSeconds: StateFlow<Float> = _currentTimeSeconds.asStateFlow()

    private val _isVoiceoverEnabled = MutableStateFlow(true)
    val isVoiceoverEnabled: StateFlow<Boolean> = _isVoiceoverEnabled.asStateFlow()

    // Sheets / Modals
    private val _showExportSheet = MutableStateFlow(false)
    val showExportSheet: StateFlow<Boolean> = _showExportSheet.asStateFlow()

    private val _showSavedSheet = MutableStateFlow(false)
    val showSavedSheet: StateFlow<Boolean> = _showSavedSheet.asStateFlow()

    private val _editingSceneIndex = MutableStateFlow<Int?>(null)
    val editingSceneIndex: StateFlow<Int?> = _editingSceneIndex.asStateFlow()

    private val _mp4ExportState = MutableStateFlow<Mp4ExportState>(Mp4ExportState.Idle)
    val mp4ExportState: StateFlow<Mp4ExportState> = _mp4ExportState.asStateFlow()

    private var playbackJob: Job? = null
    private var lastSpokenSceneIndex = -1

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun updatePrompt(value: String) {
        _promptText.value = value
    }

    fun updateDatasetSearchQuery(query: String) {
        _datasetSearchQuery.value = query
    }

    fun addBatchDatasetImages(uris: List<Uri>, defaultTitle: String, defaultDesc: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val entities = uris.mapIndexed { index, uri ->
                val id = "ds_${System.currentTimeMillis()}_$index"
                try {
                    context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
                DatasetReferenceEntity(
                    id = id,
                    title = if (uris.size == 1) defaultTitle else "$defaultTitle #${index + 1}",
                    description = defaultDesc,
                    imageUri = uri.toString(),
                    category = "Dataset",
                    addedAt = System.currentTimeMillis()
                )
            }
            datasetRepository.addImagesBatch(entities)
        }
    }

    fun deleteDatasetImage(entity: DatasetReferenceEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            datasetRepository.deleteImage(entity)
        }
    }

    fun clearDataset() {
        viewModelScope.launch(Dispatchers.IO) {
            datasetRepository.clearDataset()
        }
    }

    fun selectStyle(style: VideoVisualStyle) {
        _selectedStyle.value = style
        _activeProject.value?.let { proj ->
            val updated = proj.copy(style = style)
            _activeProject.value = updated
            _uiState.value = VideoUiState.Ready(updated)
        }
    }

    fun selectAspectRatio(aspectRatio: VideoAspectRatio) {
        _selectedAspectRatio.value = aspectRatio
        _activeProject.value?.let { proj ->
            val specs = _hardwareProfile.value.resolveRenderSpecs(aspectRatio)
            val updated = proj.copy(
                aspectRatio = aspectRatio,
                renderWidthPx = specs.widthPx,
                renderHeightPx = specs.heightPx,
                targetFps = specs.fps,
                targetBitrate = specs.bitrate,
                detailDensity = specs.detailDensity
            )
            _activeProject.value = updated
            _uiState.value = VideoUiState.Ready(updated)
        }
    }

    fun selectGenerationMode(mode: VideoGenerationMode) {
        _selectedMode.value = mode
    }

    fun updateCharacterPromptInput(value: String) {
        _characterPromptInput.value = value
    }

    fun toggleGeminiCharacterBackend(enabled: Boolean) {
        _useGeminiCharacterBackend.value = enabled
        if (enabled) {
            _selectedMode.value = VideoGenerationMode.GEMINI_CLOUD_BACKEND
        }
    }

    /**
     * Sends the character-generation prompt to the Gemini backend via Firebase AI Logic SDK.
     * Surfaces real loading, success, and error states without mock fallbacks.
     */
    fun sendCharacterPromptToGemini() {
        val rawPrompt = _characterPromptInput.value.trim().ifBlank { _promptText.value.trim() }
        if (rawPrompt.isBlank()) {
            _characterBackendState.value = CharacterBackendUiState.Error(
                "Please enter a character-generation prompt before sending to Gemini."
            )
            return
        }

        viewModelScope.launch {
            val app = getApplication<Application>()
            val style = _selectedStyle.value
            _characterBackendState.value = CharacterBackendUiState.Loading(
                "Planning character JSON & synthesizing sprite via Gemini (30-60s)..."
            )
            try {
                val result = GeminiCharacterBackend.sendCharacterPromptToBackend(
                    context = app,
                    userPrompt = rawPrompt,
                    style = style
                )
                _characterBackendState.value = CharacterBackendUiState.Success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _characterBackendState.value = CharacterBackendUiState.Error(
                    GeminiCharacterBackend.formatBackendError(e)
                )
            }
        }
    }

    fun setExportSheetVisible(visible: Boolean) {
        _showExportSheet.value = visible
        if (!visible && _mp4ExportState.value is Mp4ExportState.Error) {
            _mp4ExportState.value = Mp4ExportState.Idle
        }
    }

    fun setSavedSheetVisible(visible: Boolean) {
        _showSavedSheet.value = visible
    }

    fun openSceneEditor(index: Int?) {
        _editingSceneIndex.value = index
    }

    fun toggleVoiceover() {
        val next = !_isVoiceoverEnabled.value
        _isVoiceoverEnabled.value = next
        if (!next) {
            voiceoverManager.stop()
        }
    }

    // --- Local AI Studio: Model Importer Actions ---
    fun importModelFromUri(role: LocalModelRole, uri: Uri, localEndpointUrl: String = "") {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val spec = withContext(Dispatchers.IO) {
                LocalModelInspector.inspectUriAndPersistPermission(
                    context = app,
                    uri = uri,
                    role = role,
                    localEndpointUrl = localEndpointUrl
                )
            }
            modelRepository.saveAndActivate(spec)
            _importerStatusMessage.value = "تم استيراد النموذج: ${spec.displayName} (${spec.binaryHeaderSummary})"
        }
    }

    fun setActiveModel(spec: LocalAiModelSpec) {
        viewModelScope.launch {
            modelRepository.setActive(spec)
            _importerStatusMessage.value = "تم تفعيل النموذج في الفريق: ${spec.displayName}"
        }
    }

    fun updateModelEndpoint(modelId: String, endpointUrl: String) {
        viewModelScope.launch {
            if (endpointUrl.isNotBlank() && !LocalModelInspector.isLocalLoopbackUrl(endpointUrl)) {
                _importerStatusMessage.value = "مرفوض: يُسمح فقط بالعناوين المحلية 127.0.0.1 أو localhost بدون أي خوادم خارجية"
                return@launch
            }
            modelRepository.updateEndpoint(modelId, endpointUrl)
            _importerStatusMessage.value = if (endpointUrl.isBlank()) {
                "تم مسح عنوان الربط المحلي — يعمل المنسّق المدمج تلقائياً"
            } else {
                "تم حفظ عنوان الربط المحلي: $endpointUrl"
            }
        }
    }

    fun testLocalLoopbackEndpoint(endpointUrl: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                LocalModelInspector.pingLocalLoopbackEndpoint(endpointUrl)
            }
            _importerStatusMessage.value = result.message
        }
    }

    fun deleteModel(spec: LocalAiModelSpec) {
        viewModelScope.launch {
            modelRepository.deleteModel(spec)
            _importerStatusMessage.value = "تمت إزالة النموذج: ${spec.displayName}"
        }
    }

    fun clearImporterStatus() {
        _importerStatusMessage.value = null
    }

    fun updateOrchestratorJson(rawJson: String) {
        _orchestratorJson.value = rawJson
    }

    /**
     * 100% Local Offline AI Team Flow:
     * Prompt -> LLM Orchestrator (Structured JSON) -> Local Image Model (PNG Sprite) -> Motion & Video Engine.
     */
    fun generateVideo() {
        val prompt = _promptText.value.trim()
        if (prompt.isBlank()) {
            _uiState.value = VideoUiState.Error("يرجى كتابة وصف الفيديو أولاً.")
            return
        }

        pausePlayback()
        _mp4ExportState.value = Mp4ExportState.Idle

        viewModelScope.launch {
            val app = getApplication<Application>()
            val style = _selectedStyle.value
            val aspectRatio = _selectedAspectRatio.value
            val mode = _selectedMode.value

            try {
                _uiState.value = VideoUiState.GeneratingScript
                val activeLlm = modelRepository.getActiveForRole(LocalModelRole.LLM_ORCHESTRATOR)
                val activeImageModel = modelRepository.getActiveForRole(LocalModelRole.IMAGE_GENERATOR)
                val activeVideoModel = modelRepository.getActiveForRole(LocalModelRole.VIDEO_ENGINE)

                _pipelineStage.value = PipelineStageState.OrchestratingJson(
                    bridgeName = if (mode == VideoGenerationMode.GEMINI_CLOUD_BACKEND) {
                        "Gemini 3.8 Flash AI Director"
                    } else {
                        activeLlm?.displayName ?: "On-Device Semantic JSON Orchestrator"
                    }
                )

                // Step 1: The LLM Orchestrator -> Structured JSON
                val basePlan = if (mode == VideoGenerationMode.GEMINI_CLOUD_BACKEND) {
                    GeminiCharacterBackend.orchestrateVideoPlanViaGemini(
                        userPrompt = prompt,
                        style = style
                    )
                } else {
                    withContext(Dispatchers.IO) {
                        LocalOrchestratorBridge.orchestratePrompt(
                            prompt = prompt,
                            style = style,
                            activeLlmModel = activeLlm
                        )
                    }
                }

                // Optional Gemini Nano Banana 2.1 character sprite synthesis when explicitly toggled ON
                val plan = if (_useGeminiCharacterBackend.value) {
                    _pipelineStage.value = PipelineStageState.GeneratingKeyframes(
                        sceneIndex = 0,
                        totalScenes = basePlan.scenes.size,
                        bridgeName = "Gemini Nano Banana 2.1"
                    )
                    val geminiSpriteCache = mutableMapOf<String, String>()
                    val enrichedScenes = basePlan.scenes.mapIndexed { idx, sceneBp ->
                        _uiState.value = VideoUiState.GeneratingFrames(idx, basePlan.scenes.size)
                        val updatedActors = sceneBp.actors.map { actor ->
                            val cacheKey = "${actor.type}_${actor.primaryColorHex}"
                            val cachedPath = geminiSpriteCache[cacheKey]
                            if (cachedPath != null) {
                                actor.copy(spritePath = cachedPath)
                            } else {
                                val rawBmp = GeminiCharacterBackend.generateCharacterImage(
                                    characterImagePrompt = actor.characterImagePrompt.ifBlank {
                                        "Isolated full-body ${actor.type.lowercase()} character sprite on pure white background, ${style.promptDirective}"
                                    }
                                )
                                val cutout = SpriteCutoutProcessor.extractTransparentCharacterSprite(rawBmp)
                                if (cutout !== rawBmp && !rawBmp.isRecycled) rawBmp.recycle()
                                val spriteDir = File(app.filesDir, "sprites").apply { mkdirs() }
                                val outFile = File(
                                    spriteDir,
                                    "gemini_scene_char_${actor.type.lowercase()}_${System.currentTimeMillis()}.png"
                                )
                                withContext(Dispatchers.IO) {
                                    java.io.FileOutputStream(outFile).use { out ->
                                        cutout.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                                    }
                                    if (!cutout.isRecycled) cutout.recycle()
                                }
                                geminiSpriteCache[cacheKey] = outFile.absolutePath
                                actor.copy(spritePath = outFile.absolutePath)
                            }
                        }
                        sceneBp.copy(actors = updatedActors)
                    }
                    basePlan.copy(
                        scenes = enrichedScenes,
                        orchestratorSource = "${basePlan.orchestratorSource} + Gemini Backend"
                    )
                } else {
                    basePlan
                }

                _orchestratorJson.value = plan.toPrettyJson()
                _engineSourceLabel.value = plan.orchestratorSource

                // Step 2: Media Pipeline Engine -> Character Sprites & Scene Blueprint Execution
                val hwProfile = withContext(Dispatchers.Default) {
                    PhoneHardwareProfiler.profileDevice(app).also { _hardwareProfile.value = it }
                }

                val rawProject = withContext(Dispatchers.IO) {
                    MediaPipelineEngine.executePlanToVideoProject(
                        context = app,
                        plan = plan,
                        aspectRatio = aspectRatio,
                        hardwareProfile = hwProfile,
                        activeImageModel = activeImageModel,
                        activeVideoModel = activeVideoModel,
                        onSceneImageProgress = { done, total ->
                            _pipelineStage.value = PipelineStageState.GeneratingKeyframes(
                                sceneIndex = done,
                                totalScenes = total,
                                bridgeName = activeImageModel?.displayName ?: "On-Device Local Sprite AI"
                            )
                            _uiState.value = VideoUiState.GeneratingFrames(done, total)
                        }
                    )
                }
                val project = rawProject.copy(generationMode = mode)

                _pipelineStage.value = PipelineStageState.AssemblingTimeline(
                    bridgeName = activeVideoModel?.displayName ?: "Device MediaCodec + Canvas"
                )
                repository.saveProject(SavedProject.fromVideoProject(project))

                _activeProject.value = project
                refreshGeneratedActorSprites(project)
                _currentTimeSeconds.value = 0f
                _uiState.value = VideoUiState.Ready(project)
                _pipelineStage.value = PipelineStageState.Completed(plan.orchestratorSource)
                startPlayback()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Local AI orchestration failed", e)
                val msg = e.localizedMessage ?: "Local pipeline execution failed."
                _pipelineStage.value = PipelineStageState.Failed(msg)
                _uiState.value = VideoUiState.Error(
                    message = msg,
                    isImageBillingOrQuotaIssue = false
                )
            }
        }
    }

    /**
     * Executes user-edited Structured JSON directly through MediaPipelineEngine,
     * bypassing the LLM text parser so users have full deterministic control over actors & scenes.
     */
    fun executeCustomJsonPlan() {
        val rawJson = _orchestratorJson.value.trim()
        if (rawJson.isBlank()) {
            _uiState.value = VideoUiState.Error("لا توجد خطة JSON لتشغيلها.")
            return
        }

        val parsedResult = LocalOrchestratorBridge.parseEditedJsonPlan(
            rawJson = rawJson,
            fallbackPrompt = _promptText.value,
            fallbackStyle = _selectedStyle.value
        )
        val plan = parsedResult.getOrElse { err ->
            _uiState.value = VideoUiState.Error("خطأ في صيغة JSON: ${err.localizedMessage}")
            return
        }

        pausePlayback()
        _mp4ExportState.value = Mp4ExportState.Idle

        viewModelScope.launch {
            val app = getApplication<Application>()
            val aspectRatio = _selectedAspectRatio.value
            val activeImageModel = modelRepository.getActiveForRole(LocalModelRole.IMAGE_GENERATOR)
            val activeVideoModel = modelRepository.getActiveForRole(LocalModelRole.VIDEO_ENGINE)

            _pipelineStage.value = PipelineStageState.GeneratingKeyframes(0, plan.scenes.size, "Direct JSON Pipeline")
            val hwProfile = withContext(Dispatchers.Default) {
                PhoneHardwareProfiler.profileDevice(app).also { _hardwareProfile.value = it }
            }
            val project = withContext(Dispatchers.IO) {
                MediaPipelineEngine.executePlanToVideoProject(
                    context = app,
                    plan = plan,
                    aspectRatio = aspectRatio,
                    hardwareProfile = hwProfile,
                    activeImageModel = activeImageModel,
                    activeVideoModel = activeVideoModel
                )
            }
            repository.saveProject(SavedProject.fromVideoProject(project))
            _engineSourceLabel.value = plan.orchestratorSource
            _activeProject.value = project
            refreshGeneratedActorSprites(project)
            _currentTimeSeconds.value = 0f
            _uiState.value = VideoUiState.Ready(project)
            _pipelineStage.value = PipelineStageState.Completed("Edited JSON Direct Pipeline")
            startPlayback()
        }
    }

    fun switchToOfflineModeAndGenerate() {
        _useGeminiCharacterBackend.value = false
        _selectedMode.value = VideoGenerationMode.ON_DEVICE_OFFLINE
        generateVideo()
    }

    fun updateSceneDetails(
        sceneIndex: Int,
        title: String,
        narration: String,
        durationSeconds: Float,
        cameraMotion: CameraMotion,
        transition: SceneTransition
    ) {
        val project = _activeProject.value ?: return
        if (sceneIndex !in project.scenes.indices) return

        val updatedScenes = project.scenes.toMutableList()
        updatedScenes[sceneIndex] = updatedScenes[sceneIndex].copy(
            title = title,
            narrationSubtitle = narration,
            durationSeconds = durationSeconds.coerceIn(2f, 10f),
            cameraMotion = cameraMotion,
            transition = transition
        )
        val updated = project.copy(scenes = updatedScenes)
        _activeProject.value = updated
        _uiState.value = VideoUiState.Ready(updated)
        viewModelScope.launch {
            repository.saveProject(SavedProject.fromVideoProject(updated))
        }
    }

    // Playback Controls
    fun togglePlayPause() {
        if (_isPlaying.value) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun startPlayback() {
        val project = _activeProject.value ?: return
        if (project.scenes.isEmpty()) return

        if (_currentTimeSeconds.value >= project.totalDurationSeconds - 0.05f) {
            _currentTimeSeconds.value = 0f
        }
        lastSpokenSceneIndex = -1
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val tickMs = 33L // ~30fps smooth UI scrubbing
            var lastTick = System.currentTimeMillis()
            while (isActive && _isPlaying.value) {
                val activeProj = _activeProject.value ?: break
                val now = System.currentTimeMillis()
                val dt = (now - lastTick) / 1000f
                lastTick = now

                val nextTime = _currentTimeSeconds.value + dt
                val total = activeProj.totalDurationSeconds

                val cursor = CinemaFrameRenderer.resolveCursor(activeProj, nextTime)
                if (_isVoiceoverEnabled.value && cursor.sceneIndex != lastSpokenSceneIndex) {
                    lastSpokenSceneIndex = cursor.sceneIndex
                    voiceoverManager.speak(cursor.scene.narrationSubtitle)
                }

                if (nextTime >= total) {
                    _currentTimeSeconds.value = total
                    pausePlayback()
                    break
                } else {
                    _currentTimeSeconds.value = nextTime
                }
                delay(tickMs)
            }
        }
    }

    fun pausePlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
        voiceoverManager.stop()
    }

    fun seekTo(timeSeconds: Float) {
        val project = _activeProject.value ?: return
        _currentTimeSeconds.value = timeSeconds.coerceIn(0f, project.totalDurationSeconds)
        lastSpokenSceneIndex = -1
    }

    fun jumpToScene(sceneIndex: Int) {
        val project = _activeProject.value ?: return
        if (sceneIndex !in project.scenes.indices) return
        var acc = 0f
        for (i in 0 until sceneIndex) {
            acc += project.scenes[i].durationSeconds
        }
        _currentTimeSeconds.value = acc
        lastSpokenSceneIndex = -1
        if (_isPlaying.value && _isVoiceoverEnabled.value) {
            voiceoverManager.speak(project.scenes[sceneIndex].narrationSubtitle)
            lastSpokenSceneIndex = sceneIndex
        }
    }

    // Real MP4 Hardware Export
    fun exportToMp4() {
        val project = _activeProject.value ?: return
        pausePlayback()
        viewModelScope.launch {
            _mp4ExportState.value = Mp4ExportState.Encoding(0.02f)
            try {
                val app = getApplication<Application>()
                val mp4File = Mp4VideoExporter.exportProjectToMp4(
                    context = app,
                    project = project,
                    onProgress = { p ->
                        _mp4ExportState.value = Mp4ExportState.Encoding(p)
                    }
                )
                val updatedProj = project.copy(exportedMp4Path = mp4File.absolutePath)
                _activeProject.value = updatedProj
                _uiState.value = VideoUiState.Ready(updatedProj)
                repository.saveProject(SavedProject.fromVideoProject(updatedProj))

                _mp4ExportState.value = Mp4ExportState.Ready(mp4File)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "MP4 export failed", e)
                _mp4ExportState.value = Mp4ExportState.Error(
                    e.localizedMessage ?: "Failed to encode MP4 video."
                )
            }
        }
    }

    fun saveMp4ToGallery() {
        val readyState = _mp4ExportState.value as? Mp4ExportState.Ready ?: return
        val project = _activeProject.value ?: return
        viewModelScope.launch {
            val uri = Mp4VideoExporter.saveMp4ToGallery(
                context = getApplication(),
                mp4File = readyState.file,
                videoTitle = project.title
            )
            if (uri != null) {
                _mp4ExportState.value = readyState.copy(savedToGalleryUri = uri)
            }
        }
    }

    // Saved Projects Management
    fun loadSavedProject(entity: SavedProject) {
        pausePlayback()
        val rawProject = entity.toVideoProject()
        val specs = _hardwareProfile.value.resolveRenderSpecs(rawProject.aspectRatio)
        val project = rawProject.copy(
            renderWidthPx = specs.widthPx,
            renderHeightPx = specs.heightPx,
            targetFps = specs.fps,
            targetBitrate = specs.bitrate,
            detailDensity = specs.detailDensity
        )
        _promptText.value = project.prompt
        _selectedStyle.value = project.style
        _selectedAspectRatio.value = project.aspectRatio
        _selectedMode.value = project.generationMode
        _activeProject.value = project
        refreshGeneratedActorSprites(project)
        _currentTimeSeconds.value = 0f
        _uiState.value = VideoUiState.Ready(project)
        _showSavedSheet.value = false
        _mp4ExportState.value = project.exportedMp4Path
            ?.let { File(it) }
            ?.takeIf { it.exists() }
            ?.let { Mp4ExportState.Ready(it) }
            ?: Mp4ExportState.Idle
    }

    private fun refreshGeneratedActorSprites(project: VideoProject) {
        val seen = LinkedHashMap<String, ActorDirective>()
        project.scenes.forEachIndexed { idx, scene ->
            val raw = scene.sceneBlueprintJson ?: return@forEachIndexed
            try {
                val bp = SceneBlueprint.fromJson(JSONObject(raw), idx)
                bp.actors.forEach { actor ->
                    val key = "${actor.type}:${actor.spritePath ?: actor.id}"
                    if (!seen.containsKey(key)) {
                        seen[key] = actor
                    }
                }
            } catch (_: Exception) {
            }
        }
        _generatedActorSprites.value = seen.values.toList()
    }

    fun deleteSavedProject(entity: SavedProject) {
        viewModelScope.launch {
            repository.deleteProject(entity)
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        voiceoverManager.shutdown()
    }
}
