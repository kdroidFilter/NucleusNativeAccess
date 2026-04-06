package com.example.rustrfd

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class DialogType(val label: String) {
    PickFile("Pick File"),
    PickFiles("Pick Files"),
    PickFolder("Pick Folder"),
    PickFolders("Pick Folders"),
    SaveFile("Save File"),
    MessageInfo("Message (Info)"),
    MessageWarning("Message (Warning)"),
    MessageError("Message (Error)"),
}

data class DialogResult(
    val type: DialogType,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val paths: List<String> = emptyList(),
    val messageResult: String? = null,
    val cancelled: Boolean = false,
) {
    val summary: String
        get() = when {
            cancelled -> "Cancelled"
            messageResult != null -> messageResult
            paths.size == 1 -> paths.first().substringAfterLast('/')
            paths.isNotEmpty() -> "${paths.size} items selected"
            else -> "No result"
        }

    val formattedTime: String
        get() = timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
}
