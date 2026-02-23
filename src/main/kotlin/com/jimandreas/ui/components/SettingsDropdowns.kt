package com.jimandreas.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jimandreas.state.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdowns(
    settings: ScanSettings,
    onSettingsChange: (ScanSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EnumDropdown(
                label = "Sides",
                items = Sides.entries,
                selected = settings.sides,
                displayName = { it.displayName },
                onSelect = { onSettingsChange(settings.copy(sides = it)) },
                modifier = Modifier.weight(1f)
            )
            EnumDropdown(
                label = "Color Mode",
                items = ColorMode.entries,
                selected = settings.colorMode,
                displayName = { it.displayName },
                onSelect = { onSettingsChange(settings.copy(colorMode = it)) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ResolutionDropdown(
                selected = settings.dpi,
                onSelect = { onSettingsChange(settings.copy(dpi = it)) },
                modifier = Modifier.weight(1f)
            )
            EnumDropdown(
                label = "Paper Size",
                items = PaperSize.entries,
                selected = settings.paperSize,
                displayName = { it.displayName },
                onSelect = { onSettingsChange(settings.copy(paperSize = it)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> EnumDropdown(
    label: String,
    items: List<T>,
    selected: T,
    displayName: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = displayName(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(displayName(item)) },
                    onClick = { onSelect(item); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResolutionDropdown(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(75, 100, 150, 200, 300, 400, 600, 1200)
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = "$selected DPI",
            onValueChange = {},
            readOnly = true,
            label = { Text("Resolution") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { dpi ->
                DropdownMenuItem(
                    text = { Text("$dpi DPI") },
                    onClick = { onSelect(dpi); expanded = false }
                )
            }
        }
    }
}
