package com.avdbuddy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.OutlinedButton

@Composable fun DeleteWindow(state: AppState) {
    val devices = state.deleteDevices
    val title = "Delete ${if (devices.size == 1) "device" else "${devices.size} devices"}?"
    val cancelFocus = remember { FocusRequester() }
    fun close() { state.deleteDevices = emptyList() }

    DialogWindow(
        onCloseRequest = ::close,
        title = title,
        state = rememberDialogState(width = 460.dp, height = 360.dp),
        resizable = false,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                close(); true
            } else false
        },
    ) {
        AvdBuddyTheme {
            LaunchedEffect(Unit) { cancelFocus.requestFocus() }
            Column(
                Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)
                    .padding(24.dp).testTag("DeleteDevicesDialog"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Label(title)
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
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
}
