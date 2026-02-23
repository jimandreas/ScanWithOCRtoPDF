package com.jimandreas.pdf

import com.jimandreas.ocr.OcrWord
import com.jimandreas.state.PdfMetadata
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont
import org.apache.pdfbox.pdmodel.font.encoding.WinAnsiEncoding
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode
import org.apache.pdfbox.util.Matrix
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * Assembles a PDF/A-1b compliant document from JPEG image bytes and optional OCR text.
 *
 * Invisible text (RenderingMode.NEITHER) is layered over each page so Ctrl+F works
 * while the scanned image remains the visual content.
 */
object PdfBuilder {

    fun build(
        outputFile: File,
        jpegPages: List<ByteArray>,
        ocrWords: List<List<OcrWord>?>,
        pageDpis: List<Int>,
        pageSizes: List<Pair<Int, Int>>,
        metadata: PdfMetadata
    ) {
        PDDocument().use { document ->
            addOutputIntent(document)
            addPages(document, jpegPages, ocrWords, pageDpis, pageSizes)
            PdfMetadataWriter.write(document, metadata)
            document.save(outputFile)
        }
    }

    private fun addOutputIntent(document: PDDocument) {
        val iccBytes = loadSrgbIcc() ?: return
        val intent = PDOutputIntent(document, ByteArrayInputStream(iccBytes))
        intent.setInfo("sRGB IEC61966-2.1")
        intent.setOutputCondition("sRGB")
        intent.setOutputConditionIdentifier("sRGB")
        intent.setRegistryName("http://www.color.org")
        document.documentCatalog.addOutputIntent(intent)
    }

    private fun loadSrgbIcc(): ByteArray? = try {
        PdfBuilder::class.java.getResourceAsStream("/srgb.icc")?.readBytes()
    } catch (_: Exception) { null }

    private fun addPages(
        document: PDDocument,
        jpegPages: List<ByteArray>,
        ocrWords: List<List<OcrWord>?>,
        pageDpis: List<Int>,
        pageSizes: List<Pair<Int, Int>>
    ) {
        val font: PDFont? = loadOcrFont(document)

        jpegPages.forEachIndexed { i, jpegBytes ->
            val dpi = pageDpis.getOrElse(i) { 300 }
            val (pixelW, pixelH) = pageSizes.getOrElse(i) { Pair(2550, 3300) }
            val pageWidthPt  = (pixelW * 72.0 / dpi).toFloat()
            val pageHeightPt = (pixelH * 72.0 / dpi).toFloat()
            val mediaBox = PDRectangle(pageWidthPt, pageHeightPt)

            val page = PDPage(mediaBox)
            document.addPage(page)

            val pdImage = PDImageXObject.createFromByteArray(document, jpegBytes, "page_$i")

            PDPageContentStream(document, page, PDPageContentStream.AppendMode.OVERWRITE, true, false).use { cs ->
                // Layer 1: scanned image filling the entire page
                cs.drawImage(pdImage, 0f, 0f, pageWidthPt, pageHeightPt)

                // Layer 2: invisible OCR text overlay at exact word positions
                val words = ocrWords.getOrNull(i)
                if (words != null && font != null) {
                    drawWordOverlay(cs, font, words, pageWidthPt, pageHeightPt, dpi)
                }

            }
        }
    }

    /**
     * Loads NotoSans as a PDTrueTypeFont with WinAnsiEncoding (simple 8-bit font).
     *
     * PDType0Font with Identity-H writes glyph IDs as character codes. NotoSans digit
     * glyphs sit at GIDs 4540+; without a correct ToUnicode CMap, PDF viewers decode
     * those codes as Hangul Jamo (U+11BC…). PDTrueTypeFont with WinAnsiEncoding writes
     * the WinAnsi code point (= ASCII for 0x20–0x7E) directly into the stream, so '0'
     * (0x30) stays 0x30 for any viewer.
     */
    private fun loadOcrFont(document: PDDocument): PDFont? = try {
        val stream: InputStream? =
            PdfBuilder::class.java.getResourceAsStream("/fonts/NotoSans-Regular.ttf")
                ?: PdfBuilder::class.java.getResourceAsStream("/NotoSans-Regular.ttf")
        stream?.let { PDTrueTypeFont.load(document, it, WinAnsiEncoding.INSTANCE) }
    } catch (_: Exception) { null }

    /**
     * Places each OCR word as invisible (RenderingMode.NEITHER) text at its exact
     * pixel bounding-box position, scaled and horizontally stretched to match the
     * word's width in the scanned image.
     *
     * Coordinate mapping:
     *   image: (0,0) = top-left,  y increases downward, units = pixels
     *   PDF:   (0,0) = bottom-left, y increases upward,  units = points
     *   scale = 72 / dpi  (pixels → points)
     *   pdfY  = pageHeight − (imageY + wordHeight) * scale
     */
    private fun drawWordOverlay(
        cs: PDPageContentStream,
        font: PDFont,
        words: List<OcrWord>,
        pageWidth: Float,
        pageHeight: Float,
        dpi: Int
    ) {
        val scale = 72.0f / dpi

        for (word in words) {
            val pdfX      = word.x * scale
            val pdfY      = pageHeight - (word.y + word.height) * scale
            val pdfWidth  = word.width * scale
            val fontSize  = word.height * scale

            if (fontSize < 1f || pdfWidth < 1f || pdfX < 0f || pdfY < 0f) continue
            if (pdfX > pageWidth || pdfY > pageHeight) continue

            try {
                // WinAnsiEncoding covers 0x20–0xFF; drop anything outside that range
                val safeText = word.text.filter { c -> c.code in 0x20..0xFF }
                if (safeText.isBlank()) continue

                // Stretch the word horizontally to exactly cover its bounding box width
                val textWidth = font.getStringWidth(safeText) / 1000f * fontSize
                val hScale = if (textWidth > 0f) (pdfWidth / textWidth * 100f).coerceIn(10f, 500f) else 100f

                cs.beginText()
                cs.setFont(font, fontSize)
                cs.setRenderingMode(RenderingMode.NEITHER)
                cs.setHorizontalScaling(hScale)
                cs.setTextMatrix(Matrix.getTranslateInstance(pdfX, pdfY))
                cs.showText(safeText)
                cs.endText()
            } catch (_: Exception) {}
        }
    }
}
