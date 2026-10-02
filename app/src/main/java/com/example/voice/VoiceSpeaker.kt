package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

object VoiceSpeaker {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking = _isSpeaking.asStateFlow()

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                try {
                    // Try Hindi (India) first, fallback to US / Default
                    val result = tts?.setLanguage(Locale("hi", "IN"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale("en", "IN"))
                    }
                } catch (e: Exception) {
                    tts?.setLanguage(Locale.US)
                }
                
                tts?.setSpeechRate(1.05f)
                tts?.setPitch(1.0f)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
                Log.i("VoiceSpeaker", "TextToSpeech initialized successfully")
            } else {
                Log.e("VoiceSpeaker", "TextToSpeech initialization failed: status=$status")
            }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isReady || tts == null) {
            Log.w("VoiceSpeaker", "TTS not ready yet")
            return
        }

        // Clean out markdown asterisks or code formatting for cleaner speech
        val cleanText = text
            .replace(Regex("\\*\\*|\\*|_|#|`"), "")
            .replace(Regex("http\\S+"), "link")
            .trim()

        if (cleanText.isEmpty()) return

        val utteranceId = "utterance_${System.currentTimeMillis()}"
        val params = Bundle()
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        try {
            tts?.stop()
            _isSpeaking.value = false
        } catch (e: Exception) {
            Log.e("VoiceSpeaker", "Error stopping TTS", e)
        }
    }
}
