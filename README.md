# Centaurix

Android companion for the **Lost Vape Centaurus BT200**.

## Features

- Bluetooth LE connection and live device status
- Power, mode and 8-point VPC profile controls with readback verification
- Automatic daily puff tracking and local history
- Device themes, previews and custom wallpapers
- Languages: system, French, English, German, Russian and Polish
- Appearance: system, light, dark, AMOLED and accent colors

## Build

JDK 17 and Android SDK 35 required.

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

Download debug builds from GitHub Actions **Artifacts**.

## Limitations

The BT200 provides cumulative puff counts, not timestamped events. Counts observed across offline day boundaries cannot be dated precisely. Firmware write commands require on-device verification.

Independent project. Not affiliated with Lost Vape.
