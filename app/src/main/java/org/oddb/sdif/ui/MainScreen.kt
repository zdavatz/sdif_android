package org.oddb.sdif.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.oddb.sdif.data.DatabaseManager

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val db = remember { DatabaseManager.getInstance(context) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

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
