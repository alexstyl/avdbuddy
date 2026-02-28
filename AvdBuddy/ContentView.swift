import AppKit
import SwiftUI

struct ContentView: View {
    @StateObject private var manager = EmulatorManager()
    @State private var selectedEmulatorIDs: Set<String> = []
    @State private var hoveredEmulatorID: String?
    @State private var emulatorsPendingDeletion: [EmulatorInstance] = []
    @State private var emulatorPendingRename: EmulatorInstance?
    @State private var renameDraft = ""
    @State private var isPresentingCreateWizard = false

    private let horizontalPadding: CGFloat = 22
    private let gridSpacing: CGFloat = 24
    private let minimumCardWidth: CGFloat = 250

    var body: some View {
        ZStack {
            backgroundView

            VStack(alignment: .leading, spacing: 0) {
                content
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)

            createFAB
                .padding(.trailing, 28)
                .padding(.bottom, 26)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
        }
        .frame(minWidth: 980, minHeight: 680)
        .background(WindowConfigurationView())
        .sheet(isPresented: $isPresentingCreateWizard) {
            CreateAVDSheet(manager: manager)
        }
        .sheet(
            isPresented: Binding(
                get: { emulatorPendingRename != nil },
                set: { isPresented in
                    if !isPresented {
                        emulatorPendingRename = nil
                        renameDraft = ""
                    }
                }
            )
        ) {
            if let emulatorPendingRename {
                RenameAVDSheet(
                    manager: manager,
                    emulator: emulatorPendingRename,
                    renameDraft: $renameDraft
                )
            }
        }
        .task {
            manager.refreshEmulators()
            manager.refreshRunningStates()
        }
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(2))
                manager.refreshRunningStates()
            }
        }
        .onChange(of: manager.lastCreatedEmulatorName) { newValue in
            guard let newValue else { return }
            selectedEmulatorIDs = [newValue]
        }
        .alert(
            emulatorsPendingDeletion.count > 1 ? "Delete emulators?" : "Delete emulator?",
            isPresented: Binding(
                get: { !emulatorsPendingDeletion.isEmpty },
                set: { isPresented in
                    if !isPresented {
                        emulatorsPendingDeletion = []
                    }
                }
            ),
            presenting: emulatorsPendingDeletion
        ) { emulators in
            Button("Delete", role: .destructive) {
                Task { await delete(emulators) }
                emulatorsPendingDeletion = []
            }
            Button("Cancel", role: .cancel) {
                emulatorsPendingDeletion = []
            }
        } message: { emulators in
            if emulators.count == 1, let emulator = emulators.first {
                Text("Delete \"\(emulator.name)\"? This cannot be undone.")
            } else {
                Text("Delete \(emulators.count) selected emulators? This cannot be undone.")
            }
        }
    }

    private var backgroundView: some View {
        Color(nsColor: .windowBackgroundColor).ignoresSafeArea()
    }

    @ViewBuilder
    private var createFAB: some View {
        if #available(macOS 26.0, *) {
            Button {
                isPresentingCreateWizard = true
            } label: {
                Image(systemName: "plus")
                    .font(.system(size: 22, weight: .light))
                    .frame(width: 48, height: 48)
            }
            .labelStyle(.iconOnly)
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
        } else {
            Button {
                isPresentingCreateWizard = true
            } label: {
                Image(systemName: "plus")
                    .font(.system(size: 22, weight: .light))
                    .frame(width: 56, height: 56)
            }
            .buttonStyle(.borderedProminent)
            .clipShape(Circle())
        }
    }

    @ViewBuilder
    private var content: some View {
        if manager.emulators.isEmpty {
            emptyState
        } else {
            GeometryReader { proxy in
                let columns = gridColumns(for: proxy.size.width)

                ScrollView {
                    LazyVGrid(columns: columns, alignment: .leading, spacing: gridSpacing) {
                        ForEach(manager.emulators) { emulator in
                            EmulatorCard(
                                emulator: emulator,
                                isSelected: selectedEmulatorIDs.contains(emulator.id),
                                isHovered: hoveredEmulatorID == emulator.id,
                                isRunning: manager.isRunning(emulator),
                                isDeleting: manager.isDeleting(emulator)
                            )
                            .onHover { isHovering in
                                if isHovering {
                                    hoveredEmulatorID = emulator.id
                                } else if hoveredEmulatorID == emulator.id {
                                    hoveredEmulatorID = nil
                                }
                            }
                            .overlay {
                                CardInteractionView(
                                    onSingleClick: { modifiers in
                                        handleSelectionClick(for: emulator, modifiers: modifiers)
                                    },
                                    onDoubleClick: {
                                        selectedEmulatorIDs = [emulator.id]
                                        guard !manager.isRunning(emulator) else {
                                            manager.statusMessage = "\(emulator.name) is already running."
                                            return
                                        }
                                        Task { await manager.launch(emulator) }
                                    },
                                    onRightClick: {
                                        handleContextClick(for: emulator)
                                    },
                                    menuActions: menuActions(for: emulator)
                                )
                            }
                        }
                    }
                    .padding(.horizontal, horizontalPadding)
                    .padding(.top, 8)
                    .padding(.bottom, 24)
                    .frame(maxWidth: .infinity, alignment: .topLeading)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .scrollIndicators(.hidden)
                .background {
                    BackgroundInteractionView {
                        selectedEmulatorIDs.removeAll()
                    }
                }
            }
        }
    }

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("No devices yet")
                .font(.system(size: 28, weight: .bold, design: .rounded))
                .foregroundStyle(.white.opacity(0.95))
            Text("Create your first Android Virtual Device to start building your shelf.")
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.6))
            Button("Create AVD") {
                isPresentingCreateWizard = true
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(
                Capsule()
                    .fill(Color.white.opacity(0.12))
            )
            .overlay(
                Capsule()
                    .stroke(.white.opacity(0.14), lineWidth: 1)
            )
            .foregroundStyle(.white)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding(.top, 60)
        .padding(.horizontal, horizontalPadding)
    }

    private func gridColumns(for availableWidth: CGFloat) -> [GridItem] {
        let contentWidth = max(availableWidth - (horizontalPadding * 2), minimumCardWidth)
        let rawColumnCount = (contentWidth + gridSpacing) / (minimumCardWidth + gridSpacing)
        let columnCount = max(Int(rawColumnCount.rounded(.down)), 1)

        return Array(
            repeating: GridItem(.flexible(minimum: minimumCardWidth, maximum: .infinity), spacing: gridSpacing, alignment: .top),
            count: columnCount
        )
    }

    private func handleSelectionClick(for emulator: EmulatorInstance, modifiers: NSEvent.ModifierFlags) {
        if modifiers.contains(.command) {
            if selectedEmulatorIDs.contains(emulator.id) {
                selectedEmulatorIDs.remove(emulator.id)
            } else {
                selectedEmulatorIDs.insert(emulator.id)
            }
        } else {
            selectedEmulatorIDs = [emulator.id]
        }
    }

    private func handleContextClick(for emulator: EmulatorInstance) {
        if !selectedEmulatorIDs.contains(emulator.id) {
            selectedEmulatorIDs = [emulator.id]
        }
    }

    private func pendingDeletionTargets(for emulator: EmulatorInstance) -> [EmulatorInstance] {
        if selectedEmulatorIDs.contains(emulator.id) {
            let selected = manager.emulators.filter { selectedEmulatorIDs.contains($0.id) }
            if !selected.isEmpty {
                return selected
            }
        }
        return [emulator]
    }

    private func delete(_ emulators: [EmulatorInstance]) async {
        for emulator in emulators {
            await manager.delete(emulator)
        }
        selectedEmulatorIDs.subtract(emulators.map(\.id))
    }

    private func menuActions(for emulator: EmulatorInstance) -> [CardMenuAction] {
        let deletionTargets = pendingDeletionTargets(for: emulator)
        if deletionTargets.count > 1 {
            return [
                CardMenuAction(
                    title: "Move to Trash",
                    systemImage: nil,
                    isDestructive: true,
                    isEnabled: !manager.isBusy && deletionTargets.allSatisfy { !manager.isDeleting($0) },
                    handler: {
                        emulatorsPendingDeletion = deletionTargets
                    }
                )
            ]
        }

        return [
            CardMenuAction(
                title: "Show in Finder",
                systemImage: "folder",
                isDestructive: false,
                isEnabled: true,
                handler: {
                    if let finderURL = emulator.finderURL {
                        NSWorkspace.shared.activateFileViewerSelecting([finderURL])
                    } else {
                        manager.statusMessage = "Could not locate \(emulator.name) in Finder."
                    }
                }
            ),
            CardMenuAction(
                title: "",
                systemImage: nil,
                isDestructive: false,
                isEnabled: false,
                isSeparator: true,
                handler: {}
            ),
            CardMenuAction(
                title: "Duplicate",
                systemImage: nil,
                isDestructive: false,
                isEnabled: !manager.isBusy && !manager.isDeleting(emulator),
                handler: {
                    Task { await manager.duplicate(emulator) }
                }
            ),
            CardMenuAction(
                title: "Rename",
                systemImage: nil,
                isDestructive: false,
                isEnabled: !manager.isBusy && !manager.isDeleting(emulator),
                handler: {
                    emulatorPendingRename = emulator
                    renameDraft = emulator.name
                }
            ),
            CardMenuAction(
                title: "",
                systemImage: nil,
                isDestructive: false,
                isEnabled: false,
                isSeparator: true,
                handler: {}
            ),
            CardMenuAction(
                title: "Move to Trash",
                systemImage: nil,
                isDestructive: true,
                isEnabled: !manager.isBusy && deletionTargets.allSatisfy { !manager.isDeleting($0) },
                handler: {
                    emulatorsPendingDeletion = deletionTargets
                }
            )
        ]
    }
}

#Preview {
    ContentView()
}
