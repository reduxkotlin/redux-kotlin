# TaskFlow — iOS host

This directory holds the SwiftUI host that embeds the shared Compose
Multiplatform UI (the `TaskFlowApp` framework produced by
`:examples:taskflow:composeApp`). Only the Swift sources are checked in; the
`.xcodeproj` is a **manual follow-up** (do not auto-generate `project.pbxproj`).

## Swift sources

- `iosApp/iOSApp.swift` — `@main` SwiftUI `App` entry point.
- `iosApp/ContentView.swift` — `ComposeView: UIViewControllerRepresentable`
  wrapping `MainViewControllerKt.MainViewController()`, rendered edge-to-edge
  via `.ignoresSafeArea(.all)`.

## Wiring the Xcode project (manual)

1. Create an iOS App target in Xcode pointing at the `iosApp/` Swift sources.
2. Add a **Run Script** build phase **before** "Compile Sources":

   ```
   ./gradlew :examples:taskflow:composeApp:embedAndSignAppleFrameworkForXcode
   ```

   `embedAndSignAppleFrameworkForXcode` links the `TaskFlowApp` framework
   **and auto-syncs the compose-resources bundle** — do **not** add a
   hand-rolled resource copy script.
3. In the script phase's input/output settings, turn **User Script Sandboxing
   OFF** (`ENABLE_USER_SCRIPT_SANDBOXING = NO`) so the Gradle task can write the
   framework into the build dir.
4. Add the framework search path / link `TaskFlowApp.framework` as produced by
   the Gradle task.

## `Info.plist` keys

- `CFBundleIdentifier` = `org.reduxkotlin.sample.taskflow` (bundle id).
- `UILaunchScreen` (empty dict is fine — enables the modern launch storyboard).
- `UISupportedInterfaceOrientations` — portrait + landscape as desired.
- `CADisableMinimumFrameDurationOnPhone` = `true` (lets Compose drive ProMotion
  high-refresh rendering).

## CI / Gradle gating

Gradle gates only the **framework link**
(`linkDebugFrameworkIosSimulatorArm64`) plus an optional `swiftc` smoke compile.
The full Xcode app build is Mac-only and run manually.
