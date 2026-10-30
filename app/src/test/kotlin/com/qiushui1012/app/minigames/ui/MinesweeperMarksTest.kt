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

import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.qiushui1012.app.minigames.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two marks a board draws on its cells.
 *
 * A vector file whose `pathData` cannot be parsed inflates to nothing, and a board that draws
 * it then draws an empty cell: a mine would be invisible on the very board that ended on it.
 * That is the failure these check for, which no screenshot of a covered board would reveal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MinesweeperMarksTest {
    /** The context the drawables are read through. */
    private val context: android.content.Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun the_flag_mark_inflates() {
        val flag: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_flag)
        assertNotNull("ic_flag.xml inflates to nothing", flag)
        assertNotNull("ic_flag.xml has no intrinsic size", flag?.intrinsicWidth)
    }

    @Test
    fun the_mine_mark_inflates() {
        val mine: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_bomb)
        assertNotNull("ic_bomb.xml inflates to nothing", mine)
        assertNotNull("ic_bomb.xml has no intrinsic size", mine?.intrinsicWidth)
    }

    @Test
    fun the_copy_mark_inflates() {
        val copy: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_content_copy)
        assertNotNull("ic_content_copy.xml inflates to nothing", copy)
    }
}
