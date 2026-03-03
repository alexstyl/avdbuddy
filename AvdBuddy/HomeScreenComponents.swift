import AppKit
import SwiftUI
import UniformTypeIdentifiers

struct InfoSheet: View {
    @EnvironmentObject private var appUpdater: AppUpdater
    @State private var isExportingDiagnostics = false
    @State private var diagnosticsDocument: DiagnosticsTextDocument?
    @State private var isPresentingDiagnosticsExporter = false
    @State private var isPresentingFeedbackCopiedAlert = false

    private let feedbackEmailAddress = "avdbuddy@alexstyl.com"

    var body: some View {
        ZStack {
            Color(nsColor: .controlBackgroundColor)
                .ignoresSafeArea()

            VStack(spacing: 14) {
                Image(nsImage: NSApp.applicationIconImage)
                    .resizable()
                    .frame(width: 116, height: 116)
                    .shadow(color: .black.opacity(0.18), radius: 12, y: 4)
                    .padding(.bottom, 6)

                Text(bundleName)
                    .font(.system(size: 28, weight: .bold))

                Text(versionText)
                    .font(.system(size: 20, weight: .regular))
                    .foregroundStyle(.secondary)
                    .padding(.bottom, 20)

                VStack(spacing: 18) {
                    Button {
                        let pasteboard = NSPasteboard.general
                        pasteboard.clearContents()
                        pasteboard.setString(feedbackEmailAddress, forType: .string)
                        isPresentingFeedbackCopiedAlert = true
                    } label: {
                        InfoLinkLabel(systemName: "envelope", title: "Send feedback")
                    }
                    .buttonStyle(.plain)

                    Link(destination: URL(string: "https://github.com/alexstyl/avdbuddy")!) {
                        InfoLinkLabel(systemName: "chevron.left.forwardslash.chevron.right", title: "Star on GitHub")
                    }

                    Link(destination: URL(string: "https://x.com/alexstyl")!) {
                        InfoLinkLabel(systemName: "bird", title: "Follow on X")
                    }
                }
                .font(.system(size: 18, weight: .regular))
                .foregroundStyle(.purple)

                if let updateActionHint = appUpdater.updateActionHint {
                    Text(updateActionHint)
                        .font(.system(size: 13, weight: .regular))
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 340)
                }

                Button(appUpdater.infoButtonTitle) {
                    appUpdater.performPrimaryUpdateAction()
                }
                .buttonStyle(.bordered)
                .disabled(!appUpdater.canCheckForUpdates)
                .padding(.top, 8)

                Button("Export Diagnostics") {
                    Task {
                        await exportDiagnostics()
                    }
                }
                .buttonStyle(.bordered)
                .disabled(isExportingDiagnostics)

                if isExportingDiagnostics {
                    HStack(spacing: 8) {
                        ProgressView()
                            .controlSize(.small)
                        Text("Preparing diagnostics…")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(width: 560, height: 460)
        .alert("Email copied to clipboard", isPresented: $isPresentingFeedbackCopiedAlert) {
            Button("OK", role: .cancel) {}
        }
        .fileExporter(
            isPresented: $isPresentingDiagnosticsExporter,
            document: diagnosticsDocument,
            contentType: .plainText,
            defaultFilename: diagnosticsDocument?.suggestedFilename
        ) { _ in
            diagnosticsDocument = nil
        }
    }

    private var bundleName: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleName") as? String ?? "AvdBuddy"
    }

    private var versionText: String {
        let shortVersion = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
        return "Version \(shortVersion) (\(build))"
    }

    private func exportDiagnostics() async {
        guard !isExportingDiagnostics else { return }
        isExportingDiagnostics = true
        let installationSource = appUpdater.installationSource

        let document = await Task.detached(priority: .userInitiated) {
            let report = AppDiagnostics.report(installationSource: installationSource)
            return DiagnosticsTextDocument(text: report)
        }.value

        diagnosticsDocument = document
        isExportingDiagnostics = false
        isPresentingDiagnosticsExporter = true
    }
}

private struct DiagnosticsTextDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.plainText] }

    let text: String
    let suggestedFilename = "AvdBuddy-Diagnostics.txt"

    init(text: String) {
        self.text = text
    }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents,
              let text = String(data: data, encoding: .utf8) else {
            throw CocoaError(.fileReadCorruptFile)
        }
        self.text = text
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        guard let data = text.data(using: .utf8) else {
            throw CocoaError(.fileWriteUnknown)
        }
        return FileWrapper(regularFileWithContents: data)
    }
}

struct InfoLinkLabel: View {
    let systemName: String
    let title: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: systemName)
                .font(.system(size: 16, weight: .regular))
                .frame(width: 20, alignment: .center)

            Text(title)
                .font(.system(size: 18, weight: .regular))
        }
    }
}

struct RenameAVDSheet: View {
    @ObservedObject var manager: EmulatorManager
    let emulator: EmulatorInstance
    @Binding var renameDraft: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Rename AVD")
                .font(.system(size: 22, weight: .bold, design: .rounded))

            TextField("AVD Name", text: $renameDraft)
                .textFieldStyle(.roundedBorder)

            Text(validationMessage ?? "Choose a new name for this AVD.")
                .font(.callout)
                .foregroundStyle(validationMessage == nil ? Color.secondary : Color.red)
                .frame(maxWidth: .infinity, alignment: .leading)

            HStack {
                Spacer()
                Button("Cancel") {
                    dismiss()
                }
                Button("Rename") {
                    Task {
                        await manager.rename(emulator, to: renameDraft)
                        if manager.lastRenamedEmulatorName == renameDraft.trimmingCharacters(in: .whitespacesAndNewlines) {
                            dismiss()
                        }
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(validationMessage != nil || manager.isBusy)
            }
        }
        .padding(24)
        .frame(width: 420)
    }

    private var validationMessage: String? {
        manager.validationMessageForRename(from: emulator.name, to: renameDraft)
    }
}

struct EmulatorCard: View {
    let emulator: EmulatorInstance
    let isSelected: Bool
    let isHovered: Bool
    let isRunning: Bool
    let isDeleting: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            artwork

            VStack(alignment: .leading, spacing: 2) {
                Text(emulator.name)
                    .font(.system(size: 17, weight: .semibold, design: .rounded))
                    .foregroundStyle(Color.primary.opacity(isSelected ? 0.98 : 0.88))
                    .lineLimit(1)
            }
            .padding(.horizontal, 6)
        }
        .padding(8)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(cardFill)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(cardStroke, lineWidth: 1)
        )
        .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .animation(.easeOut(duration: 0.16), value: isHovered)
    }

    private var artwork: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(.black.opacity(0.08))
                .frame(height: 172)
                .overlay {
                    panelGradient
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .overlay(alignment: .bottomTrailing) {
                    Circle()
                        .fill(.white.opacity(0.08))
                        .frame(width: 128, height: 128)
                        .blur(radius: 12)
                        .offset(x: 32, y: 22)
                }
                .overlay(alignment: .topLeading) {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(.white.opacity(0.18), lineWidth: 1)
                }

            Image(systemName: centeredSymbol)
                .font(.system(size: 56, weight: .light))
                .foregroundStyle(.white.opacity(0.88))
                .shadow(color: .black.opacity(0.16), radius: 12, y: 4)

            if isDeleting {
                ProgressView()
                    .controlSize(.small)
                    .tint(.white)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                    .padding(14)
            }
        }
    }

    private var centeredSymbol: String {
        if isRunning {
            return "bolt.fill"
        }
        return emulator.deviceType.symbolName
    }

    private var cardFill: Color {
        if isSelected {
            return Color(nsColor: .controlAccentColor).opacity(0.26)
        }
        return Color.white.opacity(0.055)
    }

    private var cardStroke: Color {
        if isSelected {
            return Color(nsColor: .controlAccentColor).opacity(0.95)
        }
        if isHovered {
            return Color.white.opacity(0.18)
        }
        return Color.white.opacity(0.08)
    }

    @ViewBuilder
    private var panelGradient: some View {
        let style = EmulatorCardGradientStyle(seed: emulator.colorSeed)
        if #available(macOS 15.0, *) {
            MeshGradient(
                width: 3,
                height: 3,
                points: style.points,
                colors: style.colors
            )
        } else {
            ZStack {
                LinearGradient(
                    colors: [style.colors[0], style.colors[4], style.colors[8]],
                    startPoint: .bottomLeading,
                    endPoint: .topTrailing
                )

                Circle()
                    .fill(style.colors[2].opacity(0.9))
                    .frame(width: 170, height: 170)
                    .blur(radius: 28)
                    .offset(x: 74, y: -54)

                Circle()
                    .fill(style.colors[6].opacity(0.84))
                    .frame(width: 180, height: 180)
                    .blur(radius: 32)
                    .offset(x: -70, y: 72)
            }
        }
    }
}

struct CreateAVDSheet: View {
    @ObservedObject var manager: EmulatorManager

    var body: some View {
        CreateAVDWizardView(manager: manager)
    }
}

private struct EmulatorCardGradientStyle {
    let points: [SIMD2<Float>] = [
        SIMD2(0.0, 0.0), SIMD2(0.5, 0.0), SIMD2(1.0, 0.0),
        SIMD2(0.0, 0.5), SIMD2(0.5, 0.5), SIMD2(1.0, 0.5),
        SIMD2(0.0, 1.0), SIMD2(0.5, 1.0), SIMD2(1.0, 1.0)
    ]
    let colors: [Color]

    init(seed: String) {
        let hash = Self.hash(seed)
        let baseHue = Self.paletteHues[Int(hash % UInt64(Self.paletteHues.count))]
        let hueDrift = Self.unit(hash, shift: 8, scale: 0.018) - 0.009
        let primaryHue = Self.normalizedHue(baseHue + hueDrift)

        let deep = Color(hue: primaryHue, saturation: 0.82, brightness: 0.46)
        let rich = Color(hue: primaryHue, saturation: 0.78, brightness: 0.60)
        let base = Color(hue: primaryHue, saturation: 0.72, brightness: 0.72)
        let light = Color(hue: primaryHue, saturation: 0.62, brightness: 0.84)
        let mist = Color(hue: primaryHue, saturation: 0.38, brightness: 0.94)

        colors = [
            deep.opacity(0.98), rich.opacity(0.96), light.opacity(0.98),
            rich.opacity(0.97), base.opacity(0.95), light.opacity(0.94),
            deep.opacity(0.94), base.opacity(0.9), mist.opacity(0.9)
        ]
    }

    private static let paletteHues: [Double] = [
        0.00, // red
        0.02, // vermilion
        0.04, // orange red
        0.06, // orange
        0.08, // amber
        0.11, // gold
        0.14, // yellow green
        0.17, // lime
        0.21, // chartreuse
        0.26, // spring green
        0.30, // green
        0.35, // emerald
        0.41, // teal
        0.48, // aqua
        0.54, // cyan
        0.58, // sky blue
        0.61, // blue
        0.65, // cobalt
        0.69, // indigo
        0.74, // violet
        0.79, // purple
        0.84, // magenta
        0.89, // fuchsia
        0.94, // rose
        0.97  // crimson
    ]

    private static func hash(_ seed: String) -> UInt64 {
        var hash: UInt64 = 1_469_598_103_934_665_603
        for byte in seed.utf8 {
            hash ^= UInt64(byte)
            hash &*= 1_099_511_628_211
        }
        return hash
    }

    private static func unit(_ hash: UInt64, shift: Int, scale: Double) -> Double {
        let value = Double((hash >> shift) & 0xFF) / 255.0
        return value * scale
    }

    private static func normalizedHue(_ value: Double) -> Double {
        let remainder = value.truncatingRemainder(dividingBy: 1)
        return remainder >= 0 ? remainder : remainder + 1
    }
}
