package org.freelift5.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import org.freelift5.app.ui.FreeLiftApp
import org.freelift5.app.ui.theme.FreeLiftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FreeLiftTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FreeLiftApp()
                }
            }
        }
    }
}

