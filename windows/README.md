# VGallery for Windows

**Your camera roll, reimagined as reels — on the desktop.** This is the Windows build of
[VGallery](../README.md), written in **Rust + Tauri** instead of Kotlin. Same idea, same
black-and-violet look, much smaller footprint than a JVM app.

> Status: **1.0.2.** The gallery, folder grouping, grid/list, full-screen feed,
> autoplay, Trash and Settings are in. Native OS integration is intentionally small.

## Why Rust + Tauri?

| | VGallery for Windows (Tauri) | A JVM desktop port |
|---|---|---|
| Runtime | Native Rust binary + WebView2 | JVM + Compose runtime |
| Typical RAM | ~80–120 MB | ~200–300 MB |
| Startup | instant | a second or two of JVM warm-up |
| Installer | ~4–6 MB | ~40–80 MB |

Thumbnails are decoded and cached in Rust (`image` crate), the file walk and Trash are
plain `std::fs`, and the UI is vanilla TypeScript — no React, no UI framework bundle.

## What it does

- **Auto gallery** — point it at folders (Pictures, Videos, Desktop and Downloads on first
  run) and every photo and video shows up grouped folder-wise. Nothing to import.
- **Continuous photo feed** — the feed is a strip, not a pager: every picture sits in the
  same card, so a portrait and a square take the same space on screen (the extra is cropped
  from the centre). A small wheel nudge reveals the next one instead of replacing the screen.
  Videos show a poster frame and play when you tap them.
  Pinch (or `Ctrl` + wheel) zooms the **whole feed** from 1× to 10×, Google Drive
  PDF-viewer style: every item grows together, double-click toggles 2×, a sideways drag
  pans, and the feed keeps scrolling up and down at any zoom.
- **Autoplay** — the play button walks the feed hands-free. Photos hold for 1–15 seconds
  (Settings › Playback); videos, GIFs and animated images play for their own duration.
  The item under the middle of the screen is the active one.
- **Settings › About** — *About VGallery* (version, features, privacy), *About Developer*
  (M Junaid, website, source and issues) and *Check for updates*, which asks GitHub for the
  newest release and only offers it when that release ships a Windows `.exe` or `.msi`.
- **Photos & Videos tabs** — stills in one, everything that plays in the other.
- **Grid or list** — switch per folder; sort by name (natural order, so `1(2).png` before
  `1(10).png`) or newest first.
- **Group similar folders** — Settings › Library: *WhatsApp Images* and *WhatsApp Video*
  gather under **WhatsApp**, *Camera* with *Camera Roll*, `100ANDRO` with `101ANDRO`.
  Matching ignores case, accents, spacing, plurals and numbering.
- **Multi-select** — tick items, then `Select all`, copy paths, see details, or delete.
- **Open with VGallery** — after the first release launch, Explorer's *Open with* menu
  lists VGallery for 22 photo and video formats; the clicked file opens straight in the
  feed, and a second launch reuses the open window.
- **Subtitles & extra audio tracks** — the captions button in the feed (or `T`) lists
  every `.srt`/`.vtt` and `.m4a`/`.mp3`/… sitting next to the video, remembers your pick
  per file, and switches with `C`. See [Subtitles & audio tracks](#subtitles--audio-tracks).
- **Trash** — deletions move to a restorable Trash (30 days), with *Restore*, *Delete
  forever* and *Empty Trash*.
- **Themes** — System / Dark / Light, an accent colour, and a pure AMOLED black option.
  The full-screen feed is black in every theme.
- **The same logo as Android** — the exe, taskbar and installer icons are generated from
  the Android launcher logo (`make-icon.ps1`), so both apps read as VGallery.
- **In-app updates** — Settings › Check for updates downloads a newer Windows release
  inside the app, installs it, and restarts. Nothing lands in Downloads for you to run.
- **Offline by design** — no account, no analytics, no uploads. The only online moment is
  an update you ask for yourself; skip it and the app never touches the network.

## Subtitles & audio tracks

Track support is **sidecar based**: the files sit next to the video, named after it.

| File | Becomes |
|---|---|
| `Movie.srt` | the default subtitle |
| `Movie.en.srt` | “English” |
| `Movie.bn.forced.srt` | “Bengali (forced)” |
| `Movie.vtt` | a WebVTT subtitle, used as-is |
| `Movie.bn.m4a` | “Bengali” alternate audio |
| `Movie.hi.mp3` | “Hindi” alternate audio |

Language tokens can be 2-letter (`bn`), 3-letter (`ben`) or the English name
(`bengali`, `bangla`); `forced`, `sdh` and `cc` are recognised as flags. Named tracks are
listed before unnamed ones, and the last choice for each video is remembered.

- Subtitle files are read in Rust, converted from SRT to WebVTT, and rendered by
  WebView2's own text-track engine (styled with `::cue`).
- An alternate audio track is played by a hidden `HTMLAudioElement` kept in sync with the
  video's play/pause/seek, while the video's own soundtrack is muted. Volume stays
  linked.
- **Honest limit:** WebView2 cannot switch between audio tracks multiplexed *inside* an
  MP4/MKV, and it only plays the first embedded subtitle track. Extract the track you
  want into a sidecar file and it will appear in the picker. The sheet says so when a
  video has no sidecars.

A subtitle can also be cycled without the sheet with `C`; `T` opens the track picker.

## Open with VGallery (Explorer)

On every launch of a **release** build the app registers itself for photos and videos
(22 extensions) in the current user's registry, so Explorer's **Open with** menu lists
**VGallery** for a `.jpg`, `.png`, `.mp4`, `.mkv` and the rest — no admin rights required.

- It writes only per-user keys: `HKCU\Software\Classes\Applications\vgallery.exe`,
  `HKCU\Software\Classes\VGallery.Media`, and `…\OpenWithProgids` for each extension.
  It never changes your default app; pick *Set default* in Windows if you want that too.
- Double-clicking a file with *Open with › VGallery* opens its folder in the feed, starting
  at that file. Files do not have to be inside a library folder.
- If VGallery is already running, the path is forwarded to the open window instead of
  starting a second copy (single-instance plugin).
- The registered command points at the `.exe` you ran. Start the installed copy once after
  installing or moving it, and the entry follows. Uninstalling leaves those per-user keys
  behind — delete `HKCU\Software\Classes\VGallery.Media`, the
  `Applications\vgallery.exe` key and the `…\OpenWithProgids\VGallery.Media` values to
  remove the stale entry.

## Build from source

Requirements:

- **Windows 10/11** with **WebView2** (preinstalled on Windows 11 and current Windows 10)
- **Rust** (stable, MSVC toolchain)
- **Node.js 20+**

```powershell
cd windows
npm install
npm run app:dev      # dev window with hot reload
npm run app:build    # release .exe + NSIS installer + MSI
```

Artifacts land in `src-tauri/target/release/bundle/` (`nsis/` and `msi/`).

Only the frontend:

```powershell
npm run build        # type-check + bundle to dist/
```

## Project layout

```
windows/
├─ index.html
├─ icon-source.png       # 1024px app icon, cropped from the Android logo
├─ make-icon.ps1         # regenerates icon-source.png from the Android launcher icon
├─ src/
│  ├─ main.ts              # router: home, folder, settings, trash
│  ├─ styles.css           # the whole palette + feed
│  ├─ lib/
│  │  ├─ api.ts            # typed invoke() wrappers
│  │  ├─ albums.ts         # folder grouping + natural sorting
│  │  ├─ settings.ts       # localStorage settings
│  │  ├─ icons.ts          # inline SVG icons
│  │  ├─ thumbnails.ts     # lazily loaded, Rust-generated thumbnails
│  │  └─ dom.ts            # tiny DOM helpers, dialogs, toasts
│  └─ views/
│     ├─ feed.ts           # the continuous feed, zoom, autoplay, tracks
│     ├─ settings.ts
│     └─ trash.ts
└─ src-tauri/
   ├─ Cargo.toml
   ├─ tauri.conf.json
   └─ src/lib.rs           # scan, thumbnails, trash, sidecar tracks, Explorer registration
```

## Where your data lives

- Settings: `localStorage` inside the app's WebView profile.
- Thumbnail cache: `%LOCALAPPDATA%\me.mjunaid.vgallery\cache\thumbs`
- Trash: `%LOCALAPPDATA%\me.mjunaid.vgallery\data\trash` (plus `trash.json`)
- Folder list: `%APPDATA%\me.mjunaid.vgallery\roots.json`

Nothing is copied out of your folders except thumbnails and files you delete (which are
moved, not copied, into the Trash folder).

## Not in 1.0.2 yet

- Switching audio/subtitle tracks that are muxed inside an MP4/MKV (use sidecar files —
  see [Subtitles & audio tracks](#subtitles--audio-tracks)).
- Video thumbnails from arbitrary codecs (poster frames come from WebView2's own decoder).
- EXIF orientation correction in cached thumbnails.
- Live file-watching — use **Rescan** after adding files outside the app.

## License

MIT, same as the Android app.
