package com.example.rustrfd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// All rfd dialog calls run on IO to avoid blocking the UI thread.
// The rfd crate internally dispatches to the appropriate thread for native dialogs.

suspend fun pickFile(
    title: String = "Select a file",
    directory: String? = null,
): DialogResult = withContext(Dispatchers.IO) {
    runCatching {
        var dialog = FileDialog()
        dialog = dialog.set_title(title)
        if (directory != null) {
            dialog = dialog.set_directory(directory)
        }
        val path = dialog.pick_file()
        if (path != null) {
            DialogResult(type = DialogType.PickFile, paths = listOf(path))
        } else {
            DialogResult(type = DialogType.PickFile, cancelled = true)
        }
    }.getOrElse {
        DialogResult(type = DialogType.PickFile, cancelled = true)
    }
}

suspend fun pickFiles(
    title: String = "Select files",
    directory: String? = null,
): DialogResult = withContext(Dispatchers.IO) {
    runCatching {
        var dialog = FileDialog()
        dialog = dialog.set_title(title)
        if (directory != null) {
            dialog = dialog.set_directory(directory)
        }
        val paths = dialog.pick_files()
        if (!paths.isNullOrEmpty()) {
            DialogResult(type = DialogType.PickFiles, paths = paths)
        } else {
            DialogResult(type = DialogType.PickFiles, cancelled = true)
        }
    }.getOrElse {
        DialogResult(type = DialogType.PickFiles, cancelled = true)
    }
}

suspend fun pickFolder(
    title: String = "Select a folder",
    directory: String? = null,
): DialogResult = withContext(Dispatchers.IO) {
    runCatching {
        var dialog = FileDialog()
        dialog = dialog.set_title(title)
        if (directory != null) {
            dialog = dialog.set_directory(directory)
        }
        val path = dialog.pick_folder()
        if (path != null) {
            DialogResult(type = DialogType.PickFolder, paths = listOf(path))
        } else {
            DialogResult(type = DialogType.PickFolder, cancelled = true)
        }
    }.getOrElse {
        DialogResult(type = DialogType.PickFolder, cancelled = true)
    }
}

suspend fun pickFolders(
    title: String = "Select folders",
    directory: String? = null,
): DialogResult = withContext(Dispatchers.IO) {
    runCatching {
        var dialog = FileDialog()
        dialog = dialog.set_title(title)
        if (directory != null) {
            dialog = dialog.set_directory(directory)
        }
        val paths = dialog.pick_folders()
        if (!paths.isNullOrEmpty()) {
            DialogResult(type = DialogType.PickFolders, paths = paths)
        } else {
            DialogResult(type = DialogType.PickFolders, cancelled = true)
        }
    }.getOrElse {
        DialogResult(type = DialogType.PickFolders, cancelled = true)
    }
}

suspend fun pickFileOrFolder(
    title: String = "Select a file or folder",
    directory: String? = null,
): DialogResult = withContext(Dispatchers.IO) {
    runCatching {
        var dialog = FileDialog()
        dialog = dialog.set_title(title)
        if (directory != null) {
            dialog = dialog.set_directory(directory)
        }
        val path = dialog.pick_file_or_folder()
        if (path != null) {
            DialogResult(type = DialogType.PickFile, paths = listOf(path))
        } else {
            DialogResult(type = DialogType.PickFile, cancelled = true)
        }
    }.getOrElse {
        DialogResult(type = DialogType.PickFile, cancelled = true)
    }
}

suspend fun pickFilesOrFolders(
    title: String = "Select files or folders",
    directory: String? = null,
): DialogResult = withContext(Dispatchers.IO) {
    runCatching {
        var dialog = FileDialog()
        dialog = dialog.set_title(title)
        if (directory != null) {
            dialog = dialog.set_directory(directory)
        }
        val paths = dialog.pick_files_or_folders()
        if (!paths.isNullOrEmpty()) {
            DialogResult(type = DialogType.PickFiles, paths = paths)
        } else {
            DialogResult(type = DialogType.PickFiles, cancelled = true)
        }
    }.getOrElse {
        DialogResult(type = DialogType.PickFiles, cancelled = true)
    }
}

suspend fun saveFile(
    title: String = "Save file",
    fileName: String? = null,
    directory: String? = null,
): DialogResult = withContext(Dispatchers.IO) {
    runCatching {
        var dialog = FileDialog()
        dialog = dialog.set_title(title)
        if (fileName != null) {
            dialog = dialog.set_file_name(fileName)
        }
        if (directory != null) {
            dialog = dialog.set_directory(directory)
        }
        val path = dialog.save_file()
        if (path != null) {
            DialogResult(type = DialogType.SaveFile, paths = listOf(path))
        } else {
            DialogResult(type = DialogType.SaveFile, cancelled = true)
        }
    }.getOrElse {
        DialogResult(type = DialogType.SaveFile, cancelled = true)
    }
}

suspend fun showMessage(
    title: String = "Message",
    description: String = "",
    level: MessageLevel,
    buttons: MessageButtons,
): DialogResult = withContext(Dispatchers.IO) {
    val dialogType = when (level) {
        MessageLevel.Info -> DialogType.MessageInfo
        MessageLevel.Warning -> DialogType.MessageWarning
        MessageLevel.Error -> DialogType.MessageError
    }
    runCatching {
        val dialog = MessageDialog()
            .set_title(title)
            .set_description(description)
            .set_level(level)
            .set_buttons(buttons)
        val result = dialog.show()
        val resultName = result.tag.name
        DialogResult(type = dialogType, messageResult = resultName)
    }.getOrElse {
        DialogResult(type = dialogType, messageResult = "Error: ${it.message}")
    }
}
