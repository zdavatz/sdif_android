package org.oddb.sdif

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.oddb.sdif.ui.MainScreen
import org.oddb.sdif.ui.SDIFTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCrashlyticsCollectionEnabled(true)
        crashlytics.log("MainActivity onCreate")
        crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME)

        enableEdgeToEdge()
        setContent {
            SDIFTheme {
                MainScreen()
            }
        }
    }
}
