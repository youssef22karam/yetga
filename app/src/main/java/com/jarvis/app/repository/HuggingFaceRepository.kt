package com.jarvis.app.repository

import android.content.Context
import android.util.Log
import com.jarvis.app.data.DownloadProgress
import com.jarvis.app.data.HFModel
import com.jarvis.app.data.HFModelFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class HuggingFaceRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG      = "HuggingFaceRepo"
        private const val API_BASE = "https://huggingface.co/api"
        private const val HF_BASE  = "https://huggingface.co"

        // ── Featured model catalogue ──────────────────────────────────────────
        // Each group is sorted by size so users can pick based on their RAM
        val FEATURED_MODELS = listOf(

            // ── Gemma Edge (Gemma 3n) — Google's mobile-first models ──────────
            HFModel("google/gemma-3n-E2B-it-GGUF",         "Gemma 3n E2B (Edge 2B)",  downloads = 120_000,
                tags = listOf("gemma", "edge", "google")),
            HFModel("google/gemma-3n-E4B-it-GGUF",         "Gemma 3n E4B (Edge 4B)",  downloads = 95_000,
                tags = listOf("gemma", "edge", "google")),
            HFModel("bartowski/gemma-3-1b-it-GGUF",        "Gemma 3 1B",              downloads = 180_000,
                tags = listOf("gemma", "google")),
            HFModel("bartowski/gemma-3-4b-it-GGUF",        "Gemma 3 4B",              downloads = 250_000,
                tags = listOf("gemma", "google")),

            // ── Qwen 2.5 — excellent for mobile ──────────────────────────────
            HFModel("Qwen/Qwen2.5-0.5B-Instruct-GGUF",    "Qwen 2.5 0.5B",           downloads =  80_000),
            HFModel("Qwen/Qwen2.5-1.5B-Instruct-GGUF",    "Qwen 2.5 1.5B",           downloads = 130_000),
            HFModel("Qwen/Qwen2.5-3B-Instruct-GGUF",      "Qwen 2.5 3B",             downloads = 110_000),

            // ── Llama 3.2 ─────────────────────────────────────────────────────
            HFModel("lmstudio-community/Meta-Llama-3.2-1B-Instruct-GGUF",
                "Llama 3.2 1B",  downloads = 350_000),
            HFModel("lmstudio-community/Meta-Llama-3.2-3B-Instruct-GGUF",
                "Llama 3.2 3B",  downloads = 420_000),

            // ── Phi 3.5 ───────────────────────────────────────────────────────
            HFModel("microsoft/Phi-3.5-mini-instruct-gguf","Phi-3.5 Mini 3.8B",       downloads = 200_000),

            // ── SmolLM 2 — tiny but smart ─────────────────────────────────────
            HFModel("bartowski/SmolLM2-360M-Instruct-GGUF","SmolLM2 360M",            downloads =  60_000),
            HFModel("bartowski/SmolLM2-1.7B-Instruct-GGUF","SmolLM2 1.7B",            downloads = 110_000),

            // ── Whisper (STT fallback) ────────────────────────────────────────
            HFModel("ggerganov/whisper.cpp",               "Whisper STT Models",      downloads = 800_000,
                tags = listOf("whisper", "stt"))
        )
    }

    suspend fun searchModels(query: String): List<HFModel> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/models?filter=gguf&sort=downloads&direction=-1&limit=20&search=$query"
            val resp = client.newCall(Request.Builder().url(url).build()).execute()
            val body = resp.body?.string() ?: return@withContext emptyList()
            parseModelList(body)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            FEATURED_MODELS.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    suspend fun getModelFiles(modelId: String): List<HFModelFile> = withContext(Dispatchers.IO) {
        try {
            val url  = "$API_BASE/models/$modelId"
            val resp = client.newCall(Request.Builder().url(url).build()).execute()
            val body = resp.body?.string() ?: return@withContext emptyList()
            parseModelFiles(body, modelId)
        } catch (e: Exception) {
            Log.e(TAG, "Get files failed for $modelId", e)
            emptyList()
        }
    }

    fun downloadModel(modelId: String, filename: String, destFile: File): Flow<DownloadProgress> = flow {
        val url = "$HF_BASE/$modelId/resolve/main/$filename"
        Log.i(TAG, "Downloading $url → ${destFile.path}")
        try {
            val resp   = client.newCall(Request.Builder().url(url).build()).execute()
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val total  = resp.body?.contentLength() ?: -1L
            val stream = resp.body?.byteStream() ?: throw Exception("Empty body")
            destFile.parentFile?.mkdirs()
            val buf = ByteArray(65_536)  // 64 KB chunks for speed
            var downloaded = 0L
            var lastEmit   = 0L
            val start      = System.currentTimeMillis()
            destFile.outputStream().use { out ->
                while (true) {
                    val n = stream.read(buf)
                    if (n == -1) break
                    out.write(buf, 0, n)
                    downloaded += n
                    if (downloaded - lastEmit > 1_048_576 || downloaded == total) { // emit every 1 MB
                        val elapsed = (System.currentTimeMillis() - start) / 1000f
                        val speed   = if (elapsed > 0) "%.1f MB/s".format(downloaded / 1048576f / elapsed) else ""
                        emit(DownloadProgress(modelId, filename, downloaded, total, speed))
                        lastEmit = downloaded
                    }
                }
            }
            emit(DownloadProgress(modelId, filename, downloaded, downloaded, "Done"))
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            destFile.delete()
            throw e
        }
    }

    fun getModelsDir(): File = File(context.filesDir, "models").also { it.mkdirs() }
    fun getWhisperDir(): File = File(context.filesDir, "whisper").also { it.mkdirs() }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private fun parseModelList(json: String): List<HFModel> = try {
        val arr = JSONArray(json)
        (0 until minOf(arr.length(), 20)).mapNotNull {
            runCatching {
                val o = arr.getJSONObject(it)
                HFModel(o.getString("modelId"),
                    o.getString("modelId").substringAfterLast('/'),
                    o.optLong("downloads", 0),
                    o.optLong("likes", 0))
            }.getOrNull()
        }
    } catch (e: Exception) { emptyList() }

    private fun parseModelFiles(json: String, modelId: String): List<HFModelFile> = try {
        val siblings = JSONObject(json).optJSONArray("siblings") ?: return emptyList()
        (0 until siblings.length()).mapNotNull {
            runCatching {
                val f    = siblings.getJSONObject(it)
                val name = f.getString("rfilename")
                if (!name.endsWith(".gguf", true) && !name.endsWith(".bin", true)) return@runCatching null
                HFModelFile(
                    filename    = name,
                    sizeBytes   = f.optLong("size", 0L),
                    downloadUrl = "$HF_BASE/$modelId/resolve/main/$name",
                    quantization= extractQuantization(name)
                )
            }.getOrNull()
        }.filterNotNull()
            .sortedWith(compareByDescending<HFModelFile> { it.quantization == "Q4_K_M" }.thenBy { it.sizeBytes })
    } catch (e: Exception) { emptyList() }

    private fun extractQuantization(filename: String): String {
        val u = filename.uppercase()
        return when {
            "Q8_0"   in u -> "Q8_0"
            "Q6_K"   in u -> "Q6_K"
            "Q5_K_M" in u -> "Q5_K_M"
            "Q5_K_S" in u -> "Q5_K_S"
            "Q4_K_M" in u -> "Q4_K_M"
            "Q4_K_S" in u -> "Q4_K_S"
            "Q4_0"   in u -> "Q4_0"
            "Q3_K_M" in u -> "Q3_K_M"
            "Q2_K"   in u -> "Q2_K"
            "IQ4_NL" in u -> "IQ4_NL"
            "IQ3_M"  in u -> "IQ3_M"
            "F16"    in u -> "F16"
            else          -> "unknown"
        }
    }
}
