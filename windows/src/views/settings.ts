import { openUrl, revealInExplorer, type AppInfo, type Library } from "../lib/api";
import { askDialog, h, iconButton, toast } from "../lib/dom";
import { formatBytes, plural } from "../lib/format";
import { icon } from "../lib/icons";
import { ACCENTS, clampSeconds, type AccentName, type Settings, type ThemeMode } from "../lib/settings";

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

  // --- about -------------------------------------------------------------

  const openLink = (url: string) => {
    void openUrl(url).catch((error) => toast(String(error), "error"));
  };

  const about = section(
    "About",
    h(
      "div",
      { class: "about-block" },
      h("div", { class: "about-brand" }, icon("sparkle", 22), h("div", {}, h("h3", {}, "VGallery for Windows"), h("p", {}, `Version ${ctx.info?.version ?? "0.1.0"} · Rust + Tauri`))),
      h("p", { class: "about-text" }, "Your camera roll, reimagined as reels. Free, offline, and yours — the same idea as the Android app, rebuilt native for the desktop."),
      h(
        "div",
        { class: "inline-actions" },
        h("button", { class: "btn btn-ghost", type: "button", onclick: () => openLink("https://mjunaid.me") }, "M Junaid", icon("external", 15)),
        h("button", { class: "btn btn-ghost", type: "button", onclick: () => openLink("https://github.com/realmjunaid/vgallery") }, "App source code", icon("external", 15)),
      ),
    ),
    h(
      "div",
      { class: "setting-block" },
      h("div", { class: "setting-copy" }, h("h3", {}, "Local storage"), h("p", {}, "Nothing leaves this PC. No account, no analytics, no uploads.")),
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
    about,
    h("p", { class: "settings-footer" }, "VGallery · offline-first · MIT licensed"),
  );

  container.append(h("div", { class: "settings-page" }, head, body));
}
