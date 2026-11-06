package com.bdersolutions.daybreak.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

/** Puts [text] on the Windows clipboard, so it can be pasted into mail or a chat. */
fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

/** Asks where to save [text] as a plain text file. Returns false when the user cancels. */
fun saveTextFile(text: String, suggestedName: String): Boolean {
    val dialog = FileDialog(null as Frame?, "Daybreak", FileDialog.SAVE)
    dialog.file = suggestedName
    dialog.isVisible = true
    val directory = dialog.directory ?: return false
    val name = dialog.file ?: return false
    File(directory, name).writeText(text, Charsets.UTF_8)
    return true
}

/** Asks where to save a file, with [suggestedName] filled in. Returns null when the user cancels. */
fun pickSaveTarget(suggestedName: String): File? {
    val dialog = FileDialog(null as Frame?, "Daybreak", FileDialog.SAVE)
    dialog.file = suggestedName
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(directory, name)
}

/** A name that cannot leave its folder or upset the file system (same rule as the Android app). */
fun safeFileName(raw: String): String {
    val cleaned = raw.replace(Regex("[\\/:*?\"<>|\u0000-\u001f]"), "_").trim().trim('.').take(120)
    return cleaned.ifBlank { "Anhang" }
}
