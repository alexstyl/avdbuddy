package com.avdbuddy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import org.jetbrains.jewel.ui.theme.popupContainerStyle
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.OutlinedButton

@Composable fun DeleteWindow(state: AppState) {
    val devices = state.deleteDevices
    val title = "Delete ${if (devices.size == 1) "device" else "${devices.size} devices"}?"
    val cancelFocus = remember { FocusRequester() }
    fun close() { state.deleteDevices = emptyList() }

    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().background(JewelTheme.popupContainerStyle.colors.shadow)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .testTag("DeleteDialogBackdrop"))
        val shape = RoundedCornerShape(JewelTheme.popupContainerStyle.metrics.cornerSize)
        Column(
            Modifier.width(460.dp).heightIn(max = 420.dp).padding(8.dp)
                .background(JewelTheme.popupContainerStyle.colors.background, shape)
                .border(JewelTheme.popupContainerStyle.metrics.borderWidth, JewelTheme.popupContainerStyle.colors.border, shape)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        close(); true
                    } else false
                }
                .semantics { dialog(); paneTitle = title }
                .padding(24.dp).testTag("DeleteDevicesDialog"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Label(title)
            Column(
                Modifier.fillMaxWidth().heightIn(max = 120.dp).verticalScroll(rememberScrollState())
                    .testTag("DeleteDeviceNames"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                devices.forEach { Label(it.displayName) }
            }
            Label(
                "The devices and their saved data will be removed. Running devices will be stopped first. This cannot be undone.",
                color = JewelTheme.globalColors.text.info,
            )
            if (state.busy) Label("Wait for the current device operation to finish.", color = JewelTheme.globalColors.text.info)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.weight(1f))
                OutlinedButton(::close, Modifier.focusRequester(cancelFocus).testTag("CancelDelete")) {
                    Label("Cancel")
                }
                ActionButton("Delete", primary = true, enabled = !state.busy, tag = "ConfirmDelete") {
                    state.delete(devices)
                    close()
                }
            }
        }
    }
}
