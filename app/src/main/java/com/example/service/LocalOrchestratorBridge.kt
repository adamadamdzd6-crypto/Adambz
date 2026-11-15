package com.example.service

import com.example.model.ActorDirective
import com.example.model.CameraMotion
import com.example.model.LocalAiModelSpec
import com.example.model.OrchestrationPlan
import com.example.model.SceneBlueprint
import com.example.model.SceneEnvSpec
import com.example.model.SceneTransition
import com.example.model.VideoVisualStyle
import com.example.model.VisualMotif
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.abs

/**
 * The LLM Orchestrator ("العقل المدبر"):
 * Accepts raw natural-language prompts (Arabic or English), routes through an active local
 * loopback `.gguf` LLM daemon (`127.0.0.1` / `localhost` only) if running, or falls back to
 * our deterministic On-Device Semantic JSON Planner, generating the exact number of scenes
 * requested by the user without artificial repetition or padding.
 */
object LocalOrchestratorBridge {

    fun orchestratePrompt(
        prompt: String,
        style: VideoVisualStyle,
        activeLlmModel: LocalAiModelSpec?,
        sceneCountOverride: Int? = null
    ): OrchestrationPlan {
        val cleanPrompt = prompt.trim().ifBlank { "شخصية تمشي في مشهد سينمائي" }

        // 1. If user linked a real local loopback LLM server (e.g. llama.cpp server at 127.0.0.1:8080),
        //    query it first strictly over loopback.
        val endpoint = activeLlmModel?.localEndpointUrl.orEmpty()
        if (endpoint.isNotBlank() && LocalModelInspector.isLocalLoopbackUrl(endpoint)) {
            val llmPlan = tryQueryLocalLoopbackLlm(
                endpointUrl = endpoint,
                modelLabel = activeLlmModel?.displayName ?: "Local GGUF",
                prompt = cleanPrompt,
                style = style
            )
            if (llmPlan != null && llmPlan.scenes.isNotEmpty()) {
                return llmPlan
            }
        }

        // 2. Otherwise use the built-in On-Device Semantic JSON Planner
        val sourceLabel = if (activeLlmModel != null) {
            "On-Device Planner (${activeLlmModel.displayName})"
        } else {
            "On-Device Semantic JSON Orchestrator"
        }

        return buildSemanticOrchestrationPlan(
            prompt = cleanPrompt,
            style = style,
            orchestratorSource = sourceLabel,
            sceneCountOverride = sceneCountOverride
        )
    }

    fun parseEditedJsonPlan(
        rawJson: String,
        fallbackPrompt: String,
        fallbackStyle: VideoVisualStyle
    ): Result<OrchestrationPlan> {
        return try {
            val plan = OrchestrationPlan.fromJson(rawJson, fallbackPrompt, fallbackStyle)
            if (plan.scenes.isEmpty()) {
                Result.failure(IllegalArgumentException("يجب أن تحتوي خطة JSON على مشهد واحد على الأقل (scenes array)."))
            } else {
                Result.success(plan.copy(orchestratorSource = "Edited JSON Direct Pipeline"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun tryQueryLocalLoopbackLlm(
        endpointUrl: String,
        modelLabel: String,
        prompt: String,
        style: VideoVisualStyle
    ): OrchestrationPlan? {
        if (!LocalModelInspector.isLocalLoopbackUrl(endpointUrl)) return null
        val targetUrl = endpointUrl.trim().trimEnd('/') + "/v1/chat/completions"
        var conn: HttpURLConnection? = null
        return try {
            val systemPrompt = """
                You are an AI Video Orchestrator. Do NOT artificially split a single continuous scene into repetitive chunks. Only create multiple scenes if the prompt explicitly describes sequential steps. Emit ONLY valid JSON matching:
                {
                  "projectTitle": "short title",
                  "synopsis": "1 line summary",
                  "musicMood": "mood + BPM",
                  "scenes": [
                    {
                      "sceneNumber": 1,
                      "title": "scene title",
                      "narrationSubtitle": "subtitle text",
                      "imagePrompt": "detailed keyframe image prompt",
                      "durationSeconds": 6.0,
                      "cameraMotion": "ZOOM_IN|ZOOM_OUT|PAN_LEFT|PAN_RIGHT|KEN_BURNS_DRIFT",
                      "transition": "CROSSFADE|SLIDE_LEFT|ZOOM_BURST|FADE_BLACK",
                      "visualMotif": "rays|particles|waves|grid|bokeh|horizon",
                      "environment": {
                        "groundType": "GRASS|SAND|ROAD|WATER|SPACE|SNOW|CITY",
                        "weather": "CLEAR|RAIN|SNOW|STARS|SPARKS",
                        "skyTopHex": "#0B132B",
                        "skyBottomHex": "#1D3557",
                        "groundHex": "#1B4332",
                        "accentHex": "#06B6D4",
                        "props": ["TREE","MOUNTAIN","PIT_HOLE","CLOUD","BRIDGE","BUILDING","HOUSE","FIRE","RAINBOW","FLOWERS","SUN","MOON"]
                      },
                      "actors": [
                        {
                          "id": "actor_1",
                          "type": "HUMAN|DOG|CAT|BIRD|HORSE|FISH|BUTTERFLY|CAR|BIKE|BOAT|PLANE|ROBOT|MONSTER|BALL",
                          "action": "WALK|RUN|JUMP|FALL_INTO_PIT|SWIM|FLY|DRIVE|DANCE|HOVER|CHASE|IDLE",
                          "primaryColorHex": "#F59E0B",
                          "characterImagePrompt": "isolated full-body side-profile sprite of character on solid pure background for 2D animation",
                          "scale": 1.0,
                          "startX": 0.15,
                          "endX": 0.85,
                          "startY": 0.68,
                          "endY": 0.68
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent()

            val payload = JSONObject().apply {
                put("model", modelLabel)
                put("temperature", 0.3)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Prompt: $prompt | Style: ${style.label}")
                    })
                })
            }

            conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 1500
                readTimeout = 8000
                setRequestProperty("Content-Type", "application/json")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }
            if (conn.responseCode !in 200..299) return null
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val outer = JSONObject(responseText)
            val content = outer.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                .orEmpty()
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            if (content.isBlank()) return null

            val parsed = OrchestrationPlan.fromJson(content, prompt, style)
            parsed.copy(
                orchestratorSource = "Local LLM Bridge ($modelLabel @ $endpointUrl)",
                originalPrompt = prompt,
                style = style
            )
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Exact-count semantic planner:
     * - 1 continuous prompt -> 1 continuous unified scene (no fake 3-5x slicing or repetition!)
     * - Explicit sequential clauses (using "ثم", "وبعدها", "then", newlines) -> exact N scenes
     *   with smooth continuous position chaining (next scene starts where previous ended).
     */
    fun buildSemanticOrchestrationPlan(
        prompt: String,
        style: VideoVisualStyle,
        orchestratorSource: String = "On-Device Semantic JSON Orchestrator",
        sceneCountOverride: Int? = null
    ): OrchestrationPlan {
        val isArabic = prompt.any { it in '\u0600'..'\u06FF' }
        val clauses = splitPromptClauses(prompt)

        // Only expand count when user explicitly attached multiple reference photos
        val sceneCount = (sceneCountOverride ?: clauses.size).coerceIn(1, 6)

        val globalActors = detectActors(prompt, clauseHint = null)
        val globalEnv = detectEnvironment(prompt, style)
        val globalAction = detectAction(prompt, fallbackAction = defaultActionForActors(globalActors))

        val motions = listOf(
            CameraMotion.KEN_BURNS_DRIFT,
            CameraMotion.PAN_RIGHT,
            CameraMotion.ZOOM_IN,
            CameraMotion.PAN_LEFT
        )
        val transitions = listOf(
            SceneTransition.CROSSFADE,
            SceneTransition.SLIDE_LEFT,
            SceneTransition.ZOOM_BURST,
            SceneTransition.FADE_BLACK
        )

        val hashOffset = abs(prompt.hashCode())
        val isReverseFromRight = prompt.lowercase().containsAny("من اليمين", "from right", "leftward")

        var prevActorEndX: Float? = null

        val scenes = (0 until sceneCount).map { idx ->
            val clauseText = clauses.getOrNull(idx) ?: prompt

            val clauseActorsRaw = detectActors(clauseText, clauseHint = clauseText)
            val actorsBase = if (clauseActorsRaw.firstOrNull()?.label == "DEFAULT" && globalActors.isNotEmpty()) {
                globalActors
            } else {
                clauseActorsRaw
            }

            val clauseEnv = mergeEnvironments(globalEnv, detectEnvironment(clauseText, style))

            val resolvedAction = resolveSequentialAction(
                fullPrompt = prompt,
                clause = clauseText,
                sceneIndex = idx,
                totalScenes = sceneCount,
                defaultAction = globalAction
            )

            val finalProps = clauseEnv.props.toMutableSet()
            if (resolvedAction == "FALL_INTO_PIT") {
                finalProps.add("PIT_HOLE")
            }

            // Duration: 1 continuous scene plays a generous 6.5s uninterrupted; multi-clause chains cleanly
            val sceneDuration = when {
                sceneCount == 1 -> 6.5f
                sceneCount == 2 -> 4.0f
                else -> 3.5f
            }

            val directedActors = buildDirectedActors(
                actors = actorsBase,
                action = resolvedAction,
                sceneIndex = idx,
                totalScenes = sceneCount,
                groundType = clauseEnv.groundType,
                style = style,
                chainFromX = prevActorEndX,
                reverseFromRight = isReverseFromRight
            )
            prevActorEndX = directedActors.firstOrNull()?.endX

            val sceneTitle = generateSceneTitle(
                clause = clauseText,
                sceneIndex = idx
            )

            val motif = selectMotifForEnv(clauseEnv, idx, hashOffset)
            val imagePrompt = buildDetailedImagePrompt(
                clause = clauseText,
                style = style,
                env = clauseEnv,
                actors = directedActors
            )

            SceneBlueprint(
                sceneNumber = idx + 1,
                title = sceneTitle,
                narrationSubtitle = clauseText,
                imagePrompt = imagePrompt,
                durationSeconds = sceneDuration,
                cameraMotion = motions[(idx + hashOffset) % motions.size],
                transition = transitions[(idx + hashOffset) % transitions.size],
                visualMotif = motif,
                environment = clauseEnv.copy(props = finalProps.toList()),
                actors = directedActors
            )
        }

        val shortTitle = prompt.split(Regex("\\s+")).take(5).joinToString(" ")
        val synopsis = if (isArabic) {
            val unit = if (scenes.size == 1) "مشهد متصل متكامل" else "${scenes.size} مشاهد متسلسلة"
            "خطة JSON مهيكلة ($unit) • $orchestratorSource"
        } else {
            val unit = if (scenes.size == 1) "1 continuous scene" else "${scenes.size} sequential scenes"
            "Structured JSON Plan ($unit) • $orchestratorSource"
        }

        val musicMood = when (globalEnv.groundType) {
            "SPACE" -> "Deep Space Synth 92 BPM"
            "WATER" -> "Fluid Ocean Piano 88 BPM"
            "CITY", "ROAD" -> "Urban Electronic Pulse 120 BPM"
            "SAND" -> "Desert Oud & Strings 98 BPM"
            "SNOW" -> "Nordic Atmospheric Strings 84 BPM"
            else -> "Acoustic Adventure Score 108 BPM"
        }

        return OrchestrationPlan(
            planId = "plan_${UUID.randomUUID().toString().take(8)}",
            orchestratorSource = orchestratorSource,
            originalPrompt = prompt,
            projectTitle = shortTitle,
            synopsis = synopsis,
            musicMood = musicMood,
            style = style,
            scenes = scenes
        )
    }

    /**
     * Splits ONLY on true sequential/clause delimiters ("ثم", "وبعدها", "then", newlines, commas between clauses)
     * so single descriptions stay 1 continuous scene while multi-part stories execute all parts.
     */
    private fun splitPromptClauses(prompt: String): List<String> {
        val normalized = prompt
            .replace(Regex("(?:^|\\s|[،,])(ثم|وبعد ذلك|وبعدها|وبعدين|بعد ذلك|بعدها|وفجأة|فجأة)(?=\\s|$)"), " | ")
            .replace(Regex("\\b(then|and then|after that|suddenly|next)\\b", RegexOption.IGNORE_CASE), " | ")
            .replace(Regex("[\n;.،,]+"), " | ")
            .replace(Regex("\\s+(?=[0-9]+[-.)]\\s+)"), " | ")

        val parts = normalized.split("|")
            .map { it.replace(Regex("^[0-9]+[-.)]\\s*"), "").trim() }
            .filter { it.length >= 2 }

        return if (parts.isNotEmpty()) parts else listOf(prompt.trim())
    }

    private fun detectEnvironment(text: String, style: VideoVisualStyle): SceneEnvSpec {
        val lower = text.lowercase()
        val props = mutableListOf<String>()

        val weather = when {
            lower.containsAny("مطر", "تمطر", "شتاء", "عاصفة", "rain", "storm", "rainy") -> "RAIN"
            lower.containsAny("ثلج", "جليد", "تثلج", "snow", "blizzard", "winter") -> "SNOW"
            lower.containsAny("نجوم", "نجم", "مجرة", "فضاء", "ليل", "كوكب", "star", "space", "galaxy", "night") -> "STARS"
            lower.containsAny("نار", "شرار", "انفجار", "سحر", "أضواء", "نيون", "لهب", "spark", "fire", "magic", "neon") -> "SPARKS"
            else -> "CLEAR"
        }

        if (lower.containsAny("حفرة", "حفره", "بئر", "سقط", "يقع", "وقع", "pit", "hole", "trap")) props.add("PIT_HOLE")
        if (lower.containsAny("شجرة", "أشجار", "شجر", "نخلة", "نخيل", "غابة", "حديقة", "حديقه", "tree", "palm", "forest", "park", "woods")) props.add("TREE")
        if (lower.containsAny("جبل", "جبال", "تلة", "صخور", "mountain", "hill", "peak")) props.add("MOUNTAIN")
        if (lower.containsAny("غيمة", "سحاب", "غيوم", "cloud")) props.add("CLOUD")
        if (lower.containsAny("جسر", "كوبري", "bridge")) props.add("BRIDGE")
        if (lower.containsAny("بيت", "منزل", "كوخ", "قصر", "خيمة", "مسجد", "house", "home", "cabin", "castle", "tent")) props.add("HOUSE")
        if (lower.containsAny("نار", "حريق", "مخيم", "fire", "campfire", "flame")) props.add("FIRE")
        if (lower.containsAny("قوس قزح", "قوس", "rainbow")) props.add("RAINBOW")
        if (lower.containsAny("زهرة", "زهور", "ورود", "وردة", "flower", "garden", "rose")) props.add("FLOWERS")
        if (lower.containsAny("مدينة", "مباني", "برج", "شارع", "أبراج", "city", "building", "tower", "street")) props.add("BUILDING")
        if (lower.containsAny("شمس", "غروب", "شروق", "نهار", "صباح", "sun", "sunrise", "sunset", "morning")) props.add("SUN")
        if (lower.containsAny("قمر", "ليل", "مساء", "هلال", "moon", "midnight", "night")) props.add("MOON")

        val isNight = lower.containsAny("ليل", "ظلام", "نيون", "قمر", "فضاء", "مساء", "night", "dark", "midnight", "neon", "space", "cyber")
        val isSunset = lower.containsAny("غروب", "شروق", "ذهبي", "sunset", "sunrise", "golden")

        val groundType = when {
            lower.containsAny("فضاء", "كوكب", "قمر", "مجرة", "space", "planet", "cosmos", "galaxy") -> "SPACE"
            lower.containsAny("بحر", "محيط", "نهر", "بحيرة", "ماء", "موج", "سباحة", "يسبح", "قارب", "سفينة", "سمك", "غواصة", "حوت", "sea", "ocean", "water", "river", "swim", "boat", "lake", "fish", "whale") -> "WATER"
            lower.containsAny("ثلج", "جليد", "قطبي", "snow", "ice", "arctic") -> "SNOW"
            lower.containsAny("صحراء", "رمل", "كثبان", "شاطئ", "بر", "جمل", "desert", "sand", "dune", "beach") -> "SAND"
            lower.containsAny("مدينة", "شارع", "نيون", "مباني", "أزقة", "عمارة", "city", "cyber", "alley", "urban", "tokyo") || style == VideoVisualStyle.CYBERPUNK_NEON -> "CITY"
            lower.containsAny("طريق", "اسفلت", "سيارة", "سباق", "دراجة", "road", "highway", "street", "car", "drive", "bike") -> "ROAD"
            else -> "GRASS"
        }

        val (skyTop, skyBot, groundHex, accent) = when {
            groundType == "SPACE" -> listOf("#030712", "#1E1B4B", "#0F172A", "#38BDF8")
            groundType == "WATER" && !isNight -> listOf("#0369A1", "#38BDF8", "#0284C7", "#67E8F9")
            groundType == "WATER" && isNight -> listOf("#020617", "#0C4A6E", "#075985", "#22D3EE")
            groundType == "SAND" && isSunset -> listOf("#4C1D95", "#EA580C", "#B45309", "#FBBF24")
            groundType == "SAND" -> listOf("#0284C7", "#7DD3FC", "#D97706", "#F59E0B")
            groundType == "CITY" -> listOf("#090D1F", "#3B0764", "#111827", "#EC4899")
            groundType == "SNOW" -> listOf("#1E293B", "#64748B", "#E2E8F0", "#38BDF8")
            groundType == "ROAD" && isNight -> listOf("#090D1F", "#1E293B", "#1F2937", "#F59E0B")
            groundType == "ROAD" -> listOf("#1D4ED8", "#93C5FD", "#334155", "#FACC15")
            isSunset -> listOf("#311042", "#F97316", "#166534", "#FBBF24")
            isNight -> listOf("#040B1A", "#1E293B", "#14532D", "#38BDF8")
            else -> listOf("#0284C7", "#BAE6FD", "#15803D", "#FACC15")
        }

        if (props.isEmpty()) {
            when (groundType) {
                "GRASS" -> props.addAll(listOf("SUN", "CLOUD", "TREE"))
                "SAND" -> props.addAll(listOf("SUN", "MOUNTAIN"))
                "WATER" -> props.addAll(listOf("CLOUD", "SUN"))
                "CITY" -> props.addAll(listOf("BUILDING", "MOON"))
                "ROAD" -> props.addAll(listOf("MOUNTAIN", "CLOUD"))
                "SPACE" -> props.add("MOON")
                "SNOW" -> props.addAll(listOf("MOUNTAIN", "CLOUD"))
            }
        }

        return SceneEnvSpec(
            groundType = groundType,
            weather = weather,
            skyTopHex = skyTop,
            skyBottomHex = skyBot,
            groundHex = groundHex,
            accentHex = accent,
            props = props.distinct()
        )
    }

    private fun mergeEnvironments(base: SceneEnvSpec, specific: SceneEnvSpec): SceneEnvSpec {
        val combinedProps = (base.props + specific.props).distinct()
        val useSpecificGround = specific.groundType != "GRASS"
        val useSpecificWeather = specific.weather != "CLEAR"
        return SceneEnvSpec(
            groundType = if (useSpecificGround) specific.groundType else base.groundType,
            weather = if (useSpecificWeather) specific.weather else base.weather,
            skyTopHex = if (useSpecificGround) specific.skyTopHex else base.skyTopHex,
            skyBottomHex = if (useSpecificGround) specific.skyBottomHex else base.skyBottomHex,
            groundHex = if (useSpecificGround) specific.groundHex else base.groundHex,
            accentHex = if (useSpecificGround) specific.accentHex else base.accentHex,
            props = combinedProps
        )
    }

    private fun detectActors(text: String, clauseHint: String?): List<ActorDirective> {
        val lower = text.lowercase()
        val found = mutableListOf<ActorDirective>()
        val colorHex = detectCustomColor(lower)

        fun addActor(type: String, defaultColor: String, label: String) {
            if (found.none { it.type == type }) {
                found.add(
                    ActorDirective(
                        id = "actor_${found.size + 1}",
                        type = type,
                        label = label,
                        primaryColorHex = colorHex ?: defaultColor
                    )
                )
            }
        }

        if (lower.containsAny("كلب", "جرو", "ذئب", "ثعلب", "dog", "puppy", "wolf", "fox")) addActor("DOG", "#C27D38", "كلب")
        if (lower.containsAny("قط", "قطة", "قطه", "هر", "أرنب", "فأر", "cat", "kitten", "rabbit", "mouse")) addActor("CAT", "#F97316", "قطة")
        if (lower.containsAny("حصان", "خيل", "فرس", "جمل", "ناقة", "غزال", "حمار", "أسد", "اسد", "نمر", "فهد", "دب", "فيل", "زرافة", "horse", "camel", "lion", "tiger", "deer", "bear", "elephant")) addActor("HORSE", "#92400E", "حيوان")
        if (lower.containsAny("سمك", "سمكة", "قرش", "حوت", "دولفين", "أخطبوط", "fish", "shark", "whale", "dolphin")) addActor("FISH", "#0EA5E9", "سمكة")
        if (lower.containsAny("فراشة", "نحلة", "butterfly", "bee")) addActor("BUTTERFLY", "#EC4899", "فراشة")
        if (lower.containsAny("طائر", "عصفور", "نسر", "صقر", "حمامة", "بومة", "بطة", "غراب", "bird", "eagle", "hawk", "owl", "duck")) addActor("BIRD", "#F8FAFC", "طائر")
        if (lower.containsAny("دراجة", "بسكليت", "سيكل", "موتوسيكل", "bike", "bicycle", "motorcycle")) addActor("BIKE", "#10B981", "دراجة")
        if (lower.containsAny("سيارة", "سياره", "شاحنة", "حافلة", "باص", "تاكسي", "قطار", "عربة", "car", "truck", "bus", "taxi", "train", "van")) addActor("CAR", "#EF4444", "مركبة")
        if (lower.containsAny("قارب", "سفينة", "سفينه", "يخت", "مركب", "غواصة", "boat", "ship", "yacht", "vessel", "submarine")) addActor("BOAT", "#E2E8F0", "قارب")
        if (lower.containsAny("طائرة", "طائره", "صاروخ", "مركبة فضائية", "سفينة فضاء", "مروحية", "مكوك", "plane", "airplane", "jet", "rocket", "spaceship", "helicopter")) addActor("PLANE", "#38BDF8", "طائرة")
        if (lower.containsAny("روبوت", "آلي", "الي", "robot", "android", "mech", "cyborg")) addActor("ROBOT", "#06B6D4", "روبوت")
        if (lower.containsAny("تنين", "وحش", "ديناصور", "غوريلا", "monster", "dragon", "dinosaur", "beast")) addActor("MONSTER", "#10B981", "مخلوق")
        if (lower.containsAny("كرة", "كوره", "ball", "football", "soccer")) addActor("BALL", "#FACC15", "كرة")
        if (lower.containsAny("رجل", "طفل", "ولد", "بنت", "فتاة", "امرأة", "بطل", "رائد", "شخص", "إنسان", "صياد", "فارس", "لاعب", "شاب", "طباخ", "طبيب", "جندي", "ملك", "أمي", "صديق", "man", "boy", "girl", "woman", "child", "kid", "person", "hero", "player", "astronaut", "knight", "ninja", "chef")) {
            addActor("HUMAN", "#3B82F6", "شخصية")
        }

        if (found.isEmpty()) {
            val tag = if (clauseHint != null) "DEFAULT" else "شخصية"
            found.add(
                ActorDirective(
                    id = "actor_1",
                    type = "HUMAN",
                    label = tag,
                    primaryColorHex = colorHex ?: "#38BDF8"
                )
            )
        }
        return found.take(3)
    }

    private fun detectCustomColor(lower: String): String? = when {
        lower.containsAny("أحمر", "احمر", "حمراء", "red", "crimson") -> "#EF4444"
        lower.containsAny("أزرق", "ازرق", "زرقاء", "سماوي", "blue", "cyan", "azure") -> "#38BDF8"
        lower.containsAny("أخضر", "اخضر", "خضراء", "green", "emerald") -> "#22C55E"
        lower.containsAny("ذهبي", "أصفر", "اصفر", "صفراء", "gold", "golden", "yellow") -> "#FACC15"
        lower.containsAny("أبيض", "ابيض", "بيضاء", "white") -> "#F8FAFC"
        lower.containsAny("أسود", "اسود", "سوداء", "black") -> "#334155"
        lower.containsAny("بنفسجي", "أرجواني", "purple", "violet", "pink", "وردي") -> "#EC4899"
        lower.containsAny("برتقالي", "orange") -> "#F97316"
        lower.containsAny("بني", "brown") -> "#B45309"
        else -> null
    }

    private fun detectAction(text: String, fallbackAction: String): String {
        val lower = text.lowercase()
        return when {
            lower.containsAny("يقع", "وقع", "سقوط", "سقط", "يسقط", "حفرة", "حفره", "fall", "falls", "hole", "pit") -> "FALL_INTO_PIT"
            lower.containsAny("يطارد", "مطاردة", "ملاحقة", "chase", "chasing") -> "CHASE"
            lower.containsAny("يقفز", "قفز", "نط", "ينط", "يلعب", "يركل", "jump", "leap", "hop", "play", "kick") -> "JUMP"
            lower.containsAny("يركض", "يجري", "ركض", "جري", "مسرع", "يسرع", "run", "running", "sprint", "rush", "gallop") -> "RUN"
            lower.containsAny("يسبح", "تسبح", "سباحة", "يطفو", "يبحر", "تبحر", "swim", "swimming", "sail", "float") -> "SWIM"
            lower.containsAny("يطير", "تطير", "تحليق", "يحلق", "اقلاع", "fly", "flying", "soar", "launch") -> "FLY"
            lower.containsAny("يقود", "تسير", "تقود", "drive", "driving", "racing", "ride") -> "DRIVE"
            lower.containsAny("يرقص", "ترقص", "رقص", "يحتفل", "dance", "dancing", "celebrate", "wave", "greet") -> "DANCE"
            lower.containsAny("يطفو", "يحوم", "يدور", "hover", "orbit") -> "HOVER"
            lower.containsAny("يجلس", "ينام", "يقف", "ثابت", "sit", "sleep", "stand", "idle", "rest") -> "IDLE"
            lower.containsAny("يمشي", "يتمشى", "يتمشي", "يمشى", "تسير", "مشي", "walk", "walking", "stroll") -> "WALK"
            else -> fallbackAction
        }
    }

    private fun defaultActionForActors(actors: List<ActorDirective>): String {
        return when (actors.firstOrNull()?.type) {
            "BIRD", "PLANE", "BUTTERFLY" -> "FLY"
            "CAR", "BIKE" -> "DRIVE"
            "BOAT", "FISH" -> "SWIM"
            else -> "WALK"
        }
    }

    private fun resolveSequentialAction(
        fullPrompt: String,
        clause: String,
        sceneIndex: Int,
        totalScenes: Int,
        defaultAction: String
    ): String {
        val clauseAction = detectAction(clause, fallbackAction = "")
        if (clauseAction.isNotEmpty()) {
            return clauseAction
        }
        val fullLower = fullPrompt.lowercase()
        if (totalScenes >= 2 && fullLower.containsAny("يقع", "سقوط", "حفرة", "حفره", "fall", "hole", "pit")) {
            return if (sceneIndex == totalScenes - 1) "FALL_INTO_PIT" else "WALK"
        }
        return defaultAction
    }

    private fun buildDirectedActors(
        actors: List<ActorDirective>,
        action: String,
        sceneIndex: Int,
        totalScenes: Int,
        groundType: String,
        style: VideoVisualStyle,
        chainFromX: Float?,
        reverseFromRight: Boolean
    ): List<ActorDirective> {
        val baseY = when {
            action == "FLY" || groundType == "SPACE" -> 0.36f
            action == "SWIM" || groundType == "WATER" -> 0.72f
            else -> 0.67f
        }

        val segStartX = chainFromX ?: if (reverseFromRight) 0.86f else 0.14f
        val spanPerScene = if (totalScenes > 1) 0.72f / totalScenes else 0.72f

        return actors.mapIndexed { idx, actor ->
            val offsetSlot = if (action == "CHASE") idx * 0.18f else idx * 0.14f
            val (sx, ex, sy, ey) = when (action) {
                "FALL_INTO_PIT" -> listOf(segStartX.coerceAtMost(0.45f), 0.50f, baseY, baseY + 0.08f)
                "IDLE" -> listOf(0.42f + offsetSlot, 0.42f + offsetSlot, baseY, baseY)
                "FLY" -> {
                    val x1 = if (reverseFromRight) (segStartX - offsetSlot) else (segStartX + offsetSlot)
                    val x2 = if (reverseFromRight) (x1 - spanPerScene).coerceAtLeast(0.10f) else (x1 + spanPerScene).coerceAtMost(0.90f)
                    listOf(x1, x2, 0.42f - idx * 0.05f, 0.28f - idx * 0.04f)
                }
                else -> {
                    val x1 = if (reverseFromRight) (segStartX - offsetSlot) else (segStartX + offsetSlot)
                    val x2 = if (reverseFromRight) (x1 - spanPerScene).coerceAtLeast(0.10f) else (x1 + spanPerScene).coerceAtMost(0.90f)
                    listOf(x1, x2, baseY, baseY)
                }
            }
            val nounEn = when (actor.type.uppercase()) {
                "DOG" -> "cute fluffy dog"
                "CAT" -> "playful cat"
                "HORSE" -> "majestic horse"
                "BIRD" -> "graceful bird with wings"
                "FISH" -> "colorful swimming fish"
                "BUTTERFLY" -> "vibrant butterfly"
                "CAR" -> "sleek sports car"
                "BIKE" -> "sport bicycle"
                "BOAT" -> "sailing boat"
                "PLANE" -> "futuristic airplane"
                "ROBOT" -> "friendly futuristic robot"
                "MONSTER" -> "stylized dragon creature"
                "BALL" -> "vivid sports ball"
                else -> "expressive young hero character"
            }
            val spritePrompt = "Full-body side-profile 2D animation character sprite of a $nounEn (accent color ${actor.primaryColorHex}), isolated on a pure uniform solid white studio background, clean silhouette, no scenery, ${style.promptDirective}"
            actor.copy(
                action = action,
                characterImagePrompt = actor.characterImagePrompt.ifBlank { spritePrompt },
                startX = sx.coerceIn(0.08f, 0.92f),
                endX = ex.coerceIn(0.08f, 0.92f),
                startY = sy.coerceIn(0.20f, 0.85f),
                endY = ey.coerceIn(0.20f, 0.85f)
            )
        }
    }

    private fun generateSceneTitle(clause: String, sceneIndex: Int): String {
        val compact = clause.split(Regex("\\s+")).take(5).joinToString(" ")
        return compact.ifBlank { "المشهد ${sceneIndex + 1}" }
    }

    private fun selectMotifForEnv(env: SceneEnvSpec, idx: Int, hash: Int): VisualMotif {
        return when {
            env.weather == "STARS" || env.weather == "SNOW" -> VisualMotif.PARTICLES
            env.groundType == "WATER" -> VisualMotif.WAVES
            env.groundType == "CITY" || env.groundType == "ROAD" -> VisualMotif.GRID
            env.weather == "SPARKS" -> VisualMotif.BOKEH
            else -> VisualMotif.entries[(idx + hash) % VisualMotif.entries.size]
        }
    }

    private fun buildDetailedImagePrompt(
        clause: String,
        style: VideoVisualStyle,
        env: SceneEnvSpec,
        actors: List<ActorDirective>
    ): String {
        val actorDesc = actors.joinToString(" and ") { "${it.type.lowercase()} (${it.action.lowercase()})" }
        val propsDesc = if (env.props.isEmpty()) "" else " featuring ${env.props.joinToString(", ").lowercase()}"
        return "$clause — $actorDesc in ${env.groundType.lowercase()} setting, weather ${env.weather.lowercase()}$propsDesc, ${style.promptDirective}"
    }

    private fun String.containsAny(vararg tokens: String): Boolean = tokens.any { this.contains(it) }
}
