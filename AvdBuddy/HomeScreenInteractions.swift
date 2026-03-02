import AppKit
import SwiftUI

struct KeyboardShortcutMonitorView: NSViewRepresentable {
    let onCommandA: () -> Void
    let onCommandDelete: () -> Void
    let onBackgroundClick: () -> Void

    func makeNSView(context: Context) -> KeyboardShortcutMonitorNSView {
        let view = KeyboardShortcutMonitorNSView()
        view.onCommandA = onCommandA
        view.onCommandDelete = onCommandDelete
        view.onBackgroundClick = onBackgroundClick
        return view
    }

    func updateNSView(_ nsView: KeyboardShortcutMonitorNSView, context: Context) {
        nsView.onCommandA = onCommandA
        nsView.onCommandDelete = onCommandDelete
        nsView.onBackgroundClick = onBackgroundClick
    }
}

struct CardInteractionView: NSViewRepresentable {
    let onSingleClick: (NSEvent.ModifierFlags) -> Void
    let onDoubleClick: () -> Void
    let onRightClick: () -> Void
    let menuActions: [CardMenuAction]

    func makeNSView(context: Context) -> CardInteractionNSView {
        let view = CardInteractionNSView()
        view.onSingleClick = onSingleClick
        view.onDoubleClick = onDoubleClick
        view.onRightClick = onRightClick
        view.menuActions = menuActions
        return view
    }

    func updateNSView(_ nsView: CardInteractionNSView, context: Context) {
        nsView.onSingleClick = onSingleClick
        nsView.onDoubleClick = onDoubleClick
        nsView.onRightClick = onRightClick
        nsView.menuActions = menuActions
    }
}

struct BackgroundInteractionView: NSViewRepresentable {
    let onClick: () -> Void

    func makeNSView(context: Context) -> BackgroundInteractionNSView {
        let view = BackgroundInteractionNSView()
        view.onClick = onClick
        return view
    }

    func updateNSView(_ nsView: BackgroundInteractionNSView, context: Context) {
        nsView.onClick = onClick
    }
}

final class BackgroundInteractionNSView: NSView {
    var onClick: (() -> Void)?

    override func mouseDown(with event: NSEvent) {
        onClick?()
    }
}

final class KeyboardShortcutMonitorNSView: NSView {
    var onCommandA: (() -> Void)?
    var onCommandDelete: (() -> Void)?
    var onBackgroundClick: (() -> Void)?
    private var localMonitor: Any?

    override func viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        installMonitorIfNeeded()
    }

    deinit {
        removeMonitor()
    }

    private func installMonitorIfNeeded() {
        guard localMonitor == nil else { return }
        localMonitor = NSEvent.addLocalMonitorForEvents(matching: [.keyDown, .leftMouseDown]) { [weak self] event in
            guard let self, let window = self.window, event.window == window else { return event }

            if event.type == .keyDown {
                let isEditingText = (window.firstResponder as? NSTextView)?.isEditable == true
                guard let action = HomeScreenKeyboardShortcut.action(
                    forKeyCode: event.keyCode,
                    charactersIgnoringModifiers: event.charactersIgnoringModifiers,
                    modifiers: event.modifierFlags,
                    isEditingText: isEditingText
                ) else {
                    return event
                }

                switch action {
                case .selectAll:
                    self.onCommandA?()
                case .moveToTrash:
                    self.onCommandDelete?()
                }
                return nil
            }

            if event.type == .leftMouseDown,
               let hitView = window.contentView?.hitTest(event.locationInWindow),
               !hitView.hasAncestor(ofType: CardInteractionNSView.self) {
                self.onBackgroundClick?()
            }

            return event
        }
    }

    private func removeMonitor() {
        if let localMonitor {
            NSEvent.removeMonitor(localMonitor)
            self.localMonitor = nil
        }
    }
}

enum HomeScreenKeyboardShortcut {
    case selectAll
    case moveToTrash

    static func action(
        forKeyCode keyCode: UInt16,
        charactersIgnoringModifiers: String?,
        modifiers: NSEvent.ModifierFlags,
        isEditingText: Bool
    ) -> Self? {
        let flags = modifiers.intersection(.deviceIndependentFlagsMask)
        guard flags.contains(.command), !isEditingText else {
            return nil
        }

        if charactersIgnoringModifiers?.lowercased() == "a" {
            return .selectAll
        }

        if keyCode == 51 {
            return .moveToTrash
        }

        return nil
    }
}

private extension NSView {
    func hasAncestor<T: NSView>(ofType type: T.Type) -> Bool {
        var current: NSView? = self
        while let view = current {
            if view is T {
                return true
            }
            current = view.superview
        }
        return false
    }
}

final class CardInteractionNSView: NSView {
    var onSingleClick: ((NSEvent.ModifierFlags) -> Void)?
    var onDoubleClick: (() -> Void)?
    var onRightClick: (() -> Void)?
    var menuActions: [CardMenuAction] = []

    override func hitTest(_ point: NSPoint) -> NSView? {
        self
    }

    override func mouseDown(with event: NSEvent) {
        if event.clickCount == 2 {
            onDoubleClick?()
        } else {
            onSingleClick?(event.modifierFlags)
        }
    }

    override func rightMouseDown(with event: NSEvent) {
        onRightClick?()

        let menu = NSMenu()
        for action in menuActions {
            if action.isSeparator {
                menu.addItem(.separator())
                continue
            }

            let item = NSMenuItem(title: action.title, action: #selector(handleMenuItem(_:)), keyEquivalent: "")
            item.target = self
            item.isEnabled = action.isEnabled
            item.representedObject = action
            if let systemImage = action.systemImage {
                item.image = NSImage(systemSymbolName: systemImage, accessibilityDescription: action.title)
            }
            if action.isDestructive {
                item.attributedTitle = NSAttributedString(
                    string: action.title,
                    attributes: [.foregroundColor: NSColor.systemRed]
                )
            }
            menu.addItem(item)
        }

        NSMenu.popUpContextMenu(menu, with: event, for: self)
    }

    @objc
    private func handleMenuItem(_ sender: NSMenuItem) {
        guard let action = sender.representedObject as? CardMenuAction else { return }
        action.handler()
    }
}

final class CardMenuAction: NSObject {
    let title: String
    let systemImage: String?
    let isDestructive: Bool
    let isEnabled: Bool
    let isSeparator: Bool
    let handler: () -> Void

    init(
        title: String,
        systemImage: String?,
        isDestructive: Bool,
        isEnabled: Bool,
        isSeparator: Bool = false,
        handler: @escaping () -> Void
    ) {
        self.title = title
        self.systemImage = systemImage
        self.isDestructive = isDestructive
        self.isEnabled = isEnabled
        self.isSeparator = isSeparator
        self.handler = handler
    }
}

struct WindowConfigurationView: NSViewRepresentable {
    func makeNSView(context: Context) -> NSView {
        let view = NSView()
        DispatchQueue.main.async {
            configureWindow(for: view)
        }
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {
        DispatchQueue.main.async {
            configureWindow(for: nsView)
        }
    }

    private func configureWindow(for view: NSView) {
        guard let window = view.window else { return }
        window.title = "AvdBuddy"
        window.titleVisibility = .visible
        window.titlebarAppearsTransparent = true
        window.toolbarStyle = .unifiedCompact
        window.isMovableByWindowBackground = true
        window.backgroundColor = .windowBackgroundColor
        window.isOpaque = true
    }
}
