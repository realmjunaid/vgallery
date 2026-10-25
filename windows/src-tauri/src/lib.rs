//! VGallery for Windows - the native half.
//!
//! Everything here is local: we walk the folders the user pointed at, hand the
//! UI a flat list of media, cache thumbnails, and move deletions into a
//! restorable trash. No network calls, no telemetry.

use std::collections::{BTreeMap, HashSet};
use std::fs;
use std::hash::{Hash, Hasher};
use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Manager, State};
use walkdir::{DirEntry, WalkDir};

// ---------------------------------------------------------------------------
// Media catalogue
// ---------------------------------------------------------------------------

const IMAGE_EXTS: &[&str] = &[
    "jpg", "jpeg", "png", "webp", "gif", "bmp", "tif", "tiff", "avif",
];
const VIDEO_EXTS: &[&str] = &[
    "mp4", "m4v", "mkv", "webm", "mov", "avi", "wmv", "flv", "3gp", "mpg", "mpeg", "ogv",
];
/// Formats that move on their own, so autoplay gives them their own runtime.
const MOTION_EXTS: &[&str] = &["gif", "apng"];

fn kind_for(ext: &str) -> Option<&'static str> {
    if VIDEO_EXTS.contains(&ext) {
        Some("video")
    } else if MOTION_EXTS.contains(&ext) {
        Some("motion")
    } else if IMAGE_EXTS.contains(&ext) {
        Some("image")
    } else {
        None
    }
}

#[derive(Debug, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct MediaItem {
    pub id: String,
    pub path: String,
    pub name: String,
    pub folder: String,
    pub folder_path: String,
    pub ext: String,
    pub kind: String,
    pub size: u64,
    pub modified: u64,
}

#[derive(Debug, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct FolderEntry {
    pub name: String,
    pub path: String,
    pub items: Vec<MediaItem>,
}

#[derive(Debug, Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct Library {
    pub folders: Vec<FolderEntry>,
    pub roots: Vec<String>,
    pub total_count: usize,
    pub total_size: u64,
    pub scanned_at: u64,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AppInfo {
    pub version: String,
    pub data_dir: String,
    pub cache_dir: String,
    pub roots: Vec<String>,
}

fn now_secs() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

fn config_dir(app: &AppHandle) -> Result<PathBuf, String> {
    app.path()
        .app_config_dir()
        .map_err(|e| format!("Could not resolve the config folder: {e}"))
}

fn data_dir(app: &AppHandle) -> Result<PathBuf, String> {
    app.path()
        .app_data_dir()
        .map_err(|e| format!("Could not resolve the data folder: {e}"))
}

fn cache_dir(app: &AppHandle) -> Result<PathBuf, String> {
    app.path()
        .app_cache_dir()
        .map_err(|e| format!("Could not resolve the cache folder: {e}"))
}

fn trim_separators(value: &str) -> &str {
    value.trim_end_matches(|ch| ch == '\\' || ch == '/')
}

fn default_roots() -> Vec<PathBuf> {
    let candidates: Vec<PathBuf> = [
        dirs::picture_dir(),
        dirs::video_dir(),
        dirs::desktop_dir(),
        dirs::download_dir(),
    ]
    .into_iter()
    .flatten()
    .collect();

    let mut out: Vec<PathBuf> = Vec::with_capacity(candidates.len());
    for candidate in candidates {
        if !out.iter().any(|existing| paths_equal(existing, &candidate)) {
            out.push(candidate);
        }
    }
    out
}

fn paths_equal(a: &Path, b: &Path) -> bool {
    let left = a.to_string_lossy();
    let right = b.to_string_lossy();
    trim_separators(&left).eq_ignore_ascii_case(trim_separators(&right))
}

/// Saved roots live in `<config>/roots.json`. If the file is missing we fall
/// back to the usual Pictures / Videos / Desktop / Downloads folders.
fn read_roots(app: &AppHandle) -> Vec<PathBuf> {
    let Ok(dir) = config_dir(app) else {
        return default_roots();
    };
    let file = dir.join("roots.json");
    if let Ok(text) = fs::read_to_string(&file) {
        if let Ok(list) = serde_json::from_str::<Vec<String>>(&text) {
            return list.into_iter().map(PathBuf::from).collect();
        }
    }
    default_roots()
}

fn write_roots(app: &AppHandle, roots: &[PathBuf]) -> Result<(), String> {
    let dir = config_dir(app)?;
    fs::create_dir_all(&dir).map_err(|e| format!("Could not create the config folder: {e}"))?;
    let list: Vec<String> = roots
        .iter()
        .map(|p| p.to_string_lossy().to_string())
        .collect();
    let text = serde_json::to_string_pretty(&list).map_err(|e| e.to_string())?;
    fs::write(dir.join("roots.json"), text).map_err(|e| format!("Could not save folders: {e}"))
}

fn is_skipped(entry: &DirEntry) -> bool {
    if entry.depth() == 0 {
        return false;
    }
    let name = entry.file_name().to_string_lossy();
    if name.starts_with('.') {
        return true;
    }
    matches!(
        name.as_ref(),
        "$RECYCLE.BIN"
            | "System Volume Information"
            | "node_modules"
            | "__pycache__"
            | "Windows"
            | "AppData"
    )
}

fn scan_blocking(app: &AppHandle) -> Result<Library, String> {
    let roots = read_roots(app);
    let mut folders: BTreeMap<PathBuf, Vec<MediaItem>> = BTreeMap::new();
    let mut seen: HashSet<String> = HashSet::new();
    let mut total_size = 0u64;

    for root in &roots {
        if !root.is_dir() {
            continue;
        }
        let root_name = root
            .file_name()
            .map(|name| name.to_string_lossy().to_string())
            .unwrap_or_else(|| root.display().to_string());

        let walker = WalkDir::new(root)
            .follow_links(false)
            .into_iter()
            .filter_entry(|entry| !is_skipped(entry));

        for entry in walker.flatten() {
            if !entry.file_type().is_file() {
                continue;
            }
            let path = entry.path();
            let ext = path
                .extension()
                .and_then(|ext| ext.to_str())
                .map(|ext| ext.to_ascii_lowercase())
                .unwrap_or_default();
            let Some(kind) = kind_for(&ext) else {
                continue;
            };
            let path_str = path.to_string_lossy().to_string();
            if !seen.insert(path_str.clone()) {
                continue;
            }
            let Ok(meta) = entry.metadata() else {
                continue;
            };
            let parent = path.parent().unwrap_or(root);
            let folder_name = if paths_equal(parent, root) {
                root_name.clone()
            } else {
                parent
                    .file_name()
                    .map(|name| name.to_string_lossy().to_string())
                    .unwrap_or_else(|| parent.display().to_string())
            };
            let modified = meta
                .modified()
                .ok()
                .and_then(|time| time.duration_since(UNIX_EPOCH).ok())
                .map(|dur| dur.as_secs())
                .unwrap_or(0);
            let name = path
                .file_name()
                .map(|name| name.to_string_lossy().to_string())
                .unwrap_or_else(|| path_str.clone());

            total_size = total_size.saturating_add(meta.len());
            folders.entry(parent.to_path_buf()).or_default().push(MediaItem {
                id: path_str.clone(),
                path: path_str,
                name,
                folder: folder_name,
                folder_path: parent.to_string_lossy().to_string(),
                ext,
                kind: kind.to_string(),
                size: meta.len(),
                modified,
            });
        }
    }

    let folders = folders
        .into_iter()
        .map(|(path, mut items)| {
            items.sort_by(|a, b| {
                b.modified
                    .cmp(&a.modified)
                    .then_with(|| a.name.to_lowercase().cmp(&b.name.to_lowercase()))
            });
            let name = path
                .file_name()
                .map(|name| name.to_string_lossy().to_string())
                .unwrap_or_else(|| path.display().to_string());
            FolderEntry {
                name,
                path: path.to_string_lossy().to_string(),
                items,
            }
        })
        .collect::<Vec<_>>();

    let total_count = folders.iter().map(|folder| folder.items.len()).sum();

    Ok(Library {
        folders,
        roots: roots
            .iter()
            .map(|path| path.to_string_lossy().to_string())
            .collect(),
        total_count,
        total_size,
        scanned_at: now_secs(),
    })
}

// ---------------------------------------------------------------------------
// Thumbnails
// ---------------------------------------------------------------------------

fn thumb_path_for(app: &AppHandle, path: &Path, size: u32) -> Result<PathBuf, String> {
    let dir = cache_dir(app)?.join("thumbs");
    fs::create_dir_all(&dir).map_err(|e| format!("Could not create the thumbnail cache: {e}"))?;

    let mut hasher = std::collections::hash_map::DefaultHasher::new();
    path.to_string_lossy().hash(&mut hasher);
    size.hash(&mut hasher);
    if let Ok(meta) = fs::metadata(path) {
        meta.len().hash(&mut hasher);
        if let Ok(modified) = meta.modified() {
            if let Ok(dur) = modified.duration_since(UNIX_EPOCH) {
                dur.as_secs().hash(&mut hasher);
            }
        }
    }
    let key = format!("{:016x}.jpg", hasher.finish());
    Ok(dir.join(key))
}

fn thumbnail_blocking(app: &AppHandle, path: &str, size: u32) -> Result<Option<String>, String> {
    let source = PathBuf::from(path);
    if !source.is_file() {
        return Ok(None);
    }
    let ext = source
        .extension()
        .and_then(|ext| ext.to_str())
        .map(|ext| ext.to_ascii_lowercase())
        .unwrap_or_default();
    if VIDEO_EXTS.contains(&ext.as_str()) {
        // Videos are covered by a muted <video> element on the UI side.
        return Ok(None);
    }

    let out = thumb_path_for(app, &source, size)?;
    if out.is_file() {
        return Ok(Some(out.to_string_lossy().to_string()));
    }

    let decoded = image::ImageReader::open(&source)
        .map_err(|e| format!("Could not open {}: {e}", source.display()))?
        .with_guessed_format()
        .map_err(|e| format!("Could not read {}: {e}", source.display()))?
        .decode();

    let Ok(image) = decoded else {
        return Ok(None);
    };

    let thumb = image.thumbnail(size, size);
    let rgb = thumb.to_rgb8();
    let file = fs::File::create(&out).map_err(|e| format!("Could not write a thumbnail: {e}"))?;
    let mut writer = std::io::BufWriter::new(file);
    let mut encoder = image::codecs::jpeg::JpegEncoder::new_with_quality(&mut writer, 84);
    encoder
        .encode_image(&rgb)
        .map_err(|e| format!("Could not encode a thumbnail: {e}"))?;

    Ok(Some(out.to_string_lossy().to_string()))
}

// ---------------------------------------------------------------------------
// Trash
// ---------------------------------------------------------------------------

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct TrashEntry {
    pub id: String,
    pub original: String,
    pub stored: String,
    pub name: String,
    pub size: u64,
    pub trashed_at: u64,
    pub kind: String,
}

fn trash_dir(app: &AppHandle) -> Result<PathBuf, String> {
    let dir = data_dir(app)?.join("trash");
    fs::create_dir_all(&dir).map_err(|e| format!("Could not create the trash folder: {e}"))?;
    Ok(dir)
}

fn manifest_path(dir: &Path) -> PathBuf {
    dir.join("trash.json")
}

fn read_manifest(dir: &Path) -> Vec<TrashEntry> {
    fs::read_to_string(manifest_path(dir))
        .ok()
        .and_then(|text| serde_json::from_str::<Vec<TrashEntry>>(&text).ok())
        .unwrap_or_default()
}

fn write_manifest(dir: &Path, entries: &[TrashEntry]) -> Result<(), String> {
    let text = serde_json::to_string_pretty(entries).map_err(|e| e.to_string())?;
    fs::write(manifest_path(dir), text).map_err(|e| format!("Could not update the trash list: {e}"))
}

fn short_key(path: &Path) -> String {
    let mut hasher = std::collections::hash_map::DefaultHasher::new();
    path.to_string_lossy().hash(&mut hasher);
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|dur| dur.as_nanos())
        .unwrap_or(0)
        .hash(&mut hasher);
    format!("{:016x}", hasher.finish())[..12].to_string()
}

fn move_file(from: &Path, to: &Path) -> std::io::Result<()> {
    if fs::rename(from, to).is_ok() {
        return Ok(());
    }
    if let Some(parent) = to.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::copy(from, to)?;
    fs::remove_file(from)
}

fn trash_items_blocking(app: &AppHandle, paths: Vec<String>) -> Result<usize, String> {
    let dir = trash_dir(app)?;
    let mut entries = read_manifest(&dir);
    let mut moved = 0usize;

    for raw in paths {
        let source = PathBuf::from(&raw);
        if !source.is_file() {
            continue;
        }
        let meta = match fs::metadata(&source) {
            Ok(meta) => meta,
            Err(_) => continue,
        };
        let name = source
            .file_name()
            .map(|name| name.to_string_lossy().to_string())
            .unwrap_or_else(|| "item".to_string());
        let ext = source
            .extension()
            .and_then(|ext| ext.to_str())
            .map(|ext| ext.to_ascii_lowercase())
            .unwrap_or_default();
        let kind = kind_for(&ext).unwrap_or("image").to_string();
        let id = short_key(&source);
        let stored = dir.join(format!("{id}-{name}"));
        if move_file(&source, &stored).is_err() {
            continue;
        }
        entries.push(TrashEntry {
            id,
            original: raw,
            stored: stored.to_string_lossy().to_string(),
            name,
            size: meta.len(),
            trashed_at: now_secs(),
            kind,
        });
        moved += 1;
    }

    write_manifest(&dir, &entries)?;
    Ok(moved)
}

fn list_trash_blocking(app: &AppHandle) -> Result<Vec<TrashEntry>, String> {
    let dir = trash_dir(app)?;
    let entries = read_manifest(&dir);
    let before = entries.len();
    let mut kept: Vec<TrashEntry> = entries
        .into_iter()
        .filter(|entry| Path::new(&entry.stored).is_file())
        .collect();
    if kept.len() != before {
        write_manifest(&dir, &kept)?;
    }
    kept.sort_by(|a, b| b.trashed_at.cmp(&a.trashed_at));
    Ok(std::mem::take(&mut kept))
}

fn restore_trash_blocking(app: &AppHandle, ids: Vec<String>) -> Result<usize, String> {
    let dir = trash_dir(app)?;
    let mut entries = read_manifest(&dir);
    let mut restored = 0usize;

    for id in ids {
        let Some(index) = entries.iter().position(|entry| entry.id == id) else {
            continue;
        };
        let entry = entries[index].clone();
        let stored = PathBuf::from(&entry.stored);
        let original = PathBuf::from(&entry.original);
        if !stored.is_file() {
            entries.remove(index);
            continue;
        }
        if original.exists() {
            continue;
        }
        if let Some(parent) = original.parent() {
            let _ = fs::create_dir_all(parent);
        }
        if move_file(&stored, &original).is_ok() {
            entries.remove(index);
            restored += 1;
        }
    }

    write_manifest(&dir, &entries)?;
    Ok(restored)
}

fn delete_forever_blocking(app: &AppHandle, ids: Vec<String>) -> Result<usize, String> {
    let dir = trash_dir(app)?;
    let entries = read_manifest(&dir);
    let mut removed = 0usize;
    let mut kept = Vec::new();

    for entry in entries {
        if ids.contains(&entry.id) {
            let stored = PathBuf::from(&entry.stored);
            if stored.is_file() {
                let _ = fs::remove_file(&stored);
                removed += 1;
            }
            continue;
        }
        kept.push(entry);
    }

    write_manifest(&dir, &kept)?;
    Ok(removed)
}

fn empty_trash_blocking(app: &AppHandle) -> Result<usize, String> {
    let dir = trash_dir(app)?;
    let entries = read_manifest(&dir);
    let mut removed = 0usize;
    for entry in &entries {
        let stored = PathBuf::from(&entry.stored);
        if stored.is_file() && fs::remove_file(&stored).is_ok() {
            removed += 1;
        }
    }
    write_manifest(&dir, &[])?;
    Ok(removed)
}

// ---------------------------------------------------------------------------
// Subtitles and extra audio tracks
// ---------------------------------------------------------------------------

/// Sidecar subtitle formats we can hand to WebView2. `.vtt` goes through as-is;
/// `.srt` is rewritten into WebVTT first.
const SUB_EXTS: &[&str] = &["srt", "vtt"];
/// Sidecar audio a browser can play on its own, mixed under the (muted) video.
const AUDIO_EXTS: &[&str] = &[
    "m4a", "aac", "mp3", "opus", "flac", "wav", "ogg", "oga", "weba",
];

#[derive(Debug, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct TrackOption {
    /// Absolute path of the sidecar file.
    pub path: String,
    /// File name, shown under the label in the picker.
    pub name: String,
    /// Friendly title: "English", "Bengali (forced)", "Track 2"...
    pub label: String,
    /// ISO 639-1 code when the file name told us one, else "".
    pub lang: String,
    /// Lowercase extension.
    pub format: String,
}

#[derive(Debug, Serialize, Clone, Default)]
#[serde(rename_all = "camelCase")]
pub struct VideoTracks {
    pub subtitles: Vec<TrackOption>,
    pub audio: Vec<TrackOption>,
}

/// Language tokens people actually put in file names, mapped to a label and an
/// ISO code. Both the 2-letter and the 3-letter (bibliographic) forms are here.
fn language_name(token: &str) -> Option<(&'static str, &'static str)> {
    let entry = match token {
        "en" | "eng" | "english" => ("English", "en"),
        "bn" | "ben" | "bengali" | "bangla" => ("Bengali", "bn"),
        "hi" | "hin" | "hindi" => ("Hindi", "hi"),
        "ur" | "urd" | "urdu" => ("Urdu", "ur"),
        "ar" | "ara" | "arabic" => ("Arabic", "ar"),
        "es" | "spa" | "spanish" => ("Spanish", "es"),
        "fr" | "fre" | "fra" | "french" => ("French", "fr"),
        "de" | "ger" | "deu" | "german" => ("German", "de"),
        "it" | "ita" | "italian" => ("Italian", "it"),
        "pt" | "por" | "portuguese" => ("Portuguese", "pt"),
        "ru" | "rus" | "russian" => ("Russian", "ru"),
        "ja" | "jpn" | "japanese" => ("Japanese", "ja"),
        "ko" | "kor" | "korean" => ("Korean", "ko"),
        "zh" | "chi" | "zho" | "chinese" | "chs" | "cht" => ("Chinese", "zh"),
        "tr" | "tur" | "turkish" => ("Turkish", "tr"),
        "ta" | "tam" | "tamil" => ("Tamil", "ta"),
        "te" | "tel" | "telugu" => ("Telugu", "te"),
        "ml" | "mal" | "malayalam" => ("Malayalam", "ml"),
        "kn" | "kan" | "kannada" => ("Kannada", "kn"),
        "mr" | "mar" | "marathi" => ("Marathi", "mr"),
        "gu" | "guj" | "gujarati" => ("Gujarati", "gu"),
        "pa" | "pan" | "punjabi" => ("Punjabi", "pa"),
        "si" | "sin" | "sinhala" => ("Sinhala", "si"),
        "ne" | "nep" | "nepali" => ("Nepali", "ne"),
        "th" | "tha" | "thai" => ("Thai", "th"),
        "vi" | "vie" | "vietnamese" => ("Vietnamese", "vi"),
        "id" | "ind" | "indonesian" => ("Indonesian", "id"),
        "ms" | "msa" | "may" | "malay" => ("Malay", "ms"),
        "tl" | "tgl" | "fil" | "tagalog" | "filipino" => ("Filipino", "fil"),
        "my" | "mya" | "burmese" => ("Burmese", "my"),
        "km" | "khm" | "khmer" => ("Khmer", "km"),
        "fa" | "per" | "fas" | "persian" | "farsi" => ("Persian", "fa"),
        "he" | "heb" | "hebrew" => ("Hebrew", "he"),
        "nl" | "dut" | "nld" | "dutch" => ("Dutch", "nl"),
        "pl" | "pol" | "polish" => ("Polish", "pl"),
        "sv" | "swe" | "swedish" => ("Swedish", "sv"),
        "no" | "nor" | "norwegian" => ("Norwegian", "no"),
        "da" | "dan" | "danish" => ("Danish", "da"),
        "fi" | "fin" | "finnish" => ("Finnish", "fi"),
        "el" | "gre" | "ell" | "greek" => ("Greek", "el"),
        "cs" | "ces" | "cze" | "czech" => ("Czech", "cs"),
        "hu" | "hun" | "hungarian" => ("Hungarian", "hu"),
        "ro" | "ron" | "rum" | "romanian" => ("Romanian", "ro"),
        "uk" | "ukr" | "ukrainian" => ("Ukrainian", "uk"),
        _ => return None,
    };
    Some(entry)
}

fn is_track_flag(token: &str) -> bool {
    matches!(token, "forced" | "sdh" | "cc" | "hi" | "default")
}

/// Split the bit of a file name after the video's own name into a language and
/// a flag, e.g. `Movie.2024.en.forced.srt` -> (English, en, forced).
fn parse_track_tokens(rest: &str) -> (Option<(&'static str, &'static str)>, Option<String>) {
    let mut language = None;
    let mut flag: Option<String> = None;
    for token in rest.split(['.', '_', '-', ' ', '(', ')', '[', ']']).filter(|t| !t.is_empty()) {
        let lower = token.to_ascii_lowercase();
        if language.is_none() {
            if let Some(found) = language_name(&lower) {
                language = Some(found);
                continue;
            }
        }
        if flag.is_none() && is_track_flag(&lower) {
            flag = Some(lower);
        }
    }
    (language, flag)
}

fn sidecar_track(path: &Path, rest: &str, index: usize, kind: &str) -> TrackOption {
    let name = path
        .file_name()
        .map(|name| name.to_string_lossy().to_string())
        .unwrap_or_default();
    let format = path
        .extension()
        .and_then(|ext| ext.to_str())
        .map(|ext| ext.to_ascii_lowercase())
        .unwrap_or_default();
    let (language, flag) = parse_track_tokens(rest);
    let mut label = match language {
        Some((label, _)) => label.to_string(),
        // `movie.srt` / `movie.m4a` has no language token at all: it is the
        // plain default, not "Subtitle 3".
        None if rest.trim().is_empty() => match kind {
            "Subtitle" => "Default".to_string(),
            _ => "Extra audio".to_string(),
        },
        None => format!("{} {}", kind, index),
    };
    if let Some(flag) = flag {
        if flag != "default" {
            label.push_str(&format!(" ({flag})"));
        }
    }
    TrackOption {
        path: path.to_string_lossy().to_string(),
        name,
        label,
        lang: language.map(|(_, code)| code.to_string()).unwrap_or_default(),
        format,
    }
}

fn list_tracks_blocking(video_path: &str) -> Result<VideoTracks, String> {
    let video = PathBuf::from(video_path);
    if !video.is_file() {
        return Err("That video is no longer there.".to_string());
    }
    let Some(dir) = video.parent() else {
        return Ok(VideoTracks::default());
    };
    let Some(stem) = video.file_stem().map(|stem| stem.to_string_lossy().to_string()) else {
        return Ok(VideoTracks::default());
    };
    let stem = stem.to_lowercase();

    let mut subtitles: Vec<TrackOption> = Vec::new();
    let mut audio: Vec<TrackOption> = Vec::new();

    let entries = fs::read_dir(dir).map_err(|e| format!("Could not read that folder: {e}"))?;
    for entry in entries.flatten() {
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let Some(file_stem) = path.file_stem().map(|stem| stem.to_string_lossy().to_string()) else {
            continue;
        };
        let file_stem = file_stem.to_lowercase();
        if !file_stem.starts_with(&stem) {
            continue;
        }
        let rest = &file_stem[stem.len()..];
        if !rest.is_empty()
            && !rest.starts_with(['.', '_', '-', ' ', '(', '['])
        {
            continue;
        }
        let ext = path
            .extension()
            .and_then(|ext| ext.to_str())
            .map(|ext| ext.to_ascii_lowercase())
            .unwrap_or_default();
        if SUB_EXTS.contains(&ext.as_str()) {
            let index = subtitles.len() + 1;
            subtitles.push(sidecar_track(&path, rest, index, "Subtitle"));
        } else if AUDIO_EXTS.contains(&ext.as_str()) {
            let index = audio.len() + 1;
            audio.push(sidecar_track(&path, rest, index, "Audio"));
        }
    }

    // Named tracks first, then the unnamed ones in the order they were found.
    subtitles.sort_by(|a, b| {
        a.lang
            .is_empty()
            .cmp(&b.lang.is_empty())
            .then_with(|| a.label.to_lowercase().cmp(&b.label.to_lowercase()))
            .then_with(|| a.name.to_lowercase().cmp(&b.name.to_lowercase()))
    });
    audio.sort_by(|a, b| {
        a.lang
            .is_empty()
            .cmp(&b.lang.is_empty())
            .then_with(|| a.label.to_lowercase().cmp(&b.label.to_lowercase()))
            .then_with(|| a.name.to_lowercase().cmp(&b.name.to_lowercase()))
    });

    Ok(VideoTracks { subtitles, audio })
}

/// Strip the ASS override blocks and <font> wrappers that leak in from SRT
/// files, so WebVTT does not render them as literal text.
fn clean_cue_text(line: &str) -> String {
    let mut out = String::with_capacity(line.len());
    let mut chars = line.chars().peekable();
    while let Some(ch) = chars.next() {
        if ch == '{' {
            for next in chars.by_ref() {
                if next == '}' {
                    break;
                }
            }
            continue;
        }
        out.push(ch);
    }
    out.replace("<font", "")
        .replace("</font>", "")
        .trim_end()
        .to_string()
}

fn srt_to_vtt(input: &str) -> String {
    let text = input.trim_start_matches('\u{feff}');
    if text.trim_start().starts_with("WEBVTT") {
        return text.to_string();
    }

    let mut out = String::with_capacity(text.len() + 16);
    out.push_str("WEBVTT\n\n");

    let mut lines = text.lines().peekable();
    while let Some(line) = lines.next() {
        let trimmed = line.trim();
        // A cue index: a lone number followed by a timestamp line.
        if !trimmed.is_empty()
            && trimmed.chars().all(|ch| ch.is_ascii_digit())
            && lines
                .peek()
                .map(|next| next.contains("-->"))
                .unwrap_or(false)
        {
            continue;
        }
        if trimmed.contains("-->") {
            // 00:00:01,000 --> 00:00:04,000  becomes WebVTT's dotted form.
            out.push_str(&trimmed.replace(',', "."));
            out.push('\n');
            continue;
        }
        if trimmed.is_empty() {
            out.push('\n');
            continue;
        }
        out.push_str(&clean_cue_text(line));
        out.push('\n');
    }
    out
}

fn read_subtitle_blocking(path: &str) -> Result<String, String> {
    let file = PathBuf::from(path);
    let ext = file
        .extension()
        .and_then(|ext| ext.to_str())
        .map(|ext| ext.to_ascii_lowercase())
        .unwrap_or_default();
    if !SUB_EXTS.contains(&ext.as_str()) {
        return Err("That is not a subtitle file.".to_string());
    }
    let meta = fs::metadata(&file).map_err(|e| format!("Could not read that subtitle: {e}"))?;
    if meta.len() > 8 * 1024 * 1024 {
        return Err("That subtitle file is too large.".to_string());
    }
    let bytes = fs::read(&file).map_err(|e| format!("Could not read that subtitle: {e}"))?;
    let text = String::from_utf8_lossy(&bytes);
    Ok(srt_to_vtt(&text))
}

// ---------------------------------------------------------------------------
// Shell helpers
// ---------------------------------------------------------------------------

fn reveal_blocking(path: &str) -> Result<(), String> {
    let target = PathBuf::from(path);
    if !target.exists() {
        return Err("That file is no longer there.".to_string());
    }
    let arg = format!("/select,\"{}\"", target.display());
    Command::new("explorer")
        .arg(arg)
        .spawn()
        .map(|_| ())
        .map_err(|e| format!("Could not open Explorer: {e}"))
}

fn open_blocking(path: &str) -> Result<(), String> {
    let target = PathBuf::from(path);
    if !target.exists() {
        return Err("That file is no longer there.".to_string());
    }
    let target_text = target.to_string_lossy().to_string();
    Command::new("cmd")
        .args(["/C", "start", "", target_text.as_str()])
        .spawn()
        .map(|_| ())
        .map_err(|e| format!("Could not open the file: {e}"))
}

fn open_url_blocking(url: &str) -> Result<(), String> {
    if !(url.starts_with("https://") || url.starts_with("http://")) {
        return Err("Only web links can be opened.".to_string());
    }
    Command::new("cmd")
        .args(["/C", "start", "", url])
        .spawn()
        .map(|_| ())
        .map_err(|e| format!("Could not open the link: {e}"))
}

// ---------------------------------------------------------------------------
// Tauri commands
// ---------------------------------------------------------------------------

#[derive(Default)]
pub struct AppState {
    trash_lock: Arc<Mutex<()>>,
}

#[tauri::command]
async fn scan_library(app: AppHandle) -> Result<Library, String> {
    let handle = app.clone();
    tauri::async_runtime::spawn_blocking(move || scan_blocking(&handle))
        .await
        .map_err(|e| e.to_string())?
}

#[tauri::command]
fn get_roots(app: AppHandle) -> Vec<String> {
    read_roots(&app)
        .iter()
        .map(|path| path.to_string_lossy().to_string())
        .collect()
}

#[tauri::command]
fn add_root(app: AppHandle, path: String) -> Result<Vec<String>, String> {
    let candidate = PathBuf::from(&path);
    if !candidate.is_dir() {
        return Err("Please choose a folder.".to_string());
    }
    let mut roots = read_roots(&app);
    if !roots.iter().any(|root| paths_equal(root, &candidate)) {
        roots.push(candidate);
        write_roots(&app, &roots)?;
    }
    Ok(roots
        .iter()
        .map(|path| path.to_string_lossy().to_string())
        .collect())
}

#[tauri::command]
fn remove_root(app: AppHandle, path: String) -> Result<Vec<String>, String> {
    let roots: Vec<PathBuf> = read_roots(&app)
        .into_iter()
        .filter(|root| !paths_equal(root, Path::new(&path)))
        .collect();
    write_roots(&app, &roots)?;
    Ok(roots
        .iter()
        .map(|path| path.to_string_lossy().to_string())
        .collect())
}

#[tauri::command]
async fn make_thumbnail(
    app: AppHandle,
    path: String,
    size: Option<u32>,
) -> Result<Option<String>, String> {
    let handle = app.clone();
    tauri::async_runtime::spawn_blocking(move || {
        thumbnail_blocking(&handle, &path, size.unwrap_or(480))
    })
    .await
    .map_err(|e| e.to_string())?
}

#[tauri::command]
async fn trash_items(
    app: AppHandle,
    state: State<'_, AppState>,
    paths: Vec<String>,
) -> Result<usize, String> {
    let lock = state.trash_lock.clone();
    let handle = app.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let _guard = lock.lock().map_err(|_| "The trash is busy.".to_string())?;
        trash_items_blocking(&handle, paths)
    })
    .await
    .map_err(|e| e.to_string())?
}

#[tauri::command]
async fn list_trash(app: AppHandle, state: State<'_, AppState>) -> Result<Vec<TrashEntry>, String> {
    let lock = state.trash_lock.clone();
    let handle = app.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let _guard = lock.lock().map_err(|_| "The trash is busy.".to_string())?;
        list_trash_blocking(&handle)
    })
    .await
    .map_err(|e| e.to_string())?
}

#[tauri::command]
async fn restore_trash(
    app: AppHandle,
    state: State<'_, AppState>,
    ids: Vec<String>,
) -> Result<usize, String> {
    let lock = state.trash_lock.clone();
    let handle = app.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let _guard = lock.lock().map_err(|_| "The trash is busy.".to_string())?;
        restore_trash_blocking(&handle, ids)
    })
    .await
    .map_err(|e| e.to_string())?
}

#[tauri::command]
async fn delete_forever(
    app: AppHandle,
    state: State<'_, AppState>,
    ids: Vec<String>,
) -> Result<usize, String> {
    let lock = state.trash_lock.clone();
    let handle = app.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let _guard = lock.lock().map_err(|_| "The trash is busy.".to_string())?;
        delete_forever_blocking(&handle, ids)
    })
    .await
    .map_err(|e| e.to_string())?
}

#[tauri::command]
async fn empty_trash(app: AppHandle, state: State<'_, AppState>) -> Result<usize, String> {
    let lock = state.trash_lock.clone();
    let handle = app.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let _guard = lock.lock().map_err(|_| "The trash is busy.".to_string())?;
        empty_trash_blocking(&handle)
    })
    .await
    .map_err(|e| e.to_string())?
}

#[tauri::command]
async fn reveal_in_explorer(path: String) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || reveal_blocking(&path))
        .await
        .map_err(|e| e.to_string())?
}

#[tauri::command]
async fn open_external(path: String) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || open_blocking(&path))
        .await
        .map_err(|e| e.to_string())?
}

#[tauri::command]
async fn open_url(url: String) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || open_url_blocking(&url))
        .await
        .map_err(|e| e.to_string())?
}

#[tauri::command]
async fn list_video_tracks(path: String) -> Result<VideoTracks, String> {
    tauri::async_runtime::spawn_blocking(move || list_tracks_blocking(&path))
        .await
        .map_err(|e| e.to_string())?
}

#[tauri::command]
async fn read_subtitle(path: String) -> Result<String, String> {
    tauri::async_runtime::spawn_blocking(move || read_subtitle_blocking(&path))
        .await
        .map_err(|e| e.to_string())?
}

#[tauri::command]
fn app_info(app: AppHandle) -> AppInfo {
    AppInfo {
        version: app.package_info().version.to_string(),
        data_dir: data_dir(&app)
            .map(|path| path.to_string_lossy().to_string())
            .unwrap_or_default(),
        cache_dir: cache_dir(&app)
            .map(|path| path.to_string_lossy().to_string())
            .unwrap_or_default(),
        roots: read_roots(&app)
            .iter()
            .map(|path| path.to_string_lossy().to_string())
            .collect(),
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .manage(AppState::default())
        .invoke_handler(tauri::generate_handler![
            scan_library,
            get_roots,
            add_root,
            remove_root,
            make_thumbnail,
            trash_items,
            list_trash,
            restore_trash,
            delete_forever,
            empty_trash,
            reveal_in_explorer,
            open_external,
            open_url,
            list_video_tracks,
            read_subtitle,
            app_info,
        ])
        .run(tauri::generate_context!())
        .expect("VGallery failed to start");
}
