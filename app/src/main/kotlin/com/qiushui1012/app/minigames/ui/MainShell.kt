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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qiushui1012.app.minigames.AppContainer
import com.qiushui1012.app.minigames.GameInProgress
import com.qiushui1012.app.minigames.R
import com.qiushui1012.app.minigames.Space
import com.qiushui1012.app.minigames.Tab
import com.qiushui1012.app.minigames.data.Settings
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** How much room the bottom navigation takes, which a scrolling page leaves at its end. */
private val BottomNavigationInset = 84.dp

/** The width the navigation rail takes beside the content. */
private val SideRailWidth = 96.dp

/**
 * The three pages and the navigation between them.
 *
 * The navigation is an island rather than an edge-to-edge bar: in a window wider than it is
 * tall it stands on the left and the content is laid out beside it; in a portrait window it
 * floats over the bottom of the content, and every scrollable page leaves
 * [BottomNavigationInset] at its end so that the last row stops above it.
 *
 * The content lives in the same row slot in both arrangements, which is what keeps a page's
 * state alive when the window changes shape.
 */
@Composable
fun MainShell(
    container: AppContainer,
    settings: Settings,
    tab: Tab,
    onSelectTab: (Tab) -> Unit,
    onStartGame: (GameInProgress) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val side = maxWidth > maxHeight
        Row(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.width(if (side) SideRailWidth else 0.dp))
            Box(modifier = Modifier.weight(1f)) {
                when (tab) {
                    Tab.Home -> HomeScreen(container, settings, onStartGame)
                    Tab.Records -> RecordsScreen(container, settings, bottomInset = if (side) 0.dp else BottomNavigationInset)
                    Tab.Settings -> SettingsScreen(container, settings, bottomInset = if (side) 0.dp else BottomNavigationInset)
                }
            }
        }
        NavigationIsland(
            tab = tab,
            onSelect = onSelectTab,
            vertical = side,
            modifier =
                if (side) {
                    Modifier.align(Alignment.TopStart).padding(start = Space.m, top = Space.xl).width(SideRailWidth - Space.m * 2)
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = Space.l, vertical = Space.m)
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                },
        )
    }
}

/** The navigation itself: a rounded surface holding the pages, in a column or in a row. */
@Composable
private fun NavigationIsland(
    tab: Tab,
    onSelect: (Tab) -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    ShadowedSurface(shape = MaterialTheme.shapes.extraLarge, modifier = modifier) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp,
        ) {
            if (vertical) {
                Column(
                    modifier = Modifier.padding(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    for (page in Tab.entries) {
                        NavigationItem(page, tab == page, { onSelect(page) }, Modifier.fillMaxWidth())
                    }
                }
            } else {
                Row(
                    modifier = Modifier.padding(Space.s),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    for (page in Tab.entries) {
                        NavigationItem(page, tab == page, { onSelect(page) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** One page of the navigation: an icon and a label on their own rounded surface. */
@Composable
private fun NavigationItem(
    tab: Tab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = navigationLabel(tab)
    val icon = navigationIcon(tab)
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        onClick = onClick,
    ) {
        // One padding rather than two: a margin outside the surface plus a padding inside it
        // added up to a tab taller than the island holding it, so the selected state spilled
        // past the island's rounded corner.
        Box(
            modifier = Modifier.padding(horizontal = Space.m, vertical = Space.xs),
            contentAlignment = Alignment.Center,
        ) {
            // The icon sits above the label, and the label stays: an icon alone would be a
            // guess for a page the player has not opened yet, and a label alone leaves the
            // navigation looking like three words in a box.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Icon(
                    imageVector = icon,
                    // The label beside it already says what the page is; a second reading of
                    // the same words would be read out twice.
                    contentDescription = null,
                    tint =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

/** Returns a page's label in the language the interface is in. */
@Composable
private fun navigationLabel(tab: Tab): String =
    when (tab) {
        Tab.Home -> stringResource(R.string.nav_home)
        Tab.Records -> stringResource(R.string.nav_records)
        Tab.Settings -> stringResource(R.string.nav_settings)
    }

/**
 * Returns a page's icon.
 *
 * The shapes are the ones the Material Symbols page turns up for a house, a list of results and
 * a pair of sliders. They come from `material-icons-core`, which is already on the classpath and
 * holds exactly these three; the extended set is a library of thousands of shapes for three.
 */
private fun navigationIcon(tab: Tab): ImageVector =
    when (tab) {
        Tab.Home -> Icons.Filled.Home
        Tab.Records -> Icons.Filled.List
        Tab.Settings -> Icons.Filled.Settings
    }

/** The inset a scrolling page leaves at its end, for the navigation that floats over it. */
@Composable
fun navigationInset(vertical: Boolean): Dp = if (vertical) 0.dp else BottomNavigationInset