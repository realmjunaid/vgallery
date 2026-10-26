# VGallery — Scroll Your Photos Like Reels

**Your camera roll, reimagined as Reels.**  
VGallery is a free, **offline-first** photo & video gallery for **Android** and **Windows**. Scroll through your own files vertically like Instagram Reels or TikTok — no accounts, no uploads, no tracking.

<p>
  <a href="https://github.com/realmjunaid/vgallery/releases/latest">
    <img src="https://img.shields.io/badge/Download-Android_APK-7C3AED?style=for-the-badge&logo=android&logoColor=white" alt="Download Android APK">
  </a>
  <a href="https://github.com/realmjunaid/vgallery/releases/tag/v0.1.0-windows">
    <img src="https://img.shields.io/badge/Download-Windows_Setup-0078D4?style=for-the-badge&logo=windows&logoColor=white" alt="Download Windows Installer">
  </a>
</p>

<p>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Windows-10%2F11-0078D4?logo=windows&logoColor=white" alt="Windows 10/11">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="MIT License">
  <img src="https://img.shields.io/badge/Offline-First-22c55e" alt="Offline First">
</p>

## ✨ Features (Android + Windows)

- **Reels-style continuous feed** — scroll your photos and videos vertically in one flowing strip. Multiple items visible at once.
- **Global zoom (1×–10×)** — pinch or Ctrl+wheel zooms the **entire feed** together (like Google Drive PDF viewer). Horizontal pan while zoomed, vertical scroll always works.
- **Auto gallery** — instantly shows every photo & video from your folders. No import needed.
- **Photos & Videos tabs** + grid/list view.
- **Group similar folders** — WhatsApp Images + Video under one heading, Camera + Camera Roll together, etc.
- **Autoplay** — hands-free playback. Photos hold for your chosen time (1–15s), videos play to their end.
- **Subtitles & extra audio** (Windows) — sidecar `.srt`/`.vtt` and `.m4a`/`.mp3` next to videos, with track picker.
- **Trash** — delete goes to restorable Trash (30 days). Restore or delete forever.
- **Explorer "Open with"** (Windows) — right-click photos/videos → Open with VGallery.
- **Themes** — System / Dark / Light + accent colours + pure black AMOLED.
- **Private by design** — nothing leaves your device. No ads, no analytics.

## 📥 Download & Install

### Android
1. Go to the [latest release](https://github.com/realmjunaid/vgallery/releases/latest)
2. Download the `.apk`
3. Install and allow photo access.

### Windows
**Recommended:** Download the installer from the [Windows release](https://github.com/realmjunaid/vgallery/releases/tag/v0.1.0-windows)

Or go to Releases → look for "VGallery for Windows 0.1.0"

> **Note:** Windows support is currently at version **0.1.0** (preview). The experience matches the Android app closely, including the continuous feed and global zoom.

If no Windows installer is attached yet, build it yourself (see below).

## 🖥 VGallery for Windows

The Windows version is written in **Rust + Tauri**. Light, fast, and native-feeling.

- Same continuous feed + global 1×–10× zoom
- Full keyboard support (arrows, space, T/C for tracks, Ctrl+0 to reset zoom)
- Subtitles & alternate audio tracks
- Explorer integration
- ~1.5 MB installer

Full details and data locations are in [`windows/README.md`](windows/README.md).

## 🔒 Privacy

- 100% offline (except optional update check)
- Your files never leave your device
- No accounts, no tracking, no ads

## 🛠 Build from Source

### Windows
```powershell
cd windows
npm install
npx tauri build
```
The installer will be in:
`windows\src-tauri\target\release\bundle\nsis\VGallery_0.1.0_x64-setup.exe`

### Android
```bash
cd android
./gradlew :app:assembleDebug
```
APK will be in `android/app/build/outputs/apk/debug/`.

## 📄 License

MIT — see [LICENSE](LICENSE).

---

**Made with ❤️ by one developer.**  
Report issues or ideas on [GitHub](https://github.com/realmjunaid/vgallery/issues).