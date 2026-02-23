package com.jimandreas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jimandreas.ocr.TesseractOcrEngine
import com.jimandreas.scanner.WiaScannerRepository
import com.jimandreas.state.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.awt.Desktop
import java.io.File

@Composable
fun App() {
    val coroutineScope = rememberCoroutineScope()
    val scannerRepo = remember { WiaScannerRepository() }
    val ocrEngine = remember { TesseractOcrEngine() }
    val appState = remember { AppState(scannerRepo, ocrEngine) }

    LaunchedEffect(Unit) {
        appState.loadScanners(coroutineScope)
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                PresetConfigDialog(
                    scanners = appState.availableScanners,
                    selectedScanner = appState.selectedScanner,
                    isLoadingScanners = appState.isLoadingScanners,
                    scanSettings = appState.scanSettings,
                    ocrSettings = appState.ocrSettings,
                    onScannerSelect = { appState.selectedScanner = it },
                    onRefreshScanners = { appState.loadScanners(coroutineScope) },
                    onScanSettingsChange = { appState.scanSettings = it },
                    onOcrSettingsChange = { appState.ocrSettings = it },
                    onScan = { appState.startScan(coroutineScope) },
                    modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth()
                )
            }

            // Scanning progress overlay
            if (appState.isScanning) {
                appState.scanProgress?.let { progress ->
                    ScanProgressDialog(
                        progress = progress,
                        onCancel = { appState.cancelScan() }
                    )
                }
            }

            // Error dialog
            appState.errorMessage?.let { msg ->
                AlertDialog(
                    onDismissRequest = { appState.dismissError() },
                    title = { Text("Error") },
                    text = { Text(msg) },
                    confirmButton = {
                        TextButton(onClick = { appState.dismissError() }) { Text("OK") }
                    }
                )
            }

            // Scan more / finalize dialog
            if (appState.showScanMoreDialog) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Scan Complete") },
                    text = { Text("Page(s) acquired. Would you like to scan more pages or finalize the PDF?") },
                    confirmButton = {
                        Button(onClick = { appState.scanMorePages(coroutineScope) }) {
                            Text("Scan More Pages")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { appState.finalizePdf(coroutineScope) }) {
                            Text("Build PDF")
                        }
                    }
                )
            }

            // Success: offer to open file
            appState.lastOutputFile?.let { file ->
                AlertDialog(
                    onDismissRequest = { appState.lastOutputFile = null },
                    title = { Text("PDF Saved") },
                    text = { Text("Your PDF has been saved to:\n${file.absolutePath}") },
                    confirmButton = {
                        Button(onClick = {
                            openFile(file)
                            appState.lastOutputFile = null
                        }) { Text("Open PDF") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { appState.lastOutputFile = null }) { Text("Close") }
                    }
                )
            }
        }
    }
}

private fun openFile(file: File) {
    try {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file)
    } catch (_: Exception) {}
}
