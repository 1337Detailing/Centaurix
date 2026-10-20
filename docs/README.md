# Centaurix website

Static, mobile-first landing page for the Lost Vape Centaurus BT200 Android companion app.

## Files

```text
docs/
├── index.html
├── styles.css
├── app.js
├── .nojekyll
├── README.md
└── assets/
    ├── logo-centaurix.webp
    ├── favicon.png
    ├── icon-180.png
    ├── hero-bt200.webp
    ├── hero-bt200@2x.webp
    ├── og-preview.webp
    ├── screen-home.webp
    ├── screen-puffs.webp
    ├── screen-vpc.webp
    └── screen-themes.webp
```

## Deploy to GitHub Pages

The website is published automatically from `docs/` by `.github/workflows/pages.yml` when its files change. GitHub Pages must use **GitHub Actions** as the build source (already configured for this repository).

Live website: **https://1337detailing.github.io/Centaurix/**

The landing page, download messages, SEO metadata and generated device mockups are in English. Assets use relative URLs and also work from a local static server.

## GitHub downloads

Edit `GITHUB.owner` and `GITHUB.repo` at the top of `docs/app.js` if the repository changes. The page requests public GitHub Releases and workflow Artifacts. It shows a direct `.apk` link only for a Release asset; workflow artifacts lead to the run page for its ZIP download (sign-in may be required). Unavailable data falls back to GitHub Actions.

The Android workflow `.github/workflows/build.yml` uploads an Artifact for builds on `main`, and creates a Release with the APK when a tag such as `v1.0.0-beta5` is pushed:

```sh
git tag v1.0.0-beta5
git push origin v1.0.0-beta5
```

**Important:** the example Android build is debug-signed. Use an encrypted, stable release signing key for actual production distribution. Keep the tag in sync with `versionName` in `app/build.gradle.kts`.

## Image replacements

All bundled imagery is illustrative and the UI preview values are fictional. Replace the images in `docs/assets` with your own high-quality BT200 renders and actual in-app screenshots. Keep the same filenames or update their references in `index.html`.

Suggested prompts:

- **Hero (3D product render, 16:9, transparent/dark background):** "Studio-quality 3D product render of a black Lost Vape Centaurus BT200 vape mod (accurate industrial proportions, crisp side controls and screen) standing next to a modern Android smartphone running a dark black-and-gold Centaurix dashboard, cinematic softbox highlights, warm orange and coral rim lighting, black obsidian plinth, slight perspective tilt, photorealistic materials, no smoke, no people, ample negative space, 4K resolution."
- **Dashboard screenshot:** "Straight-on Android phone product screenshot, sophisticated Material Design 3 black UI, warm gold accent, two batteries, 80 W readout, BT200 Bluetooth connected status, polished dark cards, realistic typographic spacing, no third-party brands, ultra sharp 1440×3120."
- **Puff dashboard screenshot:** "Straight-on Android analytics screen for Centaurix, black background with gold bar chart, daily/weekly/monthly puff counts, 7-day trend and calendar, clean Material You components, crisp 1440×3120."
- **VPC controls screenshot:** "Straight-on Android screen of an eight-point adjustable power curve for a BT200 mod, warm gold plotting line over dark graphite Material 3 UI, rounded cards and save button, polished UI, 1440×3120."

Recommended source sizes: hero 1280×1180 and 640×590; screenshots 720×1560 or larger; Open Graph 1200×630. Export as WebP (quality 75–85) with optimized fallbacks.
