package com.jimandreas

import com.jimandreas.ocr.OcrWord
import com.jimandreas.ocr.TesseractOcrEngine
import com.jimandreas.pdf.PdfBuilder
import com.jimandreas.state.ColorMode
import com.jimandreas.state.PaperSize
import com.jimandreas.state.PdfMetadata
import com.jimandreas.state.ScanSettings
import com.jimandreas.util.ImageProcessor
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.PDFTextStripperByArea
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end integration tests for the scan → OCR → PDF pipeline.
 *
 * Each test creates a synthetic bitmap with known text, runs real Tesseract OCR,
 * builds a PDF via PdfBuilder, then reads it back with PDFBox to verify both text
 * content and invisible overlay position.
 *
 * Page geometry (Letter, 100 DPI):
 *   image  : 850 × 1100 px
 *   PDF    : 612 × 792 pt  (scale = 72/100 = 0.72)
 *   quadrants (rendering coords — Y down from top):
 *     upper-left  : x [0,  306], y [0,   396]
 *     upper-right : x [306,612], y [0,   396]
 *     lower-left  : x [0,  306], y [396, 792]
 *     lower-right : x [306,612], y [396, 792]
 */
class ScanPipelineTest {

    private val dpi = 100
    private val ocrEngine = TesseractOcrEngine()

    // -------------------------------------------------------------------------
    // Corner-position tests
    // -------------------------------------------------------------------------

    @Test
    fun topLeftTextIsRecognizedAndPositioned() {
        // "Hello" baseline at image (150, 300) → PDF rendering (108, 216) — upper-left
        runBlocking {
            val image = blankPage()
            drawText(image, "Hello", 150, 300, dpi)
            val words = ocr(image)
            assertWord(words, "Hello")

            withPdf(image, words) { doc ->
                assertEquals(1, doc.numberOfPages)
                assertTrue(fullText(doc).contains("Hello", ignoreCase = true),
                    "full-text must contain 'Hello'")
                assertTrue(
                    regionText(doc, UPPER_LEFT).contains("Hello", ignoreCase = true),
                    "expected 'Hello' in upper-left quadrant"
                )
            }
        }
    }

    @Test
    fun topRightTextIsRecognizedAndPositioned() {
        // "Tiger" baseline at image (450, 120) → PDF rendering (324, 86) — upper-right
        runBlocking {
            val image = blankPage()
            drawText(image, "Tiger", 450, 120, dpi)
            val words = ocr(image)
            assertWord(words, "Tiger")

            withPdf(image, words) { doc ->
                assertTrue(fullText(doc).contains("Tiger", ignoreCase = true),
                    "full-text must contain 'Tiger'")
                assertTrue(
                    regionText(doc, UPPER_RIGHT).contains("Tiger", ignoreCase = true),
                    "expected 'Tiger' in upper-right quadrant"
                )
            }
        }
    }

    @Test
    fun bottomLeftTextIsRecognizedAndPositioned() {
        // "Ocean" baseline at image (50, 1000) → PDF rendering (36, 720) — lower-left
        runBlocking {
            val image = blankPage()
            drawText(image, "Ocean", 50, 1000, dpi)
            val words = ocr(image)
            assertWord(words, "Ocean")

            withPdf(image, words) { doc ->
                assertTrue(fullText(doc).contains("Ocean", ignoreCase = true),
                    "full-text must contain 'Ocean'")
                assertTrue(
                    regionText(doc, LOWER_LEFT).contains("Ocean", ignoreCase = true),
                    "expected 'Ocean' in lower-left quadrant"
                )
            }
        }
    }

    @Test
    fun bottomRightTextIsRecognizedAndPositioned() {
        // "Abyss" baseline at image (450, 1000) → PDF rendering (324, 720) — lower-right
        runBlocking {
            val image = blankPage()
            drawText(image, "Abyss", 450, 1000, dpi)
            val words = ocr(image)
            assertWord(words, "Abyss")

            withPdf(image, words) { doc ->
                assertTrue(fullText(doc).contains("Abyss", ignoreCase = true),
                    "full-text must contain 'Abyss'")
                assertTrue(
                    regionText(doc, LOWER_RIGHT).contains("Abyss", ignoreCase = true),
                    "expected 'Abyss' in lower-right quadrant"
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Table test
    // -------------------------------------------------------------------------

    /**
     * Draws a 2×2 table with ruled borders and verifies that all four cell words
     * ("Alpha", "Beta", "Gamma", "Delta") appear in the PDF text layer.
     *
     * Table layout (100 DPI, 50pt font):
     *   origin : (100, 500) px
     *   cell   : 200 × 80 px — 2 columns × 2 rows = 400 × 160 px total
     *   text baseline : 60 px below each cell's top edge
     */
    @Test
    fun tableTextIsRecognized() {
        runBlocking {
            val image = blankPage()
            drawTable(image)
            val words = ocr(image)
            assertNotNull(words, "OCR returned null for table image — check eng.traineddata")

            withPdf(image, words) { doc ->
                val text = fullText(doc)
                for (word in listOf("Alpha", "Beta", "Gamma", "Delta")) {
                    assertTrue(text.contains(word, ignoreCase = true),
                        "PDF must contain '$word'; full text: $text")
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** 850×1100 white bitmap — Letter paper at 100 DPI. */
    private fun blankPage(): BufferedImage {
        val img = BufferedImage((8.5 * dpi).toInt(), (11.0 * dpi).toInt(), BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, img.width, img.height)
        g.dispose()
        return img
    }

    /** Renders [text] in black onto [image] at the given baseline, using a [fontPt]-pt font. */
    private fun drawText(image: BufferedImage, text: String, x: Int, y: Int, fontPt: Int) {
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.BLACK
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, fontPt)
        g.drawString(text, x, y)
        g.dispose()
    }

    /**
     * Draws a 2×2 ruled table onto [image] with 50pt cell text.
     *
     *   origin (100, 500), cells 200×80 px
     *   Row 0 baselines : y = 560   Row 1 baselines : y = 640
     *   Col 0 text x    : 110       Col 1 text x    : 310
     */
    private fun drawTable(image: BufferedImage) {
        val left = 100;  val top  = 500
        val cellW = 200; val cellH = 80
        val cols  = 2;   val rows  = 2
        val cellWords = arrayOf(arrayOf("Alpha", "Beta"), arrayOf("Gamma", "Delta"))

        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.BLACK
        g.stroke = BasicStroke(2f)

        // Draw grid
        for (row in 0..rows) {
            val y = top + row * cellH
            g.drawLine(left, y, left + cols * cellW, y)
        }
        for (col in 0..cols) {
            val x = left + col * cellW
            g.drawLine(x, top, x, top + rows * cellH)
        }

        // Draw cell text (50pt font — dpi/2 — to fit comfortably within each cell)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, dpi / 2)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val tx = left + col * cellW + 10     // 10 px left-padding inside cell
                val ty = top  + row * cellH + 60     // baseline 60 px below cell top
                g.drawString(cellWords[row][col], tx, ty)
            }
        }
        g.dispose()
    }

    private suspend fun ocr(image: BufferedImage): List<OcrWord>? =
        ocrEngine.recognizePage(image, "eng", dpi)

    private fun assertWord(words: List<OcrWord>?, expected: String) {
        assertNotNull(words, "OCR returned null — check eng.traineddata")
        val found = words.find { it.text.equals(expected, ignoreCase = true) }
        assertNotNull(found, "OCR must recognize '$expected'; got: ${words.map { it.text }}")
    }

    /**
     * Runs the JPEG → PdfBuilder step, opens the resulting PDF, invokes [block], then
     * deletes the temp file.
     */
    private fun withPdf(image: BufferedImage, words: List<OcrWord>?, block: (PDDocument) -> Unit) {
        val jpegBytes = ImageProcessor.toJpegBytes(image, 0.95f, ColorMode.FULL_COLOR)
        val outFile: File = Files.createTempFile("scan_test_", ".pdf").toFile()
        try {
            PdfBuilder.build(
                outputFile = outFile,
                jpegPages  = listOf(jpegBytes),
                ocrWords   = listOf(words),
                pageDpis   = listOf(dpi),
                metadata   = PdfMetadata(),
                scanSettings = ScanSettings(dpi = dpi, paperSize = PaperSize.LETTER)
            )
            Loader.loadPDF(outFile).use(block)
        } finally {
            outFile.delete()
        }
    }

    private fun fullText(doc: PDDocument): String = PDFTextStripper().getText(doc)

    /**
     * Extracts text from a page region.
     * [rect] uses rendering coordinates: origin = top-left, Y increases downward.
     */
    private fun regionText(doc: PDDocument, rect: Rectangle2D.Float): String {
        val stripper = PDFTextStripperByArea()
        stripper.addRegion("r", rect)
        stripper.extractRegions(doc.getPage(0))
        return stripper.getTextForRegion("r")
    }

    // -------------------------------------------------------------------------
    // Page quadrant regions — Letter (612×792 pt), rendering coords (Y down)
    // -------------------------------------------------------------------------
    companion object {
        // Rectangle2D.Float(x, y, width, height)
        val UPPER_LEFT  = Rectangle2D.Float(  0f,   0f, 306f, 396f)
        val UPPER_RIGHT = Rectangle2D.Float(306f,   0f, 306f, 396f)
        val LOWER_LEFT  = Rectangle2D.Float(  0f, 396f, 306f, 396f)
        val LOWER_RIGHT = Rectangle2D.Float(306f, 396f, 306f, 396f)
    }
}
