package com.draborneagle.drabornportal.translation

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.net.HttpURLConnection
import java.net.URL

class TranslationEngine(private val context: Context) : Closeable {
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val fallbackTranslator: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.TURKISH)
            .build()
    )

    suspend fun prepareModel(wifiOnly: Boolean = false) = withContext(Dispatchers.IO) {
        // Gemini ana motordur; uygulama açılışında yerel model indirmesini beklemeyiz.
        Unit
    }

    suspend fun translateScreenshot(uri: Uri): TranslationResult = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, uri)
        val detected = Tasks.await(recognizer.process(image))

        data class Candidate(
            val left: Int,
            val top: Int,
            val right: Int,
            val bottom: Int,
            val source: String,
        )

        val candidates = detected.textBlocks.mapNotNull { block ->
            val bounds = block.boundingBox ?: return@mapNotNull null
            val source = normalizeOcr(block.text)
                .lineSequence()
                .filterNot { isUiNoise(it) }
                .joinToString("\n")
                .trim()
            if (!looksUseful(source)) return@mapNotNull null
            Candidate(
                left = bounds.left.coerceAtLeast(0),
                top = bounds.top.coerceAtLeast(0),
                right = bounds.right.coerceAtLeast(bounds.left + 1),
                bottom = bounds.bottom.coerceAtLeast(bounds.top + 1),
                source = source,
            )
        }.sortedWith(compareBy<Candidate> { it.top }.thenBy { it.left })

        val geminiTranslations = translateBatchWithGemini(candidates.map { it.source })
        val overlays = mutableListOf<TranslationOverlayBlock>()

        candidates.forEachIndexed { index, candidate ->
            val geminiText = geminiTranslations[index].orEmpty().trim()
            val translated = if (geminiText.isNotBlank()) {
                geminiText
            } else if (geminiTranslations.isEmpty()) {
                translateFallback(candidate.source)
            } else {
                ""
            }
            if (!looksUsefulTranslation(translated)) return@forEachIndexed
            overlays += TranslationOverlayBlock(
                left = candidate.left,
                top = candidate.top,
                right = candidate.right,
                bottom = candidate.bottom,
                source = candidate.source,
                translated = translated,
            )
        }

        TranslationResult(
            sourceText = overlays.joinToString("\n\n") { it.source },
            translatedText = overlays.joinToString("\n\n") { it.translated },
            hadText = overlays.isNotEmpty(),
            overlays = overlays,
            imageWidth = image.width,
            imageHeight = image.height,
        )
    }

    private fun translateBatchWithGemini(sources: List<String>): Map<Int, String> {
        if (sources.isEmpty()) return emptyMap()
        return runCatching {
            val blocks = JSONArray()
            sources.forEach { source -> blocks.put(JSONObject().put("source", source)) }
            val requestBody = JSONObject()
                .put("mode", "blocks")
                .put("blocks", blocks)

            val connection = (URL(GEMINI_PROXY_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-DraBornPortal-Client", "android")
            }
            connection.outputStream.use { it.write(requestBody.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) error("Gemini HTTP ${connection.responseCode}")
            val payload = JSONObject(responseText)
            if (!payload.optBoolean("ok", false)) error("Gemini yanıtı geçersiz")
            val items = payload.optJSONArray("items") ?: JSONArray()
            buildMap {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val index = item.optInt("index", -1)
                    val translated = item.optString("translated", "").trim()
                    if (index >= 0 && translated.isNotBlank()) put(index, translated)
                }
            }
        }.getOrElse { emptyMap() }
    }

    private fun translateFallback(source: String): String {
        return runCatching {
            Tasks.await(fallbackTranslator.downloadModelIfNeeded(DownloadConditions.Builder().build()))
            val protectedText = GameGlossary.protect(source)
            val raw = Tasks.await(fallbackTranslator.translate(protectedText.text))
            GameGlossary.restore(raw, protectedText.replacements)
                .replace(Regex("\\s+([,.!?;:])"), "$1")
                .replace(Regex("[ \\t]{2,}"), " ")
                .trim()
        }.getOrDefault("")
    }

    private fun normalizeOcr(raw: String): String {
        val lines = raw.replace('\u00A0', ' ')
            .lineSequence()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .toList()
        return buildList {
            lines.forEach { line -> if (lastOrNull() != line) add(line) }
        }.joinToString("\n").trim()
    }

    private fun isUiNoise(text: String): Boolean {
        val normalized = text.lowercase()
            .replace(Regex("[^a-z ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return true

        val words = normalized.split(" ").filter { it.isNotBlank() }
        val uiWords = setOf(
            "quests", "quest", "active", "completed", "map", "skills", "skill",
            "spell", "book", "journal", "navigate", "set", "unset", "marker",
            "ping", "move", "zoom", "back", "inventory", "crafting", "settings",
            "menu", "close", "open", "select", "cancel", "confirm", "x", "a", "b", "l", "r"
        )
        if (words.isNotEmpty() && words.size <= 12 && words.all { it in uiWords }) return true

        val mapWords = setOf("valley", "woods", "forest", "river", "lake", "mountain", "mountains")
        if (words.size in 1..4 && words.lastOrNull() in mapWords) return true
        return false
    }

    private fun looksUseful(text: String): Boolean {
        if (text.length < 3 || text.length > 1100) return false
        val compact = text.filterNot { it.isWhitespace() }
        if (compact.isEmpty()) return false
        val letters = compact.count { it.isLetter() }
        val words = Regex("[A-Za-z]{2,}").findAll(text).map { it.value }.toList()
        val singleLetters = Regex("(?<![A-Za-z])[A-Za-z](?![A-Za-z])").findAll(text).count()
        if (words.isEmpty()) return false
        if (singleLetters > maxOf(3, words.size * 2)) return false
        return letters.toFloat() / compact.length >= 0.55f
    }

    private fun looksUsefulTranslation(text: String): Boolean {
        if (text.isBlank()) return false
        return text.count { it.isLetter() } >= 2
    }

    override fun close() {
        recognizer.close()
        fallbackTranslator.close()
    }

    companion object {
        private const val GEMINI_PROXY_URL =
            "https://guuwomvszlwhkmstewfl.supabase.co/functions/v1/dkd-portal-gemini-translate"
    }
}

data class TranslationResult(
    val sourceText: String,
    val translatedText: String,
    val hadText: Boolean,
    val overlays: List<TranslationOverlayBlock> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
)

data class ProtectedText(val text: String, val replacements: Map<String, String>)

object GameGlossary {
    private val protectedTerms = listOf(
        "PlayStation Portal", "PlayStation", "PSN", "RuneScape", "Dragonwilds",
        "Wise Old Man", "Ghostspeak", "Kettan", "Cathan", "Oculus", "Void"
    ).sortedByDescending { it.length }

    fun protect(source: String): ProtectedText {
        var output = source
        val replacements = linkedMapOf<String, String>()
        protectedTerms.forEachIndexed { index, term ->
            val regex = Regex(Regex.escape(term), RegexOption.IGNORE_CASE)
            regex.find(output)?.let {
                val token = "QZX${index}ZXQ"
                output = regex.replace(output, token)
                replacements[token] = it.value
            }
        }
        return ProtectedText(output, replacements)
    }

    fun restore(translated: String, replacements: Map<String, String>): String {
        var output = translated
        replacements.forEach { (token, original) ->
            output = output.replace(token, original, ignoreCase = true)
            val relaxed = token.toCharArray().joinToString("\\s*") { Regex.escape(it.toString()) }
            output = output.replace(Regex(relaxed, RegexOption.IGNORE_CASE), original)
        }
        return output.trim()
    }
}
