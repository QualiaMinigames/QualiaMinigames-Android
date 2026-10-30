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

package com.qiushui1012.app.minigames

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qiushui1012.app.minigames.data.AppLanguage
import com.qiushui1012.app.minigames.data.Settings
import com.qiushui1012.app.minigames.data.ThemeSetting
import com.qiushui1012.app.minigames.ui.GameScreen
import com.qiushui1012.app.minigames.ui.MainShell
import java.util.Locale

/**
 * The application's root.
 *
 * It reads the two settings that change how everything below is drawn, the language and the
 * theme, and hands the rest down.
 *
 * # Why the language is applied by wrapping the composition
 *
 * Android resolves a resource string when it is read, from the locale of the context in
 * effect. A language chosen in the settings therefore has to reach that context, and the way
 * to do it without recreating the activity is to provide a context whose locale is the chosen
 * one. Recreating the activity would take the Rust object behind the board with it.
 *
 * The content below is also keyed on the language: a string that has already been resolved
 * would otherwise stay on screen in the old language until something else made it recompose.
 */
@Composable
fun App(container: AppContainer) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val base = LocalContext.current
    val configuration = LocalConfiguration.current

    val localized =
        remember(settings.language, configuration) {
            val locale =
                when (settings.language) {
                    AppLanguage.System -> Locale.getDefault()
                    AppLanguage.English -> Locale.forLanguageTag("en")
                    AppLanguage.Chinese -> Locale.forLanguageTag("zh")
                }
            val adjusted = Configuration(configuration)
            adjusted.setLocale(locale)
            base.createConfigurationContext(adjusted)
        }

    var screen by remember { mutableStateOf<Screen>(Screen.Main(Tab.Home)) }

    QualiaTheme(settings.theme.toThemeChoice()) {
        CompositionLocalProvider(LocalContext provides localized) {
            key(settings.language) {
                // A dialog is a window of its own, and it resolves the locale from the
                // activity that owns it rather than from the composition: the context has to
                // be provided again inside the dialog, or its text stays in the old language.
                when (val shown = screen) {
                    is Screen.Main ->
                        MainShell(
                            container = container,
                            settings = settings,
                            tab = shown.tab,
                            onSelectTab = { screen = Screen.Main(it) },
                            onStartGame = { screen = Screen.Playing(it) },
                        )

                    is Screen.Playing ->
                        GameScreen(
                            container = container,
                            settings = settings,
                            game = shown.game,
                            onExit = { screen = Screen.Main(Tab.Home) },
                            onFinished = { screen = Screen.Main(Tab.Records) },
                        )                }
            }
        }
    }
}

/** Which screen the application is showing. */
sealed interface Screen {
    /** One of the three pages. */
    data class Main(val tab: Tab) : Screen

    /** A game in progress. */
    data class Playing(val game: GameInProgress) : Screen
}

/** The three pages of the main shell. */
enum class Tab {
    /** The games and their entries. */
    Home,

    /** The records and the best times. */
    Records,

    /** The preferences. */
    Settings,
}

/** Returns the theme choice the composition understands. */
private fun ThemeSetting.toThemeChoice(): ThemeChoice =
    when (this) {
        ThemeSetting.System -> ThemeChoice.System
        ThemeSetting.Light -> ThemeChoice.Light
        ThemeSetting.Dark -> ThemeChoice.Dark
    }
