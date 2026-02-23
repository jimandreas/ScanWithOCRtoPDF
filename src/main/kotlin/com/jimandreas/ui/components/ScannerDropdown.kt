package com.jimandreas.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.jimandreas.scanner.ScannerDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerDropdown(
    scanners: List<ScannerDevice>,
    selected: ScannerDevice?,
    isLoading: Boolean,
    onSelect: (ScannerDevice) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f).pointerHoverIcon(PointerIcon.Default, overrideDescendants = true)
        ) {
            OutlinedTextField(
                value = when {
                    isLoading -> "Loading..."
                    selected != null -> selected.displayName
                    scanners.isEmpty() -> "No scanners found"
                    else -> "Select scanner"
                },
                onValueChange = {},
                readOnly = true,
                label = { Text("Scanner") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                scanners.forEach { scanner ->
                    DropdownMenuItem(
                        text = { Text(scanner.displayName) },
                        onClick = {
                            onSelect(scanner)
                            expanded = false
                        }
                    )
                }
                if (scanners.isEmpty() && !isLoading) {
                    DropdownMenuItem(
                        text = { Text("No scanners found", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { expanded = false },
                        enabled = false
                    )
                }
            }
        }

        OutlinedButton(onClick = onRefresh, enabled = !isLoading) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text("Refresh")
            }
        }
    }
}
