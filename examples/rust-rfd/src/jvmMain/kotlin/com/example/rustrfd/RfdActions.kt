package com.example.rustrfd

// All rfd dialog calls are suspend funs (generated with withContext(Dispatchers.IO)).
// No manual dispatcher wrapping needed.

suspend fun pickFile(
    title: String = "Select a file",
    directory: String? = null,
    filters: List<Pair<String, List<String>>> = emptyList(),
): DialogResult = runCatching {
    val path = FileDialog().applyCommon(title, directory, filters).pick_file()
    if (path != null) DialogResult(type = DialogType.PickFile, paths = listOf(path))
    else DialogResult(type = DialogType.PickFile, cancelled = true)
}.getOrElse { DialogResult(type = DialogType.PickFile, cancelled = true) }

suspend fun pickFiles(
    title: String = "Select files",
    directory: String? = null,
    filters: List<Pair<String, List<String>>> = emptyList(),
): DialogResult = runCatching {
    val paths = FileDialog().applyCommon(title, directory, filters).pick_files()
    if (!paths.isNullOrEmpty()) DialogResult(type = DialogType.PickFiles, paths = paths)
    else DialogResult(type = DialogType.PickFiles, cancelled = true)
}.getOrElse { DialogResult(type = DialogType.PickFiles, cancelled = true) }

suspend fun pickFolder(
    title: String = "Select a folder",
    directory: String? = null,
): DialogResult = runCatching {
    var dialog = FileDialog().set_title(title)
    if (directory != null) dialog = dialog.set_directory(directory)
    val path = dialog.pick_folder()
    if (path != null) DialogResult(type = DialogType.PickFolder, paths = listOf(path))
    else DialogResult(type = DialogType.PickFolder, cancelled = true)
}.getOrElse { DialogResult(type = DialogType.PickFolder, cancelled = true) }

suspend fun pickFolders(
    title: String = "Select folders",
    directory: String? = null,
): DialogResult = runCatching {
    var dialog = FileDialog().set_title(title)
    if (directory != null) dialog = dialog.set_directory(directory)
    val paths = dialog.pick_folders()
    if (!paths.isNullOrEmpty()) DialogResult(type = DialogType.PickFolders, paths = paths)
    else DialogResult(type = DialogType.PickFolders, cancelled = true)
}.getOrElse { DialogResult(type = DialogType.PickFolders, cancelled = true) }

suspend fun pickFileOrFolder(
    title: String = "Select a file or folder",
    directory: String? = null,
): DialogResult = runCatching {
    var dialog = FileDialog().set_title(title)
    if (directory != null) dialog = dialog.set_directory(directory)
    val path = dialog.pick_file_or_folder()
    if (path != null) DialogResult(type = DialogType.PickFile, paths = listOf(path))
    else DialogResult(type = DialogType.PickFile, cancelled = true)
}.getOrElse { DialogResult(type = DialogType.PickFile, cancelled = true) }

suspend fun pickFilesOrFolders(
    title: String = "Select files or folders",
    directory: String? = null,
): DialogResult = runCatching {
    var dialog = FileDialog().set_title(title)
    if (directory != null) dialog = dialog.set_directory(directory)
    val paths = dialog.pick_files_or_folders()
    if (!paths.isNullOrEmpty()) DialogResult(type = DialogType.PickFiles, paths = paths)
    else DialogResult(type = DialogType.PickFiles, cancelled = true)
}.getOrElse { DialogResult(type = DialogType.PickFiles, cancelled = true) }

suspend fun saveFile(
    title: String = "Save file",
    fileName: String? = null,
    directory: String? = null,
    filters: List<Pair<String, List<String>>> = emptyList(),
): DialogResult = runCatching {
    var dialog = FileDialog().applyCommon(title, directory, filters)
    if (fileName != null) dialog = dialog.set_file_name(fileName)
    val path = dialog.save_file()
    if (path != null) DialogResult(type = DialogType.SaveFile, paths = listOf(path))
    else DialogResult(type = DialogType.SaveFile, cancelled = true)
}.getOrElse { DialogResult(type = DialogType.SaveFile, cancelled = true) }

suspend fun showMessage(
    title: String = "Message",
    description: String = "",
    level: MessageLevel,
    buttons: MessageButtons,
): DialogResult {
    val dialogType = when (level) {
        MessageLevel.Info -> DialogType.MessageInfo
        MessageLevel.Warning -> DialogType.MessageWarning
        MessageLevel.Error -> DialogType.MessageError
    }
    return runCatching {
        val dialog = MessageDialog()
            .set_title(title)
            .set_description(description)
            .set_level(level)
            .set_buttons(buttons)
        val result = dialog.show()
        DialogResult(type = dialogType, messageResult = result.tag.name)
    }.getOrElse {
        DialogResult(type = dialogType, messageResult = "Error: ${it.message}")
    }
}

private fun FileDialog.applyCommon(
    title: String,
    directory: String?,
    filters: List<Pair<String, List<String>>>,
): FileDialog {
    var d = set_title(title)
    if (directory != null) d = d.set_directory(directory)
    for ((name, extensions) in filters) {
        d = d.add_filter(name, extensions)
    }
    return d
}
