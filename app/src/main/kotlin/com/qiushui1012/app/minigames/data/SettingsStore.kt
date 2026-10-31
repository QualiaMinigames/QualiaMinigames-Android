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
    /** The width the last minesweeper game was started with. */
    val lastMinesweeperWidth: Int = 9,
    /** The height the last minesweeper game was started with. */
    val lastMinesweeperHeight: Int = 9,
    /** The mine density the last minesweeper game was started with, in thousandths of a percent. */
    val lastMinesweeperPercent: String = "123",
    /** The mode the last minesweeper game was started with, by its storage name. */
    val lastMinesweeperMode: String = "normal",
    /** The width the last nonogram game was started with. */
    val lastNonogramWidth: Int = 10,
    /** The height the last nonogram game was started with. */
    val lastNonogramHeight: Int = 10,
    /** The width the last hashi game was started with, in points. */
    val lastHashiWidth: Int = 10,
    /** The height the last hashi game was started with, in points. */
    val lastHashiHeight: Int = 10,
    /** The island share the last hashi game was started with, in thousandths. */
    val lastHashiPercent: String = "150",
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
                    lastMinesweeperWidth = preferences[Keys.lastMinesweeperWidth]?.toIntOrNull() ?: 9,
                    lastMinesweeperHeight = preferences[Keys.lastMinesweeperHeight]?.toIntOrNull() ?: 9,
                    lastMinesweeperPercent = preferences[Keys.lastMinesweeperPercent] ?: "123",
                    lastMinesweeperMode = preferences[Keys.lastMinesweeperMode] ?: "normal",
                    lastNonogramWidth = preferences[Keys.lastNonogramWidth]?.toIntOrNull() ?: 10,
                    lastNonogramHeight = preferences[Keys.lastNonogramHeight]?.toIntOrNull() ?: 10,
                    lastHashiWidth = preferences[Keys.lastHashiWidth]?.toIntOrNull() ?: 10,
                    lastHashiHeight = preferences[Keys.lastHashiHeight]?.toIntOrNull() ?: 10,
                    lastHashiPercent = preferences[Keys.lastHashiPercent] ?: "150",
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

    /**
     * Writes the parameters the next minesweeper game starts with.
     *
     * They are remembered as they are played for the same reason the pipes size is: starting a
     * game with a shape is the statement of what the player wants next time.
     */
    suspend fun setLastMinesweeper(width: Int, height: Int, percent: String, mode: String) {
        store.edit {
            it[Keys.lastMinesweeperWidth] = width.toString()
            it[Keys.lastMinesweeperHeight] = height.toString()
            it[Keys.lastMinesweeperPercent] = percent
            it[Keys.lastMinesweeperMode] = mode
        }
    }

    /** Writes the parameters the next nonogram game starts with. */
    suspend fun setLastNonogram(width: Int, height: Int) {
        store.edit {
            it[Keys.lastNonogramWidth] = width.toString()
            it[Keys.lastNonogramHeight] = height.toString()
        }
    }

    /** Writes the parameters the next hashi game starts with. */
    suspend fun setLastHashi(width: Int, height: Int, percent: String) {
        store.edit {
            it[Keys.lastHashiWidth] = width.toString()
            it[Keys.lastHashiHeight] = height.toString()
            it[Keys.lastHashiPercent] = percent
        }
    }

    /** The keys the settings are stored under. */
    private object Keys {        /** The default player name. */
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

        /** The width of the last minesweeper game. */
        val lastMinesweeperWidth = stringPreferencesKey("last_minesweeper_width")

        /** The height of the last minesweeper game. */
        val lastMinesweeperHeight = stringPreferencesKey("last_minesweeper_height")

        /** The mine density of the last minesweeper game. */
        val lastMinesweeperPercent = stringPreferencesKey("last_minesweeper_percent")

        /** The mode of the last minesweeper game. */
        val lastMinesweeperMode = stringPreferencesKey("last_minesweeper_mode")

        /** The width of the last nonogram game. */
        val lastNonogramWidth = stringPreferencesKey("last_nonogram_width")

        /** The height of the last nonogram game. */
        val lastNonogramHeight = stringPreferencesKey("last_nonogram_height")

        /** The width of the last hashi game. */
        val lastHashiWidth = stringPreferencesKey("last_hashi_width")

        /** The height of the last hashi game. */
        val lastHashiHeight = stringPreferencesKey("last_hashi_height")

        /** The island share of the last hashi game. */
        val lastHashiPercent = stringPreferencesKey("last_hashi_percent")
    }
}

/** The name the settings file is stored under. */
private const val SETTINGS_FILE = "settings"

/** The DataStore the settings live in, created once per process. */
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = SETTINGS_FILE)

/** Returns the settings store of this application. */
fun Context.createSettingsStore(): SettingsStore = SettingsStore(settingsStore)
