package com.example.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e("TtsManager", "Failed to construct TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            val result = tts?.setLanguage(Locale.JAPANESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TtsManager", "Japanese language is not supported or missing data on this device")
            }
        } else {
            Log.e("TtsManager", "TextToSpeech Initialization failed with status: $status")
        }
    }

    fun speak(text: String, isJapanese: Boolean = true) {
        if (!isReady) {
            Log.e("TtsManager", "TTS not ready yet")
            return
        }
        try {
            tts?.apply {
                val locale = if (isJapanese) {
                    Locale.JAPANESE
                } else {
                    Locale("id", "ID") // Indonesian
                }
                val langResult = setLanguage(locale)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to default
                    setLanguage(Locale.getDefault())
                }
                // Strip furigana format [Kanji]{furigana} to speak pure Japanese
                val cleanedText = if (isJapanese) cleanFuriganaForTts(text) else text
                speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, "TranslateTts_${System.currentTimeMillis()}")
            }
        } catch (e: Exception) {
            Log.e("TtsManager", "Error speaking text", e)
        }
    }

    /**
     * Converts "[今日]{きょう}は[良い]{よい}天気" to "今日は良い天気"
     */
    private fun cleanFuriganaForTts(text: String): String {
        val regex = Regex("\\[([^\\]]+)\\]\\{([^}]+)\\}")
        return regex.replace(text) { matchResult ->
            matchResult.groupValues[1] // Keep only the base kanji
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e("TtsManager", "Error stopping TTS", e)
        }
    }

    fun shutdown() {
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("TtsManager", "Error shutting down TTS", e)
        } finally {
            tts = null
        }
    }
}
