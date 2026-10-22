@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.avdbuddy

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.Orientation
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

fun main(args: Array<String>) {
    if ("--diagnostics" in args) {
        println(AndroidService(SettingsStore().load()).diagnostics())
        return
    }
    System.setProperty("apple.awt.application.name", "AvdBuddy")
    System.setProperty("apple.awt.application.appearance", "NSAppearanceNameAqua")
    application {
        val scope = rememberCoroutineScope()
        val state = remember { AppState(scope) }
        Window(
            onCloseRequest = ::exitApplication,
            title = "AvdBuddy",
            state = rememberWindowState(width = 1040.dp, height = 720.dp),
            onPreviewKeyEvent = { event -> handleMainWindowShortcut(event, state) },
        ) {
            window.minimumSize = java.awt.Dimension(760, 540)
            AvdBuddyTheme { App(state) }
        }
    }
}

// Handle shortcuts before focused content, including the emulator and text fields.
private fun handleMainWindowShortcut(event: KeyEvent, state: AppState): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.isMetaPressed) when (event.key) {
        Key.DirectionRight -> return state.rotateCurrent(clockwise = true)
        Key.DirectionLeft -> return state.rotateCurrent(clockwise = false)
    }
    if (!event.isMetaPressed && !event.isCtrlPressed) return false
    return when (event.key) {
        Key.N -> { if (!state.busy) state.resetCreate(); true }
        Key.Comma -> { state.showSettings = true; true }
        Key.Backspace -> { state.previewDevice?.let { state.deleteDevices = listOf(it) }; true }
        else -> false
    }
}

@Composable fun AvdBuddyTheme(content: @Composable () -> Unit) { IntUiTheme(isDark = false) { content() } }
@Composable fun Label(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, maxLines: Int = Int.MAX_VALUE) {
    Text(text, modifier, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}
@Composable fun Rule() { Divider(Orientation.Horizontal, Modifier.fillMaxWidth()) }
@Composable fun Icon(name: String, tint: Color = JewelTheme.contentColor, modifier: Modifier = Modifier.size(18.dp)) {
    val iconBackground = JewelTheme.globalColors.panelBackground
    Canvas(modifier) {
        val s = size.width / 24f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x1*s,y1*s), Offset(x2*s,y2*s), 1.6f*s, StrokeCap.Round)
        fun rect(x: Float, y: Float, w: Float, h: Float, r: Float = 2f) = drawRoundRect(tint, Offset(x*s,y*s), Size(w*s,h*s), CornerRadius(r*s), style = Stroke(1.6f*s))
        when(name) {
            "plus" -> { line(12f,5f,12f,19f); line(5f,12f,19f,12f) }
            "play" -> drawPath(Path().apply { moveTo(8*s,4*s); lineTo(20*s,12*s); lineTo(8*s,20*s); close() }, tint)
            "stop" -> drawRoundRect(tint, Offset(6*s,6*s), Size(12*s,12*s), CornerRadius(2*s))
            "settings" -> { for(y in listOf(6f,12f,18f)) line(3f,y,21f,y); for((x,y) in listOf(8f to 6f,16f to 12f,10f to 18f)) { drawCircle(iconBackground,2.5f*s,Offset(x*s,y*s)); drawCircle(tint,2.5f*s,Offset(x*s,y*s),style=Stroke(1.5f*s)) } }
            "refresh" -> { drawArc(tint, -50f, 285f, false, Offset(4*s,4*s), Size(16*s,16*s),style=Stroke(1.6f*s)); line(20f,3f,20f,9f); line(15f,9f,20f,9f) }
            "more" -> for(x in listOf(5f,12f,19f)) drawCircle(tint,1.5f*s,Offset(x*s,12*s))
            "close" -> { line(6f,6f,18f,18f); line(6f,18f,18f,6f) }
            "back" -> { line(15f,5f,8f,12f); line(8f,12f,15f,19f) }
            "check" -> { line(5f,12f,10f,17f); line(10f,17f,20f,6f) }
            "folder" -> { rect(3f,7f,18f,14f); line(3f,7f,3f,4f); line(3f,4f,10f,4f); line(10f,4f,13f,7f) }
            "download" -> { line(12f,3f,12f,15f); line(7f,10f,12f,15f); line(12f,15f,17f,10f); line(4f,17f,4f,21f); line(4f,21f,20f,21f); line(20f,21f,20f,17f) }
            "info" -> { drawCircle(tint,9*s,center,style=Stroke(1.6f*s)); line(12f,10f,12f,17f); drawCircle(tint,1*s,Offset(12*s,7*s)) }
            "tablet", "tv", "desktop", "automotive" -> { rect(2f,5f,20f,14f); line(9f,22f,15f,22f); line(12f,19f,12f,22f) }
            "wear os" -> { rect(6f,6f,12f,12f,5f); line(9f,2f,15f,2f); line(9f,22f,15f,22f) }
            "disk" -> { rect(3f,6f,18f,13f); line(3f,14f,21f,14f); drawCircle(tint,s,Offset(17*s,17*s)) }
            else -> { rect(6f,2f,12f,20f,3f); line(10f,5f,14f,5f); line(10f,19f,14f,19f) }
        }
    }
}
@Composable fun IconButton(name: String, description: String, tag: String = description, enabled: Boolean = true, onClick: () -> Unit) {
    Tooltip(tooltip = { Text(description) }) {
        org.jetbrains.jewel.ui.component.IconButton(onClick, enabled = enabled,
            modifier = Modifier.testTag(tag).semantics { contentDescription = description }) { Icon(name) }
    }
}
@Composable fun ActionButton(text: String, primary: Boolean = false, enabled: Boolean = true, tag: String = text, onClick: () -> Unit) {
    if (primary) DefaultButton(onClick, Modifier.testTag(tag), enabled = enabled) { Text(text) }
    else OutlinedButton(onClick, Modifier.testTag(tag), enabled = enabled) { Text(text) }
}

@Composable fun App(state: AppState, autoRefresh: Boolean = true) {
    LaunchedEffect(state) { state.observeDisplays() }
    LaunchedEffect(state, autoRefresh) {
        if (autoRefresh) { state.refresh(); while (isActive) { delay(3000); state.refresh() } }
    }
    Row(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground).testTag("DeviceShelf")) {
        Column(Modifier.width(270.dp).fillMaxHeight().background(JewelTheme.globalColors.panelBackground).testTag("DeviceSidebar")) {
            Row(Modifier.fillMaxWidth().padding(start=16.dp,end=8.dp,top=8.dp,bottom=8.dp), verticalAlignment=Alignment.CenterVertically) {
                Label("Devices", color=JewelTheme.globalColors.text.info)
                Spacer(Modifier.weight(1f))
                IconButton("plus", "Create a virtual device", "CreateDevice", !state.busy) { if(state.ready) state.resetCreate() else state.showSettings=true }
            }
            androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f), contentPadding=PaddingValues(horizontal=8.dp, vertical=4.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                items(state.devices.size, key={state.devices[it].name}) { index ->
                    Box(Modifier.animateItem()) {
                        DeviceRow(state.devices[index], state)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment=Alignment.CenterVertically) {
                IconButton("settings", "Settings", "Settings", !state.busy) { state.showSettings=true }
                IconButton("refresh", "Refresh devices", "Refresh", !state.busy) { state.refreshAsync() }
                Spacer(Modifier.weight(1f))
                IconButton("info", "About AvdBuddy", "About") { state.showAbout=true }
            }
        }
        Divider(Orientation.Vertical, Modifier.fillMaxHeight())
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val device = state.previewDevice
            if (device != null) EmulatorPanel(device, state)
            else Column(Modifier.fillMaxSize().testTag("EmptyShelf"), verticalArrangement=Arrangement.Center, horizontalAlignment=Alignment.CenterHorizontally) {
                Icon("phone", modifier=Modifier.size(48.dp))
                Spacer(Modifier.height(20.dp)); Label("Your next Android device")
                Spacer(Modifier.height(10.dp)); Label("Create a device to see it here.", color=JewelTheme.globalColors.text.info)
                Spacer(Modifier.height(24.dp)); ActionButton("Create a device", primary=true, tag="EmptyCreate") { if(state.ready) state.resetCreate() else state.showSettings=true }
            }
        }
    }
    if(state.showSettings) SettingsWindow(state)
    if(state.showCreate) CreateWindow(state)
    LaunchedEffect(state, autoRefresh) { if (autoRefresh) state.updates.monitor() }
    if(state.updates.visible) UpdatesWindow(state)
    if(state.showAbout) AboutWindow(state)
    state.renameDevice?.let { device -> RenameWindow(state,device) }
    if(state.deleteDevices.isNotEmpty()) DeleteWindow(state)
}

@Composable private fun DeviceRow(device: Device, state: AppState) {
    val selected = state.previewDevice?.name == device.name
    val running = device.name in state.running
    val operation = state.deviceOperations[device.name]
        ?: if (device.name in state.starting) "Starting…" else null
    val subtitle = operation ?: if (device.missingStorage) "Storage unavailable" else androidFamilyTitle(device.api)
    ContextMenuArea(items = { buildList {
        if (!state.busy) {
            if (running) {
                add(ContextMenuItem("Shutdown") { state.stop(device) })
                add(ContextMenuItem("Restart") { state.restart(device) })
            } else if (device.name !in state.starting && !device.missingStorage) {
                add(ContextMenuItem("Start") { state.launch(device) })
            }
        }
        if (!state.busy && device.name !in state.running) {
            add(ContextMenuItem("Duplicate") { state.duplicate(device) })
            add(ContextMenuItem("Rename…") { state.renameDevice=device })
        }
        add(ContextMenuItem("Show in Finder") { state.reveal(device) })
        if (!state.busy) {
            add(ContextMenuItem("Delete…") { state.deleteDevices=listOf(device) })
        }
    } }) {
        SimpleListItem(selected = selected, height = 56.dp,
            modifier = Modifier.fillMaxWidth().testTag("Device-${device.name}")
                .semantics { contentDescription="${device.displayName}, $subtitle" }
                .clickable { state.selectPreview(device) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                Icon(device.type.label.lowercase())
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Label(device.displayName, maxLines=1)
                    Spacer(Modifier.height(4.dp))
                    Label(subtitle, color=JewelTheme.globalColors.text.info, maxLines=1)
                }
                if(running && operation == null) Icon("play")
                if(operation != null) {
                    Spacer(Modifier.width(10.dp))
                    org.jetbrains.jewel.ui.component.CircularProgressIndicator(Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable fun Section(title: String, icon: String = "settings", subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,modifier=Modifier.size(15.dp));Spacer(Modifier.width(9.dp));Label(title)
            if(subtitle!=null){Spacer(Modifier.weight(1f));Label(subtitle,color=JewelTheme.globalColors.text.info)}
        }
        Rule();Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
@Composable fun FormRow(title:String,content:@Composable ()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=30.dp),verticalAlignment=Alignment.CenterVertically) { Label(title,color=JewelTheme.globalColors.text.info);Spacer(Modifier.weight(1f));content() }
}
@Composable fun Field(state: androidx.compose.foundation.text.input.TextFieldState, tag:String, modifier:Modifier=Modifier) {
    TextField(state=state,modifier=modifier.testTag(tag))
}
@Composable fun <T> Select(value:T,options:List<T>,onChange:(T)->Unit,label:(T)->String={it.toString()},tag:String="Select",modifier:Modifier=Modifier.widthIn(min=130.dp,max=300.dp)) {
    Dropdown(modifier=modifier.testTag(tag),menuContent={options.forEach { option -> selectableItem(selected=option==value,onClick={onChange(option)}) { Text(label(option)) } }}) { Text(label(value),maxLines=1) }
}
fun chooseDirectory(current:String):String? {
    if(System.getProperty("os.name").contains("Mac")) {
        val old=System.getProperty("apple.awt.fileDialogForDirectories")
        return try {
            System.setProperty("apple.awt.fileDialogForDirectories","true")
            val dialog=FileDialog(null as Frame?,"Choose folder",FileDialog.LOAD)
            dialog.directory=expandPath(current).path;dialog.isVisible=true
            val result=dialog.file?.let { File(dialog.directory,it).absolutePath };dialog.dispose();result
        } finally { if(old==null)System.clearProperty("apple.awt.fileDialogForDirectories") else System.setProperty("apple.awt.fileDialogForDirectories",old) }
    }
    val chooser=JFileChooser(expandPath(current));chooser.fileSelectionMode=JFileChooser.DIRECTORIES_ONLY
    return if(chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) chooser.selectedFile.path else null
}
