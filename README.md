# Rally Copilot — Android prototype v0.5.0

This is the Android Studio source project based on the supplied v0.4 MapLibre/OSRM archive.
It is a prototype for personal testing, not a production navigation app.

## Included
- Opens directly on the map screen; no custom splash screen is implemented.
- MapLibre Native Android map with a dark CARTO basemap.
- Android location permission and live location updates while the app is active.
- Long-press on the map to select a destination.
- OSRM route preview with route line, distance, estimated duration and one spoken first maneuver.
- Selectable Android Text-to-Speech voices saved locally.
- GitHub Actions workflow to build a debug APK.

## Important limitations
This version does NOT yet include address search/geocoding, saved Home/Work favorites, multiple intermediate stops, continuous turn-by-turn progress, automatic rerouting, weather data, wake-word listening ("Pilot"), the four fully implemented navigator personalities, RC rewards, shop, or leaderboard. These are planned features, not working features in this archive.

The OSRM public demo endpoint and CARTO style are used for prototype/personal testing. The public OSRM demo is best-effort and must not be treated as a production routing service. For public distribution, use a routing provider or self-hosted OSRM instance whose terms and capacity fit the app. Confirm basemap provider terms before wider distribution.

## Build in Android Studio
1. Install Android Studio and JDK 17.
2. Extract this archive.
3. In Android Studio choose **Open** and select the `RallyCopilot` folder (the folder containing `settings.gradle.kts`).
4. Wait for Gradle Sync and accept installation of Android SDK 35 if prompted.
5. Choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
6. APK location: `app/build/outputs/apk/debug/app-debug.apk`.

## Build through GitHub Actions
1. Create a GitHub repository and upload the contents of this project.
2. Open the repository's **Actions** tab.
3. Run **Build Android APK** using **Run workflow** (or push to `main`/`master`).
4. Download the `RallyCopilot-debug-apk` artifact from the completed workflow.

No Yandex MapKit key is required. MapLibre and OSRM are used in this prototype.
