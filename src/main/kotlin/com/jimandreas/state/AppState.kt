package com.jimandreas.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jimandreas.ocr.OcrEngine
import com.jimandreas.ocr.OcrWord
import com.jimandreas.pdf.PdfBuilder
import com.jimandreas.scanner.ScannedPage
import com.jimandreas.scanner.ScannerDevice
import com.jimandreas.scanner.ScannerException
import com.jimandreas.scanner.ScannerRepository
import com.jimandreas.util.AppDispatchers
import com.jimandreas.util.DefaultDispatchers
import com.jimandreas.util.ImageProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

class AppState(
    private val scannerRepository: ScannerRepository,
    private val ocrEngine: OcrEngine,
    private val dispatchers: AppDispatchers = DefaultDispatchers
) {
    // Scanner
    var availableScanners by mutableStateOf<List<ScannerDevice>>(emptyList())
    var selectedScanner by mutableStateOf<ScannerDevice?>(null)
    var isLoadingScanners by mutableStateOf(false)

    // Settings
    var scanSettings by mutableStateOf(ScanSettings())
    var ocrSettings by mutableStateOf(OcrSettings())
    var pdfMetadata by mutableStateOf(PdfMetadata())

    // Progress / state
    var isScanning by mutableStateOf(false)
    var scanProgress by mutableStateOf<ScanProgress?>(null)
    var errorMessage by mutableStateOf<String?>(null)
    var lastOutputFile by mutableStateOf<File?>(null)

    // Batch pages accumulated across ADF + flatbed loops
    private val accumulatedPages = mutableListOf<ScannedPage>()
    var showScanMoreDialog by mutableStateOf(false)

    private var scanJob: Job? = null

    fun loadScanners(scope: CoroutineScope) {
        scope.launch(dispatchers.io) {
            withContext(dispatchers.main) { isLoadingScanners = true }
            try {
                val devices = scannerRepository.enumerateDevices()
                withContext(dispatchers.main) {
                    availableScanners = devices
                    selectedScanner = devices.firstOrNull()
                    isLoadingScanners = false
                }
            } catch (e: Exception) {
                withContext(dispatchers.main) {
                    isLoadingScanners = false
                    errorMessage = "Failed to enumerate scanners: ${e.message}"
                }
            }
        }
    }

    fun startScan(scope: CoroutineScope) {
        val device = selectedScanner ?: return
        accumulatedPages.clear()
        scanJob = scope.launch { performScanCycle(device) }
    }

    fun scanMorePages(scope: CoroutineScope) {
        val device = selectedScanner ?: return
        showScanMoreDialog = false
        scanJob = scope.launch { performScanCycle(device) }
    }

    fun finalizePdf(scope: CoroutineScope) {
        showScanMoreDialog = false
        if (accumulatedPages.isNotEmpty()) {
            scanJob = scope.launch { buildPdf(accumulatedPages.toList()) }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        cleanupOnCancel()
    }

    private suspend fun performScanCycle(device: ScannerDevice) {
        withContext(dispatchers.main) {
            isScanning = true
            scanProgress = ScanProgress(phase = "Scanning...", pagesAcquired = 0)
        }

        val newImages = try {
            withContext(dispatchers.io) {
                scannerRepository.acquireImages(device, scanSettings)
            }
        } catch (e: ScannerException) {
            withContext(dispatchers.main) {
                isScanning = false
                scanProgress = null
                errorMessage = e.message
            }
            return
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            withContext(dispatchers.main) {
                isScanning = false
                scanProgress = null
                errorMessage = "Scan failed: ${e.message}"
            }
            return
        }

        accumulatedPages.addAll(newImages)

        withContext(dispatchers.main) {
            isScanning = false
            scanProgress = null
            // Only prompt for more pages / PDF if something was actually scanned.
            // newImages is empty when the user cancels the WIA dialog.
            if (newImages.isNotEmpty()) {
                showScanMoreDialog = true
            }
        }
    }

    private suspend fun buildPdf(pages: List<ScannedPage>) {
        withContext(dispatchers.main) {
            isScanning = true
            scanProgress = ScanProgress(phase = "Processing images...", pagesAcquired = pages.size)
        }

        try {
            // Compress images
            val jpegPages = withContext(dispatchers.default) {
                pages.mapIndexed { i, page ->
                    ensureActive()
                    val bytes = ImageProcessor.toJpegBytes(page.image, scanSettings.jpegQuality)
                    withContext(dispatchers.main) {
                        scanProgress = ScanProgress("Compressing page ${i + 1}/${pages.size}", i + 1)
                    }
                    bytes
                }
            }

            // OCR — use the actual DPI from each page's file metadata
            val ocrWords: List<List<OcrWord>?> = if (ocrSettings.enabled) {
                withContext(dispatchers.default) {
                    pages.mapIndexed { i, page ->
                        ensureActive()
                        withContext(dispatchers.main) {
                            scanProgress = ScanProgress("OCR page ${i + 1}/${pages.size}", i + 1)
                        }
                        ocrEngine.recognizePage(page.image, ocrSettings.language, page.dpi)
                    }
                }
            } else {
                List(pages.size) { null }
            }

            // Build PDF
            withContext(dispatchers.main) {
                scanProgress = ScanProgress("Building PDF...", pages.size)
            }
            val outputFile = withContext(dispatchers.default) {
                ensureActive()
                val tempDir = Files.createTempDirectory("scanpdf").toFile()
                val outFile = File(tempDir, "scan_${System.currentTimeMillis()}.pdf")
                PdfBuilder.build(
                    outputFile = outFile,
                    jpegPages = jpegPages,
                    ocrWords = ocrWords,
                    pageDpis = pages.map { it.dpi },
                    pageSizes = pages.map { Pair(it.image.width, it.image.height) },
                    metadata = pdfMetadata
                )
                outFile
            }

            withContext(dispatchers.main) {
                isScanning = false
                scanProgress = null
                lastOutputFile = outputFile
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            cleanupOnCancel()
            throw e
        } catch (e: Exception) {
            withContext(dispatchers.main) {
                isScanning = false
                scanProgress = null
                errorMessage = "PDF generation failed: ${e.message}"
            }
        }
    }

    private fun cleanupOnCancel() {
        isScanning = false
        scanProgress = null
        accumulatedPages.clear()
    }

    fun dismissError() { errorMessage = null }
}

data class ScanProgress(val phase: String, val pagesAcquired: Int)
