package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.SavedProject
import com.example.model.ActorDirective
import com.example.model.CameraMotion
import com.example.model.SceneTransition
import com.example.model.VideoAspectRatio
import com.example.model.VideoGenerationMode
import com.example.model.VideoProject
import com.example.model.VideoScene
import com.example.model.VideoVisualStyle
import com.example.model.VisualMotif
import com.example.model.OrchestrationPlan
import com.example.service.CinemaFrameRenderer
import com.example.service.DevicePerformanceProfile
import com.example.service.HardwareTier
import com.example.service.LocalModelInspector
import com.example.service.LocalOrchestratorBridge
import com.example.service.MediaPipelineEngine
import com.example.service.OfflineVideoDirector
import com.example.service.PhoneHardwareProfiler
import com.example.service.CharacterBackendUiState
import com.example.service.SpriteCutoutProcessor
import com.example.ui.components.AiGenerationProgressIndicator
import com.example.ui.components.ThreeDViewportScreen
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.example.ui.theme.CinePromptTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `test reusable circular progress indicator renders indeterminate and determinate states`() {
        composeTestRule.setContent {
            CinePromptTheme {
                AiGenerationProgressIndicator(
                    title = "Synthesizing 3D Mesh...",
                    subtitle = "Waiting for Gemini AI response",
                    progress = 0.65f,
                    phaseLabel = "3D GENERATION"
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ai_generation_progress").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_progress_title").assertTextEquals("Synthesizing 3D Mesh...")
        composeTestRule.onNodeWithTag("ai_progress_percent").assertTextEquals("65%")
        composeTestRule.onNodeWithText("3D GENERATION").assertIsDisplayed()
    }

    @Test
    fun `test 3D rendering surface placeholder layout renders controls and processed model slots`() {
        val sampleActor = ActorDirective(
            id = "knight_01",
            type = "KNIGHT",
            label = "Space Knight",
            action = "WALK",
            primaryColorHex = "#06B6D4",
            characterImagePrompt = "Futuristic glowing armor rig"
        )
        composeTestRule.setContent {
            CinePromptTheme {
                ThreeDViewportScreen(
                    generatedActors = listOf(sampleActor),
                    characterBackendState = CharacterBackendUiState.Idle,
                    importedModels = emptyList(),
                    isDarkTheme = true,
                    onNavigateToStudio = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("three_d_rendering_surface").assertIsDisplayed()
        composeTestRule.onNodeWithTag("three_d_surface_status").assertIsDisplayed()
        composeTestRule.onNodeWithTag("three_d_placeholder_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("three_d_controls_bar").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("three_d_processed_models_panel").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("processed_model_slot_actor_knight_01_0").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CinePrompt", appName)
    }

    @Test
    fun `test timeline cursor resolution across multi-scene project`() {
        val project = sampleProject()

        val cursorStart = CinemaFrameRenderer.resolveCursor(project, 0.5f)
        assertEquals(0, cursorStart.sceneIndex)
        assertEquals("Opening Shot", cursorStart.scene.title)

        val cursorMid = CinemaFrameRenderer.resolveCursor(project, 5.2f)
        assertEquals(1, cursorMid.sceneIndex)
        assertEquals("Climax Motion", cursorMid.scene.title)
        assertTrue(cursorMid.localProgress in 0f..1f)

        val cursorTransition = CinemaFrameRenderer.resolveCursor(project, 3.8f)
        assertEquals(0, cursorTransition.sceneIndex)
        assertNotNull(cursorTransition.nextScene)
        assertTrue(cursorTransition.transitionProgress > 0f)
    }

    @Test
    fun `test cinema frame canvas rendering in kinetic and bitmap modes`() {
        val project = sampleProject()

        // 1. Kinetic Procedural Rendering
        val kineticBitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val kineticCanvas = Canvas(kineticBitmap)
        CinemaFrameRenderer.drawFrameToCanvas(
            canvas = kineticCanvas,
            width = 320,
            height = 180,
            project = project,
            timeSeconds = 1.5f,
            sceneBitmaps = emptyMap()
        )
        assertEquals(320, kineticBitmap.width)
        assertEquals(180, kineticBitmap.height)

        // 2. Ken Burns Bitmap Rendering
        val scenePhoto = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.CYAN)
        }
        val frameBitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val frameCanvas = Canvas(frameBitmap)
        CinemaFrameRenderer.drawFrameToCanvas(
            canvas = frameCanvas,
            width = 320,
            height = 180,
            project = project,
            timeSeconds = 5.0f,
            sceneBitmaps = mapOf("s2" to scenePhoto)
        )
        assertEquals(320, frameBitmap.width)
        scenePhoto.recycle()
        kineticBitmap.recycle()
        frameBitmap.recycle()
    }

    @Test
    fun `test SavedProject serialization roundtrip preserves scenes and settings`() {
        val original = sampleProject()
        val entity = SavedProject.fromVideoProject(original)
        val restored = entity.toVideoProject()

        assertEquals(original.id, restored.id)
        assertEquals(original.title, restored.title)
        assertEquals(original.style, restored.style)
        assertEquals(original.aspectRatio, restored.aspectRatio)
        assertEquals(original.generationMode, restored.generationMode)
        assertEquals(original.scenes.size, restored.scenes.size)
        assertEquals(original.scenes[0].cameraMotion, restored.scenes[0].cameraMotion)
        assertEquals(original.scenes[1].visualMotif, restored.scenes[1].visualMotif)
        assertEquals(original.scenes[1].narrationSubtitle, restored.scenes[1].narrationSubtitle)
    }

    @Test
    fun `test wire enum parsing falls back safely`() {
        assertEquals(CameraMotion.PAN_LEFT, CameraMotion.fromWire("pan_left"))
        assertEquals(CameraMotion.ZOOM_IN, CameraMotion.fromWire("unknown_motion"))
        assertEquals(SceneTransition.SLIDE_LEFT, SceneTransition.fromWire("SLIDE_LEFT"))
        assertEquals(VisualMotif.BOKEH, VisualMotif.fromWire("bokeh"))
    }

    @Test
    fun `test hardware profiler scales resolution fps bitrate and density by device tier`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val runtimeProfile = PhoneHardwareProfiler.profileDevice(context)
        assertTrue(runtimeProfile.cpuCores >= 1)
        assertTrue(runtimeProfile.totalRamGb > 0f)

        val flagship = DevicePerformanceProfile(
            tier = HardwareTier.ULTRA_4K,
            cpuCores = 8,
            totalRamGb = 12f,
            encoderName = "c2.qti.avc.encoder",
            maxSupportedWidth = 3840,
            maxSupportedHeight = 2160,
            recommendedFps = 60,
            recommendedBitrate = 14_000_000
        )
        val entry = DevicePerformanceProfile(
            tier = HardwareTier.ENTRY_540P,
            cpuCores = 4,
            totalRamGb = 2f,
            encoderName = "c2.android.avc.encoder",
            maxSupportedWidth = 1280,
            maxSupportedHeight = 720,
            recommendedFps = 24,
            recommendedBitrate = 2_500_000
        )

        val flagshipLandscape = flagship.resolveRenderSpecs(VideoAspectRatio.LANDSCAPE_16_9)
        val entryLandscape = entry.resolveRenderSpecs(VideoAspectRatio.LANDSCAPE_16_9)
        assertEquals(0, flagshipLandscape.widthPx % 16)
        assertEquals(0, flagshipLandscape.heightPx % 16)
        assertEquals(0, entryLandscape.widthPx % 16)
        assertEquals(0, entryLandscape.heightPx % 16)
        assertTrue(flagshipLandscape.widthPx > entryLandscape.widthPx)
        assertTrue(flagshipLandscape.fps > entryLandscape.fps)
        assertTrue(flagshipLandscape.bitrate > entryLandscape.bitrate)
        assertTrue(flagshipLandscape.detailDensity > entryLandscape.detailDensity)
    }

    @Test
    fun `test offline video director creates bilingual storyboard without internet`() {
        val flagship = DevicePerformanceProfile(
            tier = HardwareTier.HIGH_1080P,
            cpuCores = 8,
            totalRamGb = 8f,
            encoderName = "Hardware AVC",
            maxSupportedWidth = 1920,
            maxSupportedHeight = 1080,
            recommendedFps = 48,
            recommendedBitrate = 8_000_000
        )

        val arProject = OfflineVideoDirector.generateOfflineProject(
            prompt = "فيديو لطفل يضحك تحت أضواء ذهبية",
            style = VideoVisualStyle.CINEMATIC_35MM,
            aspectRatio = VideoAspectRatio.PORTRAIT_9_16,
            hardwareProfile = flagship
        )

        assertEquals(VideoGenerationMode.ON_DEVICE_OFFLINE, arProject.generationMode)
        assertEquals(1, arProject.scenes.size)
        assertEquals(6.5f, arProject.scenes.first().durationSeconds, 0.01f)
        assertEquals(1088, arProject.renderWidthPx)
        assertEquals(1920, arProject.renderHeightPx)
        assertEquals(48, arProject.targetFps)
        assertTrue(arProject.detailDensity > 1.0f)
        assertTrue(arProject.synopsis.contains("1088x1920"))

        val enProject = OfflineVideoDirector.generateOfflineProject(
            prompt = "Neon cyberpunk rain chase in Tokyo",
            style = VideoVisualStyle.CYBERPUNK_NEON,
            aspectRatio = VideoAspectRatio.SQUARE_1_1,
            hardwareProfile = flagship
        )
        assertEquals(1088, enProject.renderWidthPx)
        assertEquals(1088, enProject.renderHeightPx)
        assertEquals(1, enProject.scenes.size)
    }

    @Test
    fun `test local model inspector parses GGUF v3 header and enforces loopback-only endpoints`() {
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            put('G'.code.toByte())
            put('G'.code.toByte())
            put('U'.code.toByte())
            put('F'.code.toByte())
            putInt(3)        // version 3
            putLong(291L)    // tensor count
            putLong(24L)     // kv metadata count
        }.array()

        val report = LocalModelInspector.parseBinaryHeader(header, "llama-3.2-3b-q4.gguf")
        assertTrue(report.isVerified)
        assertEquals("GGUF", report.format)
        assertTrue(report.summary.contains("v3"))
        assertTrue(report.summary.contains("291 tensors"))

        // Security check: only local loopback URLs allowed
        assertTrue(LocalModelInspector.isLocalLoopbackUrl("http://127.0.0.1:8080"))
        assertTrue(LocalModelInspector.isLocalLoopbackUrl("http://localhost:7860"))
        assertTrue(!LocalModelInspector.isLocalLoopbackUrl("https://api.openai.com/v1"))
        assertTrue(!LocalModelInspector.isLocalLoopbackUrl("http://8.8.8.8:8080"))
    }

    @Test
    fun `test orchestrator bridge builds structured JSON plan without fake repetition`() {
        val plan = LocalOrchestratorBridge.orchestratePrompt(
            prompt = "كلب يتمشى في حديقة خضراء ثم يقع في حفرة",
            style = VideoVisualStyle.PIXAR_3D,
            activeLlmModel = null
        )

        // Exact 2 clauses -> exact 2 non-repetitive sequential scenes
        assertEquals(2, plan.scenes.size)
        val firstScene = plan.scenes.first()
        val lastScene = plan.scenes.last()
        assertEquals("DOG", firstScene.actors.first().type)
        assertEquals("WALK", firstScene.actors.first().action)
        assertEquals("FALL_INTO_PIT", lastScene.actors.first().action)
        assertTrue(lastScene.environment.props.contains("PIT_HOLE"))

        val prettyJson = plan.toPrettyJson()
        val restored = OrchestrationPlan.fromJson(prettyJson)
        assertEquals(plan.scenes.size, restored.scenes.size)
        assertEquals(lastScene.actors.first().action, restored.scenes.last().actors.first().action)

        // Single continuous description must NOT split into 3-5 repetitive fragments
        val horsePlan = LocalOrchestratorBridge.orchestratePrompt(
            prompt = "حصان يجري قرب بيت ونار تحت قوس قزح",
            style = VideoVisualStyle.CINEMATIC_35MM,
            activeLlmModel = null
        )
        assertEquals(1, horsePlan.scenes.size)
        assertEquals("HORSE", horsePlan.scenes.first().actors.first().type)
        assertTrue(horsePlan.scenes.first().environment.props.containsAll(listOf("HOUSE", "FIRE", "RAINBOW")))

        // Arabic comma + sequential delimiter must execute both sequential actions cleanly
        val chasePlan = LocalOrchestratorBridge.orchestratePrompt(
            prompt = "أسد يركض في الصحراء، ثم يقفز فوق جبل",
            style = VideoVisualStyle.CINEMATIC_35MM,
            activeLlmModel = null
        )
        assertEquals(2, chasePlan.scenes.size)
        assertEquals("SAND", chasePlan.scenes.first().environment.groundType)
        assertEquals("RUN", chasePlan.scenes.first().actors.first().action)
        assertEquals("JUMP", chasePlan.scenes.last().actors.first().action)
    }

    @Test
    fun `test media pipeline renders custom JSON scene blueprints and character sprites onto Canvas`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val hw = PhoneHardwareProfiler.profileDevice(context)
        val plan = LocalOrchestratorBridge.orchestratePrompt(
            prompt = "سمكة زرقاء تسبح في البحر وطائر يطير في السماء",
            style = VideoVisualStyle.CINEMATIC_35MM,
            activeLlmModel = null
        )
        val project = MediaPipelineEngine.executePlanToVideoProject(
            context = context,
            plan = plan,
            aspectRatio = VideoAspectRatio.LANDSCAPE_16_9,
            hardwareProfile = hw
        )

        assertTrue(project.scenes.first().sceneBlueprintJson.contains("WATER"))
        val spritePaths = CinemaFrameRenderer.extractAllSpritePaths(project)
        assertTrue(spritePaths.isNotEmpty())
        assertTrue(spritePaths.all { File(it).exists() })

        val loadedSprites = spritePaths.mapNotNull { path ->
            android.graphics.BitmapFactory.decodeFile(path)?.let { path to it }
        }.toMap()
        assertTrue(loadedSprites.isNotEmpty())

        val bmp = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        CinemaFrameRenderer.drawFrameToCanvas(
            canvas = canvas,
            width = 480,
            height = 270,
            project = project,
            timeSeconds = 1.2f,
            sceneBitmaps = emptyMap(),
            spriteBitmaps = loadedSprites
        )
        assertEquals(480, bmp.width)
        bmp.recycle()
        loadedSprites.values.forEach { if (!it.isRecycled) it.recycle() }
    }

    @Test
    fun `test sprite cutout processor strips chroma green backdrop and tight crops character`() {
        val raw = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val rawPixels = IntArray(120 * 120) { android.graphics.Color.GREEN }
        for (y in 40 until 80) {
            for (x in 40 until 80) {
                rawPixels[y * 120 + x] = android.graphics.Color.rgb(220, 45, 45)
            }
        }
        raw.setPixels(rawPixels, 0, 120, 0, 0, 120, 120)

        val cutout = SpriteCutoutProcessor.extractTransparentCharacterSprite(raw)
        assertTrue("Unexpected cutout width=${cutout.width}", cutout.width in 35..46)
        assertTrue("Unexpected cutout height=${cutout.height}", cutout.height in 35..46)

        val outPixels = IntArray(cutout.width * cutout.height)
        cutout.getPixels(outPixels, 0, cutout.width, 0, 0, cutout.width, cutout.height)
        val centerPx = outPixels[(cutout.height / 2) * cutout.width + (cutout.width / 2)]
        assertEquals(
            "centerPx=0x${Integer.toHexString(centerPx)} getPixel=0x${Integer.toHexString(cutout.getPixel(cutout.width / 2, cutout.height / 2))}",
            255,
            android.graphics.Color.alpha(centerPx)
        )

        raw.recycle()
        cutout.recycle()
    }

    @Test
    fun `test local character sprite generator saves readable PNG sprite`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val actor = ActorDirective(
            id = "dog_1",
            type = "DOG",
            action = "WALK",
            primaryColorHex = "#D97706",
            characterImagePrompt = "Isolated golden dog sprite facing right"
        )
        val (bmp, path) = SpriteCutoutProcessor.createLocalCharacterSpriteFile(context, actor)
        assertNotNull(path)
        assertTrue(File(path!!).exists())
        assertEquals(256, bmp.width)
        bmp.recycle()
    }

    private fun sampleProject(): VideoProject {
        return VideoProject(
            id = "vid_test_01",
            prompt = "رحلة فضائية سينمائية",
            title = "Cosmic Voyage",
            synopsis = "An inspiring visual journey across Saturn's rings.",
            style = VideoVisualStyle.CINEMATIC_35MM,
            aspectRatio = VideoAspectRatio.LANDSCAPE_16_9,
            generationMode = VideoGenerationMode.ON_DEVICE_OFFLINE,
            musicMood = "Orchestral Ambient",
            primaryColorHex = "#0B132B",
            secondaryColorHex = "#1C2541",
            accentColorHex = "#06B6D4",
            scenes = listOf(
                VideoScene(
                    id = "s1",
                    sceneNumber = 1,
                    title = "Opening Shot",
                    visualPrompt = "Wide shot of glowing nebula",
                    narrationSubtitle = "في أعماق الفضاء السحيق تبدأ الرحلة.",
                    durationSeconds = 4.0f,
                    cameraMotion = CameraMotion.ZOOM_IN,
                    transition = SceneTransition.CROSSFADE,
                    moodColorHex = "#0B132B",
                    accentColorHex = "#06B6D4",
                    visualMotif = VisualMotif.RAYS
                ),
                VideoScene(
                    id = "s2",
                    sceneNumber = 2,
                    title = "Climax Motion",
                    visualPrompt = "Orbiting ice rings of Saturn",
                    narrationSubtitle = "حلقات من الجليد والضوء تدور بصمت.",
                    durationSeconds = 4.0f,
                    cameraMotion = CameraMotion.PAN_RIGHT,
                    transition = SceneTransition.FADE_BLACK,
                    moodColorHex = "#1E1B4B",
                    accentColorHex = "#8B5CF6",
                    visualMotif = VisualMotif.BOKEH
                )
            )
        )
    }
}
