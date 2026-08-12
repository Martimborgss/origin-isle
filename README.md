# Origin Isle — a notification caster for OriginOS's OriginIsland

Origin Isle mirrors your notifications onto the **OriginOS SuperX / OriginIsland** pill. 
Stock OriginOS only ever surfaces that pill fora short allow-list of Chinese apps; 
Origin Isle re-casts *any* notification through the same
protocol, so downloads, navigation, calls, media playback, payments and live football scores from
ordinary (including European/international) apps show up on the island too.

This is a hobby  project, built by working out the SuperX protocol. 

## Features

- **Universal notification casting** — downloads, navigation turn-by-turn, calls, progress bars and
  (optionally) plain chat messages, each mapped onto the SuperX card template that fits it best.
- **Per-app allow/deny list** (Apps tab) and a message-vs-call granularity switch (e.g. "WhatsApp
  calls yes, texts no").
- **Cast log + listener health** (Log tab) — every notification the listener saw, whether it was cast
  or skipped and why, plus live connection status and a manual reconnect.
- **Tapping a card opens the source app's notification**.
- **Live football scores** with real club crests (fetched from TheSportsDB), an adaptive pill layout
  that shows a score under each crest when the island has room and a compact combined score when
  it's crowded, and taps that open the source score app.
- **Apple-Pay-style payment cards** — a two-phase "Processing… → Paid ✓" animation (spinner, then a
  green success tick and island glow) for Google Wallet/Pay, Revolut, PayPal, N26, Monzo, Starling,
  and any other app whose notification pairs a payment verb with an amount.
- **Media session casting** with prev/play-pause/next controls wired back to the source app.
- **Calls show the caller's/app's own icon**, not a generic phone glyph.
- **"Recast all"** — sweeps every notification currently on the phone through the same cast pipeline
  in one tap.
- **Runs with no status-bar icon.** OriginOS force-shows an icon for any foreground service; Origin
  Isle instead anchors its background process with an inert `AccessibilityService` 
  (reads nothing — see [`KeepAliveAccessibilityService`](app/src/main/java/com/originisle/android/service/KeepAliveAccessibilityService.kt)),
  the same trick gesture-navigation apps use.

## ⚠️ Read this first: the package name is load-bearing

`applicationId = "com.autonavi.minimap"` in [`app/build.gradle.kts`](app/build.gradle.kts) is
**deliberate, not a mistake**. OriginOS only grants the hidden `setSuperXInfosSceneList` API to a
short whitelist of packages, and this app's `applicationId` spoofs AMap's (高德地图 / AutoNavi) real
package id to land on that whitelist. **If you change it, the island will stop working** —
`grantScenes()` will start logging `setSuperXInfosSceneList unavailable`.

Two consequences:

1. **It cannot be installed alongside the real AMap app** (same applicationId → package conflict).
2. **This can never ship on the Play Store** or be distributed as if it were AMap. It's a personal sideload
  install it on your own device, understand what you're installing.

The Kotlin package / `R` namespace is a separate, harmless identifier: `com.originisle.android`. It's
just where the code lives; it isn't checked by OriginOS.

## Build & run

Requires Android Studio (AGP 8.9+) and the `android-36` SDK platform.

1. **Open the folder in Android Studio** and let it sync.
2. **Run** onto a vivo/iQOO OriginOS device. (It will build and install fine on any Android 14+
   device, but the island itself only renders on OriginOS.)
3. On first launch, the onboarding screen walks you through the permissions. Notification access is
   the only mandatory one to continue; grant the rest for the best experience (see below).
4. Try the sample cards in the **Cast** tab to confirm the island is working before relying on real
   notifications.

### Recommended setup (all optional, but each fixes a real OriginOS quirk)

- **Battery → unrestricted**, so OriginOS doesn't kill the background caster to save power.
- **Accessibility → "Origin Isle keep-alive"**, so the app runs with no status-bar icon.
- **OriginOS auto-start allow-list** (Settings → Battery → Auto-start), or the caster gets killed
  when the screen turns off.

### Signed release build

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

### Verify a release is genuine

This app reads your notifications, so only ever install a build you can prove came from the
maintainer. Every official APK is signed with one private key and published on this repo's Releases
page with its SHA-256 checksum. To check a downloaded APK:

```
# 1. checksum matches the one published on the release
shasum -a 256 origin-isle-<version>.apk

# 2. it was signed by the maintainer's key — compare against the fingerprint below
apksigner verify --print-certs origin-isle-<version>.apk
```

Official signing certificate SHA-256 (fill in once, from `scripts/release.sh` output):

```
SHA-256: <run ./scripts/release.sh once and paste the printed fingerprint here>
```

A build whose checksum or certificate fingerprint doesn't match is **not** an official build — do not
install it. Never install an "Origin Isle" APK from anywhere other than this repo's Releases page.

## License

See [LICENSE](LICENSE) — a **source-available, no-redistribution** license
