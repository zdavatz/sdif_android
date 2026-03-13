package org.oddb.sdif.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.oddb.sdif.data.BasketDrug
import org.oddb.sdif.data.DatabaseManager
import org.oddb.sdif.data.InteractionResult
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val db = remember { DatabaseManager.getInstance(context) }
    var dbReady by remember { mutableStateOf(db.isDatabaseAvailable()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var basket by remember { mutableStateOf<List<BasketDrug>>(emptyList()) }
    var interactions by remember { mutableStateOf<List<InteractionResult>>(emptyList()) }

    if (!dbReady) {
        DatabaseDownloadScreen(
            db = db,
            onDownloadComplete = { dbReady = true }
        )
        return
    }

    if (showSettings) {
        SettingsScreen(
            db = db,
            onDismiss = { showSettings = false }
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Medication, contentDescription = null) },
                    label = { Text("Interaktions-Check") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("Klinische Suche") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("ATC-Klassen") }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> BasketCheckScreen(
                db = db,
                basket = basket,
                onBasketChange = { basket = it },
                interactions = interactions,
                onInteractionsChange = { interactions = it },
                onShowSettings = { showSettings = true },
                modifier = Modifier.padding(innerPadding)
            )
            1 -> ClinicalSearchScreen(
                db = db,
                onShowSettings = { showSettings = true },
                modifier = Modifier.padding(innerPadding)
            )
            2 -> ATCClassScreen(
                db = db,
                onShowSettings = { showSettings = true },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
private fun DatabaseDownloadScreen(
    db: DatabaseManager,
    onDownloadComplete: () -> Unit
) {
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun startDownload() {
        isDownloading = true
        errorMessage = null
        progress = 0f
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
                                    progress = totalRead.toFloat() / contentLength
                                }
                            }
                        }
                    }
                }
                if (dbFile.exists()) dbFile.delete()
                tempFile.renameTo(dbFile)
                db.reloadDatabase()
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    onDownloadComplete()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.localizedMessage ?: "Download fehlgeschlagen"
                    isDownloading = false
                }
            }
        }
    }

    // Auto-start download on first display
    LaunchedEffect(Unit) {
        startDownload()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "SDIF",
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Swiss Drug Interaction Finder",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            if (isDownloading) {
                Text("Datenbank wird heruntergeladen...")
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (errorMessage != null) {
                Text(
                    "Fehler: $errorMessage",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { startDownload() }) {
                    Text("Erneut versuchen")
                }
            }
        }
    }
}
