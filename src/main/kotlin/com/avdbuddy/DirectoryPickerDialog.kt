package com.avdbuddy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.OutlinedButton
import java.io.File

internal data class DirectoryListing(val folders: List<File>, val error: String? = null)

internal fun listDirectories(folder: File): DirectoryListing = try {
    when {
        !folder.isDirectory -> DirectoryListing(emptyList(), "Enter a path to an existing folder.")
        else -> folder.listFiles()?.let { files ->
            DirectoryListing(files.filter { it.isDirectory }.sortedBy { it.name.lowercase() })
        } ?: DirectoryListing(emptyList(), "This folder cannot be read. Choose another folder.")
    }
} catch (_: SecurityException) {
    DirectoryListing(emptyList(), "This folder cannot be read. Choose another folder.")
}

@Composable
fun DirectoryPickerDialog(initialPath: String, onClose: () -> Unit, onSelect: (String) -> Unit) {
    val pathField = rememberTextFieldState(initialPath)
    val roots = remember { File.listRoots().toList() }
    val folder = remember(pathField.text.toString()) { expandPath(pathField.text.toString()).absoluteFile }
    val listing by key(folder) {
        produceState<DirectoryListing?>(null) {
            value = withContext(Dispatchers.IO) { listDirectories(folder) }
        }
    }
    val currentListing = listing
    DialogScaffold("Choose folder", "DirectoryPickerDialog", height = 520, scrollableContent = false,
        onClose = onClose, footer = {
            Spacer(Modifier.weight(1f))
            ActionButton("Cancel", tag = "CancelDirectoryPicker", onClick = onClose)
            ActionButton("Choose folder", primary = true, enabled = pathField.text.isNotBlank() && currentListing?.error == null && currentListing != null,
                tag = "ConfirmDirectoryPicker") { onSelect(folder.path) }
        }) {
        Field(pathField, "DirectoryPath", Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Parent folder", enabled = folder.parentFile != null, tag = "ParentDirectory") {
                folder.parentFile?.let { pathField.setTextAndPlaceCursorAtEnd(it.path) }
            }
            if (roots.size > 1) roots.forEach { root ->
                ActionButton(root.path) { pathField.setTextAndPlaceCursorAtEnd(root.path) }
            }
        }
        when {
            pathField.text.isBlank() -> Label("Enter a path to an existing folder.", color = JewelTheme.globalColors.text.error)
            currentListing == null -> Label("Loading folders…", color = JewelTheme.globalColors.text.info)
            currentListing.error != null -> Label(currentListing.error, color = JewelTheme.globalColors.text.error)
            currentListing.folders.isEmpty() -> Label("No subfolders. You can choose this folder.", color = JewelTheme.globalColors.text.info)
            else -> LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("DirectoryFolders")) {
                items(currentListing.folders, key = { it.path }) { child ->
                    OutlinedButton(
                        onClick = { pathField.setTextAndPlaceCursorAtEnd(child.path) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Label(child.name, maxLines = 1) }
                }
            }
        }
    }
}
