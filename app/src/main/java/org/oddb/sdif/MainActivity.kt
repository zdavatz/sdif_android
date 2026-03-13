package org.oddb.sdif

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.oddb.sdif.ui.MainScreen
import org.oddb.sdif.ui.SDIFTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SDIFTheme {
                MainScreen()
            }
        }
    }
}
