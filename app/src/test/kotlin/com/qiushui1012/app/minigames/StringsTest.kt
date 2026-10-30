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

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two string files, compared by key.
 *
 * A key added to the English file and forgotten in the Chinese one falls back silently to
 * English, which is only correct for a reader who wants English. The comparison is of the key
 * sets rather than of the texts: a translation is allowed to say something else, but it is not
 * allowed to be missing.
 *
 * The files are read from the source tree rather than through the resource table, because a
 * compiled resource table already holds the fallback: a key that only one file declares still
 * resolves, and would resolve to the same text in both languages.
 */
class StringsTest {
    @Test
    fun the_two_files_declare_the_same_keys() {
        val english = keys(File(resourceFile("values/strings.xml")))
        val chinese = keys(File(resourceFile("values-zh/strings.xml")))
        assertEquals("only the English file declares these", emptySet<String>(), english - chinese)
        assertEquals("only the Chinese file declares these", emptySet<String>(), chinese - english)
        assertTrue("the English file holds no keys at all", english.isNotEmpty())
    }

    /** Returns the keys a string resource file declares. */
    private fun keys(file: File): Set<String> =
        Regex("""<string name="([^"]+)"""").findAll(file.readText()).map { it.groupValues[1] }.toSet()

    /**
     * Returns the absolute path of a resource file.
     *
     * The unit tests run from the module's own directory, and the search walks upward as well
     * so that a runner started from the repository root finds the same file.
     */
    private fun resourceFile(relative: String): String {
        var directory: File? = File("src/main/res").absoluteFile
        while (directory != null) {
            val candidate = File(directory, relative)
            if (candidate.isFile) {
                return candidate.absolutePath
            }
            directory = directory.parentFile?.let { File(it, "app/src/main/res") }?.takeIf { it.isDirectory }
        }
        val direct = File("app/src/main/res/$relative")
        assertTrue("$direct is not a resource file", direct.isFile)
        return direct.absolutePath
    }
}
