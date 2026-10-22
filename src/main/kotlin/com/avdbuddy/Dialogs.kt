package com.avdbuddy

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import com.composeunstyled.UnstyledButton
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Text
import java.awt.Desktop
import java.io.File
import java.net.URI

@Composable fun Sheet(title:String,tag:String,width:Int=560,height:Int=640,scrollableContent:Boolean=true,contentPadding:PaddingValues=PaddingValues(24.dp),onClose:()->Unit,footer:@Composable RowScope.()->Unit,content:@Composable ColumnScope.()->Unit) {
    val windowState = rememberWindowState(width=width.dp,height=height.dp)
    LaunchedEffect(width,height) { windowState.size = DpSize(width.dp,height.dp) }
    Window(onCloseRequest=onClose,title=title,state=windowState,resizable=false) {
        AvdBuddyTheme {
            Column(Modifier.fillMaxSize().background(Palette.background).testTag(tag)) {
                Column(Modifier.padding(start=24.dp,end=24.dp,top=22.dp,bottom=18.dp)) { Label(title,size=22,weight=FontWeight.SemiBold) }
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
                Row(Modifier.fillMaxWidth().testTag("$tag-Footer").background(Palette.toolbar.copy(alpha=.55f)).padding(horizontal=20.dp,vertical=14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically,content=footer)
            }
        }
    }
}
@Composable fun SettingsWindow(state:AppState) {
    val sdkField = rememberTextFieldState(state.settings.sdk)
    val sdk = sdkField.text.toString()
    val avdField = rememberTextFieldState(state.settings.avds)
    val avds = avdField.text.toString()
    var failure by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val tools=Toolchain(expandPath(sdk))
    Sheet("Settings","SettingsWindow",height=690,onClose={state.showSettings=false},footer={
        Spacer(Modifier.weight(1f));ActionButton("Cancel",tag="CancelSettings"){state.showSettings=false}
        ActionButton("Save",primary=true,enabled=!state.busy,tag="SaveSettings"){failure=state.saveSettings(sdkField.text.toString(),avdField.text.toString())}
    }) {
        Label("Android tools and device storage, in one place.",color=Palette.muted)
        Section("Android SDK","folder") {
            Label("SDK location",size=12,color=Palette.muted,modifier=Modifier.testTag("SdkLocationLabel"))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Field(sdkField,"SdkPath",Modifier.weight(1f))
                ActionButton("Browse…",tag="BrowseSdk"){chooseDirectory(sdk)?.let{sdkField.setTextAndPlaceCursorAtEnd(it)}}
            }
            UnstyledButton({expanded=!expanded},modifier=Modifier.fillMaxWidth().testTag("ToggleToolValidation")) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(if(tools.ready)Palette.green else Palette.error,RoundedCornerShape(6.dp)))
                    Spacer(Modifier.width(8.dp));Label(if(tools.ready) "All four Android tools found" else "Android tools are missing",size=12,color=if(tools.ready)Palette.muted else Palette.error)
                    Spacer(Modifier.weight(1f));Label(if(expanded) "⌃" else "⌄",color=Palette.muted)
                }
            }
            if(expanded) tools.tools.forEach { (name,file) -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Icon(if(file.canExecute())"check" else "close",if(file.canExecute())Palette.green else Palette.error,Modifier.size(13.dp))
                Spacer(Modifier.width(8.dp));Column { Label(name,size=12);Label(file.path,size=10,color=Palette.muted,maxLines=2) }
            } }
        }
        Section("Emulator storage","disk") {
            Label("Device location",size=12,color=Palette.muted,modifier=Modifier.testTag("AvdLocationLabel"))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Field(avdField,"AvdPath",Modifier.weight(1f))
                ActionButton("Browse…",tag="BrowseAvds"){chooseDirectory(avds)?.let{avdField.setTextAndPlaceCursorAtEnd(it)}}
            }
            Label("New devices are stored here. Switching folders leaves existing devices in place and applies to AvdBuddy only.",size=12,color=Palette.muted)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                ActionButton("Use default",tag="DefaultAvdPath"){avdField.setTextAndPlaceCursorAtEnd(defaultAvdHome())}
                ActionButton("New folder…",tag="NewAvdFolder"){
                    val parent=chooseDirectory(avds)
                    if(parent!=null) {
                        val folder=File(parent,"AvdBuddy Devices")
                        if(folder.isDirectory || folder.mkdir()) avdField.setTextAndPlaceCursorAtEnd(folder.path) else failure="Could not create the folder."
                    }
                }
            }
        }
        val images=File(expandPath(sdk),"system-images")
        Section("Android system images","download") {
            SelectionContainer { Label(images.canonicalPath,size=12,color=Palette.muted) }
            Label(if(images.isDirectory)"Managed by the Android SDK, separately from device storage." else if(java.nio.file.Files.isSymbolicLink(images.toPath())) "This location points to an unavailable drive. Connect it before downloading." else "This folder will be created when you download your first image.",size=12,color=if(java.nio.file.Files.isSymbolicLink(images.toPath())&&!images.exists())Palette.error else Palette.muted)
        }
        failure?.let{Label(it,color=Palette.error,size=12,modifier=Modifier.testTag("SettingsError"))}
    }
}

@Composable fun CreateWindow(state:AppState) {
    var step by remember { mutableStateOf(0) }
    var type by remember { mutableStateOf(DeviceType.PHONE) }
    var chosen by remember { mutableStateOf<SystemImage?>(null) }
    val nameField = rememberTextFieldState("Pixel_"+(System.currentTimeMillis()%10000))
    val name = nameField.text.toString()
    val nameIssue = deviceNameError(name, state.devices.map { it.name })
    var closeRequested by remember { mutableStateOf(false) }
    LaunchedEffect(closeRequested, state.creating) { if (closeRequested && !state.creating) state.showCreate=false }
    var profile by remember { mutableStateOf(type.profile) }
    var ram by remember { mutableStateOf("Default") }
    var storage by remember { mutableStateOf(16) }
    var sd by remember { mutableStateOf(0) }
    var frame by remember { mutableStateOf(true) }
    val output by state.output.collectAsState()
    var showProgress by remember { mutableStateOf(false) }
    var reviewPlan by remember { mutableStateOf<StoragePlan?>(null) }
    var reviewError by remember { mutableStateOf("") }
    var reviewAttempt by remember { mutableStateOf(0) }
    fun specification() = chosen?.let { CreateSpec(nameField.text.toString().trim(),it,profile,if(ram=="Default")null else ram.toInt()*1024,storage,sd.takeIf{it>0},frame) }
    LaunchedEffect(step, chosen, storage, sd, state.settings, reviewAttempt, showProgress) {
        if (step==3 && !showProgress) {
            reviewPlan=null;reviewError=""
            specification()?.let { spec ->
                try { reviewPlan=state.reviewStorage(spec) }
                catch(t:Exception) {
                    if(t is kotlinx.coroutines.CancellationException) throw t
                    reviewError=t.message ?: "Could not check storage requirements."
                }
            }
        }
    }
    val title=if(showProgress)if(state.createFinished)"Your device is ready" else if(state.createError.isNotBlank())"Couldn’t create your device" else "Creating your device" else when(step){0->"What would you like to create?";1->"Choose an Android version";2->"Make it your own";else->"Review your device"}
    Sheet(title,"CreateWindow",width=840,height=710,
        scrollableContent=step!=1 && !showProgress,
        contentPadding=if(step==1 && !showProgress) PaddingValues(start=24.dp,end=24.dp,top=20.dp) else PaddingValues(24.dp),
        onClose={if(state.creating) {closeRequested=true;state.cancelCreate()} else state.showCreate=false},footer={
        if(!showProgress) {
            if(step==3 && reviewPlan?.canCreate==false) Label("Not enough disk space",size=12,color=Palette.error)
            Spacer(Modifier.weight(1f))
            ActionButton("Cancel",tag="CancelCreate"){state.showCreate=false}
            if(step>0)ActionButton("Previous",tag="PreviousCreate"){step--}
            ActionButton(if(step==3) "Create" else "Continue",primary=true,
                enabled=!state.busy && (step!=1||chosen!=null) && (step!=2||nameIssue==null) && (step!=3 || (reviewPlan?.canCreate==true && reviewError.isBlank() && nameIssue==null)),tag="ContinueCreate") {
                if(step<3) { step++;if(step==1)state.loadCatalog() } else specification()?.let { spec ->
                    showProgress=true
                    state.create(spec)
                }
            }
        } else {
            if(state.creating) {Label(if(state.createCancelling) "Cancelling…" else "Installed Android images are kept if you cancel.",size=12,color=Palette.muted);Spacer(Modifier.weight(1f));ActionButton("Cancel creation",enabled=!state.createCancelling,tag="CancelDownload"){state.cancelCreate()} }
            else {Spacer(Modifier.weight(1f));if(state.createError.isNotBlank())ActionButton("Back",tag="BackFromError"){showProgress=false};ActionButton("Done",primary=true,tag="CreateDone"){state.showCreate=false}}
        }
    }) {
        if(showProgress) {
            if(state.createFinished) {
                Box(Modifier.fillMaxWidth().padding(vertical=12.dp),contentAlignment=Alignment.Center){Icon("check",Palette.green,Modifier.size(44.dp))}
                Label("$name is ready. Select it in the device list and press Play to start it.",size=15)
            } else if(state.creating) {
                Label(if(state.createCancelling) "Cancelling device creation…" else "Preparing Android and creating your device…",color=Palette.muted)
            }
            if(state.createError.isNotBlank()) SelectionContainer { Label(state.createError,color=Palette.error,modifier=Modifier.testTag("CreateError"),size=12) }
            ActivityLog(output, Modifier.weight(1f))

        } else when(step) {
            0 -> {
                Label("Pick the kind of Android device you want to run.",color=Palette.muted)
                DeviceType.entries.chunked(2).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { row.forEach { option ->
                    val selected=type==option
                    UnstyledButton({type=option;profile=option.profile;chosen=null},modifier=Modifier.weight(1f).testTag("Type-${option.name}").height(84.dp).background(if(selected) Palette.accent.copy(alpha=.1f) else Palette.panel,RoundedCornerShape(9.dp)).border(1.dp,if(selected)Palette.accent else Palette.border,RoundedCornerShape(9.dp))) {
                        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(option.label.lowercase(),if(selected)Palette.accent else Palette.muted,Modifier.size(25.dp));Spacer(Modifier.width(13.dp));Label(option.label,weight=FontWeight.Medium)
                            Spacer(Modifier.weight(1f));if(selected)Icon("check",Palette.accent,Modifier.size(15.dp))
                        }
                    }
                } } }
            }
            1 -> {
                if(state.catalogLoading)Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){org.jetbrains.jewel.ui.component.CircularProgressIndicator();Label("Loading Android versions…",color=Palette.muted)}
                if(state.catalogError.isNotBlank()) {Label(state.catalogError,color=Palette.error,size=12);ActionButton("Try again"){state.loadCatalog()}}
                if (!state.catalogLoading && state.catalogError.isBlank()) {
                    AndroidImagePicker(state.images,type,chosen,onSelect={chosen=it})
                }
            }
            2 -> {
                Section("Identity","phone") {
                    Label("Device name",color=Palette.muted,size=12)
                    Field(nameField,"DeviceName",Modifier.fillMaxWidth())
                    Label(nameIssue ?: "Use letters, numbers, dots, underscores or hyphens.",color=if(nameIssue!=null) Palette.error else Palette.muted,size=12)
                    Label(chosen?.title.orEmpty(),color=Palette.muted,size=12)
                }
                Section("General","settings") {
                    val profileOptions=(state.profiles.filter { it.id==profile || when(type) {
                        DeviceType.PHONE -> it.id.startsWith("pixel_")&&!it.id.contains("fold")&&!it.id.contains("tablet")
                        DeviceType.FOLDABLE -> it.id.contains("fold")
                        DeviceType.TABLET -> it.id.contains("tablet")
                        DeviceType.WEAR -> it.id.startsWith("wear")
                        DeviceType.TV -> it.id.startsWith("tv_")
                        DeviceType.CAR -> it.id.startsWith("automotive")
                        DeviceType.DESKTOP -> it.id.startsWith("desktop")
                        DeviceType.XR -> it.id.startsWith("xr")
                    }}.map{it.id}+profile).distinct()
                    FormRow("Device profile"){Select(profile,profileOptions,{profile=it},{id->state.profiles.firstOrNull{it.id==id}?.name?:id},"DeviceProfile")}
                    FormRow("Memory"){Select(ram,listOf("Default","2","4","8"),{ram=it},{if(it=="Default")it else "$it GB"},"Memory")}
                    FormRow("Device frame"){CheckboxRow("Show frame",checked=frame,onCheckedChange={frame=it},modifier=Modifier.testTag("DeviceFrame"))}
                }
                Section("Storage","disk") {
                    FormRow("Internal storage"){Select(storage,listOf(8,16,32,64),{storage=it},{"$it GiB"},"InternalStorage")}
                    FormRow("SD card"){Select(sd,listOf(0,2,4,8),{sd=it},{if(it==0)"None" else "$it GiB"},"SdCard")}
                    Label("Device files will be stored in ${state.settings.avds}",size=11,color=Palette.muted)
                }
                Section("Android image","download") {
                    Label(chosen?.description.orEmpty(),size=12)
                    val releaseImages = state.images.filter { type.accepts(it.tag) && it.api == chosen?.api }
                    FormRow("Architecture") {
                        Select(chosen?.abi.orEmpty(), releaseImages.map { it.abi }.distinct(), { abi ->
                            val options = releaseImages.filter { it.abi == abi }
                            chosen = options.firstOrNull { it.tag == chosen?.tag } ?: preferredSystemImage(options)
                        }, { if(it=="arm64-v8a") "arm64" else it }, "Architecture")
                    }
                    FormRow("Google services") {
                        val options = releaseImages.filter { it.abi == chosen?.abi }
                        Select(chosen?.tag.orEmpty(), options.map { it.tag }.distinct(), { tag ->
                            chosen = preferredSystemImage(options.filter { it.tag == tag })
                        }, { tag -> options.firstOrNull { it.tag == tag }?.services.orEmpty() }, "GoogleServices")
                    }
                    Label(if(chosen?.installed==true)"This system image is already installed." else "The Android SDK will download this image before creating your device.",color=Palette.muted,size=11)
                }
            }
            3 -> {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                        Section("Device", "phone") {
                            FormRow("Name") { Label(name,weight=FontWeight.Medium) }
                            Label("${androidFamilyTitle(chosen?.api.orEmpty())} · ${androidApiSubtitle(chosen?.api.orEmpty())}",size=12)
                            FormRow("Profile") { Label("${type.label} · ${state.profiles.firstOrNull{it.id==profile}?.name ?: profile}") }
                            FormRow("Image") { Label("${chosen?.abi} · ${chosen?.services}") }
                            FormRow("Memory / Frame") { Label("${if(ram=="Default") "Default memory" else "$ram GB"} · ${if(frame) "Shown" else "Hidden"}") }
                        }
                        Section("Device data", "disk") {
                            FormRow("Internal storage capacity") { Label("$storage GiB") }
                            FormRow("SD card capacity") { Label(if(sd==0) "None" else "$sd GiB") }
                            Label("Device data grows as you use it. We budget its full capacity plus 1 GiB for cache, snapshots and configuration; this is not the amount allocated immediately.",size=11,color=Palette.muted)
                            Label(state.settings.avds,size=11,color=Palette.muted)
                        }
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                        Section("Android image", "download") {
                            Label(if(chosen?.installed==true) "Already downloaded — no image download needed." else "This image will be downloaded before your device is created.",
                                color=if(chosen?.installed==true) Palette.green else Palette.text,modifier=Modifier.testTag("ReviewImageStatus"))
                            reviewPlan?.let { plan ->
                                if(chosen?.installed!=true) {
                                    FormRow("Download") { Label(storageSize(plan.image.download)) }
                                    FormRow("Unpacked image") { Label(storageSize(plan.image.unpacked)) }
                                    Label("The download and unpacked files need space at the same time during installation. The installed image can be shared with other devices.",size=11,color=Palette.muted)
                                } else Label("The existing shared image takes no additional installation space.",size=11,color=Palette.muted)
                            }
                        }
                        Section("Space required", "disk") {
                            if(reviewPlan==null && reviewError.isBlank()) Label("Checking image size and available disk space…",color=Palette.muted,modifier=Modifier.testTag("ReviewStorageLoading"))
                            if(reviewError.isNotBlank()) {
                                Label(reviewError,color=Palette.error,modifier=Modifier.testTag("ReviewStorageError"))
                                ActionButton("Retry storage check",tag="RetryStorageCheck") { reviewAttempt++ }
                            }
                            reviewPlan?.let { plan ->
                                FormRow("Space needed during creation") { Label(storageSize(plan.required),weight=FontWeight.SemiBold) }
                                plan.disks.forEach { disk ->
                                    Label(disk.location,weight=FontWeight.Medium)
                                    Label("${storageSize(disk.required)} required · ${storageSize(disk.available)} available",size=12,color=if(disk.enough) Palette.muted else Palette.error)
                                    Label(disk.path,size=10,color=Palette.muted)
                                }
                                Label(if(plan.canCreate) "Enough disk space is available to create this device." else "Not enough available disk space to create this device. Free space or choose another storage location in Settings.",
                                    color=if(plan.canCreate) Palette.green else Palette.error,modifier=Modifier.testTag("ReviewSpaceStatus"))
                                if(chosen?.installed!=true) Label("Includes 1 GiB of installation workspace in addition to the image and device data budgets.",size=11,color=Palette.muted)
                                ActionButton("Refresh available space",tag="RefreshReviewSpace") { reviewAttempt++ }
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
fun ActivityLog(output: String, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val horizontal = rememberScrollState()
    LaunchedEffect(output, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Column(modifier.fillMaxWidth().background(Palette.panel, RoundedCornerShape(8.dp))
        .border(1.dp, Palette.border, RoundedCornerShape(8.dp)).testTag("ActivityPanel")) {
        Row(Modifier.padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
            Icon("download", modifier=Modifier.size(15.dp));Spacer(Modifier.width(9.dp));Label("Activity",weight=FontWeight.Medium)
        }
        Rule()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            SelectionContainer(Modifier.fillMaxSize().padding(end=12.dp,bottom=12.dp)) {
                Text(output.ifBlank { "Preparing Android tools…" },
                    Modifier.fillMaxSize().testTag("CreateOutput").verticalScroll(scroll).horizontalScroll(horizontal).padding(14.dp),
                    fontFamily=FontFamily.Monospace,fontSize=11.sp,color=Palette.muted,softWrap=false)
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll),Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(bottom=12.dp))
            HorizontalScrollbar(rememberScrollbarAdapter(horizontal),Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end=12.dp))
        }
    }
}

@Composable fun RenameWindow(state:AppState,device:Device) {
    val nameField = rememberTextFieldState(device.name)
    val name = nameField.text.toString().trim()
    val issue = deviceNameError(name, state.devices.filter { it.name != device.name }.map { it.name })
    var failure by remember { mutableStateOf<String?>(null) }
    val running = device.name in state.running || device.name in state.starting
    Sheet("Rename device","RenameWindow",height=350,onClose={if(!state.busy)state.renameDevice=null},footer={
        Spacer(Modifier.weight(1f));ActionButton("Cancel",enabled=!state.busy){state.renameDevice=null}
        ActionButton("Rename",primary=true,enabled=!state.busy&&issue==null&&name!=device.name&&!running,tag="ConfirmRename") {
            failure=null
            state.rename(device,nameField.text.toString()) { error -> if(error==null)state.renameDevice=null else failure=error }
        }
    }) {
        Label("Give ${device.displayName} a new name.",color=Palette.muted)
        Field(nameField,"RenameName",Modifier.fillMaxWidth())
        val hint = when { state.busy -> "Renaming device… This window will close when finished."; running -> "Stop this device before renaming it."; issue!=null -> issue; name==device.name -> "Enter a different name to rename this device."; else -> "Use letters, numbers, dots, underscores or hyphens." }
        Label(hint,color=if(running||issue!=null) Palette.error else Palette.muted,size=12)
        failure?.let { Label(it,color=Palette.error,size=12) }
    }
}
@Composable fun DeleteWindow(state:AppState) {
    val devices=state.deleteDevices
    Sheet("Delete ${if(devices.size==1)"device" else "${devices.size} devices"}?","DeleteWindow",height=310,onClose={state.deleteDevices=emptyList()},footer={
        Spacer(Modifier.weight(1f));ActionButton("Cancel",tag="CancelDelete"){state.deleteDevices=emptyList()}
        ActionButton("Delete",primary=true,enabled=!state.busy,tag="ConfirmDelete"){state.delete(devices);state.deleteDevices=emptyList()}
    }) {Label(devices.joinToString { it.displayName },weight=FontWeight.Medium);Label("The devices and their saved data will be removed. Running devices will be stopped first. This cannot be undone.",color=Palette.muted)}
}
@Composable fun AboutWindow(state:AppState) {
    Sheet("AvdBuddy","AboutWindow",height=560,onClose={state.showAbout=false},footer={Spacer(Modifier.weight(1f));ActionButton("Done",primary=true){state.showAbout=false}}) {
        Label("Android devices. Without the IDE.",size=17,weight=FontWeight.Medium)
        Label("Version $APP_VERSION",color=Palette.muted)
        Label("Built with Kotlin, Compose Desktop, Jewel, and Compose Unstyled.",color=Palette.muted)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {ActionButton("GitHub"){Desktop.getDesktop().browse(URI("https://github.com/alexstyl/avdbuddy"))};ActionButton("Check for updates",enabled=!state.busy){state.checkUpdates()}}
        Section("Using your devices", "phone") {
            Label("Select a device in the sidebar to preview it. Selection never starts the emulator.",size=12)
            Label("Press Play to start it. A gray screen is the last captured preview; the running screen appears in color.",size=12)
            Label("Use Settings to reconnect an unavailable SDK or device folder. Changing folders leaves existing devices in place.",size=12)
            Label("Shortcuts: ⌘N creates a device. ⌘, opens Settings. ⌘⌫ asks to delete the selected device.",size=11,color=Palette.muted)
            Label("While running: ⌘→ rotates clockwise. ⌘← rotates counterclockwise.",size=11,color=Palette.muted)
        }
        ActionButton("Copy diagnostics",tag="CopyDiagnostics"){state.copyDiagnostics()}
        Label("By Alex Styl · Inspired by VirtualBuddy",color=Palette.muted,size=12)
    }
}

@Composable fun UpdatesWindow(state: AppState) {
    val updates = state.updates
    Sheet("Software updates", "UpdatesWindow", height=360, onClose={updates.visible=false}, footer={
        Spacer(Modifier.weight(1f))
        ActionButton("Close") { updates.visible=false }
        when {
            updates.downloaded != null -> ActionButton("Restart and install", primary=true, enabled=!updates.working && !state.busy) { updates.restart(state.busy) }
            updates.available != null -> ActionButton("Download update", primary=true, enabled=!updates.working) { updates.download() }
            else -> ActionButton("Check again", enabled=!updates.working && updates.unavailableReason==null) { updates.check() }
        }
    }) {
        Label(updates.message, modifier=Modifier.testTag("UpdateStatus"))
        if (updates.downloaded != null && state.busy) Label("Finish the current device operation before restarting.", color=Palette.muted)
        if (updates.unavailableReason == null) CheckboxRow(checked=updates.automatic, onCheckedChange=updates::changeAutomatic) { Label("Automatically check for updates") }
    }
}
