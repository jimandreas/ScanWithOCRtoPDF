package com.jimandreas.pdf

import com.jimandreas.state.PdfMetadata
import com.jimandreas.state.ScanSettings
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
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
        ocrTexts: List<String?>,
        metadata: PdfMetadata,
        scanSettings: ScanSettings
    ) {
        PDDocument().use { document ->
            addOutputIntent(document)
            addPages(document, jpegPages, ocrTexts, scanSettings)
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
        ocrTexts: List<String?>,
        scanSettings: ScanSettings
    ) {
        val font: PDType0Font? = loadNotoFont(document)

        jpegPages.forEachIndexed { i, jpegBytes ->
            val pageWidthPt = (scanSettings.paperSize.widthInches * 72.0).toFloat()
            val pageHeightPt = (scanSettings.paperSize.heightInches * 72.0).toFloat()
            val mediaBox = PDRectangle(pageWidthPt, pageHeightPt)

            val page = PDPage(mediaBox)
            document.addPage(page)

            val pdImage = PDImageXObject.createFromByteArray(document, jpegBytes, "page_$i")

            PDPageContentStream(document, page, PDPageContentStream.AppendMode.OVERWRITE, true, false).use { cs ->
                // Layer 1: scanned image filling the entire page
                cs.drawImage(pdImage, 0f, 0f, pageWidthPt, pageHeightPt)

                // Layer 2: invisible OCR text overlay
                val text = ocrTexts.getOrNull(i)
                if (text != null && font != null && text.isNotBlank()) {
                    drawInvisibleText(cs, font, text, pageHeightPt)
                }
            }
        }
    }

    private fun loadNotoFont(document: PDDocument): PDType0Font? = try {
        val stream: InputStream? =
            PdfBuilder::class.java.getResourceAsStream("/fonts/NotoSans-Regular.ttf")
                ?: PdfBuilder::class.java.getResourceAsStream("/NotoSans-Regular.ttf")
        stream?.let { PDType0Font.load(document, it, true) }
    } catch (_: Exception) { null }

    private fun drawInvisibleText(
        cs: PDPageContentStream,
        font: PDType0Font,
        ocrText: String,
        pageHeight: Float
    ) {
        val fontSize = 10f
        val leading = 12f

        cs.beginText()
        cs.setFont(font, fontSize)
        // Rendering mode NEITHER = invisible (no fill, no stroke) — text is still searchable
        cs.setRenderingMode(RenderingMode.NEITHER)
        cs.setTextMatrix(Matrix.getTranslateInstance(0f, pageHeight - leading))
        cs.setLeading(leading)

        ocrText.lines().take(500).forEach { line ->
            // Keep only printable BMP characters
            val safe = line.filter { c -> c.code in 0x20..0xFFFF }.take(500)
            if (safe.isNotBlank()) {
                try { cs.showText(safe) } catch (_: Exception) {}
            }
            cs.newLine()
        }
        cs.endText()
    }
}
