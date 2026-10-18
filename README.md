# VGallery — Scroll Your Photos Like Reels

**Your camera roll, reimagined as Reels.** VGallery is a free, **offline-first photo gallery app** for Android where you scroll through your own photos vertically, full-screen — just like Instagram Reels or TikTok, but for your memories. Open the app and every photo is already there, organized folder-wise. No accounts, no uploads, no tracking.

<p>
  <a href="https://github.com/realmjunaid/vgallery/releases/latest">
    <img src="https://img.shields.io/badge/Download-VGallery_v1.0.9.apk-7C3AED?style=for-the-badge&logo=android&logoColor=white" alt="Download APK">
  </a>
</p>

<p>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT">
  <img src="https://img.shields.io/badge/Offline-No%20tracking-22c55e" alt="Offline">
  <img src="https://img.shields.io/badge/Updates-Built--in-7C3AED" alt="Built-in updates">
</p>

## ✨ Features

- **Reels-style vertical scrolling** ⭐ — the heart of VGallery. Swipe up through your photos full-screen, one flowing feed, exactly like Reels. No other gallery app does this.
- **Auto gallery** — every photo on your device shows up instantly, grouped folder-wise (Camera, Download, Screenshots…) just like Google Photos. Nothing to pick, nothing to configure. Each folder card shows its item count and total size, folders are ordered newest-added first with fresh ones raised to the top behind a small green dot until you open them, and a folder's contents always list A–Z.
- **Photos & Videos tabs** — home splits the library in two: *Photos* for still images, *Videos* for everything that plays (videos, GIFs and animated WebPs), each as its own folder grid.
- **Grid or list view** — a toggle next to Delete switches a folder between the thumbnail grid and a line-by-line list showing each file's title and size.
- **Continuous full view** — tap any photo to enter a Drive-style vertical feed: every photo edge-to-edge, full original resolution, zero compression. Videos show their poster frame instead of a black box, and start playing only when you tap them. While a video plays, its remaining time counts down in the corner, so how much is left is readable at any moment.
- **Smart counter** — a tiny `23 / 100` indicator follows you as you scroll.
- **Tap to play** — only the item you tap plays (video, GIF or animated WebP); everything else stays paused to save battery.
- **Autoplay** ▶️ — the play icon beside the viewer's other actions plays the whole feed hands-free: videos run to their end and stills move on after a moment, each item gliding to the middle of the screen as it is played, until you tap the button again.
- **Pinch-to-zoom** — one global pinch scales the whole vertical feed together, photos and videos alike, from 1x up to 10x and anchored under your fingers. While zoomed, drag sideways to pan (clamped so no blank space shows) and drag or fling up and down to keep scrolling through every item — a diagonal drag does both at once. Pinch back to fit to scroll normally again.
- **Share, delete, search** — system share sheet, global name search from home, long-press multi-select in folders and on home with batch actions.
- **Built-in updates** ⬆️ — the app checks GitHub for a newer release (at most every six hours, and only if you leave it on) and offers *Update now* / *Cancel*. Update now downloads the APK, verifies its checksum and signature, and opens Android's installer without a browser.
- **Trash** 🗑️ — deleting moves items to a Google-Photos-style Trash instead of wiping them: everything stays restorable for 30 days, with *Restore*, *Delete forever* and *Empty trash* (permanent deletion still asks for Android's consent).
- **Move to folder** 📁 — pick photos or videos inside a folder and move them into any other folder on the device in one step (long-press to select, or from the full-screen viewer).
- **Details sheet** — size, resolution, date, folder and type for any photo or selection.
- **Immersive mode** — one tap hides the top bar, titles, status bar and navigation buttons for pure full-screen viewing.
- **Modern feel** — crossfade image loading, animated grids, real-photo album covers.
- **Private by design** — your photos never leave your phone. The updater is the only thing that ever touches the network, and you can switch it off (see [Privacy](#-privacy)).

## 📥 Download & Install

1. Go to the [**latest release**](https://github.com/realmjunaid/vgallery/releases/latest).
2. Download **`vgallery_v1.0.9-release.apk`** (under *Assets*).
3. Open the file on your phone and tap **Install** (allow *Install unknown apps* if asked).
4. Open **VGallery**, allow photo access — done, your gallery is ready.

> Requires Android 8.0 (API 26) or newer.

> From 1.0.9 on you can skip this page: VGallery installs its own updates. It offers a new version
> when one is out, downloads it, verifies it and hands it to Android's installer.

## 🔒 Privacy

- No account, no analytics, no ads, no crash reporting.
- Your photos are read from the on-device `MediaStore` only. Nothing about them — no image, no file
  name, no identifier — is ever uploaded.
- The app's **only** network call is the update check: it asks GitHub (and GitLab as a fallback) for
  the newest release tag. If you tap *Update now*, it downloads that release's APK, verifies the
  published checksum and the release signature, and hands it to Android's installer.
- You can turn the check off: **Settings › Automatic update check**. With it off, VGallery makes no
  network call at all — and *Check for updates* stays available whenever you want it.

That is the entire network surface: two HTTPS GETs to GitHub/GitLab and, only on request, one APK
download from GitHub.

## 🛠 Build from Source

Requirements: **JDK 17** and the **Android SDK** (compileSdk 36).

```bash
git clone https://github.com/realmjunaid/vgallery.git
cd vgallery
# point to your SDK, e.g. C:\Users\you\AppData\Local\Android\Sdk
echo "sdk.dir=C:\\Users\\you\\AppData\\Local\\Android\\Sdk" > local.properties
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/vgallery_v1.0.0.apk
```

Run the unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

## 🏗 Tech Stack

- **Kotlin + Jetpack Compose (Material 3)** — declarative UI
- **MediaStore** — instant, no-copy access to device photos
- **Coil 3** (+ `coil-gif`) — image loading with animated WebP/GIF support
- **Hilt** — dependency injection
- **DataStore** — lightweight settings persistence
- **HttpURLConnection + the GitHub Releases API** — the in-app updater (no HTTP library added)
- **JUnit + Robolectric + Kotest** — unit & property tests

## 🗂 Project Structure

```
app/src/main/java/com/vgallery/app/
├── data/gallery/      # MediaStore repository, album grouping
├── data/update/       # In-app updater (GitHub releases, download, verify, install)
├── ui/gallery/        # Auto gallery: albums → grid → full view
├── ui/settings/       # Settings: updates + About pages
├── ui/update/         # Update prompt & Settings row state
├── ui/viewer/         # Legacy folder viewer components
├── navigation/        # Nav graph & routes
├── player/            # ExoPlayer pool (legacy viewer)
└── util/thumbnail/    # Video thumbnail pipeline
```

## ❓ Frequently Asked Questions

**Is VGallery free?**
Yes — VGallery is free and open source (MIT). No ads, no premium tier.

**Does VGallery work offline?**
Everything except the update check does. Your gallery, Trash, Move, zoom, autoplay and search never
touch the network. Turn off **Automatic update check** in Settings and the app makes no network call
at all.

**How do updates work?**
When a new release is published, VGallery offers it the next time you open the app (or whenever you
tap *Check for updates* in Settings). *Update now* downloads and verifies the APK inside the app;
Android then asks for one confirmation before installing, because that is the platform's rule.

**Is VGallery a Google Photos alternative?**
If you want a simple offline gallery without cloud backup or an account, yes. VGallery only shows photos already on your phone.

**Which formats can VGallery show?**
JPG, PNG, WebP (including animated WebP) and GIF, organized automatically by folder.

## 🚀 Releasing

The release key is configured outside the repo (`keystore.properties` or `VGALLERY_*` env vars, see
the comment at the top of `app/build.gradle.kts`). Every release must be signed with that one key —
it is also what lets an installed app update in place, and what the updater checks before offering
an APK.

```bash
# 1. Bump APP_VERSION_NAME (and versionCode) in app/build.gradle.kts — the updater compares versions,
#    so a release that keeps the old number is invisible to every installed app.
# 2. Build and sign.
./gradlew :app:assembleRelease
# 3. Publish: tag vX.Y.Z, attach vgallery_vX.Y.Z-release.apk, push the tag to both remotes.
```

The updater reads the newest tag from `releases/latest` on GitHub (GitLab is the fallback) and
downloads the `.apk` asset attached to it, so the tag and the asset name matter:
`v1.0.8` + `vgallery_v1.0.8-release.apk`.

## 🤝 Contributing

Issues and pull requests are welcome! For big changes, please open an issue first to discuss what you'd like to change.

## 📄 License

MIT — see [LICENSE](LICENSE).
