package com.avdbuddy

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.UnstyledButton

/** SDK identifiers (including extension releases) share one entry in the version sidebar. */
fun SystemImage.androidFamilyId(): String = api.takeWhile(Char::isDigit).ifBlank { api }

@Composable
fun AndroidImagePicker(
    images: List<SystemImage>,
    type: DeviceType,
    chosen: SystemImage?,
    onSelect: (SystemImage?) -> Unit,
) {
    var selectedFamilyId by remember(type) { mutableStateOf<String?>(chosen?.androidFamilyId()) }
    val compatible = remember(images, type) { images.filter { type.accepts(it.tag) } }
    val families = compatible.groupBy { it.androidFamilyId() }.entries.sortedWith(
        compareByDescending<Map.Entry<String, List<SystemImage>>> { it.key.toIntOrNull() ?: Int.MAX_VALUE }
            .thenByDescending { it.key }
    )
    val activeFamily = families.firstOrNull { it.key == selectedFamilyId } ?: families.firstOrNull()
    val activeImages = activeFamily?.value.orEmpty()
    val onSelectionChange by rememberUpdatedState(onSelect)
    LaunchedEffect(activeFamily?.key, chosen, activeImages) {
        if (chosen != null && chosen !in activeImages) onSelectionChange(null)
    }
    Row(
        Modifier.fillMaxSize().testTag("AndroidVersionPicker"),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(Modifier.width(220.dp).fillMaxHeight().testTag("AndroidVersionsPane")) {
            Label("Android versions", weight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            PickerScrollPane(Modifier.weight(1f)) {
                families.forEach { (id, variants) ->
                    val active = activeFamily?.key == id
                    PickerOption("Version-$id", active, onClick = {
                        selectedFamilyId = id
                        if (chosen?.androidFamilyId() != id) onSelect(null)
                    }) {
                        Column(Modifier.weight(1f)) {
                            Label(androidFamilyTitle(id), weight = FontWeight.Medium)
                            Spacer(Modifier.height(5.dp))
                            Label("${androidApiSubtitle(id)} · ${variants.distinctBy { it.api }.size} version${if (variants.distinctBy { it.api }.size == 1) "" else "s"}", color = Palette.muted, size = 11)
                        }
                    }
                }
                if (families.isEmpty()) Label("No Android versions available for this device type.", color = Palette.muted, size = 12)
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.border))
        Column(Modifier.weight(1f).fillMaxHeight().testTag("AndroidImagesPane")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label("Versions", weight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                activeFamily?.let { Label(androidFamilyTitle(it.key), color = Palette.muted, size = 11) }
            }
            Spacer(Modifier.height(12.dp))
            PickerScrollPane(Modifier.weight(1f)) {
                val releases = activeImages.groupBy(SystemImage::api).entries
                    .sortedWith(compareByDescending(androidReleaseOrder) { it.key })
                releases.forEach { (api, variants) ->
                    PickerOption("Release-$api", chosen?.api == api, onClick = {
                        onSelect(preferredSystemImage(variants))
                    }) {
                        Column(Modifier.weight(1f)) {
                            Label(androidApiSubtitle(api), weight = FontWeight.Medium)
                            Spacer(Modifier.height(5.dp))
                            Label(androidFamilyTitle(api), color = Palette.muted, size = 11)
                        }
                    }
                }
                if (activeImages.isEmpty()) Label("Select an Android version to see its available versions.", color = Palette.muted, size = 12)
            }
        }
    }
}

@Composable
private fun PickerScrollPane(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Box(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun PickerOption(tag: String, active: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    UnstyledButton(onClick, modifier = Modifier.fillMaxWidth().testTag(tag).semantics { selected = active }
        .background(if (active) Palette.accent.copy(alpha = .09f) else Palette.panel, RoundedCornerShape(7.dp))
        .border(1.dp, if (active) Palette.accent else Palette.border, RoundedCornerShape(7.dp))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** Resolve a release to a default image; architecture/services are editable on the next screen. */
fun preferredSystemImage(images: List<SystemImage>): SystemImage? {
    val nativeAbi = if (System.getProperty("os.arch").let { it.contains("aarch") || it.contains("arm") }) "arm64-v8a" else "x86_64"
    return images.sortedWith(compareBy<SystemImage> { it.abi != nativeAbi }
        .thenByDescending { it.installed }.thenBy { it.services }.thenBy { it.path }).firstOrNull()
}
