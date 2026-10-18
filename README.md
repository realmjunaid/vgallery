# VGallery — Scroll Your Photos Like Reels

**Your camera roll, reimagined as Reels.** VGallery is a free, **100% offline photo gallery app** for Android where you scroll through your own photos vertically, full-screen — just like Instagram Reels or TikTok, but for your memories. Open the app and every photo is already there, organized folder-wise. No accounts, no uploads, no tracking.

<p>
  <a href="https://github.com/realmjunaid/vgallery/releases/latest">
    <img src="https://img.shields.io/badge/Download-VGallery_v1.0.7.apk-7C3AED?style=for-the-badge&logo=android&logoColor=white" alt="Download APK">
  </a>
</p>

<p>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT">
  <img src="https://img.shields.io/badge/Offline-No%20tracking-22c55e" alt="Offline">
</p>

## ✨ Features

- **Reels-style vertical scrolling** ⭐ — the heart of VGallery. Swipe up through your photos full-screen, one flowing feed, exactly like Reels. No other gallery app does this.
- **Auto gallery** — every photo on your device shows up instantly, grouped folder-wise (Camera, Download, Screenshots…) just like Google Photos. Nothing to pick, nothing to configure. Each folder card shows its item count and total size, folders are ordered newest-added first with fresh ones raised to the top behind a small green dot until you open them, and a folder's contents always list A–Z.
- **Photos & Videos tabs** — home splits the library in two: *Photos* for still images, *Videos* for everything that plays (videos, GIFs and animated WebPs), each as its own folder grid.
- **Grid or list view** — a toggle next to Delete switches a folder between the thumbnail grid and a line-by-line list showing each file's title and size.
- **Continuous full view** — tap any photo to enter a Drive-style vertical feed: every photo edge-to-edge, full original resolution, zero compression. Videos show their poster frame instead of a black box, and start playing only when you tap them.
- **Smart counter** — a tiny `23 / 100` indicator follows you as you scroll.
- **Tap to play** — only the item you tap plays (video, GIF or animated WebP); everything else stays paused to save battery.
- **Autoplay** ▶️ — one button in the viewer plays the whole feed hands-free: videos run to their end and stills move on after a moment, scrolling to each item automatically until you tap the button again.
- **Pinch-to-zoom** — one global pinch scales the whole vertical feed together, photos and videos alike, from 1x up to 10x and anchored under your fingers. While zoomed, drag sideways to pan (clamped so no blank space shows) and drag or fling up and down to keep scrolling through every item — a diagonal drag does both at once. Pinch back to fit to scroll normally again.
- **Share, delete, search** — system share sheet, global name search from home, long-press multi-select in folders and on home with batch actions.
- **Trash** 🗑️ — deleting moves items to a Google-Photos-style Trash instead of wiping them: everything stays restorable for 30 days, with *Restore*, *Delete forever* and *Empty trash* (permanent deletion still asks for Android's consent).
- **Move to folder** 📁 — pick photos or videos inside a folder and move them into any other folder on the device in one step (long-press to select, or from the full-screen viewer).
- **Details sheet** — size, resolution, date, folder and type for any photo or selection.
- **Immersive mode** — one tap hides the top bar, titles, status bar and navigation buttons for pure full-screen viewing.
- **Modern feel** — crossfade image loading, animated grids, real-photo album covers.
- **Private by design** — photos never leave your phone. No internet permission at all.

## 📥 Download & Install

1. Go to the [**latest release**](https://github.com/realmjunaid/vgallery/releases/latest).
2. Download **`vgallery_v1.0.7-release.apk`** (under *Assets*).
3. Open the file on your phone and tap **Install** (allow *Install unknown apps* if asked).
4. Open **VGallery**, allow photo access — done, your gallery is ready.

> Requires Android 8.0 (API 26) or newer.

## 🔒 Privacy

- No account, no analytics, no ads, no crash reporting.
- The app **does not request the INTERNET permission** — it is physically incapable of sending anything anywhere.
- Photos are read from the on-device `MediaStore` only. Nothing is uploaded, ever.

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
- **JUnit + Robolectric + Kotest** — unit & property tests

## 🗂 Project Structure

```
app/src/main/java/com/vgallery/app/
├── data/gallery/      # MediaStore repository, album grouping
├── ui/gallery/        # Auto gallery: albums → grid → full view
├── ui/settings/       # About pages (app & developer)
├── ui/viewer/         # Legacy folder viewer components
├── navigation/        # Nav graph & routes
├── player/            # ExoPlayer pool (legacy viewer)
└── util/thumbnail/    # Video thumbnail pipeline
```

## ❓ Frequently Asked Questions

**Is VGallery free?**
Yes — VGallery is free and open source (MIT). No ads, no premium tier.

**Does VGallery work offline?**
Fully. VGallery requests no internet permission, so it works with Wi-Fi and mobile data turned off.

**Is VGallery a Google Photos alternative?**
If you want a simple offline gallery without cloud backup or an account, yes. VGallery only shows photos already on your phone.

**Which formats can VGallery show?**
JPG, PNG, WebP (including animated WebP) and GIF, organized automatically by folder.

## 🤝 Contributing

Issues and pull requests are welcome! For big changes, please open an issue first to discuss what you'd like to change.

## 📄 License

MIT — see [LICENSE](LICENSE).
