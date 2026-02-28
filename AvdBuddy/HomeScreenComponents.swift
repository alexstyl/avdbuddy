import SwiftUI

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
                        if manager.validationMessageForRename(from: emulator.name, to: renameDraft) == nil,
                           manager.statusMessage.hasPrefix("Renamed ") {
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
        .shadow(color: Color.black.opacity(isHovered || isSelected ? 0.36 : 0.18), radius: isHovered || isSelected ? 18 : 10, y: 8)
        .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .animation(.easeOut(duration: 0.16), value: isHovered)
        .animation(.easeOut(duration: 0.16), value: isSelected)
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
            return Color.white.opacity(0.09)
        }
        return Color.white.opacity(0.055)
    }

    private var cardStroke: Color {
        if isSelected {
            return Color.white.opacity(0.26)
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
