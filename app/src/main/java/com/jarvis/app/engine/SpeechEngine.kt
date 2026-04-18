package com.jarvis.app.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android built-in SpeechRecognizer — zero latency, no model download needed.
 * Uses on-device recognizer when available, network as fallback.
 */
class SpeechEngine(private val context: Context) {

    companion object {
        private const val TAG = "SpeechEngine"
    }

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    suspend fun listenOnce(language: String = "en-US"): String =
        suspendCancellableCoroutine { cont ->
            if (!isAvailable) { cont.resume(""); return@suspendCancellableCoroutine }

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(r: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    Log.i(TAG, "Result: $text")
                    recognizer.destroy()
                    if (cont.isActive) cont.resume(text)
                }

                override fun onError(error: Int) {
                    Log.w(TAG, "Error: ${errorName(error)}")
                    recognizer.destroy()
                    if (cont.isActive) cont.resume("")
                }
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            }

            cont.invokeOnCancellation { recognizer.destroy() }
            try {
                recognizer.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "startListening failed", e)
                recognizer.destroy()
                if (cont.isActive) cont.resume("")
            }
        }

    private fun errorName(c: Int) = when (c) {
        SpeechRecognizer.ERROR_AUDIO                    -> "AUDIO"
        SpeechRecognizer.ERROR_CLIENT                   -> "CLIENT"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "NO_PERMISSION"
        SpeechRecognizer.ERROR_NETWORK                  -> "NETWORK"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT          -> "NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH                 -> "NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY          -> "BUSY"
        SpeechRecognizer.ERROR_SERVER                   -> "SERVER"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT           -> "SPEECH_TIMEOUT"
        else                                            -> "UNKNOWN($c)"
    }
}
