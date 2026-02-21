package com.jimandreas.ocr

import java.awt.image.BufferedImage

interface OcrEngine {
    suspend fun recognizePage(image: BufferedImage, language: String, dpi: Int = 300): List<OcrWord>?
}
