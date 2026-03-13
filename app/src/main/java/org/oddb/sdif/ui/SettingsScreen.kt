package org.oddb.sdif.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oddb.sdif.data.DatabaseManager
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    db: DatabaseManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var statusMessage by remember { mutableStateOf("") }
    var dbDate by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val dbFile = DatabaseManager.getDbFile(context)
        if (dbFile.exists()) {
            val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale("de", "CH"))
            dbDate = fmt.format(Date(dbFile.lastModified()))
        } else {
            dbDate = "Mitgeliefert"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                actions = {
                    TextButton(onClick = onDismiss) {
                        Text("Fertig")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Database section
            Text("Datenbank", style = MaterialTheme.typography.titleMedium)

            if (dbDate.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Stand")
                    Text(dbDate, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Button(
                onClick = {
                    isDownloading = true
                    downloadProgress = 0f
                    statusMessage = ""
                    scope.launch(Dispatchers.IO) {
                        try {
                            val url = URL("http://pillbox.oddb.org/interactions.db")
                            val connection = url.openConnection() as HttpURLConnection
                            connection.connect()
                            val contentLength = connection.contentLength
                            val dbFile = DatabaseManager.getDbFile(context)
                            val tempFile = File(dbFile.parent, "interactions_temp.db")
                            connection.inputStream.use { input ->
                                tempFile.outputStream().use { output ->
                                    val buffer = ByteArray(8192)
                                    var totalRead = 0L
                                    var bytesRead: Int
                                    while (input.read(buffer).also { bytesRead = it } != -1) {
                                        output.write(buffer, 0, bytesRead)
                                        totalRead += bytesRead
                                        if (contentLength > 0) {
                                            withContext(Dispatchers.Main) {
                                                downloadProgress = totalRead.toFloat() / contentLength
                                            }
                                        }
                                    }
                                }
                            }
                            if (dbFile.exists()) dbFile.delete()
                            tempFile.renameTo(dbFile)
                            db.reloadDatabase()
                            val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale("de", "CH"))
                            val newDate = fmt.format(Date(dbFile.lastModified()))
                            withContext(Dispatchers.Main) {
                                dbDate = newDate
                                statusMessage = "Erfolgreich aktualisiert"
                                isDownloading = false
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                statusMessage = "Fehler: ${e.localizedMessage}"
                                isDownloading = false
                            }
                        }
                    }
                },
                enabled = !isDownloading
            ) {
                Text("Interaktions-DB aktualisieren")
            }

            if (isDownloading) {
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Herunterladen...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (statusMessage.isNotEmpty()) {
                Text(
                    statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (statusMessage.contains("Fehler")) Color.Red else Color(0xFF4CAF50)
                )
            }

            HorizontalDivider()

            // Info section
            Text("Info", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Version")
                val version = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (_: Exception) { "1.0" }
                Text(version ?: "1.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Quelle")
                Text("pillbox.oddb.org", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
