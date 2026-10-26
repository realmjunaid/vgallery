import { open as openFolderDialog } from "@tauri-apps/plugin-dialog";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { listen } from "@tauri-apps/api/event";
import {
  addRoot,
  appInfo,
  emptyTrash,
  listTrash,
  openExternal,
  removeRoot,
  revealInExplorer,
  scanFolder,
  scanLibrary,
  takeLaunchFile,
  trashItems,
  type AppInfo,
  type FolderEntry,
  type Library,
  type MediaItem,
} from "./lib/api";
import { folderStats, groupFolders, sortGroups, sortItems, type FolderGroup } from "./lib/albums";
import { askDialog, clear, h, hasOverlay, iconButton, openSheet, toast } from "./lib/dom";
import { formatBytes, formatDate, kindLabel, plural } from "./lib/format";
import { icon, type IconName } from "./lib/icons";
import { applySettings, loadSettings, resolveTheme, saveSettings, watchSystemTheme, type Settings } from "./lib/settings";
import { thumbFor } from "./lib/thumbnails";
import { openFeed, type FeedHandle } from "./views/feed";
import { renderSettings } from "./views/settings";
import { renderTrash } from "./views/trash";

type Tab = "photos" | "videos";

type View =
  | { name: "home" }
  | { name: "folder"; folder: FolderEntry }
  | { name: "settings" }
  | { name: "trash" };

const app = document.getElementById("app") as HTMLElement;

let settings: Settings = loadSettings();
let library: Library | null = null;
let info: AppInfo | null = null;
let state: View = { name: "home" };
let homeTab: Tab = "photos";
let homeQuery = "";
let selected = new Set<string>();
let currentItems: MediaItem[] = [];
let currentFolderName = "Gallery";
let loading = true;
let loadError: string | null = null;
let trashCount = 0;
let feedHandle: FeedHandle | null = null;
let seenFolders = loadSeen();
const scrollMemory = new Map<string, number>();

const SEEN_KEY = "vgallery.seen.v1";

// ---------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------

function loadSeen(): Set<string> {
  try {
    const raw = localStorage.getItem(SEEN_KEY);
    if (raw) return new Set(JSON.parse(raw) as string[]);
  } catch {
    /* ignore a broken value */
  }
  return new Set();
}

function saveSeen(): void {
  try {
    localStorage.setItem(SEEN_KEY, JSON.stringify([...seenFolders]));
  } catch {
    /* storage full: not important */
  }
}

function markSeen(path: string): void {
  if (seenFolders.has(path)) return;
  seenFolders.add(path);
  saveSeen();
}

function itemsForTab(folder: FolderEntry, tab: Tab): MediaItem[] {
  return tab === "photos"
    ? folder.items.filter((item) => item.kind === "image")
    : folder.items.filter((item) => item.kind === "video" || item.kind === "motion");
}

function tabFolders(tab: Tab): FolderEntry[] {
  if (!library) return [];
  return library.folders
    .map((folder) => ({ ...folder, items: itemsForTab(folder, tab) }))
    .filter((folder) => folder.items.length > 0);
}

function folderCover(folder: FolderEntry): MediaItem | null {
  if (folder.items.length === 0) return null;
  return sortItems(folder.items, "newest")[0];
}

function viewKey(): string {
  if (state.name === "home") return `home:${homeTab}`;
  if (state.name === "folder") return `folder:${state.folder.path}:${homeTab}`;
  return state.name;
}

function currentContent(): HTMLElement | null {
  return document.getElementById("main-content");
}

function restoreScroll(): void {
  const content = currentContent();
  if (!content) return;
  const key = viewKey();
  const saved = scrollMemory.get(key) ?? 0;
  content.scrollTop = saved;
  content.addEventListener("scroll", () => {
    scrollMemory.set(key, content.scrollTop);
  });
}

async function copyText(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
    toast("Copied");
  } catch {
    const area = h("textarea", { style: { position: "fixed", opacity: "0" } });
    area.value = text;
    document.body.append(area);
    area.select();
    document.execCommand("copy");
    area.remove();
    toast("Copied");
  }
}

function openFile(path: string): void {
  void openExternal(path).catch((error) => toast(String(error), "error"));
}

/** Keep the Windows title bar in step with the in-app palette. */
function syncWindowTheme(): void {
  const theme = resolveTheme(settings.theme);
  void getCurrentWindow().setTheme(theme).catch(() => {
    /* older WebView2 builds may not support theming */
  });
}

// ---------------------------------------------------------------------------
// Library
// ---------------------------------------------------------------------------

async function refreshLibrary(options: { silent?: boolean } = {}): Promise<void> {
  if (!options.silent) {
    loading = true;
    loadError = null;
  }
  try {
    const next = await scanLibrary();
    library = next;
    loading = false;
    loadError = null;
    if (localStorage.getItem(SEEN_KEY) === null) {
      seenFolders = new Set(next.folders.map((folder) => folder.path));
      saveSeen();
    }
    trashCount = (await listTrash().catch(() => [])).length;
  } catch (error) {
    loading = false;
    loadError = String(error);
  }
}

function folderByPath(path: string): FolderEntry | null {
  return library?.folders.find((folder) => folder.path === path) ?? null;
}

async function pickFolder(): Promise<void> {
  try {
    const picked = await openFolderDialog({
      directory: true,
      multiple: false,
      title: "Add a folder to VGallery",
    });
    if (typeof picked !== "string") return;
    const roots = await addRoot(picked);
    if (library) library.roots = roots;
    toast("Folder added");
    await refreshLibrary({ silent: true });
    render();
  } catch (error) {
    toast(String(error), "error");
  }
}

// ---------------------------------------------------------------------------
// Render dispatcher
// ---------------------------------------------------------------------------

function render(): void {
  clear(app);
  switch (state.name) {
    case "home":
      renderHome();
      break;
    case "folder":
      renderFolder();
      break;
    case "settings":
      void renderSettingsPage();
      break;
    case "trash":
      void renderTrashPage();
      break;
  }
  restoreScroll();
}

// ---------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------

function brand(): HTMLElement {
  return h(
    "div",
    { class: "brand" },
    h("div", { class: "brand-mark" }, h("span", {}, "V"), icon("play", 13)),
    h("div", { class: "brand-copy" }, h("h1", {}, "VGallery"), h("p", {}, "for Windows")),
  );
}

function renderHome(): void {
  const shell = h("div", { class: "app-shell" });

  const photoCount = tabFolders("photos").reduce((sum, folder) => sum + folder.items.length, 0);
  const videoCount = tabFolders("videos").reduce((sum, folder) => sum + folder.items.length, 0);

  const tab = (name: Tab, label: string, iconName: IconName, count: number): HTMLElement =>
    h(
      "button",
      {
        class: `tab ${homeTab === name ? "active" : ""}`.trim(),
        type: "button",
        onclick: () => {
          if (homeTab === name) return;
          homeTab = name;
          homeQuery = "";
          scrollMemory.delete(`home:${homeTab}`);
          render();
        },
      },
      icon(iconName, 17),
      label,
      h("span", { class: "tab-count" }, String(count)),
    );

  const search = h("input", {
    class: "search-input",
    type: "search",
    placeholder: "Search folders",
    value: homeQuery,
    "aria-label": "Search folders",
  }) as HTMLInputElement;
  search.addEventListener("input", () => {
    homeQuery = search.value;
    filterHome();
  });

  const topbar = h(
    "header",
    { class: "topbar" },
    brand(),
    h("nav", { class: "tabs", role: "tablist" }, tab("photos", "Photos", "photos", photoCount), tab("videos", "Videos", "videos", videoCount)),
    h("div", { class: "search" }, icon("search", 17), search),
    iconButton("refresh", "Rescan library", () => {
      void (async () => {
        toast("Scanning your folders…");
        await refreshLibrary();
        render();
      })();
    }),
    iconButton("settings", "Settings", () => {
      state = { name: "settings" };
      render();
    }),
  );

  const content = h("main", { class: "content home-content", id: "main-content" });
  shell.append(topbar, content);
  app.append(shell);

  if (loading) {
    content.append(loadingState());
    return;
  }
  if (loadError) {
    content.append(
      errorState(loadError, () => {
        void refreshLibrary().then(render);
      }),
    );
    return;
  }

  const folders = tabFolders(homeTab);
  const totalSize = folders.reduce((sum, folder) => sum + folderStats(folder).size, 0);
  const totalItems = folders.reduce((sum, folder) => sum + folder.items.length, 0);

  const head = h(
    "div",
    { class: "home-head" },
    h(
      "div",
      {},
      h("h2", {}, homeTab === "photos" ? "Photos" : "Videos"),
      h(
        "p",
        {},
        library && library.folders.length > 0
          ? `${totalItems} ${plural(totalItems, "item")} · ${formatBytes(totalSize)} · ${folders.length} ${plural(folders.length, "folder")}`
          : "Your library, folder by folder.",
      ),
    ),
    h(
      "div",
      { class: "home-actions" },
      h(
        "button",
        { class: "btn btn-ghost", type: "button", onclick: () => void pickFolder() },
        icon("plus", 17),
        "Add folder",
      ),
    ),
  );

  const groupsRoot = h("div", { class: "home-groups", id: "home-groups" });
  content.append(head, groupsRoot);

  if (folders.length === 0) {
    content.append(emptyLibrary(homeTab));
    return;
  }

  const allGroups = sortGroups(groupFolders(folders, settings.groupSimilar), "newest");
  if (settings.groupSimilar) {
    for (const group of allGroups) {
      const wrapper = h("section", { class: "group-block", dataset: { search: groupSearchText(group) } });
      if (group.folders.length > 1) {
        wrapper.append(
          h(
            "div",
            { class: "group-head" },
            h("h3", { class: "group-title" }, group.title),
            h("span", { class: "group-sub" }, `${group.folders.length} folders`),
          ),
        );
      }
      const grid = h("div", { class: "folder-grid" });
      for (const folder of group.folders) {
        grid.append(folderCard(folder, homeTab));
      }
      wrapper.append(grid);
      groupsRoot.append(wrapper);
    }
  } else {
    // No grouping: one grid, so the cards line up in a single flow.
    const grid = h("div", { class: "folder-grid" });
    for (const folder of allGroups.flatMap((group) => group.folders)) {
      grid.append(folderCard(folder, homeTab));
    }
    groupsRoot.append(grid);
  }

  const noMatch = h("div", { class: "empty-state compact hidden", id: "no-match" }, icon("search", 34), h("h2", {}, "No folders match"), h("p", {}, "Try another name."));
  content.append(noMatch);

  filterHome();
}

function groupSearchText(group: FolderGroup): string {
  return `${group.title} ${group.folders.map((folder) => folder.name).join(" ")}`.toLowerCase();
}

function filterHome(): void {
  const query = homeQuery.trim().toLowerCase();
  const cards = document.querySelectorAll<HTMLElement>(".folder-card");
  let visible = 0;
  for (const card of cards) {
    const match = query === "" || (card.dataset.search ?? "").includes(query);
    card.classList.toggle("hidden", !match);
    if (match) visible += 1;
  }
  // A grouped block disappears with its last matching card.
  for (const group of document.querySelectorAll<HTMLElement>(".group-block")) {
    const any = group.querySelector(".folder-card:not(.hidden)") !== null;
    group.classList.toggle("hidden", !any);
  }
  document.getElementById("no-match")?.classList.toggle("hidden", visible !== 0 || query === "");
}

function folderCard(folder: FolderEntry, tab: Tab): HTMLElement {
  const stats = folderStats(folder);
  const cover = folderCover(folder);
  const isNew = !seenFolders.has(folder.path);

  const coverWrap = h("div", { class: "folder-cover" });
  if (cover) {
    const thumb = thumbFor(cover, 640);
    // The cover keeps the photo's own colours; a soft gradient sits behind it.
    if (cover.kind !== "image") coverWrap.classList.add("has-motion");
    coverWrap.append(thumb);
  } else {
    coverWrap.classList.add("is-fallback");
  }

  const badgeText = `${stats.count} ${plural(stats.count, tab === "photos" ? "photo" : "video")}`;

  const coverBits: (Node | string)[] = [
    h("div", { class: "cover-shade" }),
    h("span", { class: "folder-count" }, badgeText),
    h("span", { class: "folder-size" }, formatBytes(stats.size)),
  ];
  if (isNew) coverBits.push(h("span", { class: "new-dot", title: "New folder" }));
  coverWrap.append(...coverBits);

  if (cover?.kind === "video") {
    coverWrap.append(h("span", { class: "cover-play" }, icon("play", 18)));
  }

  const card = h(
    "article",
    {
      class: "folder-card",
      tabindex: "0",
      role: "button",
      dataset: { search: folder.name.toLowerCase() },
      onclick: () => openFolder(folder, tab),
      onkeydown: (event: KeyboardEvent) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          openFolder(folder, tab);
        }
      },
    },
    coverWrap,
    h(
      "div",
      { class: "folder-meta" },
      h("h3", { title: folder.name }, folder.name),
      h("p", {}, `${stats.count} ${plural(stats.count, "item")} · ${formatBytes(stats.size)}`),
    ),
  );
  return card;
}

function openFolder(folder: FolderEntry, tab: Tab): void {
  markSeen(folder.path);
  const items = itemsForTab(folder, tab);
  if (items.length === 0) {
    toast("That folder has nothing to show yet.");
    return;
  }
  selected = new Set();
  state = { name: "folder", folder: { ...folder, items } };
  render();
}

function loadingState(): HTMLElement {
  const grid = h("div", { class: "folder-grid skeleton-grid" });
  for (let i = 0; i < 8; i += 1) grid.append(h("div", { class: "skeleton-card" }));
  return h(
    "div",
    { class: "home-loading" },
    h("div", { class: "loading-line" }, h("span", { class: "spinner" }), "Reading your library…"),
    grid,
  );
}

function errorState(message: string, retry: () => void): HTMLElement {
  return h(
    "div",
    { class: "empty-state" },
    icon("info", 40),
    h("h2", {}, "Could not read the library"),
    h("p", {}, message),
    h("button", { class: "btn btn-primary", type: "button", onclick: retry }, "Try again"),
  );
}

function emptyLibrary(tab: Tab): HTMLElement {
  const otherTab: Tab = tab === "photos" ? "videos" : "photos";
  const hasAnything = (library?.totalCount ?? 0) > 0;
  return h(
    "div",
    { class: "empty-state", id: "empty-library" },
    icon(tab === "photos" ? "photos" : "videos", 42),
    h("h2", {}, tab === "photos" ? "No photos yet" : "No videos yet"),
    h(
      "p",
      {},
      hasAnything
        ? `There are no ${tab} in your folders yet. Switch to ${otherTab === "photos" ? "Photos" : "Videos"}, or add another folder.`
        : "Add a folder and VGallery will lay it out like a reel.",
    ),
    h(
      "div",
      { class: "inline-actions" },
      hasAnything
        ? h(
            "button",
            {
              class: "btn btn-ghost",
              type: "button",
              onclick: () => {
                homeTab = otherTab;
                homeQuery = "";
                render();
              },
            },
            icon(otherTab === "photos" ? "photos" : "videos", 17),
            otherTab === "photos" ? "Open Photos" : "Open Videos",
          )
        : h("button", { class: "btn btn-primary", type: "button", onclick: () => void pickFolder() }, icon("plus", 17), "Add folder"),
      h("button", { class: "btn btn-ghost", type: "button", onclick: () => { state = { name: "settings" }; render(); } }, icon("settings", 16), "Settings"),
    ),
  );
}

// ---------------------------------------------------------------------------
// Folder view
// ---------------------------------------------------------------------------

function renderFolder(): void {
  const active = state;
  if (active.name !== "folder") {
    state = { name: "home" };
    render();
    return;
  }
  const folder = folderByPath(active.folder.path);
  if (!folder) {
    state = { name: "home" };
    render();
    return;
  }
  const items = sortItems(itemsForTab(folder, homeTab), settings.sort);
  currentItems = items;
  currentFolderName = folder.name;

  const stats = folderStats({ ...folder, items });

  const sortSelect = h(
    "select",
    {
      class: "select",
      "aria-label": "Sort items",
      onchange: (event: Event) => {
        settings.sort = (event.target as HTMLSelectElement).value as "newest" | "name";
        saveSettings(settings);
        render();
      },
    },
    h("option", { value: "name" }, "Name A–Z"),
    h("option", { value: "newest" }, "Newest first"),
  ) as HTMLSelectElement;
  sortSelect.value = settings.sort;

  const gridButton = h(
    "button",
    {
      class: `icon-btn ${settings.folderView === "grid" ? "is-active" : ""}`.trim(),
      type: "button",
      title: "Grid view",
      "aria-label": "Grid view",
      onclick: () => {
        settings.folderView = "grid";
        saveSettings(settings);
        render();
      },
    },
    icon("grid", 17),
  );
  const listButton = h(
    "button",
    {
      class: `icon-btn ${settings.folderView === "list" ? "is-active" : ""}`.trim(),
      type: "button",
      title: "List view",
      "aria-label": "List view",
      onclick: () => {
        settings.folderView = "list";
        saveSettings(settings);
        render();
      },
    },
    icon("list", 17),
  );

  const head = h(
    "header",
    { class: "folder-head" },
    iconButton("back", "Back to library", () => goHome()),
    h(
      "div",
      { class: "folder-heading" },
      h("h1", { title: folder.name }, folder.name),
      h(
        "p",
        {},
        `${stats.count} ${plural(stats.count, "item")} · ${formatBytes(stats.size)}`,
      ),
    ),
    h("div", { class: "head-spacer" }),
    h("div", { class: "view-switch" }, gridButton, listButton),
    sortSelect,
  );

  const content = h("main", { class: "content folder-content", id: "main-content" });
  const shell = h("div", { class: "app-shell" }, head, content);

  const itemsWrap = h("div", { class: settings.folderView === "grid" ? "media-grid" : "media-list" });
  if (items.length === 0) {
    content.append(
      h(
        "div",
        { class: "empty-state compact" },
        icon(homeTab === "photos" ? "photos" : "videos", 38),
        h("h2", {}, homeTab === "photos" ? "No photos in this folder" : "No videos in this folder"),
        h("p", {}, "Switch tabs, or go back to the library and pick another folder."),
        h(
          "div",
          { class: "inline-actions" },
          h("button", { class: "btn btn-ghost", type: "button", onclick: () => goHome() }, icon("back", 16), "Back to library"),
        ),
      ),
    );
  } else {
    content.append(itemsWrap);
    items.forEach((item, index) => {
      itemsWrap.append(settings.folderView === "grid" ? gridTile(item, index, items) : listRow(item, index, items));
    });
  }

  const bar = buildSelectionBar();
  shell.append(bar);
  app.append(shell);

  requestAnimationFrame(() => updateSelection());
}

function goHome(): void {
  selected = new Set();
  state = { name: "home" };
  render();
}

function gridTile(item: MediaItem, index: number, items: MediaItem[]): HTMLElement {
  const tile = h("figure", {
    class: "tile",
    tabindex: "0",
    role: "button",
    "aria-label": `${item.name}, ${kindLabel(item.kind)}, ${formatBytes(item.size)}`,
    dataset: { id: item.id },
    onclick: () => activateItem(item, index, items),
    onkeydown: (event: KeyboardEvent) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        activateItem(item, index, items);
      }
    },
  });

  const media = h("div", { class: "tile-media" });
  media.append(thumbFor(item, 520));

  const check = h(
    "button",
    { class: "tile-check", type: "button", title: "Select", "aria-label": `Select ${item.name}` },
    icon("check", 14),
  );
  check.addEventListener("click", (event) => {
    event.stopPropagation();
    toggleSelect(item.id);
  });

  const tileBits: (Node | string)[] = [media, check];
  if (item.kind === "video") tileBits.push(h("span", { class: "tile-badge" }, icon("play", 11), "Video"));
  else if (item.kind === "motion") tileBits.push(h("span", { class: "tile-badge" }, "GIF"));
  tileBits.push(
    h(
      "figcaption",
      { class: "tile-caption" },
      h("span", { class: "tile-name", title: item.name }, item.name),
      h("span", { class: "tile-size" }, formatBytes(item.size)),
    ),
  );
  tile.append(...tileBits);
  return tile;
}

function listRow(item: MediaItem, index: number, items: MediaItem[]): HTMLElement {
  const row = h("div", {
    class: "list-row",
    tabindex: "0",
    role: "button",
    "aria-label": `${item.name}, ${kindLabel(item.kind)}, ${formatBytes(item.size)}`,
    dataset: { id: item.id },
    onclick: () => activateItem(item, index, items),
    onkeydown: (event: KeyboardEvent) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        activateItem(item, index, items);
      }
    },
  });

  const thumb = h("div", { class: "list-thumb" });
  thumb.append(thumbFor(item, 160));

  const check = h(
    "button",
    { class: "tile-check always", type: "button", title: "Select", "aria-label": `Select ${item.name}` },
    icon("check", 14),
  );
  check.addEventListener("click", (event) => {
    event.stopPropagation();
    toggleSelect(item.id);
  });

  row.append(
    check,
    thumb,
    h(
      "div",
      { class: "list-copy" },
      h("strong", { title: item.name }, item.name),
      h("span", {}, `${kindLabel(item.kind)} · ${item.ext.toUpperCase()} · ${formatDate(item.modified)}`),
    ),
    h("span", { class: "list-size" }, formatBytes(item.size)),
    h("span", { class: "list-open" }, icon(item.kind === "video" ? "play" : "expand", 16)),
  );
  return row;
}

function activateItem(item: MediaItem, index: number, items: MediaItem[]): void {
  if (selected.size > 0) {
    toggleSelect(item.id);
    return;
  }
  openViewer(items, index, currentFolderName);
}

function buildSelectionBar(): HTMLElement {
  const countLabel = h("span", { class: "selection-count" });
  const bar = h(
    "div",
    { class: "selection-bar", id: "selection-bar" },
    countLabel,
    h(
      "div",
      { class: "selection-actions" },
      h("button", { class: "btn btn-ghost", type: "button", onclick: () => selectAll() }, icon("check", 16), "All"),
      h("button", { class: "btn btn-ghost", type: "button", onclick: () => void shareSelected() }, icon("share", 16), "Share"),
      h("button", { class: "btn btn-ghost", type: "button", onclick: () => showDetails(selectedItems()) }, icon("info", 16), "Details"),
      h("button", { class: "btn btn-ghost danger-text", type: "button", onclick: () => void deleteSelected() }, icon("trash", 16), "Delete"),
      iconButton("close", "Clear selection", () => {
        selected = new Set();
        updateSelection();
      }),
    ),
  );
  return bar;
}

function selectedItems(): MediaItem[] {
  return currentItems.filter((item) => selected.has(item.id));
}

function toggleSelect(id: string): void {
  if (selected.has(id)) selected.delete(id);
  else selected.add(id);
  updateSelection();
}

function selectAll(): void {
  if (currentItems.length > 0 && selected.size === currentItems.length) {
    clearSelection();
    return;
  }
  selected = new Set(currentItems.map((item) => item.id));
  updateSelection();
}

function clearSelection(): void {
  selected = new Set();
  updateSelection();
}

function updateSelection(): void {
  const bar = document.getElementById("selection-bar");
  if (bar) bar.classList.toggle("show", selected.size > 0);
  const count = bar?.querySelector(".selection-count");
  if (count) count.textContent = `${selected.size} selected`;
  document.querySelectorAll<HTMLElement>("[data-id]").forEach((node) => {
    const isSelected = selected.has(node.dataset.id ?? "");
    node.classList.toggle("selected", isSelected);
    const check = node.querySelector<HTMLButtonElement>(".tile-check");
    check?.setAttribute("aria-pressed", String(isSelected));
  });
}

async function deleteSelected(): Promise<void> {
  const items = selectedItems();
  if (items.length === 0) return;
  const ok = await askDialog({
    title: `Move ${items.length} ${plural(items.length, "item")} to Trash?`,
    body: h(
      "p",
      {},
      "They stay restorable for 30 days from Settings › Trash. Nothing is deleted for good right now.",
    ),
    actions: [
      { label: "Cancel", value: false },
      { label: "Move to Trash", value: true, danger: true, primary: true },
    ],
  });
  if (!ok) return;

  try {
    releaseVideoThumbs();
    const moved = await trashItems(items.map((item) => item.path));
    if (moved === 0) {
      toast("Nothing could be moved — the file may be open in another app.", "error");
      return;
    }
    if (moved < items.length) {
      toast(
        `${moved} of ${items.length} moved to Trash. The rest are open in another app.`,
        "error",
      );
    } else {
      toast(`${moved} ${plural(moved, "item")} moved to Trash`);
    }
    selected = new Set();
    await refreshLibrary({ silent: true });
    if (state.name === "folder" && !folderByPath(state.folder.path)) {
      state = { name: "home" };
    }
    render();
  } catch (error) {
    toast(String(error), "error");
  }
}

/**
 * Windows keeps a file locked while a <video> is still pointing at it. Clear
 * the poster frames before moving files so the move cannot fail on a lock.
 */
function releaseVideoThumbs(): void {
  for (const video of document.querySelectorAll<HTMLVideoElement>("video.thumb-media")) {
    video.pause();
    video.removeAttribute("src");
    video.load();
  }
}

async function shareSelected(): Promise<void> {
  const items = selectedItems();
  if (items.length === 0) return;
  const close = openSheet(
    `Share ${items.length} ${plural(items.length, "item")}`,
    () => {
      return h(
        "div",
        { class: "sheet-body" },
        h(
          "p",
          { class: "sheet-note" },
          "Windows has no system share sheet for files, so these copy the selection instead.",
        ),
        h(
          "div",
          { class: "sheet-actions" },
          h(
            "button",
            {
              class: "btn btn-ghost",
              type: "button",
              onclick: () => {
                void copyText(items.map((item) => item.path).join("\r\n"));
                close();
              },
            },
            icon("share", 17),
            "Copy full paths",
          ),
          h(
            "button",
            {
              class: "btn btn-ghost",
              type: "button",
              onclick: () => {
                void copyText(items.map((item) => item.name).join("\r\n"));
                close();
              },
            },
            icon("list", 17),
            "Copy file names",
          ),
          h(
            "button",
            {
              class: "btn btn-ghost",
              type: "button",
              onclick: () => {
                void revealInExplorer(items[0].path).catch((error) => toast(String(error), "error"));
                close();
              },
            },
            icon("folder", 17),
            "Reveal in Explorer",
          ),
        ),
      );
    },
    { wide: false },
  );
}

function showDetails(items: MediaItem[]): void {
  if (items.length === 0) return;
  const single = items.length === 1 ? items[0] : null;
  const totalSize = items.reduce((sum, item) => sum + item.size, 0);

  openSheet(
    single ? "Details" : `${items.length} items`,
    () =>
      h(
        "div",
        { class: "sheet-body details" },
        single
          ? h(
              "div",
              { class: "detail-list" },
              detailRow("Name", single.name),
              detailRow("Type", `${kindLabel(single.kind)} · ${single.ext.toUpperCase()}`),
              detailRow("Size", formatBytes(single.size)),
              detailRow("Modified", formatDate(single.modified)),
              detailRow("Folder", single.folder),
              h(
                "div",
                { class: "detail-row path-row" },
                h("span", { class: "detail-label" }, "Path"),
                h("code", { class: "detail-value path" }, single.path),
                h("button", { class: "icon-btn subtle", type: "button", title: "Copy path", "aria-label": "Copy path", onclick: () => void copyText(single.path) }, icon("share", 15)),
              ),
            )
          : h(
              "div",
              { class: "detail-list" },
              detailRow("Items", String(items.length)),
              detailRow("Total size", formatBytes(totalSize)),
              h(
                "ul",
                { class: "detail-names" },
                ...items.slice(0, 40).map((item) => h("li", {}, item.name)),
                items.length > 40 ? h("li", { class: "muted" }, `and ${items.length - 40} more…`) : null,
              ),
            ),
        h(
          "div",
          { class: "sheet-actions spread" },
          single
            ? h("button", { class: "btn btn-ghost", type: "button", onclick: () => { openFile(single.path); } }, icon("external", 17), "Open")
            : null,
          h(
            "button",
            {
              class: "btn btn-ghost",
              type: "button",
              onclick: () => void revealInExplorer((single ?? items[0]).path).catch((error) => toast(String(error), "error")),
            },
            icon("folder", 17),
            "Reveal",
          ),
          h(
            "button",
            {
              class: "btn btn-ghost",
              type: "button",
              onclick: () => void copyText(items.map((item) => item.path).join("\r\n")),
            },
            icon("share", 17),
            "Copy paths",
          ),
        ),
      ),
    { wide: !single },
  );
}

function detailRow(label: string, value: string): HTMLElement {
  return h(
    "div",
    { class: "detail-row" },
    h("span", { class: "detail-label" }, label),
    h("span", { class: "detail-value" }, value),
  );
}

// ---------------------------------------------------------------------------
// Feed
// ---------------------------------------------------------------------------

function openViewer(items: MediaItem[], startIndex: number, title: string): void {
  if (feedHandle) return;
  feedHandle = openFeed({
    items,
    startIndex,
    title,
    autoplaySeconds: settings.autoplaySeconds,
    onClose: () => {
      feedHandle = null;
    },
    onInfo: (item) => showDetails([item]),
    onOpen: (item) => openFile(item.path),
  });
}

/**
 * Open a file handed to us by Explorer. We read its folder on demand, so the
 * file does not have to be inside one of the library roots.
 */
async function openFromOs(path: string): Promise<void> {
  try {
    const scan = await scanFolder(path);
    if (scan.items.length === 0) {
      toast("That file is not a photo or video VGallery can show.", "error");
      return;
    }
    const items = sortItems(scan.items, "name");
    const wanted = path.toLowerCase();
    const index = Math.max(
      0,
      items.findIndex((item) => item.path.toLowerCase() === wanted),
    );
    if (feedHandle) {
      feedHandle.destroy();
      feedHandle = null;
      await new Promise((resolve) => setTimeout(resolve, 180));
    }
    openViewer(items, index, scan.name);
  } catch (error) {
    toast(String(error), "error");
  }
}

// ---------------------------------------------------------------------------
// Settings & Trash
// ---------------------------------------------------------------------------

async function renderSettingsPage(): Promise<void> {
  const content = h("main", { class: "content", id: "main-content" });
  app.append(h("div", { class: "app-shell" }, content));

  renderSettings(content, {
    settings,
    library,
    info,
    trashCount,
    onChange: (next) => {
      settings = next;
      saveSettings(settings);
      applySettings(settings);
      syncWindowTheme();
    },
    onBack: () => goHome(),
    onRefresh: async () => {
      toast("Scanning your folders…");
      await refreshLibrary();
      render();
    },
    onOpenTrash: () => {
      state = { name: "trash" };
      render();
    },
    onAddFolder: async () => {
      await pickFolder();
    },
    onRemoveFolder: async (path) => {
      try {
        const roots = await removeRoot(path);
        if (library) library.roots = roots;
        await refreshLibrary({ silent: true });
        render();
        toast("Folder removed from the library");
      } catch (error) {
        toast(String(error), "error");
      }
    },
    onEmptyTrash: async () => {
      try {
        const removed = await emptyTrash();
        toast(`${removed} ${plural(removed, "item")} deleted forever`);
        trashCount = 0;
        await refreshLibrary({ silent: true });
        render();
      } catch (error) {
        toast(String(error), "error");
      }
    },
  });
}

async function renderTrashPage(): Promise<void> {
  const content = h("main", { class: "content", id: "main-content" });
  app.append(h("div", { class: "app-shell" }, content));
  await renderTrash(content, {
    onBack: () => {
      state = { name: "settings" };
      render();
    },
    onChanged: async () => {
      await refreshLibrary({ silent: true });
    },
  });
}

// ---------------------------------------------------------------------------
// Keyboard
// ---------------------------------------------------------------------------

document.addEventListener("keydown", (event) => {
  if (feedHandle) return;
  // A dialog or sheet owns the keys while it is open.
  if (hasOverlay()) return;
  const target = event.target as HTMLElement | null;
  const typing = target?.tagName?.match(/INPUT|TEXTAREA|SELECT/);
  if (typing) {
    // Escape leaves a search box without losing the library.
    if (event.key === "Escape" && state.name === "home") {
      (target as HTMLInputElement).blur();
    }
    return;
  }

  if (state.name === "settings" || state.name === "trash") {
    if (event.key === "Escape") {
      event.preventDefault();
      if (state.name === "trash") {
        state = { name: "settings" };
        render();
      } else {
        goHome();
      }
    }
    return;
  }

  if (state.name === "home") {
    if (event.key === "/" && !event.ctrlKey && !event.metaKey && !event.altKey) {
      event.preventDefault();
      document.querySelector<HTMLInputElement>(".search-input")?.focus();
    }
    return;
  }

  if (state.name !== "folder") return;
  if (event.key === "Escape") {
    if (selected.size > 0) clearSelection();
    else goHome();
  } else if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "a") {
    event.preventDefault();
    selectAll();
  } else if (event.key === "Delete" && selected.size > 0) {
    event.preventDefault();
    void deleteSelected();
  }
});

// ---------------------------------------------------------------------------
// Boot
// ---------------------------------------------------------------------------

async function boot(): Promise<void> {
  applySettings(settings);
  watchSystemTheme(() => {
    if (settings.theme === "system") applySettings(settings);
  });

  // A second launch ("Open with" while we are running) forwards its file here.
  await listen<string>("vgallery:open-file", (event) => {
    void openFromOs(event.payload);
  }).catch(() => {
    /* not running under Tauri: nothing to listen to */
  });

  render();
  info = await appInfo().catch(() => null);
  await refreshLibrary();
  render();

  const launchFile = await takeLaunchFile().catch(() => null);
  if (launchFile) await openFromOs(launchFile);
}

void boot();
