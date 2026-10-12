# NIKNOLIG

Android app scaffold with an initial, permission-gated BLE discovery and
connection flow for Elektro-Engel.

## BLE scope and safety

- Scanning requires runtime BLE permissions on Android 12+ and location
  permission on Android 8–11. The app explains the reason before requesting a
  permission rationale, never starts scanning after a denial, stops scanning
  when the screen leaves the foreground, and ends scans after 15 seconds.
- A GATT connection starts only after the user selects a discovered device.
  The app can disconnect it, but does not discover services, read/write
  characteristics, send commands, or remember devices.
- A BLE connection is not device authentication. SecurityChain P0, trust
  verification, and security approvals are not implemented; no approval or
  device access is granted automatically.
- The Elektro-Engel button opens the central assistant information surface.

## Build and tests

The project uses JDK 17, Gradle Wrapper 8.13, Android Gradle Plugin 8.11.1,
Kotlin 2.2.21, and compile SDK 35.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

The BLE state manager has JVM unit tests using a fake transport. The Android
Bluetooth APIs and physical-device behavior are not covered by those tests.
The GitHub Actions workflow runs the unit tests and assembles the debug APK,
then publishes `NIKNOLIG-debug-apk` as a 14-day downloadable workflow artifact.

Validation on 2026-10-12: this environment could not resolve the Android Gradle
Plugin 8.11.1 from configured repositories, so the current source and tests
could not be built locally. The earlier successful GitHub build at commit
`e291d4d` predates the BLE changes and did not upload an APK artifact. The
updated run is blocked with `action_required` before creating any jobs; no
current APK artifact exists until repository workflow approval allows that run
to execute ([run #50](https://github.com/nikolausnold/NIKNOLIG/actions/runs/38233687841)).
