import { convertFileSrc, invoke } from "@tauri-apps/api/core";

export type MediaKind = "image" | "video" | "motion";

export interface MediaItem {
  id: string;
  path: string;
  name: string;
  folder: string;
  folderPath: string;
  ext: string;
  kind: MediaKind;
  size: number;
  modified: number;
}

export interface FolderEntry {
  name: string;
  path: string;
  items: MediaItem[];
}

export interface Library {
  folders: FolderEntry[];
  roots: string[];
  totalCount: number;
  totalSize: number;
  scannedAt: number;
}

export interface TrashEntry {
  id: string;
  original: string;
  stored: string;
  name: string;
  size: number;
  trashedAt: number;
  kind: MediaKind;
}

export interface AppInfo {
  version: string;
  dataDir: string;
  cacheDir: string;
  roots: string[];
}

/** A subtitle or extra audio file that sits next to a video. */
export interface TrackOption {
  path: string;
  name: string;
  label: string;
  lang: string;
  format: string;
}

export interface VideoTracks {
  subtitles: TrackOption[];
  audio: TrackOption[];
}

declare global {
  interface Window {
    __TAURI_INTERNALS__?: unknown;
  }
}

export const inTauri = (): boolean => typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;

/** Turn an absolute Windows path into something an <img>/<video> can load. */
export function assetUrl(path: string): string {
  if (!inTauri()) return path;
  try {
    return convertFileSrc(path);
  } catch {
    return path;
  }
}

export const scanLibrary = (): Promise<Library> => invoke<Library>("scan_library");
export const getRoots = (): Promise<string[]> => invoke<string[]>("get_roots");
export const addRoot = (path: string): Promise<string[]> => invoke<string[]>("add_root", { path });
export const removeRoot = (path: string): Promise<string[]> => invoke<string[]>("remove_root", { path });
export const makeThumbnail = (path: string, size = 480): Promise<string | null> =>
  invoke<string | null>("make_thumbnail", { path, size });
export const trashItems = (paths: string[]): Promise<number> => invoke<number>("trash_items", { paths });
export const listTrash = (): Promise<TrashEntry[]> => invoke<TrashEntry[]>("list_trash");
export const restoreTrash = (ids: string[]): Promise<number> => invoke<number>("restore_trash", { ids });
export const deleteForever = (ids: string[]): Promise<number> => invoke<number>("delete_forever", { ids });
export const emptyTrash = (): Promise<number> => invoke<number>("empty_trash");
export const revealInExplorer = (path: string): Promise<void> => invoke<void>("reveal_in_explorer", { path });
export const openExternal = (path: string): Promise<void> => invoke<void>("open_external", { path });
export const openUrl = (url: string): Promise<void> => invoke<void>("open_url", { url });
export const listVideoTracks = (path: string): Promise<VideoTracks> =>
  invoke<VideoTracks>("list_video_tracks", { path });
/** Returns WebVTT text; `.srt` sidecars are converted in Rust. */
export const readSubtitle = (path: string): Promise<string> =>
  invoke<string>("read_subtitle", { path });
export const appInfo = (): Promise<AppInfo> => invoke<AppInfo>("app_info");
