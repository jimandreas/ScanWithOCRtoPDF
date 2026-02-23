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
    onScannerSelect: (ScannerDevice) -> Unit,
    onRefreshScanners: () -> Unit,
    onScanSettingsChange: (ScanSettings) -> Unit,
    onOcrSettingsChange: (OcrSettings) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Scan to PDF", style = MaterialTheme.typography.headlineSmall)

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
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
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
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    OcrCheckbox(
                        settings = ocrSettings,
                        onSettingsChange = onOcrSettingsChange,
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
