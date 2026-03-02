//
//  AvdBuddyTests.swift
//  AvdBuddyTests
//
//  Created by Alex Styl on 2/28/26.
//

import AppKit
import Testing
@testable import AvdBuddy

struct AvdBuddyTests {

    @Test
    @MainActor
    func recognizesCommandAAsSelectAll() {
        let action = HomeScreenKeyboardShortcut.action(
            forKeyCode: 0,
            charactersIgnoringModifiers: "a",
            modifiers: [.command],
            isEditingText: false
        )

        if case .selectAll? = action {
            #expect(Bool(true))
        } else {
            Issue.record("Expected Command-A to map to selectAll, got \(String(describing: action))")
        }
    }

    @Test
    @MainActor
    func recognizesCommandDeleteAsMoveToTrash() {
        let action = HomeScreenKeyboardShortcut.action(
            forKeyCode: 51,
            charactersIgnoringModifiers: nil,
            modifiers: [.command],
            isEditingText: false
        )

        if case .moveToTrash? = action {
            #expect(Bool(true))
        } else {
            Issue.record("Expected Command-Delete to map to moveToTrash, got \(String(describing: action))")
        }
    }

    @Test
    @MainActor
    func ignoresShortcutsWhileEditingText() {
        #expect(
            HomeScreenKeyboardShortcut.action(
                forKeyCode: 51,
                charactersIgnoringModifiers: nil,
                modifiers: [.command],
                isEditingText: true
            ) == nil
        )
    }

}
