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

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qiushui1012.app.minigames.AppContainer
import com.qiushui1012.app.minigames.GameInProgress
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.data.GameKind
import com.qiushui1012.app.minigames.data.Record
import com.qiushui1012.app.minigames.data.Settings
import kotlinx.coroutines.launch

/**
 * A dialog the shell draws above the navigation.
 *
 * The shell owns the dialogs rather than the pages, because a dialog covers the navigation and
 * only the shell is above it; and because the system's back press closes the topmost dialog
 * before it leaves the page, which is one decision made in one place.
 *
 * [NewGame] carries no save: the card on the home screen continues a saved game by itself, and
 * a new game over one is asked about before this dialog opens, so there is never a save left to
 * offer here.
 */
sealed interface ShellDialog {
    /** The parameters of a new game. */
    data class NewGame(val kind: GameKind) : ShellDialog

    /** The warning that a new game would discard the one in progress. */
    data class DiscardSave(val kind: GameKind) : ShellDialog

    /** One record, in full. */
    data class RecordDetail(val record: Record) : ShellDialog

    /** The question that comes before every record is deleted. */
    data object ClearRecords : ShellDialog
}

/**
 * Draws whichever dialog is open.
 *
 * [onDiscardConfirmed] is what happens after the player agrees to lose the saved game: the
 * save is dropped and the parameters follow, so the next dialog belongs to a game that no
 * longer has anything to continue.
 */
@Composable
fun ShellDialogContent(
    dialog: ShellDialog,
    container: AppContainer,
    settings: Settings,
    onDismiss: () -> Unit,
    onStartGame: (GameInProgress) -> Unit,
    onDiscardConfirmed: (GameKind) -> Unit,
) {
    when (dialog) {
        is ShellDialog.NewGame ->
            when (dialog.kind) {
                GameKind.Pipes ->
                    PipesNewGameDialog(
                        container = container,
                        settings = settings,
                        onDismiss = onDismiss,
                        onStart = {
                            onDismiss()
                            onStartGame(it)
                        },
                    )

                GameKind.Sudoku ->
                    SudokuNewGameDialog(
                        container = container,
                        settings = settings,
                        onDismiss = onDismiss,
                        onStart = {
                            onDismiss()
                            onStartGame(it)
                        },
                    )
            }

        is ShellDialog.DiscardSave ->
            DiscardSaveDialog(
                onConfirm = { onDiscardConfirmed(dialog.kind) },
                onDismiss = onDismiss,
            )

        is ShellDialog.RecordDetail -> RecordDetailDialog(record = dialog.record, onDismiss = onDismiss)

        ShellDialog.ClearRecords ->
            ClearRecordsDialog(
                onConfirm = {
                    container.scope.launch { container.records.deleteAllRecords() }
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
    }
}

/** The warning shown before a new game discards the one in progress. */
@Composable
fun DiscardSaveDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalForm(
        title = stringResource(R.string.dialog_discard_title),
        onDismiss = onDismiss,
        confirm = { ModalConfirm(stringResource(R.string.dialog_confirm), onClick = onConfirm) },
    ) {
        Text(stringResource(R.string.dialog_discard_save))
    }
}
