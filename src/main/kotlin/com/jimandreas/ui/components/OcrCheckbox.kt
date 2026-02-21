package com.jimandreas.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jimandreas.state.OcrSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrCheckbox(
    settings: OcrSettings,
    onSettingsChange: (OcrSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(
                checked = settings.enabled,
                onCheckedChange = { onSettingsChange(settings.copy(enabled = it)) }
            )
            Text("Make Searchable (OCR)", style = MaterialTheme.typography.bodyMedium)
        }
        AnimatedVisibility(visible = settings.enabled) {
            var expanded by remember { mutableStateOf(false) }
            val languages = OcrSettings.SUPPORTED_LANGUAGES
            val selectedLabel = languages.firstOrNull { it.first == settings.language }?.second ?: settings.language

            Row(
                modifier = Modifier.padding(start = 40.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Language:", style = MaterialTheme.typography.bodySmall)
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.width(200.dp)
                ) {
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        languages.forEach { (code, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    onSettingsChange(settings.copy(language = code))
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
