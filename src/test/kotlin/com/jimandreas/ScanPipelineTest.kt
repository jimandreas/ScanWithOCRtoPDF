package com.jimandreas

import com.jimandreas.ocr.TesseractOcrEngine
import com.jimandreas.pdf.PdfBuilder
import com.jimandreas.state.ColorMode
import com.jimandreas.state.PaperSize
import com.jimandreas.state.PdfMetadata
import com.jimandreas.state.ScanSettings
import com.jimandreas.util.ImageProcessor
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.PDFTextStripperByArea
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScanPipelineTest {

    @Test
    fun simpleBitmapProducesSearchablePdfWithCorrectTextAndPosition() {
        runBlocking {
            val dpi = 100
            val image = createTestBitmap(dpi)

            // JPEG compression — same path as production pipeline
            val jpegBytes = ImageProcessor.toJpegBytes(image, 0.95f, ColorMode.FULL_COLOR)

            // Real Tesseract OCR
            val ocrEngine = TesseractOcrEngine()
            val words = ocrEngine.recognizePage(image, "eng", dpi)

            assertNotNull(words, "Tesseract returned null — check that eng.traineddata is present")
            val helloWord = words.find { it.text.equals("Hello", ignoreCase = true) }
            assertNotNull(helloWord, "OCR must recognize 'Hello'; got: ${words.map { it.text }}")

            // Build PDF
            val outFile = Files.createTempFile("scan_test_", ".pdf").toFile()
            try {
                PdfBuilder.build(
                    outputFile = outFile,
                    jpegPages = listOf(jpegBytes),
                    ocrWords = listOf(words),
                    pageDpis = listOf(dpi),
                    metadata = PdfMetadata(),
                    scanSettings = ScanSettings(dpi = dpi, paperSize = PaperSize.LETTER)
                )

                Loader.loadPDF(outFile).use { doc ->
                    // Exactly one page
                    assertEquals(1, doc.numberOfPages)

                    // Full-text extraction must contain "Hello"
                    val allText = PDFTextStripper().getText(doc)
                    assertTrue(
                        allText.contains("Hello", ignoreCase = true),
                        "PDF text must contain 'Hello'; extracted: $allText"
                    )

                    // Position check: "Hello" drawn at baseline (150, 300) in a 850×1100 image.
                    // PDF scale = 72/100 = 0.72 → pdfX ≈ 108 pt, rendering Y from top ≈ 234 pt.
                    // Search region covers upper-left 400×350 pt of the 612×792 pt Letter page.
                    val page = doc.getPage(0)
                    val stripper = PDFTextStripperByArea()
                    stripper.addRegion(
                        "upper-left",
                        Rectangle2D.Float(0f, 0f, 400f, 350f)
                    )
                    stripper.extractRegions(page)
                    val regionText = stripper.getTextForRegion("upper-left")
                    assertTrue(
                        regionText.contains("Hello", ignoreCase = true),
                        "PDF text for 'Hello' must appear in upper-left region; region text: $regionText"
                    )
                }
            } finally {
                outFile.delete()
            }
        }
    }

    private fun createTestBitmap(dpi: Int): BufferedImage {
        val widthPx  = (8.5 * dpi).toInt()   // 850 at 100 DPI
        val heightPx = (11.0 * dpi).toInt()  // 1100 at 100 DPI
        val image = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.fillRect(0, 0, widthPx, heightPx)
        g.color = Color.BLACK
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, dpi)  // 1 inch tall at chosen DPI
        g.drawString("Hello", 150, 300)                  // baseline at (150, 300)
        g.dispose()
        return image
    }
}
