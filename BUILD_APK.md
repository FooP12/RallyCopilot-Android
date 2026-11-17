# Build APK

## Android Studio
Open the `RallyCopilot` project folder, wait for Gradle Sync, then run:
**Build → Build Bundle(s) / APK(s) → Build APK(s)**.

The debug APK is created at:
`app/build/outputs/apk/debug/app-debug.apk`

Use JDK 17 and Android SDK Platform 35.

## GitHub Actions
The project includes `.github/workflows/android-apk.yml`.
Push the project to GitHub, then open **Actions → Build Android APK → Run workflow**.
After the workflow succeeds, download the `RallyCopilot-debug-apk` artifact.

This archive contains source code and build configuration, not a precompiled APK.
