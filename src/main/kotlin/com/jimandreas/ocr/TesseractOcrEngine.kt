package com.jimandreas.ocr

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.sourceforge.tess4j.Tesseract
import java.awt.image.BufferedImage
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers

/**
 * Tess4J Tesseract OCR engine with cooperative coroutine cancellation.
 *
 * Tessdata is resolved from:
 *   1. The compose application resources directory (packaged distribution)
 *   2. A bundled tessdata folder relative to the working directory (dev mode)
 */
class TesseractOcrEngine : OcrEngine {

    private val tessDataPath: String by lazy { resolveTessDataPath() }

    override suspend fun recognizePage(image: BufferedImage, language: String): String? {
        coroutineContext.ensureActive()
        return withContext(Dispatchers.Default) {
            coroutineContext.ensureActive()
            try {
                val tess = Tesseract().apply {
                    setDatapath(tessDataPath)
                    setLanguage(language)
                    setOcrEngineMode(1) // LSTM_ONLY
                    setPageSegMode(3)   // PSM_AUTO
                }
                tess.doOCR(image)?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                // If tessdata not found, return null (OCR disabled gracefully)
                null
            }
        }
    }

    private fun resolveTessDataPath(): String {
        // 1. Packaged distribution: resources dir set by Compose
        val resourcesDir = System.getProperty("compose.application.resources.dir")
        if (resourcesDir != null) {
            val tessDir = File(resourcesDir, "tessdata")
            if (tessDir.exists()) return tessDir.parent
        }

        // 2. Dev mode: look for appResources/windows-x64/tessdata relative to working dir
        val devPath = File("appResources/windows-x64")
        if (devPath.exists()) return devPath.absolutePath

        // 3. Relative tessdata folder in working dir
        val localTess = File("tessdata")
        if (localTess.exists()) return File(".").absolutePath

        // 4. Fallback: let Tess4J use its default extraction path
        return ""
    }
}
