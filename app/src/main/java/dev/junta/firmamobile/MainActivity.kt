package dev.junta.firmamobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.junta.firmamobile.ui.AppRoot
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JuntaFirmaTheme {
                AppRoot()
            }
        }
    }
}
