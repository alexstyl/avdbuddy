@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.avdbuddy

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledDropdownMenu
import com.composeunstyled.DropdownMenuPanel
import com.composeunstyled.MenuItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.*
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

object Palette {
    val background = Color(0xFF302B2C)
    val toolbar = Color(0xFF403A3B)
    val panel = Color(0xFF3B3435)
    val border = Color(0xFF524849)
    val text = Color(0xFFE9E3DE)
    val muted = Color(0xFFADA2A1)
    val accent = Color(0xFFE4A447)
    val error = Color(0xFFFFA29B)
    val green = Color(0xFF8FC78A)
}

fun main(args: Array<String>) {
    if ("--diagnostics" in args) {
        println(AndroidService(SettingsStore().load()).diagnostics())
        return
    }
    System.setProperty("apple.awt.application.name", "AvdBuddy")
    System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua")
    application {
        val scope = rememberCoroutineScope()
        val state = remember { AppState(scope) }
        Window(onCloseRequest = ::exitApplication, title = "AvdBuddy", state = rememberWindowState(width = 1040.dp, height = 720.dp)) {
            window.minimumSize = java.awt.Dimension(760, 540)
            AvdBuddyTheme { App(state) }
        }
    }
}

@Composable fun AvdBuddyTheme(content: @Composable () -> Unit) { IntUiTheme(isDark = true) { content() } }
@Composable fun Label(text: String, modifier: Modifier = Modifier, color: Color = Palette.text, size: Int = 13, weight: FontWeight = FontWeight.Normal, maxLines: Int = Int.MAX_VALUE) {
    Text(text, modifier, color = color, fontSize = size.sp, fontWeight = weight, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}
@Composable fun Rule() { Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.border.copy(alpha = .65f))) }
@Composable fun Icon(name: String, tint: Color = Palette.muted, modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier) {
        val s = size.width / 24f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x1*s,y1*s), Offset(x2*s,y2*s), 1.6f*s, StrokeCap.Round)
        fun rect(x: Float, y: Float, w: Float, h: Float, r: Float = 2f) = drawRoundRect(tint, Offset(x*s,y*s), Size(w*s,h*s), CornerRadius(r*s), style = Stroke(1.6f*s))
        when(name) {
            "plus" -> { line(12f,5f,12f,19f); line(5f,12f,19f,12f) }
            "play" -> drawPath(Path().apply { moveTo(8*s,4*s); lineTo(20*s,12*s); lineTo(8*s,20*s); close() }, tint)
            "stop" -> drawRoundRect(tint, Offset(6*s,6*s), Size(12*s,12*s), CornerRadius(2*s))
            "settings" -> { for(y in listOf(6f,12f,18f)) line(3f,y,21f,y); for((x,y) in listOf(8f to 6f,16f to 12f,10f to 18f)) { drawCircle(Palette.toolbar,2.5f*s,Offset(x*s,y*s)); drawCircle(tint,2.5f*s,Offset(x*s,y*s),style=Stroke(1.5f*s)) } }
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
    androidx.compose.foundation.TooltipArea(tooltip = { Box(Modifier.background(Palette.toolbar, RoundedCornerShape(5.dp)).border(1.dp,Palette.border,RoundedCornerShape(5.dp)).padding(8.dp)) { Label(description,size=12) } }) {
        UnstyledButton(onClick, enabled = enabled, modifier = Modifier.testTag(tag).semantics { contentDescription = description }.size(32.dp).clip(RoundedCornerShape(5.dp))) { Icon(name, if(enabled) Palette.muted else Palette.muted.copy(alpha=.35f)) }
    }
}
@Composable fun ActionButton(text: String, primary: Boolean = false, enabled: Boolean = true, tag: String = text, onClick: () -> Unit) {
    if (primary) DefaultButton(onClick, Modifier.testTag(tag), enabled = enabled) { Text(text) }
    else OutlinedButton(onClick, Modifier.testTag(tag), enabled = enabled) { Text(text) }
}

@Composable fun App(state: AppState, autoRefresh: Boolean = true) {
    LaunchedEffect(state, autoRefresh) {
        if (autoRefresh) { state.refresh(); while (isActive) { delay(3000); state.refresh() } }
    }
    Column(Modifier.fillMaxSize().background(Palette.background).testTag("DeviceShelf").onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && (it.isMetaPressed || it.isCtrlPressed)) when(it.key) {
            Key.A -> { state.selected = state.devices.map(Device::name).toSet(); true }
            Key.N -> { if (!state.busy) { state.resetCreate() }; true }
            Key.Comma -> { state.showSettings = true; true }
            Key.Backspace -> { if (state.selectedDevices.isNotEmpty()) state.deleteDevices = state.selectedDevices; true }
            else -> false
        } else false
    }) {
        Row(Modifier.fillMaxWidth().height(48.dp).background(Palette.toolbar).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon("phone", Palette.accent, Modifier.size(19.dp)); Spacer(Modifier.width(9.dp))
            Label("AvdBuddy", weight = FontWeight.SemiBold, size = 14)
            Spacer(Modifier.weight(1f))
            IconButton("info", "About AvdBuddy", "About") { state.showAbout = true }
            IconButton("refresh", "Refresh devices", "Refresh", !state.busy) { state.refreshAsync() }
            IconButton("settings", "Settings", "Settings", !state.busy) { state.showSettings = true }
            Spacer(Modifier.width(7.dp)); Box(Modifier.width(1.dp).height(18.dp).background(Palette.border)); Spacer(Modifier.width(7.dp))
            IconButton("plus", "Create a virtual device", "CreateDevice", !state.busy) { if (state.ready) state.resetCreate() else state.showSettings = true }
        }
        Rule()
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically) {
            Label("Your devices",size=13,weight=FontWeight.Medium)
            Spacer(Modifier.width(10.dp)); Label("${state.devices.size}",size=12,color=Palette.muted)
            Spacer(Modifier.weight(1f))
            if(state.running.isNotEmpty()) ActionButton("Stop all", enabled = !state.busy) { state.stopAll() }
            if(state.selectedDevices.size > 1) { Spacer(Modifier.width(10.dp)); ActionButton("Delete selected",enabled=!state.busy) { state.deleteDevices=state.selectedDevices } }
        }
        if (state.devices.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().testTag("EmptyShelf"), verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                Box(Modifier.size(72.dp).background(Palette.panel,RoundedCornerShape(18.dp)),contentAlignment=Alignment.Center) { Icon("phone",Palette.muted,Modifier.size(36.dp)) }
                Spacer(Modifier.height(20.dp)); Label("A home for your Android devices",size=21,weight=FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp)); Label("Create a virtual device. Keep Android Studio closed.",color=Palette.muted)
                Spacer(Modifier.height(24.dp)); ActionButton("Create a device",primary=true,tag="EmptyCreate") { if(state.ready) state.resetCreate() else state.showSettings=true }
                Spacer(Modifier.height(40.dp))
            }
        } else {
            LazyVerticalGrid(GridCells.Adaptive(266.dp), Modifier.weight(1f).padding(horizontal=22.dp), contentPadding=PaddingValues(bottom=24.dp),horizontalArrangement=Arrangement.spacedBy(18.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                items(state.devices,key={it.name}) { DeviceCard(it,state) }
            }
        }
        if(state.status.isNotBlank()) {
            Rule()
            Row(Modifier.fillMaxWidth().background(Palette.panel).padding(start=18.dp,end=8.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Label(state.status,Modifier.weight(1f),if(state.error) Palette.error else Palette.text,maxLines=3,size=12)
                IconButton("close","Dismiss message") { state.status="" }
            }
        }
        Rule()
        Row(Modifier.fillMaxWidth().height(34.dp).padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
            Label(if(state.busy) "Working…" else "${state.devices.size} device${if(state.devices.size==1) "" else "s"} · ${formatSize(state.totalBytes)}",color=Palette.muted,size=11)
            Spacer(Modifier.weight(1f)); Label("${state.running.size} running",size=11,color=Palette.muted)
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

@Composable private fun DeviceCard(device: Device,state: AppState) {
    val selected = device.name in state.selected
    val running = device.name in state.running
    var toggleSelection by remember { mutableStateOf(false) }
    var extendSelection by remember { mutableStateOf(false) }
    ContextMenuArea(items = { listOf(
        ContextMenuItem(if(running) "Stop" else "Launch") { if(running) state.stop(device) else state.launch(device) },
        ContextMenuItem("Duplicate") { state.duplicate(device) },
        ContextMenuItem("Rename…") { state.renameDevice=device },
        ContextMenuItem("Show in Finder") { state.reveal(device) },
        ContextMenuItem("Delete…") { state.deleteDevices=listOf(device) }
    ) }) {
        Column(Modifier.fillMaxWidth().testTag("Device-${device.name}").clip(RoundedCornerShape(11.dp)).background(Palette.panel)
            .border(if(selected) 2.dp else 1.dp,if(selected) Palette.accent else Palette.border,RoundedCornerShape(11.dp))
            .semantics { this.selected=selected; contentDescription="${device.displayName}, ${androidFamilyTitle(device.api)}, ${device.type.label}" }
            .onPointerEvent(PointerEventType.Press) {
                toggleSelection=it.keyboardModifiers.isMetaPressed || it.keyboardModifiers.isCtrlPressed
                extendSelection=it.keyboardModifiers.isShiftPressed
            }
            .combinedClickable(enabled=!state.busy,onClick={
                state.selected = when {
                    toggleSelection -> if(device.name in state.selected) state.selected-device.name else state.selected+device.name
                    extendSelection && state.selected.isNotEmpty() -> {
                        val names=state.devices.map(Device::name)
                        val anchor=names.indexOf(state.selected.first()).coerceAtLeast(0)
                        val end=names.indexOf(device.name)
                        names.subList(minOf(anchor,end),maxOf(anchor,end)+1).toSet()
                    }
                    else -> setOf(device.name)
                }
            },onDoubleClick={if(running) state.stop(device) else state.launch(device)}).padding(7.dp)) {
            Box(Modifier.fillMaxWidth().height(155.dp).clip(RoundedCornerShape(6.dp))) {
                DevicePreview(device)
                if(running) Row(Modifier.align(Alignment.TopEnd).padding(9.dp).background(Color(0xCC242424),RoundedCornerShape(20.dp)).padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).background(Palette.green,RoundedCornerShape(4.dp))); Spacer(Modifier.width(5.dp));Label("Running",size=10)
                }
            }
            Spacer(Modifier.height(11.dp))
            Row(Modifier.padding(horizontal=7.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label(device.displayName,size=13,weight=FontWeight.Medium,maxLines=1)
                    Spacer(Modifier.height(4.dp)); Label(if(device.missingStorage) "Storage unavailable" else "${androidFamilyTitle(device.api)} · ${device.type.label}",color=if(device.missingStorage) Palette.error else Palette.muted,size=11,maxLines=1)
                }
                IconButton(if(running) "stop" else "play",if(running) "Stop ${device.displayName}" else "Launch ${device.displayName}","Launch-${device.name}",!state.busy && !device.missingStorage) { if(running) state.stop(device) else state.launch(device) }
                DeviceActions(device,state)
            }
            Spacer(Modifier.height(7.dp))
        }
    }
}

@Composable private fun DeviceActions(device:Device,state:AppState) {
    var expanded by remember { mutableStateOf(false) }
    UnstyledDropdownMenu(expanded,{expanded=it},panel={
        DropdownMenuPanel(Modifier.width(180.dp).background(Palette.toolbar,RoundedCornerShape(6.dp)).border(1.dp,Palette.border,RoundedCornerShape(6.dp)).padding(4.dp)) {
            MenuItem(onClick={state.duplicate(device)},enabled=!state.busy&&device.name !in state.running,modifier=Modifier.fillMaxWidth().testTag("Duplicate-${device.name}")) { Label("Duplicate",Modifier.padding(9.dp)) }
            MenuItem(onClick={state.renameDevice=device},enabled=!state.busy&&device.name !in state.running,modifier=Modifier.fillMaxWidth().testTag("Rename-${device.name}")) { Label("Rename…",Modifier.padding(9.dp)) }
            MenuItem(onClick={state.reveal(device)},modifier=Modifier.fillMaxWidth().testTag("Reveal-${device.name}")) { Label("Show in Finder",Modifier.padding(9.dp)) }
            MenuItem(onClick={state.deleteDevices=listOf(device)},enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("Delete-${device.name}")) { Label("Delete…",Modifier.padding(9.dp),color=Palette.error) }
        }
    },anchor={IconButton("more","Device actions","Actions-${device.name}",!state.busy){expanded=true}})
}

@Composable private fun DevicePreview(device: Device) {
    val hash=(device.config["avdbuddy.color.seed"]?:device.name).hashCode()
    val colors=if(hash%2==0) listOf(Color(0xFF48586A),Color(0xFF1D3039),Color(0xFF10202B)) else listOf(Color(0xFF8A5B44),Color(0xFF3D363C),Color(0xFF252C38))
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors)),contentAlignment=Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w=size.width;val h=size.height
            drawCircle(Color.White.copy(alpha=.035f),w*.65f,Offset(w*.9f,h*.9f))
            drawCircle(Color.White.copy(alpha=.035f),w*.4f,Offset(w*.08f,h*.15f))
            val tablet=device.type in listOf(DeviceType.TABLET,DeviceType.TV,DeviceType.DESKTOP,DeviceType.CAR,DeviceType.FOLDABLE)
            val pw=if(tablet) 146.dp.toPx() else 66.dp.toPx();val ph=if(tablet) 91.dp.toPx() else 127.dp.toPx()
            val x=(w-pw)/2;val y=(h-ph)/2
            drawRoundRect(Color(0xFF0E1113),Offset(x-3,y-3),Size(pw+6,ph+6),CornerRadius(11.dp.toPx()))
            drawRoundRect(Brush.linearGradient(listOf(colors[0].copy(red=(colors[0].red+.15f).coerceAtMost(1f)),colors[1])),Offset(x,y),Size(pw,ph),CornerRadius(9.dp.toPx()))
            clipRect(x,y,x+pw,y+ph) {
                drawCircle(Color(0xFFB9CDB6).copy(alpha=.4f),pw*.74f,Offset(x+pw*.9f,y+ph*.75f))
                drawCircle(Color(0xFF5A7670).copy(alpha=.8f),pw*.8f,Offset(x+pw*.22f,y+ph*.86f))
                drawCircle(Color(0xFF203E44).copy(alpha=.85f),pw*.75f,Offset(x+pw*.63f,y+ph*1.04f))
            }
            drawCircle(Color(0xFF101516),2.dp.toPx(),Offset(w/2,y+5.dp.toPx()))
            drawRoundRect(Color.White.copy(alpha=.65f),Offset(w/2-10.dp.toPx(),y+ph-5.dp.toPx()),Size(20.dp.toPx(),2.dp.toPx()),CornerRadius(2.dp.toPx()))
        }
    }
}

@Composable fun Section(title: String, icon: String = "settings", subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Palette.panel).border(1.dp,Palette.border,RoundedCornerShape(8.dp))) {
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,modifier=Modifier.size(15.dp));Spacer(Modifier.width(9.dp));Label(title,weight=FontWeight.Medium)
            if(subtitle!=null){Spacer(Modifier.weight(1f));Label(subtitle,color=Palette.muted,size=11)}
        }
        Rule();Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
@Composable fun FormRow(title:String,content:@Composable ()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=30.dp),verticalAlignment=Alignment.CenterVertically) { Label(title,color=Palette.muted);Spacer(Modifier.weight(1f));content() }
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
