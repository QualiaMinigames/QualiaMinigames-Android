// Copyright (C) 2026 QualiaMinigames contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.qiushui1012.app.minigames.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qiushui1012.app.minigames.AppContainer
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.Space
import com.qiushui1012.app.minigames.data.AppLanguage
import com.qiushui1012.app.minigames.data.Settings
import com.qiushui1012.app.minigames.data.ThemeSetting
import kotlinx.coroutines.launch

/**
 * The size of the application's mark in the about section.
 *
 * It is a mark, not an icon in a list: large enough that the frame, the welded corner and the
 * gamepad are each legible, and small enough to leave the name beside it on one line.
 */
private val LogoSize = 64.dp

/**
 * Everything that is not a game: the player's name, the language, the theme, the feedback
 * toggles, and what this build is.
 *
 * Every control writes immediately. There is no save button because there is nothing to
 * submit: a switch that is on is the state, and a value that is typed is written when the
 * field loses focus, which is what leaving the screen does.
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    settings: Settings,
    bottomInset: Dp,
) {
    val scope = rememberCoroutineScope()
    var playerName by remember(settings.playerName) { mutableStateOf(settings.playerName) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l, vertical = Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        ScreenHeader(stringResource(R.string.settings_title))

        SectionCard(header = { SectionTitle(stringResource(R.string.settings_player)) }) {
            OutlinedTextField(
                value = playerName,
                onValueChange = {
                    playerName = it
                    scope.launch { container.settings.setPlayerName(it) }
                },
                label = { Text(stringResource(R.string.settings_player)) },
                placeholder = { Text(stringResource(R.string.settings_player_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard(header = { SectionTitle(stringResource(R.string.settings_language)) }) {
            ChoiceRow(
                options = AppLanguage.entries,
                selected = settings.language,
                label = { languageLabel(it) },
                onSelect = { scope.launch { container.settings.setLanguage(it) } },
            )
        }

        SectionCard(header = { SectionTitle(stringResource(R.string.settings_theme)) }) {
            ChoiceRow(
                options = ThemeSetting.entries,
                selected = settings.theme,
                label = { themeLabel(it) },
                onSelect = { scope.launch { container.settings.setTheme(it) } },
            )
        }

        SectionCard(header = { SectionTitle(stringResource(R.string.settings_sound)) }) {
            SwitchRow(
                label = stringResource(R.string.settings_sound),
                explainer = stringResource(R.string.settings_sound_explainer),
                checked = settings.sound,
                onCheckedChange = { scope.launch { container.settings.setSound(it) } },
            )
        }

        SectionCard(header = { SectionTitle(stringResource(R.string.settings_haptics)) }) {
            SwitchRow(
                label = stringResource(R.string.settings_haptics),
                explainer = stringResource(R.string.settings_haptics_explainer),
                checked = settings.haptics,
                onCheckedChange = { scope.launch { container.settings.setHaptics(it) } },
            )
        }

        SectionCard(header = { SectionTitle(stringResource(R.string.settings_about)) }) {
            AboutHeader()
            LabeledValue(stringResource(R.string.settings_core_version), rulesVersionOrEmpty())
            LabeledValue(stringResource(R.string.settings_license), stringResource(R.string.settings_license_value))
            LabeledValue(stringResource(R.string.settings_repository), RepositoryUrl)
        }

        Column(modifier = Modifier.padding(bottom = bottomInset)) {}
    }
}

/**
 * The header of the about section: the mark of the application beside its name.
 *
 * The mark is the same drawing as the launcher icon, from `app_logo.xml`, because a reader who
 * got here from the launcher recognises it. It is drawn at a size that reads as a mark rather
 * than a thumbnail, and the two pieces of text beside it are the name and the version, so the
 * section says what the application is before it starts listing what it is made of.
 */
@Composable
private fun AboutHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier.size(LogoSize),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.settings_version_with_value, BuildVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A labelled switch with a line explaining what it turns on. */
@Composable
private fun SwitchRow(
    label: String,
    explainer: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                explainer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Returns the label of a language. */
@Composable
private fun languageLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.System -> stringResource(R.string.settings_language_system)
        // A language is named in itself: a reader looking for their own has to recognise it,
        // and "English" is of no use to somebody who reads only Chinese.
        AppLanguage.English -> "English"
        AppLanguage.Chinese -> "中文"
    }

/** Returns the label of a theme. */
@Composable
private fun themeLabel(theme: ThemeSetting): String =
    when (theme) {
        ThemeSetting.System -> stringResource(R.string.settings_theme_system)
        ThemeSetting.Light -> stringResource(R.string.settings_theme_light)
        ThemeSetting.Dark -> stringResource(R.string.settings_theme_dark)
    }
