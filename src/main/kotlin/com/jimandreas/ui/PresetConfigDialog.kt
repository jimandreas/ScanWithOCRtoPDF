package com.jimandreas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jimandreas.scanner.ScannerDevice
import com.jimandreas.state.*
import com.jimandreas.ui.components.*

@Composable
fun PresetConfigDialog(
    scanners: List<ScannerDevice>,
    selectedScanner: ScannerDevice?,
    isLoadingScanners: Boolean,
    scanSettings: ScanSettings,
    ocrSettings: OcrSettings,
    pdfMetadata: PdfMetadata,
    onScannerSelect: (ScannerDevice) -> Unit,
    onRefreshScanners: () -> Unit,
    onScanSettingsChange: (ScanSettings) -> Unit,
    onOcrSettingsChange: (OcrSettings) -> Unit,
    onMetadataChange: (PdfMetadata) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Configure Presets", style = MaterialTheme.typography.headlineSmall)

            // Scanner selection
            ScannerDropdown(
                scanners = scanners,
                selected = selectedScanner,
                isLoading = isLoadingScanners,
                onSelect = onScannerSelect,
                onRefresh = onRefreshScanners,
                modifier = Modifier.fillMaxWidth()
            )

            // Input settings group
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Input", style = MaterialTheme.typography.labelLarge)
                    SettingsDropdowns(
                        settings = scanSettings,
                        onSettingsChange = onScanSettingsChange,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Optimization group
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Optimization", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    QualitySlider(
                        quality = scanSettings.jpegQuality,
                        onQualityChange = { onScanSettingsChange(scanSettings.copy(jpegQuality = it)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // OCR group
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Post-Processing", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    OcrCheckbox(
                        settings = ocrSettings,
                        onSettingsChange = onOcrSettingsChange,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Metadata group
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("PDF Metadata", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = pdfMetadata.author,
                        onValueChange = { onMetadataChange(pdfMetadata.copy(author = it)) },
                        label = { Text("Author") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pdfMetadata.title,
                        onValueChange = { onMetadataChange(pdfMetadata.copy(title = it)) },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pdfMetadata.keywords,
                        onValueChange = { onMetadataChange(pdfMetadata.copy(keywords = it)) },
                        label = { Text("Keywords") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Scan button
            Button(
                onClick = onScan,
                enabled = selectedScanner != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan")
            }
        }
    }
}
