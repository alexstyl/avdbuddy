@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.avdbuddy

import com.avdbuddy.iconography.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import java.awt.Desktop
import java.io.File
import java.net.URI

private class DirectoryRequest(val path: String, val onSelected: (String) -> Unit)

@Composable fun DialogScaffold(title:String,tag:String,width:Int=560,height:Int=640,scrollableContent:Boolean=true,contentPadding:PaddingValues=PaddingValues(24.dp),onClose:()->Unit,footer:@Composable RowScope.()->Unit,content:@Composable ColumnScope.()->Unit) {
    AppDialog(title, tag, onClose, Modifier.width(width.dp).height(height.dp)) {
        Column(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
            Column(Modifier.padding(start=24.dp,end=24.dp,top=22.dp,bottom=18.dp)) { Label(title) }
            Rule()
            Box(Modifier.weight(1f).testTag("$tag-Content")) {
                if (scrollableContent) {
                    val scroll = rememberScrollState()
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(contentPadding),verticalArrangement=Arrangement.spacedBy(18.dp),content=content)
                    VerticalScrollbar(rememberScrollbarAdapter(scroll),Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical=4.dp))
                } else {
                    Column(Modifier.fillMaxSize().padding(contentPadding),verticalArrangement=Arrangement.spacedBy(18.dp),content=content)
                }
            }
            Rule()
            Row(Modifier.fillMaxWidth().testTag("$tag-Footer").background(JewelTheme.globalColors.panelBackground).padding(horizontal=20.dp,vertical=14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically,content=footer)
        }
    }
}
@Composable fun SettingsDialog(state:AppState) {
    val sdkField = rememberTextFieldState(state.settings.sdk)
    val sdk = sdkField.text.toString()
    val avdField = rememberTextFieldState(state.settings.avds)
    val avds = avdField.text.toString()
    var failure by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var directoryRequest by remember { mutableStateOf<DirectoryRequest?>(null) }
    fun chooseDirectory(path: String, onSelected: (String) -> Unit) {
        directoryRequest = DirectoryRequest(path, onSelected)
    }
    val tools=Toolchain(expandPath(sdk))
    DialogScaffold("Settings","SettingsDialog",height=690,onClose={state.showSettings=false},footer={
        Spacer(Modifier.weight(1f));ActionButton("Cancel",tag="CancelSettings"){state.showSettings=false}
        ActionButton("Save",primary=true,enabled=!state.busy,tag="SaveSettings"){failure=state.saveSettings(sdkField.text.toString(),avdField.text.toString())}
    }) {
        Label("Android tools and device storage, in one place.",color=JewelTheme.globalColors.text.info)
        Section("Android SDK", Icons.Folder) {
            Label("SDK location",color=JewelTheme.globalColors.text.info,modifier=Modifier.testTag("SdkLocationLabel"))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Field(sdkField,"SdkPath",Modifier.weight(1f))
                ActionButton("Browse…",tag="BrowseSdk"){chooseDirectory(sdk){sdkField.setTextAndPlaceCursorAtEnd(it)}}
            }
            OutlinedButton({expanded=!expanded},modifier=Modifier.fillMaxWidth().testTag("ToggleToolValidation")) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Icon(if(tools.ready) Icons.Check else Icons.XMark)
                    Spacer(Modifier.width(8.dp));Label(if(tools.ready) "All four Android tools found" else "Android tools are missing",color=if(tools.ready)JewelTheme.globalColors.text.info else JewelTheme.globalColors.text.error)
                    Spacer(Modifier.weight(1f));Label(if(expanded) "⌃" else "⌄",color=JewelTheme.globalColors.text.info)
                }
            }
            if(expanded) tools.tools.forEach { (name,file) -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Icon(if(file.canExecute()) Icons.Check else Icons.XMark,if(file.canExecute())JewelTheme.contentColor else JewelTheme.globalColors.text.error,Modifier.size(13.dp))
                Spacer(Modifier.width(8.dp));Column { Label(name);Label(file.path,color=JewelTheme.globalColors.text.info,maxLines=2) }
            } }
        }
        Section("Emulator storage", Icons.CircleStack) {
            Label("Device location",color=JewelTheme.globalColors.text.info,modifier=Modifier.testTag("AvdLocationLabel"))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Field(avdField,"AvdPath",Modifier.weight(1f))
                ActionButton("Browse…",tag="BrowseAvds"){chooseDirectory(avds){avdField.setTextAndPlaceCursorAtEnd(it)}}
            }
            Label("New devices are stored here. Switching folders leaves existing devices in place and applies to AvdBuddy only.",color=JewelTheme.globalColors.text.info)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                ActionButton("Use default",tag="DefaultAvdPath"){avdField.setTextAndPlaceCursorAtEnd(defaultAvdHome())}
                ActionButton("New folder…",tag="NewAvdFolder"){
                    chooseDirectory(avds) { parent ->
                        val folder=File(parent,"AvdBuddy Devices")
                        if(folder.isDirectory || folder.mkdir()) avdField.setTextAndPlaceCursorAtEnd(folder.path) else failure="Could not create the folder."
                    }
                }
            }
        }
        val images=File(expandPath(sdk),"system-images")
        Section("Android system images", Icons.ArrowDownTray) {
            SelectionContainer { Label(images.canonicalPath,color=JewelTheme.globalColors.text.info) }
            Label(if(images.isDirectory)"Managed by the Android SDK, separately from device storage." else if(java.nio.file.Files.isSymbolicLink(images.toPath())) "This location points to an unavailable drive. Connect it before downloading." else "This folder will be created when you download your first image.",color=if(java.nio.file.Files.isSymbolicLink(images.toPath())&&!images.exists())JewelTheme.globalColors.text.error else JewelTheme.globalColors.text.info)
        }
        failure?.let{Label(it,color=JewelTheme.globalColors.text.error,modifier=Modifier.testTag("SettingsError"))}
    }
    directoryRequest?.let { request ->
        DirectoryPickerDialog(request.path, onClose = { directoryRequest = null }) { path ->
            request.onSelected(path)
            directoryRequest = null
        }
    }
}

@Composable fun CreateDeviceDialog(state: AppState) {
    val profiles = remember { HardwareProfiles.all }
    var profile by remember { mutableStateOf(profiles.first()) }
    val images = remember(state.images) { googlePlayImages(state.images) }
    var selectedPath by remember { mutableStateOf<String?>(null) }
    val image = images.firstOrNull { it.path == selectedPath } ?: images.firstOrNull()
    val nameField = rememberTextFieldState()
    val suggestedName = suggestedDeviceName(profile.name, state.visibleDevices.map(Device::displayName))
    val name = nameField.text.toString().trim().ifBlank { suggestedName }
    val nameIssue = creationNameError(name, state.visibleDevices.map(Device::displayName))
    val spec = image?.let { profile.createSpec(name, it, state.visibleDevices.map(Device::name)) }
    var plan by remember { mutableStateOf<StoragePlan?>(null) }
    var planError by remember { mutableStateOf("") }
    var retry by remember { mutableStateOf(0) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(profile.id, image?.path, image?.installed, state.settings, retry) {
        plan = null; planError = ""
        if (spec != null && !state.catalogLoading) {
            try { plan = state.reviewStorage(spec) }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                planError = e.message ?: "Could not check available storage."
            }
        }
    }
    fun close() { state.showCreate = false }
    AppDialog("Create device", "CreateDeviceDialog", ::close, Modifier.width(510.dp).heightIn(max = 650.dp)) {
        LaunchedEffect(Unit) { focus.requestFocus() }
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 14.dp, top = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Label("Create device")
                Spacer(Modifier.weight(1f))
                IconButton(Icons.XMark, "Close create device", "CloseCreate") { close() }
            }
            Rule()
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Label("Name", color = JewelTheme.globalColors.text.info)
                    org.jetbrains.jewel.ui.component.TextField(
                        state = nameField,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("DeviceName"),
                        placeholder = { Text(suggestedName) },
                    )
                    if (nameIssue != null) Label(nameIssue, color = JewelTheme.globalColors.text.error)
                }
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Label("Device", color = JewelTheme.globalColors.text.info)
                    Select(profile, profiles, { profile = it }, { it.name }, "DeviceProfile", Modifier.fillMaxWidth())
                }
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Label("Android version", color = JewelTheme.globalColors.text.info)
                    when {
                        state.catalogLoading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            org.jetbrains.jewel.ui.component.CircularProgressIndicator(Modifier.size(16.dp))
                            Label("Finding available Android versions…", color = JewelTheme.globalColors.text.info)
                        }
                        state.catalogError.isNotBlank() -> {
                            Label(state.catalogError, color = JewelTheme.globalColors.text.error)
                            ActionButton("Try again", tag = "RetryCatalog") { state.loadCatalog() }
                        }
                        image != null -> Select(image, images, { selectedPath = it.path },
                            { "${androidFamilyTitle(it.api)} · API ${creationApiLabel(it)}" }, "AndroidVersion", Modifier.fillMaxWidth())
                        else -> {
                            Label("No compatible Google Play images are available for this computer.", color = JewelTheme.globalColors.text.info)
                            ActionButton("Refresh versions", tag = "RetryCatalog") { state.loadCatalog(force = true) }
                        }
                    }
                }
                if (image != null && !state.catalogLoading && state.catalogError.isBlank()) {
                    when {
                        planError.isNotBlank() -> {
                            Label(planError, color = JewelTheme.globalColors.text.error)
                            ActionButton("Retry storage check", tag = "RetryStorageCheck") { retry++ }
                        }
                        plan?.canCreate == false -> {
                            Label("Not enough storage. Free space or change the location in Settings.", color = JewelTheme.globalColors.text.error)
                            ActionButton("Check again", tag = "RetryStorageCheck") { retry++ }
                        }
                    }
                }
            }
            Rule()
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                ActionButton("Cancel", tag = "CancelCreate") { close() }
                ActionButton("Create", primary = true,
                    enabled = !state.busy && !state.catalogLoading && state.catalogError.isBlank() && spec != null && nameIssue == null && plan?.canCreate == true && planError.isBlank(), tag = "ConfirmCreate") {
                    spec?.let(state::create)
                }
            }
        }
    }
}

@Composable fun RenameDeviceDialog(state:AppState,device:Device) {
    val nameField = rememberTextFieldState(device.name)
    val name = nameField.text.toString().trim()
    val issue = deviceNameError(name, state.devices.filter { it.name != device.name }.map { it.name })
    var failure by remember { mutableStateOf<String?>(null) }
    val running = device.name in state.running || device.name in state.starting
    DialogScaffold("Rename device","RenameDeviceDialog",height=350,onClose={if(!state.busy)state.renameDevice=null},footer={
        Spacer(Modifier.weight(1f));ActionButton("Cancel",enabled=!state.busy){state.renameDevice=null}
        ActionButton("Rename",primary=true,enabled=!state.busy&&issue==null&&name!=device.name&&!running,tag="ConfirmRename") {
            failure=null
            state.rename(device,nameField.text.toString()) { error -> if(error==null)state.renameDevice=null else failure=error }
        }
    }) {
        Label("Give ${device.displayName} a new name.",color=JewelTheme.globalColors.text.info)
        Field(nameField,"RenameName",Modifier.fillMaxWidth())
        val hint = when { state.busy -> "Renaming device… This dialog will close when finished."; running -> "Stop this device before renaming it."; issue!=null -> issue; name==device.name -> "Enter a different name to rename this device."; else -> "Use letters, numbers, dots, underscores or hyphens." }
        Label(hint,color=if(running||issue!=null) JewelTheme.globalColors.text.error else JewelTheme.globalColors.text.info)
        failure?.let { Label(it,color=JewelTheme.globalColors.text.error) }
    }
}
@Composable fun AboutDialog(state:AppState) {
    DialogScaffold("AvdBuddy","AboutDialog",height=560,onClose={state.showAbout=false},footer={Spacer(Modifier.weight(1f));ActionButton("Done",primary=true){state.showAbout=false}}) {
        Label("Android devices. Without the IDE.")
        Label("Version $APP_VERSION",color=JewelTheme.globalColors.text.info)
        ActionButton(if (state.license.licensed) "Licensed · Manage license" else "Unlicensed · Activate license") { state.license.visible = true }
        Label("Built with Kotlin, Compose Desktop, and Jewel.",color=JewelTheme.globalColors.text.info)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {ActionButton("GitHub"){Desktop.getDesktop().browse(URI("https://github.com/alexstyl/avdbuddy"))};ActionButton("Check for updates",enabled=!state.busy){state.checkUpdates()}}
        Section("Using your devices", Icons.DevicePhoneMobile) {
            Label("Select a device in the sidebar to preview it. Selection never starts the emulator.")
            Label("Press Play to start it. A gray screen is the last captured preview; the running screen appears in color.")
            Label("Use Settings to reconnect an unavailable SDK or device folder. Changing folders leaves existing devices in place.")
            Label("Shortcuts: ${HostPlatform().shortcutModifier}N creates a device. ${HostPlatform().shortcutModifier}, opens Settings. ${HostPlatform().shortcutModifier}Backspace asks to delete the selected device.",color=JewelTheme.globalColors.text.info)
            Label("While running: ${HostPlatform().shortcutModifier}→ rotates clockwise. ${HostPlatform().shortcutModifier}← rotates counterclockwise.",color=JewelTheme.globalColors.text.info)
        }
        ActionButton("Copy diagnostics",tag="CopyDiagnostics"){state.copyDiagnostics()}
        Label("By Alex Styl · Inspired by VirtualBuddy",color=JewelTheme.globalColors.text.info)
    }
}

@Composable fun LicenseDialog(license: AppLicense) {
    val key = rememberTextFieldState()
    DialogScaffold("License", "LicenseDialog", height=400, onClose={license.visible=false}, footer={
        ActionButton("Manage license") { Desktop.getDesktop().browse(URI(LICENSE_ORIGIN)) }
        Spacer(Modifier.weight(1f))
        ActionButton("Close") { license.visible=false }
        ActionButton(if (license.licensed) "Reactivate" else "Activate", primary=true, enabled=!license.working) { license.activate(key.text.toString()) }
    }) {
        Label(if (license.licensed) "Licensed" else "Unlicensed")
        license.updatesThrough?.let { Label("Updates included through $it. Your licensed version remains yours to use.") }
        Label("All current features are available without a license.")
        Label("License key")
        Field(key, "LicenseKey", Modifier.fillMaxWidth())
        if (license.message.isNotBlank()) Label(license.message)
    }
}

@Composable fun UpdatesDialog(state: AppState) {
    val updates = state.updates
    DialogScaffold("Software updates", "UpdatesDialog", height=360, onClose={updates.visible=false}, footer={
        Spacer(Modifier.weight(1f))
        ActionButton("Close") { updates.visible=false }
        when {
            updates.downloaded != null -> ActionButton("Restart and install", primary=true, enabled=!updates.working && !state.busy) { updates.restart(state.busy) }
            updates.available != null -> ActionButton("Download update", primary=true, enabled=!updates.working) { updates.download() }
            else -> ActionButton("Check again", enabled=!updates.working && updates.unavailableReason==null) { updates.check() }
        }
    }) {
        Label(updates.message, modifier=Modifier.testTag("UpdateStatus"))
        if (updates.downloaded != null && state.busy) Label("Finish the current device operation before restarting.", color=JewelTheme.globalColors.text.info)
        if (updates.unavailableReason == null) CheckboxRow(checked=updates.automatic, onCheckedChange=updates::changeAutomatic) { Label("Automatically check for updates") }
    }
}
