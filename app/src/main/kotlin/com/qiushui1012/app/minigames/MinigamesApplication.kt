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

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import android.content.Context
import com.qiushui1012.app.minigames.data.RecordsStore
import com.qiushui1012.app.minigames.data.SettingsStore
import com.qiushui1012.app.minigames.data.createRecordsStore
import com.qiushui1012.app.minigames.data.createSettingsStore

/**
 * The application object.
 *
 * It owns the two stores and nothing else. A store is created once and shared, because a
 * second DataStore over the same file throws: the platform allows exactly one per file per
 * process, and a screen that built its own would take the application down on the second one.
 */
class MinigamesApplication : Application() {
    /** The stores and the settings this process uses. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container =
            AppContainer(
                settings = createSettingsStore(),
                records = createRecordsStore(this),
                // Writes that must outlive the screen that asked for them: finishing a game
                // records it and then leaves the screen, and a scope owned by that screen
                // would be cancelled between the two.
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            )
    }
}

/**
 * What the composition is given.
 *
 * A plain holder rather than a dependency-injection framework: there are two stores, and a
 * framework would be more moving parts than the thing it holds.
 */
class AppContainer(
    /** The user's preferences. */
    val settings: SettingsStore,
    /** The finished games and the unfinished ones. */
    val records: RecordsStore,
    /** The scope for work that must not end with the screen that started it. */
    val scope: CoroutineScope,
)

/** Returns the container of this application. */
val Context.appContainer: AppContainer
    get() = (applicationContext as MinigamesApplication).container
