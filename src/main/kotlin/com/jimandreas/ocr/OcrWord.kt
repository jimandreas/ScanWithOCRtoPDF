package com.jimandreas.ocr

/**
 * A single word recognized by the OCR engine, with its pixel bounding box
 * in the source image coordinate system (origin = top-left, y increases downward).
 */
data class OcrWord(
    val text: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)
