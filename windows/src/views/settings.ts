import { openUrl, revealInExplorer, type AppInfo, type Library } from "../lib/api";
import { askDialog, h, iconButton, openSheet, toast } from "../lib/dom";
import { formatBytes, plural } from "../lib/format";
import { icon, type IconName } from "../lib/icons";
import { ACCENTS, clampSeconds, type AccentName, type Settings, type ThemeMode } from "../lib/settings";

const UPDATE_REPO = "realmjunaid/vgallery";
const RELEASES_API = `https://api.github.com/repos/${UPDATE_REPO}/releases/latest`;
const SOURCE_URL = `https://github.com/${UPDATE_REPO}`;
const ISSUES_URL = `${SOURCE_URL}/issues`;
const DEVELOPER_URL = "https://mjunaid.me";

interface ReleaseAsset {
  name: string;
  browser_download_url: string;
}

interface WindowsRelease {
  tag: string;
  url: string;
  asset: ReleaseAsset | null;
}

function versionParts(value: string): number[] {
  const parts = value
    .trim()
    .replace(/^v/i, "")
    .split(/[^0-9]+/)
    .filter(Boolean)
    .slice(0, 3)
    .map((part) => Number(part) || 0);
  while (parts.length < 3) parts.push(0);
  return parts;
}

function isNewerVersion(remote: string, local: string): boolean {
  const a = versionParts(remote);
  const b = versionParts(local);
  for (let i = 0; i < 3; i += 1) {
    if (a[i] !== b[i]) return a[i] > b[i];
  }
  return false;
}

/** The newest GitHub release, only offered when it ships a Windows installer. */
async function fetchLatestWindowsRelease(signal?: AbortSignal): Promise<WindowsRelease | null> {
  const response = await fetch(RELEASES_API, {
    headers: { Accept: "application/vnd.github+json" },
    signal,
  });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`GitHub answered ${response.status}. Try again later.`);
  const data = (await response.json()) as {
    tag_name?: string;
    html_url?: string;
    assets?: ReleaseAsset[];
  };
  const tag = String(data.tag_name ?? "").trim();
  if (!tag) return null;
  const assets = Array.isArray(data.assets) ? data.assets : [];
  // Android publishes APKs to the same repo; never offer one to a Windows PC.
  const windows = assets.filter((entry) => /\.(exe|msi)$/i.test(entry.name));
  const preferred =
    windows.find((entry) => /(win|x64|setup)/i.test(entry.name)) ?? windows[0] ?? null;
  return { tag, url: data.html_url ?? `${SOURCE_URL}/releases`, asset: preferred };
}

export interface SettingsContext {
  settings: Settings;
  library: Library | null;
  info: AppInfo | null;
  trashCount: number;
  onChange: (settings: Settings) => void;
  onBack: () => void;
  onRefresh: () => Promise<void>;
  onOpenTrash: () => void;
  onAddFolder: () => Promise<void>;
  onRemoveFolder: (path: string) => Promise<void>;
  onEmptyTrash: () => Promise<void>;
}

function section(title: string, ...children: (Node | null)[]): HTMLElement {
  return h(
    "section",
    { class: "settings-section" },
    h("h2", {}, title),
    h("div", { class: "card" }, ...children),
  );
}

function row(title: string, subtitle: string, control: Node): HTMLElement {
  return h(
    "div",
    { class: "setting-row" },
    h("div", { class: "setting-copy" }, h("h3", {}, title), h("p", {}, subtitle)),
    h("div", { class: "setting-control" }, control),
  );
}

function toggle(checked: boolean, onChange: (value: boolean) => void): HTMLElement {
  const button = h("button", {
    class: `switch ${checked ? "on" : ""}`.trim(),
    type: "button",
    role: "switch",
    "aria-checked": String(checked),
    onclick: () => {
      const next = !button.classList.contains("on");
      button.classList.toggle("on", next);
      button.setAttribute("aria-checked", String(next));
      onChange(next);
    },
  });
  return button;
}

function segmented<T extends string>(
  options: { value: T; label: string }[],
  value: T,
  onPick: (value: T) => void,
): HTMLElement {
  const wrap = h("div", { class: "segmented", role: "group" });
  for (const option of options) {
    const button = h(
      "button",
      {
        class: `segment ${option.value === value ? "active" : ""}`.trim(),
        type: "button",
        onclick: () => {
          for (const child of wrap.children) child.classList.remove("active");
          button.classList.add("active");
          onPick(option.value);
        },
      },
      option.label,
    );
    wrap.append(button);
  }
  return wrap;
}

export function renderSettings(container: HTMLElement, ctx: SettingsContext): void {
  const { settings } = ctx;
  const update = (patch: Partial<Settings>) => {
    Object.assign(settings, patch);
    ctx.onChange(settings);
  };

  const back = iconButton("back", "Back to library", () => ctx.onBack());

  const head = h(
    "header",
    { class: "subpage-head" },
    back,
    h("div", {}, h("h1", {}, "Settings"), h("p", {}, "Appearance, playback, library and Trash")),
    h("div", { class: "head-spacer" }),
    h(
      "button",
      { class: "btn btn-ghost", type: "button", onclick: () => void ctx.onRefresh() },
      icon("refresh", 18),
      "Rescan",
    ),
  );

  // --- appearance --------------------------------------------------------

  const accRow = h("div", { class: "accent-row" });
  for (const [name, accent] of Object.entries(ACCENTS) as [AccentName, (typeof ACCENTS)[AccentName]][]) {
    const dot = h("button", {
      class: `accent-dot ${settings.accent === name ? "active" : ""}`.trim(),
      type: "button",
      title: accent.label,
      "aria-label": accent.label,
      style: { background: accent.hex },
      onclick: () => {
        for (const child of accRow.children) child.classList.remove("active");
        dot.classList.add("active");
        update({ accent: name });
      },
    });
    accRow.append(dot);
  }

  const appearance = section(
    "Appearance",
    row(
      "Theme",
      "Follow Windows, or force dark or light.",
      segmented<ThemeMode>(
        [
          { value: "system", label: "System" },
          { value: "dark", label: "Dark" },
          { value: "light", label: "Light" },
        ],
        settings.theme,
        (value) => update({ theme: value }),
      ),
    ),
    row(
      "Pure black",
      "Use true AMOLED black instead of charcoal in the dark theme.",
      toggle(settings.amoled, (value) => update({ amoled: value })),
    ),
    row("Accent", "Used for selection, badges and the play state.", accRow),
  );

  // --- playback ----------------------------------------------------------

  const secondsValue = h("span", { class: "stepper-value" }, `${settings.autoplaySeconds}s`);
  const slider = h("input", {
    type: "range",
    min: "1",
    max: "15",
    step: "1",
    value: String(settings.autoplaySeconds),
    class: "slider",
  }) as HTMLInputElement;
  const setSeconds = (value: number) => {
    const next = clampSeconds(value);
    secondsValue.textContent = `${next}s`;
    slider.value = String(next);
    update({ autoplaySeconds: next });
  };
  const stepper = h(
    "div",
    { class: "stepper" },
    h("button", { class: "stepper-btn", type: "button", onclick: () => setSeconds(settings.autoplaySeconds - 1), "aria-label": "Slower" }, icon("minus", 16)),
    secondsValue,
    h("button", { class: "stepper-btn", type: "button", onclick: () => setSeconds(settings.autoplaySeconds + 1), "aria-label": "Faster" }, icon("plus", 16)),
  );
  slider.addEventListener("input", () => setSeconds(Number(slider.value)));

  const playback = section(
    "Playback",
    row(
      "Photo autoplay",
      "How long a still photo stays on screen in the full-screen feed.",
      h("div", { class: "stepper-stack" }, stepper, slider),
    ),
    h(
      "div",
      { class: "card-note" },
      icon("clock", 15),
      h("span", {}, "Videos, GIFs and animated images always play for their own length — this timer is for photos only."),
    ),
  );

  // --- library -----------------------------------------------------------

  const library = ctx.library;
  const groupToggle = toggle(settings.groupSimilar, (value) => update({ groupSimilar: value }));

  const rootRows = h("div", { class: "root-list" });
  const roots = library?.roots ?? ctx.info?.roots ?? [];
  if (roots.length === 0) {
    rootRows.append(h("p", { class: "empty-note" }, "No folders yet. Add one to start the gallery."));
  }
  for (const root of roots) {
    rootRows.append(
      h(
        "div",
        { class: "root-row" },
        icon("folder", 18),
        h("span", { class: "root-path", title: root }, root),
        h("button", {
          class: "icon-btn subtle",
          type: "button",
          title: "Remove folder",
          "aria-label": `Remove ${root}`,
          onclick: async () => {
            const ok = await askDialog({
              title: "Remove this folder?",
              body: h("p", {}, "VGallery will stop showing its photos. The files stay on disk."),
              actions: [
                { label: "Keep", value: false },
                { label: "Remove", value: true, danger: true, primary: true },
              ],
            });
            if (ok) await ctx.onRemoveFolder(root);
          },
        }, icon("close", 16)),
      ),
    );
  }

  const totalLine = library
    ? `${library.totalCount} ${plural(library.totalCount, "item")} · ${formatBytes(library.totalSize)} · ${library.folders.length} ${plural(library.folders.length, "folder")}`
    : "Scanning…";

  const librarySection = section(
    "Library",
    row(
      "Group similar folders",
      "Gathers Camera Roll with Camera, WhatsApp Images with WhatsApp Video, and so on.",
      groupToggle,
    ),
    h(
      "div",
      { class: "setting-block" },
      h("div", { class: "setting-copy" }, h("h3", {}, "Folders"), h("p", {}, totalLine)),
      rootRows,
      h(
        "div",
        { class: "inline-actions" },
        h(
          "button",
          { class: "btn btn-ghost", type: "button", onclick: () => void ctx.onAddFolder() },
          icon("plus", 17),
          "Add folder",
        ),
      ),
    ),
  );

  // --- trash -------------------------------------------------------------

  const trashSection = section(
    "Trash",
    row(
      "Deleted items",
      "Restore anything within 30 days, or remove it for good.",
      h(
        "div",
        { class: "inline-actions" },
        h(
          "button",
          { class: "btn btn-ghost", type: "button", onclick: () => ctx.onOpenTrash() },
          icon("trash", 17),
          ctx.trashCount > 0 ? `Open Trash (${ctx.trashCount})` : "Open Trash",
        ),
        ctx.trashCount > 0
          ? h(
              "button",
              {
                class: "btn btn-ghost danger-text",
                type: "button",
                onclick: async () => {
                  const ok = await askDialog({
                    title: "Empty the Trash?",
                    body: h("p", {}, `${ctx.trashCount} ${plural(ctx.trashCount, "item")} will be deleted permanently. This cannot be undone.`),
                    actions: [
                      { label: "Cancel", value: false },
                      { label: "Delete forever", value: true, danger: true, primary: true },
                    ],
                  });
                  if (ok) await ctx.onEmptyTrash();
                },
              },
              "Empty",
            )
          : null,
      ),
    ),
  );

  // --- updates -----------------------------------------------------------

  const openLink = (url: string) => {
    void openUrl(url).catch((error) => toast(String(error), "error"));
  };

  const version = ctx.info?.version ?? "0.1.0";
  const updateActions = h("div", { class: "inline-actions" });
  const updateNote = h(
    "p",
    { class: "update-state", role: "status", "aria-live": "polite" },
    `Version ${version} installed. GitHub is asked only when you check.`,
  );
  const checkButton = h(
    "button",
    { class: "btn btn-ghost", type: "button" },
    icon("refresh", 17),
    "Check for updates",
  );
  updateActions.append(checkButton);

  let updateBusy = false;
  const setUpdateNote = (text: string, kind: "" | "ok" | "warn" = "") => {
    updateNote.textContent = text;
    updateNote.className = `update-state ${kind}`.trim();
  };

  checkButton.addEventListener("click", async () => {
    if (updateBusy) return;
    updateBusy = true;
    checkButton.disabled = true;
    checkButton.setAttribute("aria-busy", "true");
    checkButton.replaceChildren(icon("refresh", 17), "Checking…");
    setUpdateNote("Asking GitHub for the newest Windows release…");
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 12_000);
    try {
      const release = await fetchLatestWindowsRelease(controller.signal);
      if (!release) {
        setUpdateNote("No releases are published yet — this build is still a preview.", "warn");
        return;
      }
      if (!release.asset) {
        setUpdateNote(
          `The newest release (${release.tag}) has no Windows installer yet. The Android build is published there, so Windows stays on ${version}.`,
          "warn",
        );
        return;
      }
      if (!isNewerVersion(release.tag, version)) {
        setUpdateNote(`You're up to date — ${version} is the newest Windows build.`, "ok");
        return;
      }
      setUpdateNote(`Version ${release.tag} is ready for Windows.`, "ok");
      const asset = release.asset;
      updateActions.replaceChildren(
        checkButton,
        h(
          "button",
          {
            class: "btn btn-primary",
            type: "button",
            onclick: () =>
              void openUrl(asset.browser_download_url).catch((error) =>
                toast(String(error), "error"),
              ),
          },
          icon("download", 17),
          "Download",
        ),
        h(
          "button",
          { class: "btn btn-ghost", type: "button", onclick: () => openLink(release.url) },
          "Release notes",
          icon("external", 15),
        ),
      );
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        setUpdateNote("GitHub took too long to answer. Check your connection and try again.", "warn");
      } else {
        setUpdateNote("Could not reach GitHub. Check your connection and try again.", "warn");
        toast(error instanceof Error ? error.message : "Update check failed", "error");
      }
    } finally {
      window.clearTimeout(timeout);
      updateBusy = false;
      checkButton.disabled = false;
      checkButton.removeAttribute("aria-busy");
      checkButton.replaceChildren(icon("refresh", 17), "Check for updates");
    }
  });

  const updatesSection = section(
    "Updates",
    h(
      "div",
      { class: "setting-block" },
      h(
        "div",
        { class: "setting-copy" },
        h("h3", {}, "Windows releases"),
        h(
          "p",
          {},
          "Downloaded from GitHub. A release is offered only when it includes a Windows .exe or .msi.",
        ),
      ),
      updateActions,
      updateNote,
    ),
  );

  // --- about -------------------------------------------------------------

  function linkRow(
    iconName: IconName,
    title: string,
    subtitle: string,
    onClick: () => void,
  ): HTMLElement {
    return h(
      "button",
      { class: "setting-link", type: "button", onclick: onClick },
      h(
        "span",
        { class: "setting-link-main" },
        icon(iconName, 19),
        h("span", { class: "setting-copy" }, h("h3", {}, title), h("p", {}, subtitle)),
      ),
      icon("chevron", 17),
    );
  }

  function aboutBullet(iconName: IconName, title: string, body: string): HTMLElement {
    return h(
      "div",
      { class: "about-bullet" },
      icon(iconName, 19),
      h("div", {}, h("h4", {}, title), h("p", {}, body)),
    );
  }

  function sheetLink(title: string, subtitle: string, onClick: () => void): HTMLElement {
    return h(
      "button",
      { class: "sheet-link", type: "button", onclick: onClick },
      h("span", { class: "sheet-link-copy" }, h("h4", {}, title), h("p", {}, subtitle)),
      icon("external", 16),
    );
  }

  function openAboutAppSheet(): void {
    openSheet("About VGallery", (close) => {
      return h(
        "div",
        { class: "sheet-body about-sheet" },
        h(
          "div",
          { class: "about-sheet-brand" },
          icon("sparkle", 26),
          h(
            "div",
            {},
            h("h3", {}, "VGallery for Windows"),
            h("p", {}, `Version ${version} · Rust + Tauri`),
          ),
        ),
        h(
          "p",
          { class: "about-text" },
          "Your camera roll, reimagined as reels. Free, offline, and yours — the same idea as the Android app, rebuilt native for the desktop.",
        ),
        aboutBullet(
          "folder",
          "Folder-wise gallery",
          "Every folder in your Pictures, Videos, Desktop and Downloads, newest first, with Photos and Videos tabs and a grid or list.",
        ),
        aboutBullet(
          "play",
          "Continuous feed",
          "Scroll every photo and video in one strip. Pinch or Ctrl + wheel zooms the whole feed from 1× to 10×, and Autoplay walks it hands-free.",
        ),
        aboutBullet(
          "captions",
          "Subtitles & audio",
          "Videos pick up .srt, .vtt and extra audio sidecars next to the file, and remember the track you chose.",
        ),
        aboutBullet(
          "trash",
          "Trash",
          "Deleting goes to a Trash first, restorable for 30 days — it lives in Settings.",
        ),
        aboutBullet(
          "windows",
          "At home on Windows",
          "Explorer › Open with, a single open window, and no account, ads, analytics or uploads.",
        ),
        h(
          "div",
          { class: "sheet-actions spread" },
          h(
            "button",
            { class: "btn btn-ghost", type: "button", onclick: () => openLink(SOURCE_URL) },
            "Source code",
            icon("external", 15),
          ),
          h("button", { class: "btn btn-primary", type: "button", onclick: close }, "Done"),
        ),
      );
    });
  }

  function openAboutDeveloperSheet(): void {
    openSheet("About Developer", (close) => {
      return h(
        "div",
        { class: "sheet-body about-sheet" },
        h(
          "div",
          { class: "about-sheet-brand" },
          icon("person", 26),
          h("div", {}, h("h3", {}, "M Junaid"), h("p", {}, "Android developer · mjunaid.me")),
        ),
        h(
          "p",
          { class: "about-text" },
          "VGallery is made by one developer who wanted a gallery that simply shows your photos and videos. No account, no ads, no feed, no uploads — just the albums already on the device, opened fast.",
        ),
        h(
          "p",
          { class: "about-text" },
          "Ideas and bug reports from real use shape what gets built next: folder-wise albums, the Trash, the continuous viewer, pinch zoom and autoplay all came from that feedback. If something feels wrong, tell me and it gets fixed in the next build.",
        ),
        h(
          "div",
          { class: "about-links" },
          sheetLink("mjunaid.me", "Website — opens in your browser", () =>
            openLink(DEVELOPER_URL),
          ),
          sheetLink("Source code", `github.com/${UPDATE_REPO} — opens on GitHub`, () =>
            openLink(SOURCE_URL),
          ),
          sheetLink("Issues & ideas", "Bug reports and feature requests", () =>
            openLink(ISSUES_URL),
          ),
        ),
        h(
          "div",
          { class: "sheet-actions spread" },
          h("button", { class: "btn btn-primary", type: "button", onclick: close }, "Done"),
        ),
      );
    });
  }

  const about = section(
    "About",
    linkRow("sparkle", "About VGallery", "Version, features and privacy", openAboutAppSheet),
    linkRow("person", "About Developer", "Who makes VGallery", openAboutDeveloperSheet),
    h(
      "div",
      { class: "setting-block" },
      h(
        "div",
        { class: "setting-copy" },
        h("h3", {}, "Local storage"),
        h("p", {}, "Nothing leaves this PC. No account, no analytics, no uploads."),
      ),
      h(
        "div",
        { class: "inline-actions" },
        h(
          "button",
          {
            class: "btn btn-ghost",
            type: "button",
            onclick: () => {
              const dir = ctx.info?.dataDir;
              if (dir) void revealInExplorer(dir).catch((error) => toast(String(error), "error"));
            },
          },
          "Open data folder",
          icon("folder", 15),
        ),
        h(
          "button",
          {
            class: "btn btn-ghost",
            type: "button",
            onclick: () => {
              const dir = ctx.info?.cacheDir;
              if (dir) void revealInExplorer(dir).catch((error) => toast(String(error), "error"));
            },
          },
          "Open cache folder",
          icon("folder", 15),
        ),
      ),
    ),
  );

  const body = h(
    "div",
    { class: "settings-body" },
    appearance,
    playback,
    librarySection,
    trashSection,
    updatesSection,
    about,
    h("p", { class: "settings-footer" }, "VGallery · offline-first · MIT licensed"),
  );

  container.append(h("div", { class: "settings-page" }, head, body));
}
