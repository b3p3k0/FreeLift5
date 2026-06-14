package org.freelift5.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.freelift5.app.theme.AppThemeId
import org.freelift5.app.theme.ThemeBehavior
import org.freelift5.app.theme.ThemePreferences
import org.freelift5.app.ui.theme.AppThemeDefinition
import org.freelift5.app.ui.theme.ThemeCatalog

@Composable
fun AppearanceSettingsSection(
    preferences: ThemePreferences,
    activeTheme: AppThemeDefinition,
    onBehaviorChange: (ThemeBehavior, AppThemeId) -> Unit,
    onThemeSelected: (AppThemeId) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = preferences.behavior == ThemeBehavior.FOLLOW_SYSTEM,
                    onClick = {
                        onBehaviorChange(ThemeBehavior.FOLLOW_SYSTEM, activeTheme.id)
                    },
                    label = { Text("Follow device") },
                    modifier = Modifier.testTag("theme-behavior-follow-system"),
                )
                FilterChip(
                    selected = preferences.behavior == ThemeBehavior.FIXED,
                    onClick = {
                        onBehaviorChange(ThemeBehavior.FIXED, activeTheme.id)
                    },
                    label = { Text("Use one theme") },
                    modifier = Modifier.testTag("theme-behavior-fixed"),
                )
            }
            Text(
                if (preferences.behavior == ThemeBehavior.FOLLOW_SYSTEM) {
                    "Choose a day theme and a night theme. FreeLift5 follows your device setting."
                } else {
                    "Keep the same colors even when your device changes between light and dark."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (preferences.behavior == ThemeBehavior.FOLLOW_SYSTEM) {
                ThemeGroup(
                    title = "Day theme",
                    themes = ThemeCatalog.lightThemes,
                    selected = preferences.lightTheme,
                    onThemeSelected = onThemeSelected,
                )
                ThemeGroup(
                    title = "Night theme",
                    themes = ThemeCatalog.darkThemes,
                    selected = preferences.darkTheme,
                    onThemeSelected = onThemeSelected,
                )
            } else {
                ThemeGroup(
                    title = "Theme",
                    themes = ThemeCatalog.all,
                    selected = preferences.fixedTheme,
                    onThemeSelected = onThemeSelected,
                )
            }
        }
    }
}

@Composable
private fun ThemeGroup(
    title: String,
    themes: List<AppThemeDefinition>,
    selected: AppThemeId,
    onThemeSelected: (AppThemeId) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        themes.forEach { theme ->
            ThemeChoiceRow(
                theme = theme,
                selected = theme.id == selected,
                onClick = { onThemeSelected(theme.id) },
            )
        }
    }
}

@Composable
private fun ThemeChoiceRow(
    theme: AppThemeDefinition,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .testTag("theme-${theme.id.persistedId}")
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(theme.name, fontWeight = FontWeight.SemiBold)
            Text(
                theme.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            theme.swatches.forEach { swatch ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(swatch),
                )
            }
        }
    }
}
