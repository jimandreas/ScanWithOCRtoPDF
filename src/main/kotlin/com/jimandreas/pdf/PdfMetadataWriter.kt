package com.jimandreas.pdf

import com.jimandreas.state.PdfMetadata
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import org.apache.pdfbox.pdmodel.common.PDMetadata
import org.apache.xmpbox.XMPMetadata
import org.apache.xmpbox.schema.DublinCoreSchema
import org.apache.xmpbox.schema.PDFAIdentificationSchema
import org.apache.xmpbox.schema.XMPBasicSchema
import org.apache.xmpbox.xml.XmpSerializer
import java.io.ByteArrayOutputStream
import java.util.Calendar

object PdfMetadataWriter {

    fun write(document: PDDocument, metadata: PdfMetadata) {
        writeDocInfo(document, metadata)
        writeXmp(document, metadata)
    }

    private fun writeDocInfo(document: PDDocument, metadata: PdfMetadata) {
        document.documentInformation = PDDocumentInformation().also { info ->
            if (metadata.author.isNotBlank()) info.author = metadata.author
            if (metadata.title.isNotBlank()) info.title = metadata.title
            if (metadata.keywords.isNotBlank()) info.keywords = metadata.keywords
            info.creator = "ScanWithOCRtoPDF"
            info.producer = "Apache PDFBox 3"
            info.creationDate = Calendar.getInstance()
            info.modificationDate = Calendar.getInstance()
        }
    }

    private fun writeXmp(document: PDDocument, metadata: PdfMetadata) {
        val xmp = XMPMetadata.createXMPMetadata()

        // PDF/A-1b identification (required for conformance)
        val pdfaId: PDFAIdentificationSchema = xmp.createAndAddPDFAIdentificationSchema()
        pdfaId.part = 1
        pdfaId.conformance = "B"

        // Dublin Core
        val dc: DublinCoreSchema = xmp.createAndAddDublinCoreSchema()
        dc.addDate(Calendar.getInstance())
        if (metadata.title.isNotBlank()) dc.title = metadata.title
        if (metadata.author.isNotBlank()) dc.addCreator(metadata.author)
        if (metadata.keywords.isNotBlank()) {
            metadata.keywords.split(",", ";")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { dc.addSubject(it) }
        }

        // XMP Basic
        val xmpBasic: XMPBasicSchema = xmp.createAndAddXMPBasicSchema()
        xmpBasic.creatorTool = "ScanWithOCRtoPDF"
        xmpBasic.createDate = Calendar.getInstance()
        xmpBasic.modifyDate = Calendar.getInstance()

        // Serialize and attach to catalog
        val baos = ByteArrayOutputStream()
        XmpSerializer().serialize(xmp, baos, true)

        val pdMetadata = PDMetadata(document)
        pdMetadata.importXMPMetadata(baos.toByteArray())
        document.documentCatalog.metadata = pdMetadata
    }
}
