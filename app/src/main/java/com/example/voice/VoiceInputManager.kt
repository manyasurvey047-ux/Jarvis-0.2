package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceInputManager(
    private val context: Context,
    private val onTextRecognized: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText = _partialText.asStateFlow()

    fun init() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w("VoiceInputManager", "Speech recognition not available on device")
            return
        }

        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        Log.w("VoiceInputManager", "Speech recognition error code: $error")
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val bestMatch = matches?.firstOrNull()?.trim()
                        if (!bestMatch.isNullOrEmpty()) {
                            Log.i("VoiceInputManager", "Recognized speech: $bestMatch")
                            onTextRecognized(bestMatch)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        _partialText.value = text
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } catch (e: Exception) {
            Log.e("VoiceInputManager", "Failed to init SpeechRecognizer", e)
        }
    }

    fun startListening() {
        if (recognizer == null) {
            init()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            recognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            Log.e("VoiceInputManager", "Error starting listening", e)
        }
    }

    fun stopListening() {
        try {
            recognizer?.stopListening()
            _isListening.value = false
        } catch (e: Exception) {
            Log.e("VoiceInputManager", "Error stopping listening", e)
        }
    }

    fun destroy() {
        try {
            recognizer?.destroy()
            recognizer = null
            _isListening.value = false
        } catch (e: Exception) {
            Log.e("VoiceInputManager", "Error destroying SpeechRecognizer", e)
        }
    }
}
