# Vehicle Tracker Watch (Android prototype)

Kotlin/Compose app that records nearby BLE advertisements, allows marking observed signal identifiers as recognized, and posts a high-priority notification after three consecutive 20-second scan windows show a previously untrusted Bluetooth identifier.

**Important limitations:** This version observes *all* BLE advertisers and does **not** identify AirTags or other item trackers reliably. Rotating identifiers can invalidate the trusted list. It cannot determine whether a signal is attached to your car, whether it is moving with you, or whether your car is being targeted. Count changes alone are not treated as proof. Stationary/movement confirmation is not yet implemented. Android's built-in unknown tracker alerts should remain enabled. Foreground service scanning is subject to OS/device restrictions and battery settings.

## Build
Open the repository in Android Studio, sync Gradle, then Build > Build APK(s) or use `gradle assembleDebug`. Install the debug APK via Android Studio or adb. Grant Bluetooth scanning and notifications when prompted, then tap Start monitoring. A persistent foreground-service notification appears when monitoring runs. Tap Refresh sightings to see captured devices.

No network access is requested. Sightings are held on the device in preferences, capped at 250 signal IDs.

## Next iterations
- Motion-aware parked verification (activity recognition / accelerometer).
- Tracker-protocol classification and improved association across rotating IDs, with proper real-device testing.
- Separate baseline familiar devices from persistent suspects and notification throttling.
- Automated tests, privacy controls, and clearer detection-health diagnostics.
