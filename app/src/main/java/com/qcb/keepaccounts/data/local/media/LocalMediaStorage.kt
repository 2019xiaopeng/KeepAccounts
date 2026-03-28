package com.qcb.keepaccounts.data.local.media

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun persistImageForSlot(
    context: Context,
    sourceUri: Uri,
    slot: String,
): String? = withContext(Dispatchers.IO) {
    runCatching {
        val mediaDir = File(context.filesDir, "media")
        if (!mediaDir.exists()) mediaDir.mkdirs()

        val safeSlot = slot
            .trim()
            .ifBlank { "image" }
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")

        mediaDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("${safeSlot}_") }
            ?.forEach { it.delete() }

        val ext = resolveImageExtension(context, sourceUri)
        val target = File(mediaDir, "${safeSlot}_${System.currentTimeMillis()}.$ext")

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return@runCatching null

        target.absolutePath
    }.getOrNull()
}

private fun resolveImageExtension(context: Context, sourceUri: Uri): String {
    val mime = context.contentResolver.getType(sourceUri).orEmpty().lowercase()
    return when {
        "png" in mime -> "png"
        "webp" in mime -> "webp"
        "gif" in mime -> "gif"
        else -> "jpg"
    }
}
