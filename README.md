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
- **Accessibility → "Origin Isle keep-alive"**, so the app runs with no status-bar icon (same
  mechanism as a gesture-navigation app).
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

Then `./gradlew :app:assembleRelease`. Without `keystore.properties` present, `assembleRelease` still
builds — just unsigned — so cloning this repo doesn't require anyone's private key.

> Switching between a debug-signed and release-signed install requires uninstalling first (Android
> rejects a signature mismatch), which also drops notification access and the keep-alive accessibility
> grant — re-grant them after.

## How a card reaches the island

`OriginIslandBuilder.grantScenes()` reflectively calls
`NotificationManager.setSuperXInfosSceneList(...)` to whitelist this package for a set of scenes
(only `NAVIGATION` is reliably granted on the devices this was tested on). Then the app posts an
ordinary `Notification` under the tag `"VIVO_SUPERX_TAG"` whose `extras` bundle is built by
`OriginIslandBuilder.buildBundle(...)` — hundreds of `notification.superx.*` / `island.superx.*` keys
describing the card body, the collapsed pill, and the tiny status capsule. OriginOS recognises the
tag + keys and renders the island instead of (or alongside) a normal shade notification. A card is
HIGH-importance-channel, non-empty-content, `forceShow`-flagged and carries a content intent — miss
any of those and vivo silently drops the post with no error. See
[`docs/PROTOCOL.md`](docs/PROTOCOL.md) for the distilled acceptance rules, and
[`OriginIslandConstants.kt`](app/src/main/java/com/originisle/android/island/OriginIslandConstants.kt)
for the full key dictionary.

## Project layout

```
com.originisle.android/
  MainActivity.kt, OriginIsleApp.kt      entry points
  island/                                the SuperX protocol engine
    OriginIslandConstants.kt               the full bundle-key dictionary (protocol reference)
    OriginIslandBuilder.kt                 assembles the SuperX extras Bundle + grantScenes()
    PlaygroundService.kt                   posts/updates/ends cards; owns the foreground-service fallback
  service/                               background plumbing
    NotificationCastListener.kt             decides cast-or-skip for every notification
    KeepAliveAccessibilityService.kt        anchors the process with no status-bar icon
    MediaControlReceiver.kt                 routes media-button taps back to the source app
    IconCache.kt                            per-cast-id state shared by the pieces above
  cards/                                 one poster per island card type
    GenericCard.kt, MediaCard.kt, PaymentCard.kt, SportsCard.kt
  sports/                                live-score parsing + crest fetching
    SportsParser.kt, SportsFeed.kt
  ui/                                    Compose UI (dark-only)
    Theme.kt, OnboardingScreen.kt, CastTab.kt, AppsTab.kt, LogTab.kt, samples/
  log/
    CastLog.kt                             the in-app "why did/didn't this cast" log
```

## License

See [LICENSE](LICENSE) (MIT). The vivo/OriginOS SuperX protocol itself is not this project's
intellectual property — it's documented here for interoperability and educational purposes.
