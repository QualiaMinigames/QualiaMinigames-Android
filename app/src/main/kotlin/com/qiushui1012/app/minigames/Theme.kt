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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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

/**
 * The blue water is drawn in.
 *
 * One blue for both schemes: it is light enough to read on the ink background and dark enough
 * to tell apart from the plate, so the two schemes need no second value of it.
 */
val Water = Color(0xFFA8CAD4)

/**
 * A deep green for a board the player cleared: the counterpart of [AlertLight].
 *
 * It is a third signal rather than a second brand colour, and it exists for the same reason
 * the red does: "you won" and "you lost" are told apart in one glance, and a grey line above a
 * board reads as neither.
 */
val SuccessLight = Color(0xFF1B5E20)

/** The same green, lightened until it reads on a dark surface. */
val SuccessDark = Color(0xFFA5D6A7)

/**
 * The blue a sudoku digit the player entered is drawn in.
 *
 * One blue for both schemes, for the same reason [Water] needs only one: the tone reads on the
 * white surface and on the ink one alike.
 */
val EnteredDigit = Color(0xFF81B2C0)

/**
 * The typeface every duration is written in.
 *
 * A duration is read by comparing it with another, so its digits have to be the same width as
 * the digits above and below it in a list: a proportional face makes `1:07` narrower than
 * `10:12` and the times stop lining up. The file is a subset of Cascadia Code holding the
 * digits, the colon, the hyphen and the two quote marks, which is every character a duration
 * and a seed can be written with; the whole face is about 390 KB, this is about 17 KB.
 *
 * A character outside the subset falls back to the platform's own font rather than a box, so
 * the family is safe to use for anything, but only the times are drawn with it.
 */
val DurationFont: FontFamily =
    FontFamily(
        Font(R.font.cascadia_code, weight = androidx.compose.ui.text.font.FontWeight.Normal),
    )

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
