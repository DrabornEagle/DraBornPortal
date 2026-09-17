package com.draborneagle.drabornportal.translation

/** Screen-space OCR/translation block. Coordinates use original screenshot pixels. */
data class TranslationOverlayBlock(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val source: String,
    val translated: String,
)
