# VGallery for Windows

**Your camera roll, reimagined as reels — on the desktop.** This is the Windows build of
[VGallery](../README.md), written in **Rust + Tauri** instead of Kotlin. Same idea, same
black-and-violet look, much smaller footprint than a JVM app.

> Status: **0.1.0 preview.** The gallery, folder grouping, grid/list, full-screen feed,
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
- **Reels-style vertical feed** — one item per screen, scroll-snapped, full resolution.
  The wheel glides continuously and settles on the item it was heading for, so it feels
  like the phone instead of jumping a screen per notch. Videos show a poster frame and
  play when you tap them; images zoom with `Ctrl` + wheel (or double-click) and pan while
  zoomed.
- **Autoplay** — the play button walks the feed hands-free. Photos hold for 1–15 seconds
  (Settings › Playback); videos, GIFs and animated images play for their own duration.
  The item being played stays in the middle and the next one glides up into it.
- **Photos & Videos tabs** — stills in one, everything that plays in the other.
- **Grid or list** — switch per folder; sort by name (natural order, so `1(2).png` before
  `1(10).png`) or newest first.
- **Group similar folders** — Settings › Library: *WhatsApp Images* and *WhatsApp Video*
  gather under **WhatsApp**, *Camera* with *Camera Roll*, `100ANDRO` with `101ANDRO`.
  Matching ignores case, accents, spacing, plurals and numbering.
- **Multi-select** — tick items, then `Select all`, copy paths, see details, or delete.
- **Subtitles & extra audio tracks** — the captions button in the feed (or `T`) lists
  every `.srt`/`.vtt` and `.m4a`/`.mp3`/… sitting next to the video, remembers your pick
  per file, and switches with `C`. See [Subtitles & audio tracks](#subtitles--audio-tracks).
- **Trash** — deletions move to a restorable Trash (30 days), with *Restore*, *Delete
  forever* and *Empty Trash*.
- **Themes** — System / Dark / Light, an accent colour, and a pure AMOLED black option.
  The full-screen feed is black in every theme.
- **The same logo as Android** — the exe, taskbar and installer icons are generated from
  the Android launcher logo (`make-icon.ps1`), so both apps read as VGallery.
- **Offline by design** — no account, no analytics, no uploads, no network calls.

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
│     ├─ feed.ts           # the scroll-snapped viewer, autoplay, subtitles + audio tracks
│     ├─ settings.ts
│     └─ trash.ts
└─ src-tauri/
   ├─ Cargo.toml
   ├─ tauri.conf.json
   └─ src/lib.rs           # scan, thumbnails, trash, sidecar tracks, shell helpers
```

## Where your data lives

- Settings: `localStorage` inside the app's WebView profile.
- Thumbnail cache: `%LOCALAPPDATA%\me.mjunaid.vgallery\cache\thumbs`
- Trash: `%LOCALAPPDATA%\me.mjunaid.vgallery\data\trash` (plus `trash.json`)
- Folder list: `%APPDATA%\me.mjunaid.vgallery\roots.json`

Nothing is copied out of your folders except thumbnails and files you delete (which are
moved, not copied, into the Trash folder).

## Not in 0.1.0 yet

- Built-in updater (the Android app has one; Windows will get a signed release check).
- Switching audio/subtitle tracks that are muxed inside an MP4/MKV (use sidecar files —
  see [Subtitles & audio tracks](#subtitles--audio-tracks)).
- Video thumbnails from arbitrary codecs (poster frames come from WebView2's own decoder).
- EXIF orientation correction in cached thumbnails.
- Live file-watching — use **Rescan** after adding files outside the app.

## License

MIT, same as the Android app.
