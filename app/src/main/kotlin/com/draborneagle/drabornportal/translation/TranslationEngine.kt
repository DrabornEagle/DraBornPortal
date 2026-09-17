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

class TranslationEngine(private val context: Context) : Closeable {
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val translator: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.TURKISH)
            .build()
    )

    suspend fun prepareModel(wifiOnly: Boolean = false) = withContext(Dispatchers.IO) {
        val builder = DownloadConditions.Builder()
        if (wifiOnly) builder.requireWifi()
        Tasks.await(translator.downloadModelIfNeeded(builder.build()))
    }

    suspend fun translateScreenshot(uri: Uri): TranslationResult = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, uri)
        val detected = Tasks.await(recognizer.process(image))
        val overlays = mutableListOf<TranslationOverlayBlock>()

        detected.textBlocks.forEach { block ->
            val bounds = block.boundingBox ?: return@forEach
            val source = normalizeOcr(block.text)
            if (!looksUseful(source)) return@forEach

            val protectedText = GameGlossary.protect(source)
            val raw = Tasks.await(translator.translate(protectedText.text))
            val translated = GameGlossary.restore(raw, protectedText.replacements)
                .replace(Regex("\\s+([,.!?;:])"), "$1")
                .trim()
            if (translated.isBlank()) return@forEach

            overlays += TranslationOverlayBlock(
                left = bounds.left.coerceAtLeast(0),
                top = bounds.top.coerceAtLeast(0),
                right = bounds.right.coerceAtLeast(bounds.left + 1),
                bottom = bounds.bottom.coerceAtLeast(bounds.top + 1),
                source = source,
                translated = translated,
            )
        }

        TranslationResult(
            sourceText = overlays.joinToString("\n") { it.source },
            translatedText = overlays.joinToString("\n") { it.translated },
            hadText = overlays.isNotEmpty(),
            overlays = overlays,
            imageWidth = image.width,
            imageHeight = image.height,
        )
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

    private fun looksUseful(text: String): Boolean {
        if (text.length < 3 || text.length > 1200) return false
        val compact = text.filterNot { it.isWhitespace() }
        if (compact.isEmpty()) return false
        val letters = compact.count { it.isLetter() }
        val latinWords = Regex("[A-Za-z]{2,}").findAll(text).count()
        return letters.toFloat() / compact.length >= 0.48f && latinWords > 0
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
    val overlays: List<TranslationOverlayBlock> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
)

data class ProtectedText(val text: String, val replacements: Map<String, String>)

object GameGlossary {
    private val protectedTerms = listOf(
        "PlayStation Portal", "PlayStation", "PSN", "RuneScape", "Dragonwilds",
        "Dragon Slayer", "Wise Old Man", "Restless Ghost", "Ghostspeak", "Kettan", "Oculus", "Void"
    ).sortedByDescending { it.length }

    fun protect(source: String): ProtectedText {
        var output = source
        val replacements = linkedMapOf<String, String>()
        protectedTerms.forEachIndexed { index, term ->
            val regex = Regex(Regex.escape(term), RegexOption.IGNORE_CASE)
            regex.find(output)?.let { match ->
                val token = "QZX${index}ZXQ"
                output = regex.replace(output, token)
                replacements[token] = match.value
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
