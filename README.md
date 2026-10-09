# Asylum Locator 331

Android screen capture and OCR scanner for a moderator using Last Asylum: Plague (`com.phs.global`). **Live-game compatibility has not been verified by the build environment.** The previous hardcoded-name demonstration has been removed. Production results are created only from captured game screens, never test fixtures.

## Download and install on a phone

Open [Releases](https://github.com/Agispsi/AsylumLocator331/releases), select the latest successfully tested build, and download `AsylumLocator331.apk`. Open it from Downloads, allow your browser/file manager to install apps when Android asks, and install. If Android reports a conflicting package/signature, uninstall the previous Asylum Locator first. Uninstalling removes locally stored observations and calibration; export records first if needed. These are debug-signed direct-download builds; signatures can change between fresh GitHub runners.

## First use (no computer required)

1. Enter a full or partial player name, optional Sanctuary level, and sweep dimensions (1–20 rows and columns). Server is fixed to 331.
2. Allow floating controls. Enable **Asylum map controls** under Android Accessibility. If Android blocks a sideloaded accessibility service, open this app's **App info → menu → Allow restricted settings**, then enable it. The app does not bypass Android's permission screens.
3. Start screen capture and accept Android's screen-sharing prompt. Use full-display capture. The app opens Last Asylum if installed. Keep the game open and the display unlocked. Drag the overlay status strip to move controls out of the way.
4. With the map visible at a zoom where player names are readable, select **Setup → Map**. Drag a rectangle excluding HUD/chat/buttons. Select a small, static icon unique to the map-only screen as the screen guard. Tap the center of a visible player's printed name, then the center of that player's Sanctuary. This teaches the name-to-building offset. These points are not game world coordinates.
5. Tap a Sanctuary. Select **Setup → Sanctuary** and mark the one-line player name, Sanctuary level (not VIP/power), a static panel-only heading/icon, the star/Add Tag button, and the panel close control.
6. Open its star/Add Tag screen. Select **Setup → Add Tag**. Mark the entire `#331 X:… Y:…` line, the static Add Tag heading, and the Cancel/close control that returns to the **same Sanctuary panel**. Never calibrate the Save button as close.
7. Return to a known Sanctuary panel. Tap **Read**. The app reads name/level twice, opens Add Tag, reads coordinates twice, closes it, and rereads the same Sanctuary. Any mismatch stops the operation and stores no result. A successful round trip is required once per capture session before automatic scanning. If that player's name/level matches your current search, a result is saved; otherwise the round trip only validates the controls.
8. Close the panel, return to the map, and tap **Scan**. The app recognizes matching labels, opens each candidate, verifies its level and Add Tag coordinates, and sweeps through overlapping map views in a serpentine pattern. Do not manually move or zoom the map while it runs.
9. **Stop** cancels scanning. **Exit** or the notification's Stop ends screen capture. Return to the locator and refresh results. Tap a result to compare the saved Sanctuary and Add Tag screenshots; mark it visually confirmed only if every value is correct.

## What is implemented

- Real Android MediaProjection screen capture in a foreground service with visible stop controls.
- Bundled on-device ML Kit Latin OCR (no OCR model download needed at runtime).
- Case-insensitive substring matching after removing bracketed alliance tags. Tags alone never match. Optional exact Sanctuary-level filter; VIP is not used.
- Accessibility gestures restricted to the foreground game package, using user-calibrated positions. No fixed game UI coordinates, reverse-engineered endpoints, credentials, network interception, chat, purchases or attack actions.
- Guard checks for the expected map, Sanctuary and Add Tag screens; cancellation on unexpected screens, loss of game focus, rotation, failed gestures, ambiguous OCR, mismatched player, inconsistent coordinates or wrong server.
- Coordinates parsed only from the calibrated Add Tag region, with explicit server/X/Y. No inferred world coordinates and no `O → 0` substitutions.
- Double-read name/level and coordinates, then verification of the same player after returning from Add Tag. Saved evidence and observation timestamps; duplicate key is server + X + Y + normalized player name. Same name at different positions remains distinct.
- Bounded scan progress, half-viewport overlapping swipes, repeated-view detection, cancellation, local persistence, JSON/log export, local deletion and manual evidence review.

## Scope and accuracy limits

This is a **bounded visible-label map scanner**, not a publisher database query or a verified full-server locator. It searches labels that OCR can read in each visited viewport. Hidden labels, unreadable text, name wrapping, some decorative fonts, non-Latin scripts, nonuniform building/name offsets, changing game UI, zoom changes, shields, animations, and map loading can prevent detection or cause a stop. Names not displayed until a Sanctuary is tapped cannot be discovered by this label-driven approach; use Read for an already opened Sanctuary. This specific game's label visibility and panel return behavior still require testing on the actual phone. Calibration is intentionally mandatory instead of guessing controls from an unseen UI.

Rows/columns count *screen views*, not world-coordinate tiles. Swipes and image fingerprints do not prove geographic coverage. Repeated-view detection is approximate and can stop early or miss repetition on animated terrain. The app never claims that a whole server was searched, never returns an unconditional “no match,” and continues to label coverage incomplete even when the planned sweep finishes. A moving player can be missed between observations. A saved observation is a location read at that time, not a promise the player remains there. OCR can be consistently wrong across multiple frames: inspect the evidence before relying on it. Calibration guards reduce unintended taps but do not prove screen identity in every UI state.

The MediaProjection capture path must be tested on the user's phone. If the game displays black/secure content, this build does not bypass that protection. Full-display capture is requested because screen-to-gesture alignment is necessary; Android permission consent cannot be automated. Android 8+ is supported by the app, but emulator tests cover the Android version in the workflow, not every device. Battery/thermal throttling, screen locks, OS process termination, and other apps can interrupt a scan. Interrupted scans are not resumed or called complete.

No documented supported game player-location API was found during initial research. User described this as an authorized moderator tool; the app does not independently establish publisher authorization. Permission to automate is a deployment matter, separate from build success.

## Verification

GitHub Actions runs `testDebugUnitTest`, `lintDebug`, builds both APKs, then runs real ML Kit and database integration tests in an Android emulator. Fixtures are rendered exclusively in test code. Tests cover all four requested matching forms, alliance-only false positives, levels, ambiguous/missing coordinates, wrong-server rejection, cross-player pairing, deduplication, route bounds and incomplete-coverage status. Android tests verify OCR on rendered fixtures and evidence persistence. **These are not live Last Asylum tests.** No live GoneAway location or full-server search has been verified.

Every successful release includes the APK and SHA-256 checksum. Actions artifacts include unit, lint and instrumentation reports. Never equate APK compilation, synthetic OCR tests, or a successful calibrated round trip with exhaustive map coverage.

## Data

Observations, screenshots and scan logs stay in private app storage. Export is a user-selected local document; no backend is configured. The application does not upload screenshot contents or game credentials. The bundled ML Kit dependency can include its own SDK diagnostics. Evidence screenshots include whatever the captured game screen shows. Delete observations from the main screen or uninstall the app to remove app-local data.
