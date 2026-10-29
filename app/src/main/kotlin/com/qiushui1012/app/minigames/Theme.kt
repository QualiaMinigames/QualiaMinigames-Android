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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * The application's visual language.
 *
 * # Where the colors come from
 *
 * They are the mark's own two tones: the plate `#EAF2F4` and the ink `#091012`. The plate is
 * the background, white is for the surfaces that sit on it, and the ink carries the text and
 * the primary action. There is deliberately no third color: adding a brand color to a
 * two-tone drawing invents one.
 *
 * The one exception is the red a board uses for a loop and for a conflict. It is a signal
 * rather than a style, and it is the same red in both schemes, lightened in the dark one
 * until it reads on the ink.
 *
 * # The dark scheme swaps the two roles
 *
 * It is not a second palette: the ink becomes the background and the plate becomes what is
 * drawn on it, so the two schemes are the same drawing read in both directions.
 *
 * # Why the theme paints a background
 *
 * The content is drawn on a [Surface] of the scheme's own background rather than on whatever
 * is behind the window. A `Text` outside a surface takes Material's default content color,
 * which is black: invisible in the light scheme only because the plate is nearly white, and
 * unreadable in the dark one.
 */
@Composable
fun QualiaTheme(theme: ThemeChoice = ThemeChoice.System, content: @Composable () -> Unit) {
    val dark =
        when (theme) {
            ThemeChoice.System -> isSystemInDarkTheme()
            ThemeChoice.Light -> false
            ThemeChoice.Dark -> true
        }
    val colors = if (dark) QualiaDarkColors else QualiaLightColors
    MaterialTheme(colorScheme = colors, shapes = QualiaShapes) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.background,
            contentColor = colors.onBackground,
            content = content,
        )
    }
}

/** The theme the user chose. */
enum class ThemeChoice {
    /** Follow the operating system. */
    System,
    /** Always light. */
    Light,
    /** Always dark. */
    Dark,
}

/** The plate of the mark: the background everything sits on. */
private val Plate = Color(0xFFEAF2F4)

/** The line work of the mark: text, icons and the primary action. */
private val Ink = Color(0xFF091012)

/** A recessed plate, for a selected row or a secondary surface. */
private val Recessed = Color(0xFFD8E5E9)

/** The muted ink of secondary text. */
private val MutedInk = Color(0xFF44545A)

/** A deep red for a loop or a conflict: dark enough to read as "stop" without a second hue. */
val AlertLight = Color(0xFF8C1D18)

/** The same red, lightened until it reads on a dark surface. */
val AlertDark = Color(0xFFFFB4AB)

/** The blue water is drawn in, dark enough to read against the plate. */
val WaterLight = Color(0xFF1F5FA8)

/** The same blue, lightened for the ink background. */
val WaterDark = Color(0xFF8FC4F0)

/** The ink lifted just enough to be told apart from the background it sits on. */
private val Raised = Color(0xFF16232A)

/** The ink lifted further, for a selected row or a secondary surface on a dark background. */
private val RaisedMore = Color(0xFF22333A)

/** The plate pulled toward the ink: secondary text on a dark surface. */
private val MutedPlate = Color(0xFFA6BABF)

/** The light scheme, built from the mark. */
internal val QualiaLightColors =
    lightColorScheme(
        primary = Ink,
        onPrimary = Color.White,
        primaryContainer = Recessed,
        onPrimaryContainer = Ink,
        secondary = MutedInk,
        onSecondary = Color.White,
        secondaryContainer = Recessed,
        onSecondaryContainer = Ink,
        tertiary = Ink,
        onTertiary = Color.White,
        background = Plate,
        onBackground = Ink,
        surface = Color.White,
        onSurface = Ink,
        surfaceVariant = Recessed,
        onSurfaceVariant = MutedInk,
        surfaceContainerHighest = Recessed,
        outline = Color(0xFF9FB2B8),
        outlineVariant = Color(0xFFC7D6DA),
        error = AlertLight,
        onError = Color.White,
        errorContainer = Color(0xFFF7DAD7),
        onErrorContainer = Color(0xFF3B0907),
        scrim = Ink,
    )

/** The dark scheme: the mark's two tones with their roles swapped. */
internal val QualiaDarkColors =
    darkColorScheme(
        primary = Plate,
        onPrimary = Ink,
        primaryContainer = RaisedMore,
        onPrimaryContainer = Plate,
        secondary = MutedPlate,
        onSecondary = Ink,
        secondaryContainer = RaisedMore,
        onSecondaryContainer = Plate,
        tertiary = Plate,
        onTertiary = Ink,
        background = Ink,
        onBackground = Plate,
        surface = Raised,
        onSurface = Plate,
        surfaceVariant = RaisedMore,
        onSurfaceVariant = MutedPlate,
        surfaceContainerHighest = RaisedMore,
        outline = Color(0xFF46585E),
        outlineVariant = Color(0xFF2A383D),
        error = AlertDark,
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        scrim = Ink,
    )

/** Whether the current scheme is the dark one, which the board's own colors follow. */
@Composable
fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** The corner radius the shadow geometry is derived from. */
val EntryCardCornerRadius = 20.dp

/** The shadow starts this far below the surface, and its softness is the same length. */
val CardShadowOffset = EntryCardCornerRadius * (2f / 3f)

/** The shadow's spread, tightened to two thirds of the entry card's corner radius. */
val CardShadowBlur = CardShadowOffset

/** Rounded like the mark: a sixth of the side rather than a near-square corner. */
private val QualiaShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

/**
 * The spacing scale.
 *
 * One scale for the whole interface, so that "a bit of space" is a decision made once.
 */
object Space {
    /** 4 dp: between a label and its value. */
    val xs = 4.dp

    /** 8 dp: between an icon and its text, between rows of one control. */
    val s = 8.dp

    /** 12 dp: inside a card, between the fields of a form. */
    val m = 12.dp

    /** 16 dp: the padding of a screen, between cards. */
    val l = 16.dp

    /** 24 dp: between sections. */
    val xl = 24.dp

    /** 32 dp: around a screen's content when there is room. */
    val xxl = 32.dp
}

/** Sizes used by more than one screen. */
object Sizes {
    /** An icon in a navigation item. */
    val navIcon = 24.dp

    /** An icon inside a header button. */
    val actionIcon = 22.dp

    /** The narrowest a board cell may be drawn, which is what keeps a tap reachable. */
    val minCell = 16.dp
}
