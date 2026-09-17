package com.draborneagle.drabornportal.translation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TranslationHistoryItem(
    val source: String,
    val translated: String,
    val createdAt: Long,
)

class TranslationHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("dkd_translation_history", Context.MODE_PRIVATE)

    fun load(): List<TranslationHistoryItem> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        TranslationHistoryItem(
                            source = item.optString("source"),
                            translated = item.optString("translated"),
                            createdAt = item.optLong("createdAt"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(source: String, translated: String): List<TranslationHistoryItem> {
        if (source.isBlank() || translated.isBlank()) return load()
        val current = load().toMutableList()

        // Avoid storing the exact same capture repeatedly while polling.
        current.removeAll { it.source == source }
        current.add(
            0,
            TranslationHistoryItem(
                source = source,
                translated = translated,
                createdAt = System.currentTimeMillis(),
            )
        )
        val trimmed = current.take(MAX_ITEMS)
        save(trimmed)
        return trimmed
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun save(items: List<TranslationHistoryItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("source", item.source)
                    .put("translated", item.translated)
                    .put("createdAt", item.createdAt)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "items"
        const val MAX_ITEMS = 50
    }
}
