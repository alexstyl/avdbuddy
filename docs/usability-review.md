# AvdBuddy usability review

Scope: the Compose app on `main`, reviewed in the isolated
`sidequest/usability-review` worktree. Based on Jakob Nielsen’s
[ten usability heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/).

| Principle | Assessment and resulting behavior |
| --- | --- |
| 1. State clarity | Creation now acknowledges cancellation and disables repeated cancellation requests. Rename stays open while working and reports completion or failure. |
| 2. Familiar language | Creation feedback describes the current task without implying that every image needs downloading. Completion directs users to the device list and Play. |
| 3. User agency | Closing the creation window requests cancellation and closes after the operation finishes. Failed renames retain the typed name for correction. |
| 4. Predictable conventions | Storage selectors and review use GiB consistently. Existing Mac window controls and navigation remain intact. |
| 5. Mistake avoidance | Names are checked before proceeding, including duplicates on a case-insensitive filesystem. Running or starting devices cannot be duplicated or renamed through backend actions. |
| 6. Visible choices | Naming constraints and reasons for disabled Rename/Continue actions appear next to the input. Existing review keeps selections visible before creation. |
| 7. Efficient interaction | Existing creation/settings/deletion shortcuts remain available, alongside visible controls. No additional mandatory wizard step was added. |
| 8. Focused presentation | The two-panel home layout is preserved. General guidance is grouped in About, and validation appears only where it applies. |
| 9. Actionable failure | Name errors identify what to change. Rename errors appear within the dialog so the user can correct and retry. |
| 10. Useful assistance | About explains selecting versus starting a device, monochrome previews, storage settings, and existing keyboard shortcuts. |

Verification: backend tests and packaged build; interactive inspection of the actual
Compose window, including About guidance, unchanged-name feedback, invalid input,
disabled Rename, and cancellation of that edit without changing device data.
No automated UI tests were created or run. Active download cancellation was inspected
in code, not exercised against a real image download. This is a heuristic review,
not a claim of user-research validation or a complete accessibility audit.
