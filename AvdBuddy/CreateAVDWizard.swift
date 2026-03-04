import Combine
import SwiftUI

struct CreateAVDWizardView: View {
    @ObservedObject var manager: EmulatorManager
    @Environment(\.dismiss) private var dismiss
    @StateObject private var model: CreateAVDWizardModel

    init(manager: EmulatorManager) {
        self.manager = manager
        _model = StateObject(wrappedValue: CreateAVDWizardModel(manager: manager))
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            Divider()
            content
            footer
        }
        .frame(width: 920, height: 640)
        .task {
            await model.loadCatalogIfNeeded()
        }
        .onChange(of: model.didCreateSuccessfully) { created in
            if created {
                dismiss()
            }
        }
    }

    private var header: some View {
        HStack(spacing: 14) {
            if model.canGoBack {
                Button {
                    model.goBack()
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 15, weight: .semibold))
                        .frame(width: 28, height: 28)
                }
                .buttonStyle(.plain)
            } else {
                Color.clear.frame(width: 28, height: 28)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text("Create AVD")
                    .font(.system(size: 20, weight: .bold, design: .rounded))
                Text(model.step.subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Spacer()
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 18)
    }

    @ViewBuilder
    private var content: some View {
        switch model.step {
        case .deviceType:
            deviceTypeStep
        case .name:
            nameStep
        case .androidVersion:
            androidVersionStep
        case .customizations:
            customizationsStep
        case .creating:
            creatingStep
        }
    }

    private var footer: some View {
        HStack {
            if model.step == .creating {
                Button(model.isCancellingCreation ? "Cancelling…" : "Cancel") {
                    model.cancelCreation()
                }
                .disabled(model.isCancellingCreation)
            } else {
                Button("Cancel") {
                    dismiss()
                }
            }

            Spacer()

            if let footerMessage = model.footerMessage {
                Text(footerMessage)
                    .font(.callout)
                    .foregroundStyle(model.hasFooterError ? Color.red : .secondary)
            }

            Spacer()

            if model.step != .creating {
                Button("Previous") {
                    model.goBack()
                }
                .disabled(!model.canGoBack)

                Button(model.primaryActionTitle) {
                    Task { await model.advance() }
                }
                .buttonStyle(.borderedProminent)
                .disabled(!model.canAdvance)
            }
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 18)
    }

    private var deviceTypeStep: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("Choose the kind of device you want to create.")
                .font(.title3.weight(.semibold))

            LazyVGrid(columns: [GridItem(.adaptive(minimum: 160), spacing: 18)], spacing: 18) {
                ForEach(CreateAVDDeviceType.allCases) { deviceType in
                    Button {
                        model.selectDeviceType(deviceType)
                    } label: {
                        VStack(alignment: .leading, spacing: 14) {
                            Image(systemName: deviceType.symbolName)
                                .font(.system(size: 28, weight: .light))
                                .frame(width: 56, height: 56)
                                .background(
                                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                                        .fill(Color.accentColor.opacity(0.12))
                                )

                            Text(deviceType.rawValue)
                                .font(.headline)
                                .foregroundStyle(.primary)

                            Text(deviceDescription(for: deviceType))
                                .font(.callout)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.leading)
                        }
                        .frame(maxWidth: .infinity, minHeight: 160, alignment: .topLeading)
                        .padding(20)
                        .background(
                            RoundedRectangle(cornerRadius: 20, style: .continuous)
                                .fill(model.selection.deviceType == deviceType ? Color.accentColor.opacity(0.12) : Color.primary.opacity(0.04))
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 20, style: .continuous)
                                .stroke(model.selection.deviceType == deviceType ? Color.accentColor : Color.primary.opacity(0.08), lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var nameStep: some View {
        VStack(spacing: 24) {
            Spacer()

            VStack(alignment: .leading, spacing: 16) {
                Text("Name your AVD")
                    .font(.system(size: 28, weight: .bold, design: .rounded))

                HStack(spacing: 10) {
                    TextField("AVD Name", text: $model.selection.avdName)
                        .textFieldStyle(.roundedBorder)
                        .font(.system(size: 18, weight: .semibold, design: .rounded))

                    Button {
                        model.suggestName()
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .frame(width: 30, height: 30)
                    }
                    .buttonStyle(.plain)
                }

                Text(model.nameValidationMessage ?? "Use a short emulator-safe id. Letters, numbers, `_`, `-`, and `.` are supported.")
                    .font(.callout)
                    .foregroundStyle(model.nameValidationMessage == nil ? .secondary : Color.red)
            }
            .frame(width: 520)

            Spacer()
        }
        .padding(24)
    }

    private var androidVersionStep: some View {
        HStack(spacing: 24) {
            VStack(alignment: .leading, spacing: 12) {
                Text("Android Versions")
                    .font(.headline)

                if model.isLoadingCatalog {
                    VStack(alignment: .leading, spacing: 14) {
                        ProgressView("Loading Android catalog…")
                        Text(model.catalogLoadingMessage)
                            .font(.callout)
                            .foregroundStyle(.secondary)
                        Text("This can take a minute or two while `sdkmanager --list` fetches the Google SDK repositories.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        if !model.catalogCommandOutput.isEmpty {
                            debugOutputView(title: "Command Output", text: model.catalogCommandOutput)
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                } else {
                    ScrollView {
                        LazyVStack(spacing: 10) {
                            ForEach(model.versionFamilies) { family in
                                Button {
                                    model.selectVersionFamily(family)
                                } label: {
                                    HStack {
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(family.title)
                                                .font(.headline)
                                            if let subtitle = family.subtitle {
                                                Text(subtitle)
                                                    .font(.caption)
                                                    .foregroundStyle(.secondary)
                                            }
                                        }
                                        Spacer()
                                    }
                                    .padding(14)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .background(
                                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                                            .fill(model.selection.selectedVersionFamilyID == family.id ? Color.accentColor.opacity(0.12) : Color.primary.opacity(0.04))
                                    )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            }
            .frame(width: 280)
            .frame(maxHeight: .infinity, alignment: .topLeading)

            VStack(alignment: .leading, spacing: 18) {
                if let selectedFamily = model.selectedVersionFamily {
                    Text("Versions")
                        .font(.headline)

                    ScrollView {
                        VStack(alignment: .leading, spacing: 10) {
                            ForEach(selectedFamily.releases) { release in
                                Button {
                                    model.selection.selectedVersionIdentifier = release.versionIdentifier
                                    model.syncCustomizationDefaults()
                                } label: {
                                    HStack {
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(release.title)
                                                .font(.headline)
                                            if let subtitle = release.subtitle {
                                                Text(subtitle)
                                                    .font(.caption)
                                                    .foregroundStyle(.secondary)
                                            }
                                        }
                                        Spacer()
                                        if release.installedCount > 0 {
                                            Image(systemName: "checkmark.circle.fill")
                                                .foregroundStyle(.green)
                                        }
                                    }
                                    .padding(14)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .background(
                                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                                            .fill(model.selection.selectedVersionIdentifier == release.versionIdentifier ? Color.accentColor.opacity(0.12) : Color.primary.opacity(0.04))
                                    )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.bottom, 4)
                    }
                } else {
                    VStack(alignment: .leading, spacing: 10) {
                        Image(systemName: "square.stack.3d.up")
                            .font(.system(size: 32, weight: .light))
                            .foregroundStyle(.secondary)
                        Text("Choose an Android Version")
                            .font(.headline)
                        Text("Pick an Android family on the left, then choose a concrete version on the right.")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .padding(24)
    }

    private var customizationsStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Customize your AVD")
                    .font(.system(size: 30, weight: .bold, design: .rounded))

                sectionCard {
                    VStack(spacing: 0) {
                        settingsRow("Architecture", systemImage: "cpu") {
                            if model.availableArchitectures.count <= 1 {
                                Text(model.availableArchitectures.first ?? "Unavailable")
                                    .foregroundStyle(.secondary)
                            } else {
                                Picker("Architecture", selection: Binding(
                                    get: { model.selection.architecture ?? model.availableArchitectures.first ?? "" },
                                    set: { model.selection.architecture = $0 }
                                )) {
                                    ForEach(model.availableArchitectures, id: \.self) { architecture in
                                        Text(architecture).tag(architecture)
                                    }
                                }
                                .labelsHidden()
                                .pickerStyle(.menu)
                                .frame(minWidth: 140, alignment: .trailing)
                            }
                        }

                        Divider()

                        settingsRow("Device Profile", systemImage: "iphone.gen3") {
                            Picker("Device Profile", selection: Binding(
                                get: { model.selection.deviceProfile },
                                set: { model.updateDeviceProfile($0) }
                            )) {
                                ForEach(model.selection.deviceType.profileOptions) { profile in
                                    Text(profile.name).tag(profile)
                                }
                            }
                            .labelsHidden()
                            .pickerStyle(.menu)
                            .frame(minWidth: 200, alignment: .trailing)
                        }

                        Divider()

                        settingsRow("RAM", systemImage: "memorychip") {
                            Picker("RAM", selection: $model.selection.ramPreset) {
                                ForEach(RAMPreset.allCases) { preset in
                                    Text(preset.rawValue).tag(preset)
                                }
                            }
                            .labelsHidden()
                            .pickerStyle(.menu)
                            .frame(minWidth: 120, alignment: .trailing)
                        }

                        Divider()

                        settingsRow("Internal Storage", systemImage: "internaldrive") {
                            Picker("Storage", selection: $model.selection.storagePreset) {
                                ForEach(StoragePreset.allCases) { preset in
                                    Text(preset.rawValue).tag(preset)
                                }
                            }
                            .labelsHidden()
                            .pickerStyle(.menu)
                            .frame(minWidth: 120, alignment: .trailing)
                        }
                    }
                }

                sectionCard {
                    settingsRow("SD Card", systemImage: "sdcard") {
                        Picker("SD Card", selection: $model.selection.sdCardPreset) {
                            ForEach(SDCardPreset.allCases) { preset in
                                Text(preset.rawValue).tag(preset)
                            }
                        }
                        .labelsHidden()
                        .pickerStyle(.menu)
                        .frame(minWidth: 120, alignment: .trailing)
                    }
                }

                sectionCard {
                    settingsRow("Google Play Services", systemImage: "globe") {
                        Picker("Google Services", selection: $model.selection.googleServices) {
                            ForEach(model.availableGoogleServicesOptions, id: \.self) { option in
                                Text(option.rawValue).tag(option)
                            }
                        }
                        .labelsHidden()
                        .pickerStyle(.menu)
                        .frame(minWidth: 220, alignment: .trailing)
                    }
                }

                if model.currentProfileSupportsDeviceFrame {
                    sectionCard {
                        settingsRow("Device Frame", systemImage: "iphone") {
                            Toggle("Show Device Frame", isOn: model.deviceFrameBinding)
                                .labelsHidden()
                        }
                    }
                }

            }
            .padding(.horizontal, 20)
            .padding(.vertical, 18)
        }
    }

    private var creatingStep: some View {
        VStack(spacing: 20) {
            Spacer()

            ProgressView()
                .controlSize(.large)

            Text(model.progressTitle)
                .font(.system(size: 28, weight: .bold, design: .rounded))

            Text(model.progressMessage)
                .font(.callout)
                .foregroundStyle(.secondary)

            if !model.creationCommandOutput.isEmpty {
                debugOutputView(title: "Create Command Output", text: model.creationCommandOutput)
            }

            Spacer()
        }
        .padding(24)
    }

    private func sectionCard<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color.primary.opacity(0.04))
        )
    }

    private func settingsRow<Content: View>(_ title: String, systemImage: String, @ViewBuilder content: () -> Content) -> some View {
        HStack(alignment: .center, spacing: 16) {
            Label(title, systemImage: systemImage)
                .font(.headline)
                .frame(maxWidth: .infinity, alignment: .leading)

            content()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
    }

    private func summaryTile(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption.weight(.medium))
                .foregroundStyle(.secondary)
            Text(value)
                .font(.headline)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color.primary.opacity(0.04))
        )
    }

    private func debugOutputView(title: String, text: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            DebugOutputScrollView(text: text)
            .frame(minHeight: 120, maxHeight: 220)
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.primary.opacity(0.04))
        )
    }

    private func deviceDescription(for deviceType: CreateAVDDeviceType) -> String {
        switch deviceType {
        case .phone: "Standard handheld Android emulator."
        case .foldable: "Foldable Android devices with inner and outer displays."
        case .tablet: "Large-screen Android tablet experience."
        case .wearOS: "Watch-sized Wear OS emulator."
        case .desktop: "Desktop-mode Android with freeform windows."
        case .tv: "Android TV and living-room surfaces."
        case .automotive: "In-car Android Automotive experiences."
        case .xr: "Immersive Android XR devices and glasses."
        }
    }
}

private struct DebugOutputScrollView: View {
    let text: String

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    Text(text.isEmpty ? "No output yet." : text)
                        .font(.system(.caption, design: .monospaced))
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .topLeading)

                    Color.clear
                        .frame(height: 1)
                        .id("debug-output-bottom")
                }
            }
            .onAppear {
                scrollToBottom(with: proxy)
            }
            .onChange(of: text) { _ in
                scrollToBottom(with: proxy)
            }
        }
    }

    private func scrollToBottom(with proxy: ScrollViewProxy) {
        DispatchQueue.main.async {
            withAnimation(.easeOut(duration: 0.12)) {
                proxy.scrollTo("debug-output-bottom", anchor: .bottom)
            }
        }
    }
}

@MainActor
final class CreateAVDWizardModel: ObservableObject {
    enum Step: Int, CaseIterable {
        case deviceType
        case name
        case androidVersion
        case customizations
        case creating

        var subtitle: String {
            switch self {
            case .deviceType: "Choose a device type"
            case .name: "Name your Android Virtual Device"
            case .androidVersion: "Choose an Android version"
            case .customizations: "Adjust the final details"
            case .creating: "Downloading and creating"
            }
        }
    }

    @Published var step: Step = .deviceType
    @Published var selection = CreateAVDSelection()
    @Published var footerMessage: String?
    @Published var hasFooterError = false
    @Published var progressTitle = "Preparing AVD"
    @Published var progressMessage = "Loading Android images and creating your emulator."
    @Published var didCreateSuccessfully = false
    @Published var creationCommandOutput = ""
    @Published var isCancellingCreation = false

    private unowned let manager: EmulatorManager
    private var lastSuggestedName: String
    private var rememberedShowDeviceFramePreference = true
    private var cancellables: Set<AnyCancellable> = []

    init(manager: EmulatorManager) {
        self.manager = manager
        self.lastSuggestedName = ""
        selection.avdName = generateSuggestedName(for: selection.deviceType)
        lastSuggestedName = selection.avdName
        syncCustomizationDefaults()
        manager.objectWillChange
            .sink { [weak self] _ in
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)
    }

    var releases: [AndroidRelease] {
        versionFamilies.flatMap(\.releases)
    }

    private var allImages: [AndroidSystemImage] {
        manager.systemImageCatalogState.images
    }

    var isLoadingCatalog: Bool {
        manager.systemImageCatalogState.isLoading
    }

    var catalogLoadingMessage: String {
        "Fetching the Android image catalog from Google."
    }

    var catalogCommandOutput: String {
        manager.systemImageCatalogState.output
    }

    var versionFamilies: [AndroidVersionFamily] {
        AndroidSystemImageCatalog.versionFamilies(from: allImages, for: selection.deviceType)
    }

    var selectedVersionFamily: AndroidVersionFamily? {
        guard let selectedVersionFamilyID = selection.selectedVersionFamilyID else { return versionFamilies.first }
        return versionFamilies.first(where: { $0.id == selectedVersionFamilyID })
    }

    var selectedRelease: AndroidRelease? {
        guard let selectedVersionIdentifier = selection.selectedVersionIdentifier else { return selectedVersionFamily?.releases.first }
        return releases.first(where: { $0.versionIdentifier == selectedVersionIdentifier })
    }

    var nameValidationMessage: String? {
        manager.validationMessageForCreateName(selection.avdName)
    }

    var availableGoogleServicesOptions: [GoogleServicesOption] {
        let options = AndroidSystemImageCatalog.availableGoogleServiceOptions(
            for: selectedRelease,
            deviceType: selection.deviceType
        )
        return options.isEmpty ? [.none] : options
    }

    var availableArchitectures: [String] {
        let architectures = AndroidSystemImageCatalog.availableArchitectures(
            for: selectedRelease,
            deviceType: selection.deviceType,
            googleServices: selection.googleServices
        )
        return architectures.isEmpty ? [] : architectures
    }

    var currentProfileSupportsDeviceFrame: Bool {
        profileSupportsDeviceFrame(selection.deviceProfile)
    }

    var deviceFrameBinding: Binding<Bool> {
        Binding(
            get: { self.selection.showDeviceFrame },
            set: { newValue in self.updateShowDeviceFrame(newValue) }
        )
    }

    var currentDeviceTypeSupportsAnyDeviceFrame: Bool {
        selection.deviceType.profileOptions.contains(where: profileSupportsDeviceFrame)
    }

    var canGoBack: Bool {
        step.rawValue > 0 && step != .creating
    }

    var canAdvance: Bool {
        switch step {
        case .deviceType:
            return true
        case .name:
            return nameValidationMessage == nil
        case .androidVersion:
            return selection.selectedVersionFamilyID != nil && selection.selectedVersionIdentifier != nil
        case .customizations:
            return selectedConfiguration != nil
        case .creating:
            return false
        }
    }

    var primaryActionTitle: String {
        switch step {
        case .customizations: "Create AVD"
        case .creating: ""
        default: "Continue"
        }
    }

    var googleServicesSummary: String {
        let options = AndroidSystemImageCatalog.availableGoogleServiceOptions(
            for: selectedRelease,
            deviceType: selection.deviceType
        )
        return options.map(\.rawValue).joined(separator: ", ")
    }

    var architectureSummary: String {
        let architectures = availableArchitectures
        return architectures.isEmpty ? "Unavailable" : architectures.joined(separator: ", ")
    }

    var downloadSummary: String {
        guard let configuration = selectedConfiguration else { return "Choose a compatible image" }
        guard let image = allImages.first(where: { $0.packagePath == configuration.packagePath }) else { return "Download required" }
        return image.isInstalled ? "Already installed" : "Download required"
    }

    func loadCatalogIfNeeded() async {
        guard allImages.isEmpty else {
            if selection.selectedVersionFamilyID == nil {
                selection.selectedVersionFamilyID = versionFamilies.first?.id
            }
            if selection.selectedVersionIdentifier == nil {
                selection.selectedVersionIdentifier = selectedVersionFamily?.defaultReleaseIdentifier
            }
            syncCustomizationDefaults()
            return
        }
        footerMessage = "Fetching the Android image catalog from Google. This can take a while."
        hasFooterError = false
        do {
            let result = try await manager.loadSystemImagesWithDebugOutput()
            if selection.selectedVersionFamilyID == nil {
                selection.selectedVersionFamilyID = versionFamilies.first?.id
            }
            if selection.selectedVersionIdentifier == nil {
                selection.selectedVersionIdentifier = selectedVersionFamily?.defaultReleaseIdentifier
            }
            syncCustomizationDefaults()
            footerMessage = "Loaded \(result.images.count) Android image variants."
        } catch {
            footerMessage = "Could not load Android versions."
            hasFooterError = true
        }
    }

    func selectDeviceType(_ deviceType: CreateAVDDeviceType) {
        let previousSuggestedName = lastSuggestedName
        selection.deviceType = deviceType
        selection.deviceProfile = deviceType.profileOptions[0]
        if selection.avdName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            selection.avdName == previousSuggestedName {
            let suggestedName = generateSuggestedName(for: deviceType)
            selection.avdName = suggestedName
            lastSuggestedName = suggestedName
        }
        selection.selectedVersionFamilyID = AndroidSystemImageCatalog.versionFamilies(from: allImages, for: deviceType).first?.id
        selection.selectedVersionIdentifier = AndroidSystemImageCatalog.versionFamilies(from: allImages, for: deviceType).first?.defaultReleaseIdentifier
        syncCustomizationDefaults()
    }

    func selectVersionFamily(_ family: AndroidVersionFamily) {
        selection.selectedVersionFamilyID = family.id
        if !family.releases.contains(where: { $0.versionIdentifier == selection.selectedVersionIdentifier }) {
            selection.selectedVersionIdentifier = family.defaultReleaseIdentifier
        }
        syncCustomizationDefaults()
    }

    func suggestName() {
        let suggestedName = generateSuggestedName(for: selection.deviceType)
        selection.avdName = suggestedName
        lastSuggestedName = suggestedName
    }

    func updateShowDeviceFrame(_ isEnabled: Bool) {
        rememberedShowDeviceFramePreference = isEnabled
        syncDeviceFrameAvailability()
    }

    func syncCustomizationDefaults() {
        selection.googleServices = AndroidSystemImageCatalog.preferredGoogleServicesOption(
            for: selectedRelease,
            deviceType: selection.deviceType
        ) ?? .none

        selection.architecture = AndroidSystemImageCatalog.preferredArchitecture(
            for: selectedRelease,
            deviceType: selection.deviceType,
            googleServices: selection.googleServices
        )

        syncDeviceFrameAvailability()
    }

    func updateDeviceProfile(_ profile: AVDDeviceProfile) {
        selection.deviceProfile = profile
        syncDeviceFrameAvailability()
    }

    private func syncDeviceFrameAvailability() {
        selection.showDeviceFrame = effectiveDeviceFramePreference(
            isSupported: currentProfileSupportsDeviceFrame,
            rememberedPreference: rememberedShowDeviceFramePreference
        )
    }

    private func profileSupportsDeviceFrame(_ profile: AVDDeviceProfile) -> Bool {
        manager.hasUsableDeviceFrame(for: profile.id)
    }

    private func effectiveDeviceFramePreference(
        isSupported: Bool,
        rememberedPreference: Bool
    ) -> Bool {
        isSupported ? rememberedPreference : false
    }

    func goBack() {
        guard let previous = Step(rawValue: step.rawValue - 1) else { return }
        step = previous
    }

    func cancelCreation() {
        guard step == .creating else { return }
        isCancellingCreation = true
        progressTitle = "Cancelling create"
        progressMessage = "Stopping the current download or AVD creation command."
        manager.cancelCreateOperation()
    }

    func advance() async {
        footerMessage = nil
        hasFooterError = false

        switch step {
        case .deviceType:
            step = .name
        case .name:
            guard nameValidationMessage == nil else { return }
            step = .androidVersion
        case .androidVersion:
            guard selection.selectedVersionIdentifier != nil else { return }
            syncCustomizationDefaults()
            step = .customizations
        case .customizations:
            guard let configuration = selectedConfiguration else {
                footerMessage = "No compatible Android image was found for that combination."
                hasFooterError = true
                return
            }
            let requiresDownload = selectedImage?.isInstalled == false
            step = .creating
            isCancellingCreation = false
            progressTitle = requiresDownload ? "Downloading Android image" : "Creating \(configuration.avdName)"
            progressMessage = requiresDownload
                ? "The selected system image is not installed yet, so AvdBuddy is downloading it from Google before creating the AVD."
                : "The required system image is already installed. AvdBuddy is now creating the AVD and applying your settings."
            creationCommandOutput = "Waiting for output..."
            let result = await manager.createAVDStreaming(from: configuration) { [weak self] chunk in
                Task { @MainActor in
                    guard let self else { return }
                    if self.creationCommandOutput.hasSuffix("Waiting for output...") {
                        self.creationCommandOutput = self.creationCommandOutput.replacingOccurrences(of: "Waiting for output...", with: "")
                    }
                    self.creationCommandOutput += chunk
                }
            }
            switch result {
            case .success(let output):
                creationCommandOutput = output
                didCreateSuccessfully = true
            case .failure(_, let output):
                if !output.isEmpty {
                    creationCommandOutput = output
                }
                footerMessage = manager.statusMessage
                hasFooterError = true
                step = .customizations
            case .cancelled(let output):
                if !output.isEmpty {
                    creationCommandOutput = output
                }
                footerMessage = "Create cancelled."
                hasFooterError = false
                step = .customizations
            }
        case .creating:
            break
        }
    }

    private var selectedConfiguration: CreateAVDResolvedConfiguration? {
        AndroidSystemImageCatalog.resolve(selection: selection, images: allImages)
    }

    private var selectedImage: AndroidSystemImage? {
        guard let selectedConfiguration else { return nil }
        return allImages.first(where: { $0.packagePath == selectedConfiguration.packagePath })
    }

    private func generateSuggestedName(for deviceType: CreateAVDDeviceType) -> String {
        for _ in 0..<24 {
            let candidate = deviceType.randomSuggestedName()
            if manager.validationMessageForCreateName(candidate) == nil {
                return candidate
            }
        }

        let fallback = "AVD_\(UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(8))"
        return manager.validationMessageForCreateName(fallback) == nil ? fallback : "AVD_\(Int.random(in: 1000...9999))"
    }
}
