export type ThemeMode = "system" | "dark" | "light";

export const ACCENTS = {
  violet: { label: "Violet", hex: "#8B5CF6" },
  blue: { label: "Blue", hex: "#3B82F6" },
  green: { label: "Green", hex: "#22C55E" },
  pink: { label: "Pink", hex: "#EC4899" },
  amber: { label: "Amber", hex: "#F59E0B" },
} as const;

export type AccentName = keyof typeof ACCENTS;

export interface Settings {
  theme: ThemeMode;
  amoled: boolean;
  accent: AccentName;
  autoplaySeconds: number;
  groupSimilar: boolean;
  sort: "newest" | "name";
  folderView: "grid" | "list";
}

const KEY = "vgallery.settings.v1";

export const DEFAULT_SETTINGS: Settings = {
  theme: "dark",
  amoled: true,
  accent: "violet",
  autoplaySeconds: 4,
  groupSimilar: false,
  sort: "name",
  folderView: "grid",
};

export function loadSettings(): Settings {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return { ...DEFAULT_SETTINGS };
    const parsed = JSON.parse(raw) as Partial<Settings>;
    const merged: Settings = { ...DEFAULT_SETTINGS, ...parsed };
    if (!(merged.accent in ACCENTS)) merged.accent = DEFAULT_SETTINGS.accent;
    merged.autoplaySeconds = clampSeconds(merged.autoplaySeconds);
    return merged;
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

export function saveSettings(settings: Settings): void {
  localStorage.setItem(KEY, JSON.stringify(settings));
}

export function clampSeconds(value: number): number {
  if (!Number.isFinite(value)) return DEFAULT_SETTINGS.autoplaySeconds;
  return Math.min(15, Math.max(1, Math.round(value)));
}

const systemQuery = window.matchMedia("(prefers-color-scheme: dark)");

export function resolveTheme(mode: ThemeMode): "dark" | "light" {
  if (mode === "system") return systemQuery.matches ? "dark" : "light";
  return mode;
}

/** Paint the palette onto <body>; every component reads the CSS variables. */
export function applySettings(settings: Settings): void {
  const body = document.body;
  const resolved = resolveTheme(settings.theme);
  body.dataset.theme = resolved;
  body.dataset.amoled = String(settings.amoled);
  body.dataset.accent = settings.accent;
  body.style.setProperty("--accent", ACCENTS[settings.accent].hex);
  body.style.colorScheme = resolved;
}

export function watchSystemTheme(onChange: () => void): () => void {
  const listener = () => onChange();
  systemQuery.addEventListener("change", listener);
  return () => systemQuery.removeEventListener("change", listener);
}
