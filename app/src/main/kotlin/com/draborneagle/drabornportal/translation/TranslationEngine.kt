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
import java.io.Closeable

/**
 * Fully local OCR + EN->TR translation pipeline after the ML Kit translation
 * model has been downloaded once. No per-translation paid API is used.
 */
class TranslationEngine(
    private val context: Context,
) : Closeable {
    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val translator: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.TURKISH)
            .build()
    )

    suspend fun prepareModel(wifiOnly: Boolean = false) = withContext(Dispatchers.IO) {
        val conditionsBuilder = DownloadConditions.Builder()
        if (wifiOnly) conditionsBuilder.requireWifi()
        Tasks.await(translator.downloadModelIfNeeded(conditionsBuilder.build()))
    }

    suspend fun translateScreenshot(uri: Uri): TranslationResult = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, uri)
        val detected = Tasks.await(recognizer.process(image))
        val source = normalizeOcr(detected.text)

        if (source.isBlank()) {
            return@withContext TranslationResult(
                sourceText = "",
                translatedText = "",
                hadText = false,
            )
        }

        // Translating line-by-line preserves game subtitle / quest UI structure
        // better than flattening the whole screenshot into one paragraph.
        val translatedLines = source.lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val protectedLine = GameGlossary.protect(line)
                val rawTranslation = Tasks.await(translator.translate(protectedLine.text))
                GameGlossary.restore(rawTranslation, protectedLine.replacements)
            }
            .toList()

        TranslationResult(
            sourceText = source,
            translatedText = translatedLines.joinToString("\n"),
            hadText = true,
        )
    }

    private fun normalizeOcr(raw: String): String {
        val normalized = raw
            .replace('\u00A0', ' ')
            .lineSequence()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() }
            .toList()

        // Remove immediately repeated OCR lines without reordering screen text.
        return buildList {
            normalized.forEach { line ->
                if (lastOrNull() != line) add(line)
            }
        }.joinToString("\n")
    }

    override fun close() {
        recognizer.close()
        translator.close()
    }
}

data class TranslationResult(
    val sourceText: String,
    val translatedText: String,
    val hadText: Boolean,
)

data class ProtectedText(
    val text: String,
    val replacements: Map<String, String>,
)

/**
 * Protects game/product proper nouns from being mangled by generic translation.
 * Later versions will merge these defaults with a per-game user dictionary.
 */
object GameGlossary {
    private val protectedTerms = listOf(
        "PlayStation",
        "PlayStation Portal",
        "PSN",
        "RuneScape",
        "Dragonwilds",
        "Oculus",
    ).sortedByDescending { it.length }

    fun protect(source: String): ProtectedText {
        var output = source
        val replacements = linkedMapOf<String, String>()

        protectedTerms.forEachIndexed { index, term ->
            val regex = Regex(Regex.escape(term), RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(output)) {
                val token = "QZX${index}ZXQ"
                val actual = regex.find(output)?.value ?: term
                output = regex.replace(output, token)
                replacements[token] = actual
            }
        }
        return ProtectedText(output, replacements)
    }

    fun restore(translated: String, replacements: Map<String, String>): String {
        var output = translated
        replacements.forEach { (token, original) ->
            output = output.replace(token, original, ignoreCase = true)
            // Defensive restore for translators that insert a space around digits.
            val relaxed = token.toCharArray().joinToString("\\s*") { Regex.escape(it.toString()) }
            output = output.replace(Regex(relaxed, RegexOption.IGNORE_CASE), original)
        }
        return output.trim()
    }
}
