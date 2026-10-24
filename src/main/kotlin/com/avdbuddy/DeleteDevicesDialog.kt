package com.avdbuddy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.OutlinedButton

@Composable fun DeleteDevicesDialog(state: AppState) {
    val devices = state.deleteDevices
    val title = "Delete ${if (devices.size == 1) "device" else "${devices.size} devices"}?"
    val cancelFocus = remember { FocusRequester() }
    fun close() { state.deleteDevices = emptyList() }

    AppDialog(title, "DeleteDevicesDialog", ::close, Modifier.width(460.dp).heightIn(max = 420.dp)) {
        LaunchedEffect(Unit) { cancelFocus.requestFocus() }
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
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
