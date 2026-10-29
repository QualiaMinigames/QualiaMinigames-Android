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

package com.qiushui1012.app.minigames.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** The language the interface is read in. */
enum class AppLanguage(val tag: String?) {
    /** Follow the operating system. */
    System(null),

    /** English, the default locale of the resources. */
    English("en"),

    /** Chinese. */
    Chinese("zh"),
    ;

    companion object {
        /** Returns the language a stored name names, or [System] when it names none of ours. */
        fun fromName(name: String?): AppLanguage = entries.firstOrNull { it.name == name } ?: System
    }
}

/** The theme the user chose. */
enum class ThemeSetting {
    /** Follow the operating system. */
    System,
    /** Always light. */
    Light,
    /** Always dark. */
    Dark,
    ;

    companion object {
        /** Returns the theme a stored name names, or [System] when it names none of ours. */
        fun fromName(name: String?): ThemeSetting = entries.firstOrNull { it.name == name } ?: System
    }
}

/** Everything the settings screen owns. */
data class Settings(
    /** The name a game uses when the player does not type one. */
    val playerName: String = "",
    /** The language the interface is read in. */
    val language: AppLanguage = AppLanguage.System,
    /** The theme the interface is drawn in. */
    val theme: ThemeSetting = ThemeSetting.System,
    /** Whether a short tone plays on a turn or a digit. */
    val sound: Boolean = true,
    /** Whether a short vibration accompanies the same actions. */
    val haptics: Boolean = true,
    /** The size the last pipes game was started with, which the next one offers. */
    val lastPipesSize: Int = 9,
    /** Whether the last pipes game wrapped its edges. */
    val lastPipesWrapped: Boolean = false,
    /** The difficulty the last sudoku game was started with. */
    val lastSudokuDifficulty: String = "easy",
)

/**
 * The settings, stored in a DataStore.
 *
 * The values are read as a [Flow] so that a change shows up on every screen at once: the
 * language and the theme are read at the root of the composition, and a screen that cached
 * them would keep the old wording after a switch.
 */
class SettingsStore(private val store: DataStore<Preferences>) {
    /** The settings as they are now. */
    val settings: Flow<Settings> =
        store.data
            .catch { failure ->
                // A preferences file that cannot be read is reported as empty rather than
                // crashing the application: the alternative is no interface at all, and the
                // values it held are defaults the user can set again.
                if (failure is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw failure
                }
            }
            .map { preferences ->
                Settings(
                    playerName = preferences[Keys.playerName].orEmpty(),
                    language = AppLanguage.fromName(preferences[Keys.language]),
                    theme = ThemeSetting.fromName(preferences[Keys.theme]),
                    sound = preferences[Keys.sound] ?: true,
                    haptics = preferences[Keys.haptics] ?: true,
                    lastPipesSize = preferences[Keys.lastPipesSize]?.toIntOrNull() ?: 9,
                    lastPipesWrapped = preferences[Keys.lastPipesWrapped] ?: false,
                    lastSudokuDifficulty = preferences[Keys.lastSudokuDifficulty] ?: "easy",
                )
            }

    /** Writes a new default player name. */
    suspend fun setPlayerName(name: String) {
        store.edit { it[Keys.playerName] = name }
    }

    /** Writes the language. */
    suspend fun setLanguage(language: AppLanguage) {
        store.edit { it[Keys.language] = language.name }
    }

    /** Writes the theme. */
    suspend fun setTheme(theme: ThemeSetting) {
        store.edit { it[Keys.theme] = theme.name }
    }

    /** Writes whether sound is on. */
    suspend fun setSound(enabled: Boolean) {
        store.edit { it[Keys.sound] = enabled }
    }

    /** Writes whether haptics are on. */
    suspend fun setHaptics(enabled: Boolean) {
        store.edit { it[Keys.haptics] = enabled }
    }

    /**
     * Writes the parameters the next pipes game starts with.
     *
     * The parameters are remembered as they are used rather than as a setting of their own:
     * an entry that is started with a size is the statement of what the player wants next
     * time, and a second control for the same value would be a second place to keep in step.
     */
    suspend fun setLastPipes(size: Int, wrapped: Boolean) {
        store.edit {
            it[Keys.lastPipesSize] = size.toString()
            it[Keys.lastPipesWrapped] = wrapped
        }
    }

    /** Writes the difficulty the next sudoku game starts with. */
    suspend fun setLastSudokuDifficulty(difficulty: String) {
        store.edit { it[Keys.lastSudokuDifficulty] = difficulty }
    }

    /** The keys the settings are stored under. */
    private object Keys {
        /** The default player name. */
        val playerName = stringPreferencesKey("player_name")

        /** The language choice, by enum name. */
        val language = stringPreferencesKey("language")

        /** The theme choice, by enum name. */
        val theme = stringPreferencesKey("theme")

        /** Whether sound is on. */
        val sound = booleanPreferencesKey("sound")

        /** Whether haptics are on. */
        val haptics = booleanPreferencesKey("haptics")

        /** The size of the last pipes game. */
        val lastPipesSize = stringPreferencesKey("last_pipes_size")

        /** Whether the last pipes game wrapped. */
        val lastPipesWrapped = booleanPreferencesKey("last_pipes_wrapped")

        /** The difficulty of the last sudoku game. */
        val lastSudokuDifficulty = stringPreferencesKey("last_sudoku_difficulty")
    }
}

/** The name the settings file is stored under. */
private const val SETTINGS_FILE = "settings"

/** The DataStore the settings live in, created once per process. */
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = SETTINGS_FILE)

/** Returns the settings store of this application. */
fun Context.createSettingsStore(): SettingsStore = SettingsStore(settingsStore)
