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

    override suspend fun recognizePage(image: BufferedImage, language: String, dpi: Int): List<OcrWord>? {
        coroutineContext.ensureActive()
        return withContext(Dispatchers.Default) {
            coroutineContext.ensureActive()
            try {
                val tess = Tesseract().apply {
                    setDatapath(tessDataPath)
                    setLanguage(language)
                    setOcrEngineMode(1) // LSTM_ONLY
                    setPageSegMode(3)   // PSM_AUTO
                    setVariable("user_defined_dpi", dpi.toString())
                }
                // RIL_WORD = 3: get per-word text and pixel bounding boxes
                tess.getWords(image, 3).mapNotNull { word ->
                    val bbox = word.boundingBox ?: return@mapNotNull null
                    val text = word.text
                        ?.trim()
                        ?.filter { c -> c.code in 0x20..0xFFFF }
                        ?: return@mapNotNull null
                    if (text.isBlank() || bbox.width <= 0 || bbox.height <= 0) return@mapNotNull null
                    OcrWord(text, bbox.x, bbox.y, bbox.width, bbox.height)
                }.takeIf { it.isNotEmpty() }
            } catch (e: net.sourceforge.tess4j.TesseractException) {
                val msg = e.message ?: ""
                if (msg.contains("Failed loading language") || msg.contains("couldn't load any languages")) {
                    throw Exception(
                        "Tessdata file not found for language '$language' " +
                        "(${language}.traineddata).\n\n" +
                        "Download it from https://github.com/tesseract-ocr/tessdata " +
                        "and place it in appResources/windows-x64/tessdata/"
                    )
                }
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun resolveTessDataPath(): String {
        // 1. Packaged distribution: resources dir set by Compose
        val resourcesDir = System.getProperty("compose.application.resources.dir")
        if (resourcesDir != null) {
            val tessDir = File(resourcesDir, "tessdata")
            if (tessDir.exists()) return tessDir.absolutePath
        }

        // 2. Dev mode: look for appResources/windows-x64/tessdata relative to working dir
        val devTessdata = File("appResources/windows-x64/tessdata")
        if (devTessdata.exists()) return devTessdata.absolutePath

        // 3. Relative tessdata folder in working dir
        val localTess = File("tessdata")
        if (localTess.exists()) return localTess.absolutePath

        // 4. Fallback: let Tess4J use its default extraction path
        return ""
    }
}
