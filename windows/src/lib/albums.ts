import type { FolderEntry, MediaItem } from "./api";
import { naturalCompare } from "./format";

export interface FolderGroup {
  key: string;
  title: string;
  folders: FolderEntry[];
  items: MediaItem[];
  count: number;
  size: number;
  latest: number;
  photos: number;
  videos: number;
}

const GENERIC_SUFFIXES = new Set([
  "image",
  "images",
  "img",
  "imgs",
  "photo",
  "photos",
  "picture",
  "pictures",
  "pic",
  "pics",
  "video",
  "videos",
  "movie",
  "movies",
  "roll",
  "camera roll",
  "media",
  "files",
]);

function stripAccents(value: string): string {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "");
}

function singular(word: string): string {
  const value = word.toLowerCase();
  if (/(ss|us|is)$/.test(value)) return value;
  if (/(ches|shes|xes|zes|ses)$/.test(value)) return value.slice(0, -2);
  if (value.endsWith("s") && value.length > 3) return value.slice(0, -1);
  return value;
}

/** Split "WhatsApp Video", "CameraRoll" and "100ANDRO" into words. */
export function splitWords(name: string): string[] {
  const spaced = name
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/([A-Z]+)([A-Z][a-z])/g, "$1 $2")
    .replace(/([A-Za-z])(\d)/g, "$1 $2")
    .replace(/(\d)([A-Za-z])/g, "$1 $2")
    .replace(/[_\-.,]+/g, " ");
  return spaced.split(/\s+/).filter(Boolean);
}

/** Two folders share a key when their cleaned-up names match. */
export function groupKey(name: string): string {
  const words = splitWords(name);
  while (words.length > 1) {
    const last = words[words.length - 1].toLowerCase();
    if (!GENERIC_SUFFIXES.has(last) && !GENERIC_SUFFIXES.has(singular(last))) break;
    words.pop();
  }
  while (words.length > 1 && /^\d+$/.test(words[0])) words.shift();
  const joined = stripAccents(words.join(""))
    .toLowerCase()
    .replace(/[^a-z0-9]/g, "");
  return singular(joined);
}

function commonPrefix(names: string[]): string {
  if (names.length === 0) return "";
  let prefix = names[0];
  for (const name of names.slice(1)) {
    let index = 0;
    const limit = Math.min(prefix.length, name.length);
    while (index < limit && prefix[index].toLowerCase() === name[index].toLowerCase()) index += 1;
    prefix = prefix.slice(0, index);
  }
  return prefix;
}

function titleCase(value: string): string {
  if (!value) return "Other";
  return value.charAt(0).toUpperCase() + value.slice(1);
}

/** A human heading for a group: "WhatsApp Images" + "WhatsApp Video" -> "WhatsApp". */
function groupTitle(folders: FolderEntry[], key: string): string {
  if (folders.length === 1) return folders[0].name;
  const names = folders.map((folder) => folder.name);
  const prefix = commonPrefix(names)
    .replace(/[\s_\-.,\d]+$/g, "")
    .trim();
  const compact = prefix.replace(/[^A-Za-z0-9]/g, "");
  if (compact.length >= 3 && prefix.length >= 3) return prefix;
  const withoutDigits = names[0].replace(/^[\d\s_\-]+/, "").trim();
  return withoutDigits || titleCase(key);
}

export function folderStats(folder: FolderEntry): { count: number; size: number; latest: number; photos: number; videos: number } {
  let size = 0;
  let latest = 0;
  let photos = 0;
  let videos = 0;
  for (const item of folder.items) {
    size += item.size;
    if (item.modified > latest) latest = item.modified;
    if (item.kind === "video") videos += 1;
    else photos += 1;
  }
  return { count: folder.items.length, size, latest, photos, videos };
}

export function groupFolders(folders: FolderEntry[], enabled: boolean): FolderGroup[] {
  if (!enabled) {
    return folders.map((folder) => {
      const stats = folderStats(folder);
      return {
        key: folder.path,
        title: folder.name,
        folders: [folder],
        items: folder.items,
        ...stats,
      };
    });
  }

  const buckets = new Map<string, FolderEntry[]>();
  for (const folder of folders) {
    const key = groupKey(folder.name) || folder.path.toLowerCase();
    const bucket = buckets.get(key);
    if (bucket) bucket.push(folder);
    else buckets.set(key, [folder]);
  }

  const groups: FolderGroup[] = [];
  for (const [key, members] of buckets) {
    const items = members.flatMap((folder) => folder.items);
    let size = 0;
    let latest = 0;
    let photos = 0;
    let videos = 0;
    for (const item of items) {
      size += item.size;
      if (item.modified > latest) latest = item.modified;
      if (item.kind === "video") videos += 1;
      else photos += 1;
    }
    groups.push({
      key,
      title: groupTitle(members, key),
      folders: members,
      items,
      count: items.length,
      size,
      latest,
      photos,
      videos,
    });
  }
  return groups;
}

export function sortGroups(groups: FolderGroup[], sort: "newest" | "name"): FolderGroup[] {
  const copy = [...groups];
  if (sort === "newest") {
    copy.sort((a, b) => b.latest - a.latest || naturalCompare(a.title, b.title));
  } else {
    copy.sort((a, b) => naturalCompare(a.title, b.title));
  }
  return copy;
}

export function sortItems(items: MediaItem[], sort: "newest" | "name"): MediaItem[] {
  const copy = [...items];
  if (sort === "newest") {
    copy.sort((a, b) => b.modified - a.modified || naturalCompare(a.name, b.name));
  } else {
    copy.sort((a, b) => naturalCompare(a.name, b.name));
  }
  return copy;
}
