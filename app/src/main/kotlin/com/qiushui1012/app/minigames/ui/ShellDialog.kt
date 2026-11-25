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
import com.qiushui1012.app.minigames.data.GameRecord
import com.qiushui1012.app.minigames.data.Play
import com.qiushui1012.app.minigames.data.PlayRef
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

    /** One play, in full, with the group that names its parameters. */
    data class RecordDetail(val group: GameRecord, val play: Play) : ShellDialog

    /**
     * The question that comes before some plays are deleted.
     *
     * It carries the identities the player selected rather than reading them from the page:
     * a confirmation is a question about a particular set of plays, and a set that changed
     * between the question and the answer would delete something nobody agreed to.
     */
    data class DeleteRecords(val refs: Set<PlayRef>) : ShellDialog

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

                GameKind.Minesweeper ->
                    MinesweeperNewGameDialog(
                        container = container,
                        settings = settings,
                        onDismiss = onDismiss,
                        onStart = {
                            onDismiss()
                            onStartGame(it)
                        },
                    )

                GameKind.Nonogram ->
                    NonogramNewGameDialog(
                        container = container,
                        settings = settings,
                        onDismiss = onDismiss,
                        onStart = {
                            onDismiss()
                            onStartGame(it)
                        },
                    )

                GameKind.Hashi ->
                    HashiNewGameDialog(
                        container = container,
                        settings = settings,
                        onDismiss = onDismiss,
                        onStart = {
                            onDismiss()
                            onStartGame(it)
                        },
                    )

                GameKind.ArrowCube ->
                    ArrowCubeNewGameDialog(
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

        is ShellDialog.RecordDetail ->
            RecordDetailDialog(group = dialog.group, play = dialog.play, onDismiss = onDismiss)

        is ShellDialog.DeleteRecords ->
            DeleteRecordsDialog(
                count = dialog.refs.size,
                onConfirm = {
                    container.scope.launch { container.records.delete(dialog.refs) }
                    onDismiss()
                },
                onDismiss = onDismiss,
            )

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
