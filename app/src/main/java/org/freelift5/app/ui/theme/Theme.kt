package org.freelift5.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun FreeLiftTheme(
    theme: AppThemeDefinition,
    content: @Composable () -> Unit,
) {
    SyncSystemBars(theme)
    MaterialTheme(
        colorScheme = theme.colorScheme,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            content = content,
        )
    }
}

@Composable
private fun SyncSystemBars(theme: AppThemeDefinition) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val window = activity.window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !theme.isDark
            isAppearanceLightNavigationBars = !theme.isDark
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = theme.colorScheme.surface.toArgb()
        }
    }
}
