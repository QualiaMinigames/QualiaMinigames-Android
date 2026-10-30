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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.Space

/**
 * A form that covers the page until it is answered.
 *
 * # Why this is not an `AlertDialog`
 *
 * A dialog opens a window of its own, and that window resolves its resources through the
 * activity rather than through the composition: a language chosen in the settings reaches
 * every screen and stops at the dialog, which then reads in the language the activity was
 * created in. Covering the page inside the same composition keeps one locale for everything,
 * one theme, and one place where a back press is handled.
 *
 * # What it covers
 *
 * The form covers the whole window, navigation included. It is drawn by the shell above both
 * the page and the navigation island, and its scrim is what takes every tap, so the navigation
 * behind it is neither visible as available nor reachable: a dialog is a question, and a page
 * tab is not an answer to it.
 *
 * The scrim is laid down edge to edge, over the strip the status bar occupies, but the form
 * itself keeps clear of the system bars: the page behind it is dimmed from the very top of the
 * window, and the card still ends above the gesture bar.
 *
 * A click outside the card does nothing. The card is a form, and a form is not something a
 * stray tap should discard; the ways out are the buttons, and they say what they do.
 *
 * [dismissLabel] is the label of the secondary button; a null label leaves the confirm button
 * as the only way out, which is what a dialog that only has to be read wants.
 */
@Composable
fun ModalForm(
    title: String,
    onDismiss: () -> Unit,
    confirm: @Composable () -> Unit,
    dismissLabel: String? = stringResource(R.string.dialog_cancel),
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        contentAlignment = Alignment.Center,
    ) {
        ShadowedSurface(
            shape = MaterialTheme.shapes.extraLarge,
            modifier =
                Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = Space.l, vertical = Space.xl),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(Space.xl),
                    verticalArrangement = Arrangement.spacedBy(Space.m),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    content()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                    ) {
                        Box(Modifier.weight(1f))
                        if (dismissLabel != null) {
                            TextButton(onClick = onDismiss) { Text(dismissLabel) }
                        }
                        confirm()
                    }
                }
            }
        }
    }
}

/** The primary action of a [ModalForm]. */
@Composable
fun ModalConfirm(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled) { Text(label) }
}
