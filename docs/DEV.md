# Developer guide

This doc covers building Origin Isle from source, the package-name spoof it relies on, and how to
cut a signed release. For what the app does and how to install it, see the [README](../README.md).

## ⚠️ Read this first: the package name is load-bearing

`applicationId = "com.autonavi.minimap"` in [`app/build.gradle.kts`](../app/build.gradle.kts) is
**deliberate, not a mistake**. OriginOS only grants the hidden `setSuperXInfosSceneList` API to a
short whitelist of packages, and this app's `applicationId` spoofs AMap's (高德地图 / AutoNavi) real
package id to land on that whitelist. **If you change it, the island will stop working** —
`grantScenes()` will start logging `setSuperXInfosSceneList unavailable`.

Two consequences:

1. **It cannot be installed alongside the real AMap app** (same applicationId → package conflict).
2. **This can never ship on the Play Store** or be distributed as if it were AMap. It's a personal
   sideload — install it on your own device, understand what you're installing.

The Kotlin package / `R` namespace is a separate, harmless identifier: `com.originisle.android`. It's
just where the code lives; it isn't checked by OriginOS.

## Build & run

Requires Android Studio (AGP 8.9+) and the `android-36` SDK platform.

1. **Open the folder in Android Studio** and let it sync.
2. **Run** onto a vivo/iQOO OriginOS device. (It will build and install fine on any Android 14+
   device, but the island itself only renders on OriginOS.)
3. On first launch, the onboarding screen walks you through the permissions. Notification access is
   the only mandatory one to continue; grant the rest for the best experience (see the README).
4. Try the sample cards in the **Cast** tab to confirm the island is working before relying on real
   notifications.

### Recommended device setup (all optional, but each fixes a real OriginOS quirk)

- **Battery → unrestricted**, so OriginOS doesn't kill the background caster to save power.
- **Accessibility → "Origin Isle keep-alive"**, so the app runs with no status-bar icon.
- **OriginOS auto-start allow-list** (Settings → Battery → Auto-start), or the caster gets killed
  when the screen turns off.

## Signed release build

The debug build works fine for personal use (SuperX doesn't care about the signing key). To build a
release APK you can update over time:

```
keytool -genkeypair -v -keystore originisle-release.jks -alias originisle -keyalg RSA -keysize 2048 -validity 10000
```

Create `keystore.properties` at the repo root (gitignored — never commit it):

```
storeFile=../originisle-release.jks
storePassword=...
keyAlias=originisle
keyPassword=...
```

A `keystore.properties.template` is committed as a reference — copy it to `keystore.properties` and
fill in the real values on the machine that holds your key. `keystore.properties`, `*.jks` and
`*.keystore` are gitignored — never commit them, and keep a safe backup: losing the keystore or its
passwords means you can never publish a signature-compatible update again.

Then run the release helper from the repo root:

```
./scripts/release.sh
```

It builds `:app:assembleRelease`, verifies the signature, and writes the signed APK plus its SHA-256
checksum to `dist/`, printing the signing certificate's fingerprint. Upload **both** the APK and the
`.sha256` file to the GitHub Release. (Plain `./gradlew :app:assembleRelease` still works; without
`keystore.properties` it builds *unsigned*, which is why the script refuses to run without it.)

> Switching between a debug-signed and release-signed install requires uninstalling first (Android
> rejects a signature mismatch), which also drops notification access and the keep-alive accessibility
> grant — re-grant them after.

## Verify a release is genuine

This app reads your notifications, so only ever install a build you can prove came from the
maintainer. Every official APK is signed with one private key and published on this repo's Releases
page with its SHA-256 checksum. To check a downloaded APK:

```
# 1. checksum matches the one published on the release
shasum -a 256 origin-isle-<version>.apk

# 2. it was signed by the maintainer's key — compare against the fingerprint below
apksigner verify --print-certs origin-isle-<version>.apk
```

Official signing certificate SHA-256:

```
5D:2D:FA:7E:F6:A9:6B:95:4A:C2:43:61:A3:30:BA:9B:2C:EA:45:18:C5:B8:63:58:C9:F4:FF:B1:1D:79:E6:00
```

A build whose checksum or certificate fingerprint doesn't match is **not** an official build — do not
install it. Never install an "Origin Isle" APK from anywhere other than this repo's Releases page.
