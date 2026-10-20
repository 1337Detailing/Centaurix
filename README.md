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

## Limitations

The BT200 provides cumulative puff counts, not timestamped events. Counts observed across offline day boundaries cannot be dated precisely. Firmware write commands require on-device verification.

Independent project. Not affiliated with Lost Vape.


## Mises à jour intégrées

Les APK sont compilés par GitHub Actions. Dans la section « Plus », le bouton
« Rechercher une mise à jour » vérifie les versions publiées sur GitHub Releases.
Une nouvelle version est annoncée en plein écran dans l'application. L'APK est
vérifié avec SHA-256 avant de passer à l'installateur Android. Une connexion
Internet et une signature de build compatible sont nécessaires.
