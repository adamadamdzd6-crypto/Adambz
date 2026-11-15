package com.example.service

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceoverManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val arabicRegex = Regex("[\\u0600-\\u06FF]")

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            isReady = (status == TextToSpeech.SUCCESS)
        }
    }

    fun speak(text: String) {
        if (!isReady || text.isBlank()) return
        val locale = if (arabicRegex.containsMatchIn(text)) {
            Locale("ar")
        } else {
            Locale.US
        }
        tts?.language = locale
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "scene_${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
