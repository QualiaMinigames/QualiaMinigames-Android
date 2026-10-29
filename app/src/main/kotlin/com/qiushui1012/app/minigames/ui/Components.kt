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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qiushui1012.app.minigames.CardShadowBlur
import com.qiushui1012.app.minigames.CardShadowOffset
import com.qiushui1012.app.minigames.Space

/**
 * The pieces every screen is built from.
 *
 * They exist so that a screen reads as its own layout rather than as a pile of styling: the
 * spacing, the corner radius, and the way a heading sits above its content are decided here
 * once, and the screens below compose them.
 */

/**
 * The one shadow every surface in the interface uses.
 *
 * A layer of the same width, offset down by [CardShadowOffset] and blurred by
 * [CardShadowBlur], drawn before the surface so that the surface keeps its own shape and does
 * not draw a second Material shadow. The blur is a true gaussian rather than an elevation
 * silhouette, so no hard rectangle shows under a rounded card.
 */
@Composable
fun ShadowedSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val color = shadowColor()
    Box(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .offset(y = CardShadowOffset)
                    .blur(CardShadowBlur, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .background(color, shape),
        )
        content()
    }
}

/** The shadow color of the current scheme. */
@Composable
private fun shadowColor(): Color {
    // A dark shadow is invisible on a dark plate, so the dark scheme gets the same faint edge
    // as a highlight instead, which is the equivalent visual weight. Both are deliberately
    // very faint: the shadow is a soft edge, not a second card-shaped plate.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) {
        Color(red = 1f, green = 1f, blue = 1f, alpha = 0.04f)
    } else {
        Color(red = 0f, green = 0f, blue = 0f, alpha = 0.06f)
    }
}

/**
 * A titled card: the shape of every section below a screen's header.
 *
 * [title] is optional because some sections are one control whose own label is enough.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    ShadowedSurface(shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                title?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                content()
            }
        }
    }
}

/** A heading with its actions on the right, which is what a screen's header is. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f))
        actions()
    }
}

/** A label above a value, the way a settings row reads. */
@Composable
fun LabeledValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * A row of mutually exclusive options that wraps instead of squeezing.
 *
 * An option keeps the width its label asks for, and the row wraps as a whole when the options
 * no longer fit side by side. That is the opposite of dividing the row into equal shares: a
 * share-based option can become narrower than its label on a small window, and the label then
 * breaks into a tall, thin button that is hard to read and hard to hit.
 */
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        for (option in options) {
            val text = label(option)
            ChoiceButton(
                label = text,
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

/** One option of a row of mutually exclusive choices. */
@Composable
fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    surfaceButton(label = label, selected = selected, onClick = onClick) { Text(label) }
}

/** The shared surface a choice is drawn on, so that every row of choices matches. */
@Composable
private fun surfaceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            ),
        onClick = onClick,
    ) {
        Box(modifier = Modifier.padding(horizontal = Space.m, vertical = Space.s)) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides
                    if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            ) {
                content()
            }
        }
    }
}

/** A text button that reads as a link, for the one place a value is opened elsewhere. */
@Composable
fun LinkButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(text) }
}
