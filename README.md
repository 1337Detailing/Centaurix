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

Once the two permanent signing secrets are configured, download signed release APKs from GitHub Actions **Artifacts** or GitHub Releases. See [SIGNING.md](SIGNING.md) for setup and key backup instructions.

## Limitations

The BT200 provides cumulative puff counts, not timestamped events. Counts observed across offline day boundaries cannot be dated precisely. Firmware write commands require on-device verification.

Independent project. Not affiliated with Lost Vape.

## Website

The static landing page is in [`docs/`](docs/) and can be published with GitHub Pages (branch `main`, folder `/docs`). Setup, images, and release automation: [docs/README.md](docs/README.md).
