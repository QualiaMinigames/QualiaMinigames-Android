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

package com.qiushui1012.app.minigames.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * Writes an exported image where the player can find it.
 *
 * # Two paths, because the platform has two
 *
 * From Android 10 the media store accepts an image from any application without a permission
 * of its own, and the file lands in the pictures collection where a gallery shows it. Below
 * that, the media store only accepts an entry for a file the application has already written
 * into shared storage, which needs `WRITE_EXTERNAL_STORAGE`; rather than ask for a permission
 * to save a picture, the file is written into the application's own external directory there
 * and the path is handed back, which is a place the player can still reach.
 *
 * The returned [SavedImage] carries both what to show and what to share: the content URI on
 * the newer path, the file on the older one.
 */
object ImageExport {
    /** What an export produced. */
    data class SavedImage(
        /** Where the image is, as a URI a share sheet and a gallery both accept. */
        val uri: Uri,
        /** Where the image is, in words, for the message that confirms the save. */
        val displayPath: String,
    )

    /**
     * Writes an image to the pictures collection.
     *
     * Returns null when the platform refuses the write, which is what a full disk looks like
     * from here: the caller reports it rather than pretending the export happened.
     */
    fun save(context: Context, bitmap: Bitmap, fileName: String): SavedImage? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveThroughMediaStore(context, bitmap, fileName)
        } else {
            saveToAppPictures(context, bitmap, fileName)
        }

    /** Writes through the media store, which needs no permission from Android 10 on. */
    private fun saveThroughMediaStore(context: Context, bitmap: Bitmap, fileName: String): SavedImage? {
        val resolver = context.contentResolver
        val details =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/QualiaMinigames")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, details) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    resolver.delete(uri, null, null)
                    return null
                }
            } ?: run {
                resolver.delete(uri, null, null)
                return null
            }
            details.clear()
            details.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, details, null, null)
            SavedImage(uri, "${Environment.DIRECTORY_PICTURES}/QualiaMinigames/$fileName")
        } catch (_: java.io.IOException) {
            // The insert succeeded but the bytes did not land; leaving the pending entry
            // behind would show as a broken image in the gallery.
            resolver.delete(uri, null, null)
            null
        }
    }

    /** Writes into the application's own pictures directory, for Android 9 and lower. */
    private fun saveToAppPictures(context: Context, bitmap: Bitmap, fileName: String): SavedImage? {
        val directory =
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
        val target = File(directory, fileName)
        return try {
            FileOutputStream(target).use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
            SavedImage(androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", target), target.absolutePath)
        } catch (_: java.io.IOException) {
            null
        }
    }
}
