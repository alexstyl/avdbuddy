@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.avdbuddy

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.avdbuddy.iconography.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.component.styling.SimpleListItemStyle
import org.jetbrains.jewel.ui.component.styling.SimpleListItemMetrics
import org.jetbrains.jewel.ui.theme.simpleListItemStyle
import org.jetbrains.jewel.ui.typography
import org.jetbrains.jewel.ui.Orientation
import com.composeunstyled.DrawerPlacement
import com.composeunstyled.DrawerPresentation
import com.composeunstyled.DrawerSnapPoint
import com.composeunstyled.DrawerSnapPoints
import com.composeunstyled.Panel
import com.composeunstyled.UnstyledDrawer
import com.composeunstyled.UnstyledDrawerState
import com.composeunstyled.Viewport
import com.composeunstyled.DialogHost

fun main(args: Array<String>) {
    if ("--diagnostics" in args) {
        println(AndroidService(SettingsStore().load()).diagnostics())
        return
    }
    if (HostOs.detect() == HostOs.MAC) {
        System.setProperty("apple.awt.application.name", "AvdBuddy")
        System.setProperty("apple.awt.application.appearance", "NSAppearanceNameAqua")
    }
    application {
        val scope = rememberCoroutineScope()
        val state = remember { AppState(scope) }
        val drawerState = rememberSidebarDrawerState()
        Window(
            onCloseRequest = ::exitApplication,
            title = APP_NAME,
            state = rememberWindowState(width = 1040.dp, height = 720.dp),
            onPreviewKeyEvent = { event ->
                handleMainWindowShortcut(event, state) { drawerState.targetValue = !drawerState.targetValue }
            },
        ) {
            window.minimumSize = java.awt.Dimension(760, 540)
            val isMacOS = System.getProperty("os.name").startsWith("Mac")
            if (isMacOS) {
                // Keep the native decorated window, with content behind a transparent title bar.
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            }
            AvdBuddyTheme {
                DialogHost {
                    App(state, topInset = if (isMacOS) 28.dp else 0.dp, drawerState = drawerState)
                }
            }
        }
    }
}

// Handle shortcuts before focused content, including the emulator and text fields.
private fun handleMainWindowShortcut(event: KeyEvent, state: AppState, toggleSidebar: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown || state.hasOpenDialog) return false
    if (if (HostOs.detect() == HostOs.MAC) event.isMetaPressed else event.isCtrlPressed) when (event.key) {
        Key.DirectionRight -> return state.rotateCurrent(clockwise = true)
        Key.DirectionLeft -> return state.rotateCurrent(clockwise = false)
        Key.B -> { toggleSidebar(); return true }
    }
    if (!event.isMetaPressed && !event.isCtrlPressed) return false
    return when (event.key) {
        Key.N -> { if (!state.busy) state.resetCreate(); true }
        Key.Comma -> { state.showSettings = true; true }
        Key.Backspace -> { if (!state.busy && state.selectedDevices.none { it.name in state.starting }) state.deleteDevices = state.selectedDevices; true }
        else -> false
    }
}

@Composable fun AvdBuddyTheme(content: @Composable () -> Unit) { IntUiTheme(isDark = false) { content() } }
@Composable fun Label(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, maxLines: Int = Int.MAX_VALUE) {
    Text(text, modifier, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}
@Composable fun Rule() { Divider(Orientation.Horizontal, Modifier.fillMaxWidth()) }
@Composable fun Icon(icon: ImageVector, tint: Color = JewelTheme.contentColor, modifier: Modifier = Modifier.size(18.dp)) {
    Image(icon, contentDescription = null, modifier = modifier, colorFilter = ColorFilter.tint(tint))
}
@Composable fun IconButton(icon: ImageVector, description: String, tag: String = description, enabled: Boolean = true, onClick: () -> Unit) {
    Tooltip(tooltip = { Text(description) }) {
        org.jetbrains.jewel.ui.component.IconButton(onClick, enabled = enabled,
            modifier = Modifier.testTag(tag).semantics { contentDescription = description }) { Icon(icon) }
    }
}
@Composable fun ActionButton(text: String, primary: Boolean = false, enabled: Boolean = true, tag: String = text, onClick: () -> Unit) {
    if (primary) DefaultButton(onClick, Modifier.testTag(tag), enabled = enabled) { Text(text) }
    else OutlinedButton(onClick, Modifier.testTag(tag), enabled = enabled) { Text(text) }
}

@Composable private fun rememberSidebarDrawerState(): UnstyledDrawerState<Boolean> = remember {
    UnstyledDrawerState(
        initialValue = true,
        snapPoints = DrawerSnapPoints {
            false at DrawerSnapPoint.Zero
            true at DrawerSnapPoint.ContentSize
        },
    )
}

@Composable fun App(state: AppState, autoRefresh: Boolean = true, topInset: Dp = 0.dp,
                    drawerState: UnstyledDrawerState<Boolean> = rememberSidebarDrawerState()) {
    val headerHeight = maxOf(topInset, 32.dp)
    LaunchedEffect(state) { state.observeDisplays() }
    LaunchedEffect(state, autoRefresh) {
        if (autoRefresh) { state.refresh(); while (isActive) { delay(3000); state.refresh() } }
    }
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().background(Color(0xFFF1F1F1)).testTag("DeviceShelf")) {
        UnstyledDrawer(
            state = drawerState,
            placement = DrawerPlacement.Start,
            presentation = DrawerPresentation.InPlace,
            dismissOnClickOutside = false,
            modifier = Modifier.fillMaxSize(),
        ) {
            Viewport<Boolean>(Modifier.fillMaxSize()) {
                Panel(Modifier.width(270.dp).fillMaxHeight()) {
                    Box(Modifier.fillMaxSize().background(Color(0xFFF1F1F1))) {
                        DeviceSidebar(state, headerHeight, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize()
            .padding(start = with(LocalDensity.current) { drawerState.offset.toDp() })
            .then(if (drawerState.offset > 0f) Modifier.clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFD6D6D6), RoundedCornerShape(8.dp)) else Modifier)
            .background(Color.White)
            .padding(top = headerHeight)) {
            val device = state.previewDevice
            if (state.selected.size > 1) {
                Box(Modifier.fillMaxSize().testTag("MultipleDevicesSelected"), contentAlignment = Alignment.Center) {
                    Label("Multiple devices selected", color = JewelTheme.globalColors.text.info)
                }
            } else if (device != null) EmulatorPanel(device, state)
            else Column(Modifier.fillMaxSize().testTag("EmptyShelf"), verticalArrangement=Arrangement.Center, horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(Icons.DevicePhoneMobile, modifier=Modifier.size(48.dp))
                Spacer(Modifier.height(20.dp)); Label("Your next Android device")
                Spacer(Modifier.height(10.dp)); Label("Create a device to see it here.", color=JewelTheme.globalColors.text.info)
                Spacer(Modifier.height(24.dp)); ActionButton("Create a device", primary=true, tag="EmptyCreate") { if(state.ready) state.resetCreate() else state.showSettings=true }
            }
        }
    }
    Row(
        Modifier.align(Alignment.TopStart)
            .width(maxOf(with(LocalDensity.current) { drawerState.offset.toDp() }, if (topInset > 0.dp) 136.dp else 64.dp))
            .height(headerHeight).padding(end = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(Icons.ViewColumns, if (drawerState.targetValue) "Hide sidebar" else "Show sidebar", "ToggleSidebar") {
            drawerState.targetValue = !drawerState.targetValue
        }
        IconButton(Icons.Plus, "Create a virtual device", "CreateDevice", !state.busy) { if(state.ready) state.resetCreate() else state.showSettings=true }
    }
    if(state.showCreate) CreateDeviceDialog(state)
    if(state.deleteDevices.isNotEmpty()) DeleteDevicesDialog(state)
    }
    if(state.showSettings) SettingsDialog(state)
    LaunchedEffect(state, autoRefresh) { if (autoRefresh) state.updates.monitor() }
    if(state.showAbout) AboutDialog(state)
    if(state.updates.visible) UpdatesDialog(state)
    if(state.license.visible) LicenseDialog(state.license)
    state.renameDevice?.let { device -> RenameDeviceDialog(state,device) }
}

@Composable private fun DeviceSidebar(state: AppState, topInset: Dp, modifier: Modifier = Modifier) {
    val searchField = rememberTextFieldState()
    val query = searchField.text.toString().trim()
    Column(modifier.fillMaxHeight().testTag("DeviceSidebar").padding(top = topInset)) {
        TextField(
            state = searchField,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 8.dp, bottom = 4.dp)
                .testTag("DeviceSearch").semantics { contentDescription = "Search devices" },
            placeholder = { Text("Search devices") },
            leadingIcon = { Icon(Icons.MagnifyingGlass, tint = JewelTheme.globalColors.text.info, modifier = Modifier.size(18.dp).testTag("DeviceSearchIcon")) },
            trailingIcon = if (searchField.text.isNotEmpty()) {
                { IconButton(Icons.XMark, "Clear search", "ClearDeviceSearch") { searchField.setTextAndPlaceCursorAtEnd("") } }
            } else null,
        )
        val sidebarDevices = state.visibleDevices.filter { it.displayName.contains(query, ignoreCase = true) }.sortedWith(
            compareBy<Device> { it.name !in state.running }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                .thenBy { it.name }
        )
        val orderedNames = sidebarDevices.map { it.name }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) false
            else if ((event.isMetaPressed || event.isCtrlPressed) && event.key == Key.A) {
                state.selected = orderedNames.toSet()
                true
            } else if (event.key == Key.DirectionUp || event.key == Key.DirectionDown) {
                val current = sidebarDevices.indexOfFirst { it.name == state.previewDevice?.name }
                val next = (current + if (event.key == Key.DirectionDown) 1 else -1).coerceIn(0, (sidebarDevices.size - 1).coerceAtLeast(0))
                sidebarDevices.getOrNull(next)?.let { state.selectDevice(it, extend = event.isShiftPressed, orderedNames = orderedNames) }
                true
            } else false
        }, contentPadding=PaddingValues(horizontal=8.dp, vertical=4.dp)) {
            if (sidebarDevices.isEmpty() && query.isNotEmpty()) {
                item {
                    Label("No matching devices", modifier = Modifier.padding(8.dp).testTag("NoDeviceMatches"),
                        color = JewelTheme.globalColors.text.info)
                }
            }
            items(sidebarDevices.size, key={sidebarDevices[it].name}) { index ->
                Box(Modifier.animateItem()) {
                    DeviceRow(sidebarDevices[index], state, orderedNames)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment=Alignment.CenterVertically) {
            IconButton(Icons.Cog6Tooth, "Settings", "Settings", !state.busy) { state.showSettings=true }
            Spacer(Modifier.weight(1f))
            ActionButton(if (state.license.licensed) "Licensed" else "Unlicensed", tag="LicenseStatus") { state.license.visible=true }
            IconButton(Icons.InformationCircle, "About AvdBuddy", "About") { state.showAbout=true }
        }
    }
}

@Composable private fun DeviceRow(device: Device, state: AppState, orderedNames: List<String>) {
    val baseStyle = JewelTheme.simpleListItemStyle
    val itemStyle = remember(baseStyle) {
        SimpleListItemStyle(baseStyle.colors, SimpleListItemMetrics(
            innerPadding = baseStyle.metrics.innerPadding,
            outerPadding = PaddingValues(0.dp),
            selectionBackgroundCornerSize = CornerSize(8.dp),
            iconTextGap = baseStyle.metrics.iconTextGap,
        ))
    }
    val selected = device.name in state.selected
    val running = device.name in state.running
    val pending = state.pendingCreation?.name == device.name
    val operation = if (pending) {
        if (state.creating) "Setting up…" else "Setup failed"
    } else state.deviceOperations[device.name]
        ?: if (device.name in state.starting) "Starting…" else null
    val subtitle = operation ?: if (device.missingStorage) "Storage unavailable" else androidFamilyTitle(device.api)
    var clickModifiers by remember { mutableStateOf(PointerKeyboardModifiers()) }
    val menuState = remember { ContextMenuState() }
    LaunchedEffect(menuState.status) {
        if (menuState.status is ContextMenuState.Status.Open) state.selectForContextMenu(device)
    }
    ContextMenuArea(state = menuState, items = {
        val targets = if (device.name in state.selected) state.selectedDevices else listOf(device)
        val startable = targets.filter { it.name !in state.running && it.name !in state.starting && !it.missingStorage }
        val stoppable = targets.filter { it.name in state.running && it.name !in state.starting }
        buildList {
            if (pending) {
                if (state.creating) add(ContextMenuItem("Cancel setup") { state.cancelCreate() })
                else {
                    add(ContextMenuItem("Try again") { state.retryCreation() })
                    add(ContextMenuItem("Remove") { state.discardPendingCreation() })
                }
            } else if (!state.busy) {
                if (startable.isNotEmpty()) add(ContextMenuItem("Start") { state.launch(startable) })
                if (stoppable.isNotEmpty()) {
                    add(ContextMenuItem("Stop") { state.stop(stoppable) })
                    add(ContextMenuItem("Restart") { state.restart(stoppable) })
                }
                if (targets.size == 1 && device.name !in state.running && device.name !in state.starting) {
                    add(ContextMenuItem("Duplicate") { state.duplicate(device) })
                    add(ContextMenuItem("Rename…") { state.renameDevice = device })
                }
                add(ContextMenuItem("Show in ${HostPlatform().fileManagerName}") { state.reveal(targets) })
                if (targets.none { it.name in state.starting }) {
                    add(ContextMenuItem("Remove…") { state.deleteDevices = targets })
                }
            }
        }
    }) {
        SimpleListItem(selected = selected, height = 56.dp, style = itemStyle,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).testTag("Device-${device.name}")
                .semantics { contentDescription="${device.displayName}, $subtitle"; this.selected = selected }
                .onPointerEvent(PointerEventType.Press, pass = PointerEventPass.Initial) { event ->
                    if (event.buttons.isPrimaryPressed) {
                        clickModifiers = event.keyboardModifiers
                    }
                }
                .clickable {
                    state.selectDevice(device, toggle = clickModifiers.isMetaPressed || clickModifiers.isCtrlPressed,
                        extend = clickModifiers.isShiftPressed, orderedNames = orderedNames)
                    clickModifiers = PointerKeyboardModifiers()
                }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).background(if (running) Color.White else Color(0xFFE1E1E1), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(device.type.icon, tint = if (running) Color(0xFF2E7D32) else JewelTheme.globalColors.text.info)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Label(device.displayName, maxLines=1)
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, color=JewelTheme.globalColors.text.info, style=JewelTheme.typography.small,
                        maxLines=1, overflow=TextOverflow.Ellipsis)
                }
                if(operation != null && (!pending || state.creating)) {
                    Spacer(Modifier.width(10.dp))
                    org.jetbrains.jewel.ui.component.CircularProgressIndicator(Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable fun Section(title: String, icon: ImageVector = Icons.Cog6Tooth, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
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
