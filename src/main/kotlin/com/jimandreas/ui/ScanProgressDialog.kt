package com.jimandreas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jimandreas.state.ScanProgress

@Composable
fun ScanProgressDialog(
    progress: ScanProgress,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = {}) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Processing", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = progress.phase,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (progress.pagesAcquired > 0) {
                    Text(
                        text = "Pages: ${progress.pagesAcquired}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }
}
