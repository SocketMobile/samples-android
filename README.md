# Socket Mobile CaptureSDK — Android Samples

These sample apps demonstrate how to integrate [Socket Mobile CaptureSDK](https://www.socketmobile.com/developers) into an Android app. Each sample is a standalone module that supports Socket Mobile barcode and NFC scanners, as well as SocketCam (camera-based scanning).

CaptureSDK communicates with scanners through the [Socket Mobile Companion](https://play.google.com/store/apps/details?id=com.socketmobile.companion) app, which hosts the Capture Service on the Android device.

> Make sure the Companion app is installed on your Android device before running any of the samples.


> [!WARNING]
> ## Please Update to CaptureSDK 2.1 Before December 1, 2026
> Versions prior to 2.1.x will be removed from our repositories on December 1, 2026 for all platforms.

> [!WARNING]                                                                                                                                                                                                   
> As we're transitioning our scanners to Bluetooth Low Energy and improving our CaptureSDKs, updating to the latest version 2.1 is required to stay compatible and benefit from all our latest products and    
features.


- [Samples](#samples)
- [Prerequisites](#prerequisites)
- [Getting started](#getting-started)
- [The samples in detail](#the-samples-in-detail)
- [Which sample should I start with?](#which-sample-should-i-start-with)
- [Device compatibility](#device-compatibility)
- [Project layout](#project-layout)
- [Resources](#resources)
- [License](#license)

## Samples

| Sample | SDK flavor | What it shows |
|---|---|---|
| **[hellocapture](hellocapture/)** | `capture-android` (EventBus) | Scanning with Socket Mobile scanners and **SocketCam** (camera), including the SocketCam custom view finder. |
| **[hellocapture-ble-android](hellocapture-ble-android/)** | `capture-android` (EventBus) | **Bluetooth LE** scanners (S550, D600, S370, …): discovery, connection, trigger, and theme/LED control. |
| **[hellocapture-plainjava](hellocapture-plainjava/)** | `capture` (plain Java) | The plain Java SDK used directly — no EventBus, programmatic app key, explicit device open. |

## Prerequisites

- **Android Studio** (latest) with **JDK 17**
- A physical Android device (**min SDK 22** / Android 5.1), USB debugging enabled
- The **[Socket Mobile Companion](https://play.google.com/store/apps/details?id=com.socketmobile.companion)** app installed on the device
- A Socket Mobile scanner — or **SocketCam** (camera), which needs no extra hardware
- For your *own* app: a developer ID and app key from the **[Socket Mobile developer portal](https://www.socketmobile.com/developers)**

> These samples ship with a demo app key bound to the application id `com.example.socketmobile.android.hellocapture`, so they run out of the box. To ship your own app you must register and use your own developer ID and app key.

## Getting started

1. Install **Companion** on the device, open it once, and grant the necessary permissions.
2. Clone this repo and open it in Android Studio (or build from the command line).
3. Select a module's run configuration and **Run** it on the device — or from the CLI:
   ```
   ./gradlew :hellocapture:installDebug
   ```
4. Connect a scanner in the sample:
   - **Bluetooth / NFC scanner:** power it on and pair it in Companion; it then connects automatically.
   - **SocketCam (camera):** enable it in the sample and grant the camera permission when prompted.
5. Scan — decoded data appears in the app.

## The samples in detail

### hellocapture

Barcode/NFC and **SocketCam** scanning through the `capture-android` wrapper. Events are delivered over greenrobot EventBus, and the app key is read from the manifest.

- **Launch screen** lets you choose a SocketCam experience: *default view finder* or *custom view finder*.
- **Main screen:** toggle **Start Camera Scanning** (SocketCam), watch connected devices appear in a list with per-device status, trigger scans, and view decoded data.
- SocketCam (camera) devices expose **Trigger** / **Trigger Continuous**; physical scanners are triggered with their hardware button.

```
./gradlew :hellocapture:installDebug
```

### hellocapture-ble-android

**Bluetooth Low Energy** scanners (S550, D600, S370, …) through the `capture-android` wrapper.

- **Add Device** starts BLE discovery; discovered devices populate the **Select Device** dropdown, and selecting one connects it.
- Connected devices appear in a list with per-device status and **Trigger / Theme / Disconnect / Remove** actions.

```
./gradlew :hellocapture-ble-android:installDebug
```

### hellocapture-plainjava

The plain Java SDK (`com.socketmobile:capture`) used directly — the lowest-level integration, without the `capture-android` wrapper.

- **No EventBus:** every notification arrives on a single `CaptureClient.Listener`, on a background thread (UI updates go through `runOnUiThread`).
- **Programmatic app key** via `AppKey`, instead of manifest meta-data.
- The app **starts the Companion Capture service itself** and **opens AVAILABLE devices explicitly** — both of which the `capture-android` wrapper does for you.
- Reuses hellocapture's app key and `applicationId`, so **installing it replaces hellocapture** on the device.

```
./gradlew :hellocapture-plainjava:installDebug
```

## Which sample should I start with?

- **New to CaptureSDK / want the easiest integration** → **hellocapture** (or **hellocapture-ble-android** for BLE devices). The `capture-android` wrapper handles EventBus registration, service startup, and device opening for you.
- **Need a custom event pipeline without the Android wrapper** → **hellocapture-plainjava**.

## Device compatibility

Which scanners work with which CaptureSDK version, and the release history, are in the **[CHANGELOG](CHANGELOG.md)**.

## Project layout

```
samples-android/
├── hellocapture/              # SocketCam + scanners (capture-android)
├── hellocapture-ble-android/  # Bluetooth LE scanners (capture-android)
├── hellocapture-plainjava/    # Plain Java SDK (capture)
├── gradle/libs.versions.toml  # CaptureSDK version (captureSdk) + dependencies
└── settings.gradle            # the three modules
```

All three samples are modules of this single Gradle project. The CaptureSDK version is defined once in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) (`captureSdk`, currently **2.1.2**).

```
./gradlew assembleDebug        # build all three modules
```

## Resources

- [CaptureSDK documentation](https://docs.socketmobile.com/capture/java/en/latest/)
- [Socket Mobile developer portal](https://www.socketmobile.com/developers)
- [Companion app (Google Play)](https://play.google.com/store/apps/details?id=com.socketmobile.companion)

## License

See [LICENSE](LICENSE).

The Android robot is reproduced or modified from work created and shared by Google and used according to terms described in the [Creative Commons 3.0 Attribution License](https://creativecommons.org/licenses/by/3.0/).
