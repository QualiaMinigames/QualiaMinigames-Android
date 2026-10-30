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

import com.qiushui1012.app.minigames.BuildConfig

/**
 * The version this build reports, which the settings screen shows.
 *
 * It is read from the manifest the build wrote rather than typed here: a version stated twice
 * is a version that drifts, and the copy in this file stayed at the version it was first
 * written with while the manifest moved on.
 */
val BuildVersion: String = BuildConfig.VERSION_NAME

/** Where the source of this application lives, which the settings screen shows. */
const val RepositoryUrl: String = "https://github.com/QualiaMinigames/QualiaMinigames-Android"
