# Centaurix

Independent, offline-first Android companion for the Lost Vape Centaurus BT200. Built in Kotlin + Jetpack Compose + Material 3. Visually inspired by Cloudix: dark glasslike surfaces, soft gold accents, modular cards and five-tab navigation.

## Available in 1.0 beta

- BLE scan, stable GATT connection, notifications and framing, MTU negotiation and read polling.
- Confirmed hardware path: service FFA0, char FFA1/FFA3, status command 01, reply 81 / CRC in big endian.
- Live dashboard for dual batteries, active watts, active mode/profile and puffs.
- Read and edit 3 device profiles: Power / Turbo / temperature-control SS316, TI, NI / Voltage / 8-step VPC. Save with confirmation, official reply acknowledgements and refresh. Validate within official app's ranges; no ability to fire remotely.
- Reset a profile only after confirmation.
- Read built-in theme metadata, switch 5 manufacturer-provided theme variants. Original-app protocol 08, reply 88.
- Local image chooser, crop/convert to RGB565 at 240×320 and 90×120. Chunked BLE transfer (30/31/32, B0/B1/B2 acks), progress indicator and MTU check. **Experimental: requires a real hardware transfer test; don't use during critical use.**
- Offline local history, diagnostics export, reconnect, no account or online telemetry.

## Test status

The status read has been confirmed on an actual BT200 by the user (92%/97% batteries, Power mode, 5 W). Other write commands were recovered from the official Centaurus 1.0.9 APK and passed protocol unit tests. The features are **not yet claimed hardware-tested** until user confirmation on a box. Android CI runs unit tests and creates a debug APK.

## Safety

Centaurix never calls any firing command and never modifies firmware/safety limits. Power/profile changes require confirmation and input validation. Do not use beta builds for device firmware updates. Keep the official app available until all features are verified.

Not endorsed by, affiliated with or supported by Lost Vape.
