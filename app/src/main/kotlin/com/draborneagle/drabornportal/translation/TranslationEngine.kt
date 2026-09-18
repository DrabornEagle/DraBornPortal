package com.draborneagle.drabornportal.translation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * v0.4.2: Android artık Web ile aynı çalışma düzenini kullanır.
 * Görüntünün tamamı tek istekte görsel algılama + Türkçe çeviri motoruna gönderilir.
 * Böylece Android ve Web aynı metin seçimini, aynı filtrelemeyi ve aynı koordinat tipini alır.
 */
class TranslationEngine(private val context: Context) : Closeable {

    suspend fun prepareModel(wifiOnly: Boolean = false) = withContext(Dispatchers.IO) {
        // Sunucu tarafındaki DrabornEagle oyun çeviri motoru kullanılır; yerel model hazırlığı gerekmez.
        Unit
    }

    suspend fun translateScreenshot(uri: Uri): TranslationResult = withContext(Dispatchers.IO) {
        val original = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            ?: error("Görüntü açılamadı")

        try {
            val originalWidth = original.width
            val originalHeight = original.height
            if (originalWidth <= 0 || originalHeight <= 0) error("Görüntü boyutu geçersiz")

            val encoded = prepareImageForWebCompatibleTranslation(original)
            val payload = requestImageTranslation(encoded.base64, encoded.mimeType)
            val items = payload.optJSONArray("items")
            val overlays = buildList {
                if (items != null) {
                    for (index in 0 until items.length()) {
                        val item = items.optJSONObject(index) ?: continue
                        val source = item.optString("source", "").trim()
                        val translated = item.optString("translated", "").trim()
                        if (source.isBlank() || translated.isBlank()) continue

                        val leftN = item.optDouble("left", 0.0).coerceIn(0.0, 1000.0)
                        val topN = item.optDouble("top", 0.0).coerceIn(0.0, 1000.0)
                        val rightN = item.optDouble("right", 0.0).coerceIn(0.0, 1000.0)
                        val bottomN = item.optDouble("bottom", 0.0).coerceIn(0.0, 1000.0)
                        if (rightN <= leftN || bottomN <= topN) continue

                        val left = (leftN / 1000.0 * originalWidth).roundToInt().coerceIn(0, originalWidth - 1)
                        val top = (topN / 1000.0 * originalHeight).roundToInt().coerceIn(0, originalHeight - 1)
                        val right = (rightN / 1000.0 * originalWidth).roundToInt().coerceIn(left + 1, originalWidth)
                        val bottom = (bottomN / 1000.0 * originalHeight).roundToInt().coerceIn(top + 1, originalHeight)

                        add(
                            TranslationOverlayBlock(
                                left = left,
                                top = top,
                                right = right,
                                bottom = bottom,
                                source = source,
                                translated = translated,
                            )
                        )
                    }
                }
            }.sortedWith(compareBy<TranslationOverlayBlock> { it.top }.thenBy { it.left })

            TranslationResult(
                sourceText = overlays.joinToString("\n\n") { it.source },
                translatedText = overlays.joinToString("\n\n") { it.translated },
                hadText = overlays.isNotEmpty(),
                overlays = overlays,
                imageWidth = originalWidth,
                imageHeight = originalHeight,
            )
        } finally {
            original.recycle()
        }
    }

    private fun prepareImageForWebCompatibleTranslation(bitmap: Bitmap): EncodedImage {
        val maxSide = 1800
        val largest = maxOf(bitmap.width, bitmap.height)
        val ratio = if (largest > maxSide) maxSide.toFloat() / largest.toFloat() else 1f
        val targetWidth = maxOf(1, (bitmap.width * ratio).roundToInt())
        val targetHeight = maxOf(1, (bitmap.height * ratio).roundToInt())

        val prepared = if (targetWidth != bitmap.width || targetHeight != bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        } else {
            bitmap
        }

        return try {
            val bytes = ByteArrayOutputStream().use { output ->
                if (!prepared.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                    error("Görüntü hazırlanamadı")
                }
                output.toByteArray()
            }
            EncodedImage(
                base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                mimeType = "image/jpeg",
            )
        } finally {
            if (prepared !== bitmap) prepared.recycle()
        }
    }

    private fun requestImageTranslation(imageBase64: String, mimeType: String): JSONObject {
        val body = JSONObject()
            .put("mode", "image")
            .put("mime_type", mimeType)
            .put("image_base64", imageBase64)

        val connection = (URL(TRANSLATION_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 35_000
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-DraBornPortal-Client", "android")
        }

        return try {
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Çeviri servisi HTTP $code")
            val response = JSONObject(responseText)
            if (!response.optBoolean("ok", false)) error("Çeviri servisi geçersiz yanıt verdi")
            response
        } finally {
            connection.disconnect()
        }
    }

    override fun close() = Unit

    private data class EncodedImage(
        val base64: String,
        val mimeType: String,
    )

    companion object {
        private const val TRANSLATION_ENDPOINT =
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

/**
 * Eski test/uyumluluk yüzeyi korunuyor. Ana çeviri akışı artık bunu kullanmıyor.
 */
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
