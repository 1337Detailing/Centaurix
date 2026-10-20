# Centaurix — permanent APK signing

The app id is `fr.centaurix.app`. An Android APK updates an installed app only when its **applicationId stays the same**, its **versionCode increases**, and its **signing certificate stays identical**.

## 1. Create and back up the private key

Generate this key on a computer you control (requires Java JDK 17 or newer):

```sh
keytool -genkeypair -v -storetype PKCS12 \
  -keystore centaurix-release.p12 \
  -alias centaurix \
  -keyalg RSA -keysize 3072 -validity 10000
```

Pick a strong password. In PKCS#12 format the key and store password should be the same. This is your **permanent** signing identity. Keep at least two secure, offline backups of `centaurix-release.p12` and its password. **Do not commit or share it. Do not send it through chat.**

If an already-installed Centaurix version was signed with another key (e.g. a CI debug key), Android will refuse an in-place update. A one-time uninstall/install is required to migrate to this new signing identity. Back up important local information first.

## 2. Store the key in GitHub Secrets

On Linux or Termux, encode the keystore into a single line:

```sh
base64 -w 0 centaurix-release.p12 > signing-base64.txt
```

On macOS:

```sh
base64 < centaurix-release.p12 | tr -d '\\n' > signing-base64.txt
```

On Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("centaurix-release.p12")) | Set-Content -NoNewline signing-base64.txt
```

In the repository, go to **Settings → Secrets and variables → Actions → New repository secret**. Add exactly these two **repository secrets**:

| Secret | Value |
| --- | --- |
| `CENTAURIX_SIGNING_KEYSTORE_BASE64` | The complete, single-line contents of `signing-base64.txt` |
| `CENTAURIX_SIGNING_PASSWORD` | Your permanent keystore password |

Delete `signing-base64.txt` after adding it. Protect the `.p12` file and password in separate encrypted backups.

## 3. Build and publish

Run the `Centaurix Android APK` workflow from **Actions → Run workflow** (branch `main`). Once both secrets are present, it builds a signed *release* APK, verifies its signing certificate and uploads it as an Actions artifact. Without secrets, branch builds only run tests and **publish no distributable APK**.

To publish a public GitHub Release, tag the current commit with the version in `app/build.gradle.kts`:

```sh
git tag v1.0.0-beta6
git push origin v1.0.0-beta6
```

The workflow checks that the tag matches `versionName`, and then uploads `Centaurix-1.0.0-beta6.apk` directly to the GitHub Release.

For every later version, **increase versionCode**, adjust `versionName`, and use the **same private key** and password. Do not rotate or delete the signing key.

## Verify on your machine

```sh
keytool -list -v -storetype PKCS12 -keystore centaurix-release.p12 -alias centaurix
```

The SHA-256 certificate fingerprint must remain the same in every new release. You can also inspect the built APK using Android SDK's `apksigner verify --print-certs app-release.apk`.

If you only have Android, Java's `keytool` is also available through Termux with `pkg install openjdk-17`. Keep the `.p12` secure and backed up outside Termux before changing devices.
