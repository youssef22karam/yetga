package com.jarvis.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "jarvis_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        val KEY_ACTIVE_MODEL_PATH  = stringPreferencesKey("active_model_path")
        val KEY_WHISPER_MODEL_PATH = stringPreferencesKey("whisper_model_path")
        val KEY_SYSTEM_PROMPT      = stringPreferencesKey("system_prompt")
        val KEY_MAX_TOKENS         = intPreferencesKey("max_tokens")
        val KEY_TEMPERATURE        = floatPreferencesKey("temperature")
        val KEY_N_THREADS          = intPreferencesKey("n_threads")
        val KEY_CTX_SIZE           = intPreferencesKey("ctx_size")
        val KEY_TTS_RATE           = floatPreferencesKey("tts_rate")
        val KEY_TTS_PITCH          = floatPreferencesKey("tts_pitch")
        val KEY_LANGUAGE           = stringPreferencesKey("language")
        val KEY_USE_BUILTIN_STT    = booleanPreferencesKey("use_builtin_stt")

        // Use ALL available CPU cores by default for maximum speed
        val DEFAULT_N_THREADS: Int get() =
            Runtime.getRuntime().availableProcessors().coerceAtLeast(4)

        const val DEFAULT_MAX_TOKENS    = 512
        const val DEFAULT_TEMPERATURE   = 0.7f
        const val DEFAULT_CTX_SIZE      = 4096
        const val DEFAULT_TTS_RATE      = 1.0f
        const val DEFAULT_TTS_PITCH     = 0.95f
        const val DEFAULT_LANGUAGE      = "en-US"
        const val DEFAULT_USE_BUILTIN   = true   // Built-in STT is much faster

        const val DEFAULT_SYSTEM_PROMPT = """You are JARVIS, an advanced AI assistant running locally on this Android device. You are helpful, concise, and capable. When asked to perform actions on the phone (like liking posts, writing notes, clicking buttons), describe what you're doing step by step. Keep responses brief and conversational unless asked for detail."""
    }

    val activeModelPath: Flow<String>  = context.dataStore.data.map { it[KEY_ACTIVE_MODEL_PATH]  ?: "" }
    val whisperModelPath: Flow<String> = context.dataStore.data.map { it[KEY_WHISPER_MODEL_PATH] ?: "" }
    val systemPrompt: Flow<String>     = context.dataStore.data.map { it[KEY_SYSTEM_PROMPT]      ?: DEFAULT_SYSTEM_PROMPT }
    val maxTokens: Flow<Int>           = context.dataStore.data.map { it[KEY_MAX_TOKENS]         ?: DEFAULT_MAX_TOKENS }
    val temperature: Flow<Float>       = context.dataStore.data.map { it[KEY_TEMPERATURE]        ?: DEFAULT_TEMPERATURE }
    val nThreads: Flow<Int>            = context.dataStore.data.map { it[KEY_N_THREADS]          ?: DEFAULT_N_THREADS }
    val ctxSize: Flow<Int>             = context.dataStore.data.map { it[KEY_CTX_SIZE]           ?: DEFAULT_CTX_SIZE }
    val ttsRate: Flow<Float>           = context.dataStore.data.map { it[KEY_TTS_RATE]           ?: DEFAULT_TTS_RATE }
    val ttsPitch: Flow<Float>          = context.dataStore.data.map { it[KEY_TTS_PITCH]          ?: DEFAULT_TTS_PITCH }
    val language: Flow<String>         = context.dataStore.data.map { it[KEY_LANGUAGE]           ?: DEFAULT_LANGUAGE }
    val useBuiltinStt: Flow<Boolean>   = context.dataStore.data.map { it[KEY_USE_BUILTIN_STT]    ?: DEFAULT_USE_BUILTIN }

    suspend fun setActiveModelPath(v: String) = context.dataStore.edit { it[KEY_ACTIVE_MODEL_PATH]  = v }
    suspend fun setWhisperModelPath(v: String) = context.dataStore.edit { it[KEY_WHISPER_MODEL_PATH] = v }
    suspend fun setSystemPrompt(v: String)     = context.dataStore.edit { it[KEY_SYSTEM_PROMPT]      = v }
    suspend fun setMaxTokens(v: Int)           = context.dataStore.edit { it[KEY_MAX_TOKENS]         = v }
    suspend fun setTemperature(v: Float)       = context.dataStore.edit { it[KEY_TEMPERATURE]        = v }
    suspend fun setNThreads(v: Int)            = context.dataStore.edit { it[KEY_N_THREADS]          = v }
    suspend fun setCtxSize(v: Int)             = context.dataStore.edit { it[KEY_CTX_SIZE]           = v }
    suspend fun setTtsRate(v: Float)           = context.dataStore.edit { it[KEY_TTS_RATE]           = v }
    suspend fun setTtsPitch(v: Float)          = context.dataStore.edit { it[KEY_TTS_PITCH]          = v }
    suspend fun setLanguage(v: String)         = context.dataStore.edit { it[KEY_LANGUAGE]           = v }
    suspend fun setUseBuiltinStt(v: Boolean)   = context.dataStore.edit { it[KEY_USE_BUILTIN_STT]    = v }
}
