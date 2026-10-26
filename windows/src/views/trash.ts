import {
  assetUrl,
  deleteForever,
  emptyTrash,
  listTrash,
  makeThumbnail,
  restoreTrash,
  type TrashEntry,
} from "../lib/api";
import { askDialog, clear, h, iconButton, toast } from "../lib/dom";
import { formatAge, formatBytes, plural } from "../lib/format";
import { icon } from "../lib/icons";

export interface TrashContext {
  onBack: () => void;
  onChanged: () => Promise<void>;
}

const THIRTY_DAYS = 30 * 86_400;

export async function renderTrash(container: HTMLElement, ctx: TrashContext): Promise<void> {
  const list = h("div", { class: "trash-list" });
  const subline = h("p", { class: "subpage-sub" }, "Loading Trash…");

  const emptyButton = h(
    "button",
    {
      class: "btn btn-ghost danger-text",
      type: "button",
      onclick: async () => {
        const entries = await listTrash().catch(() => [] as TrashEntry[]);
        if (entries.length === 0) return;
        const ok = await askDialog({
          title: "Empty the Trash?",
          body: h("p", {}, `${entries.length} ${plural(entries.length, "item")} will be deleted permanently. This cannot be undone.`),
          actions: [
            { label: "Cancel", value: false },
            { label: "Delete forever", value: true, danger: true, primary: true },
          ],
        });
        if (!ok) return;
        try {
          const removed = await emptyTrash();
          toast(`${removed} ${plural(removed, "item")} deleted`);
          await ctx.onChanged();
          await load();
        } catch (error) {
          toast(String(error), "error");
        }
      },
    },
    icon("trash", 17),
    "Empty Trash",
  );

  const head = h(
    "header",
    { class: "subpage-head" },
    iconButton("back", "Back to settings", ctx.onBack),
    h("div", {}, h("h1", {}, "Trash"), subline),
    h("div", { class: "head-spacer" }),
    emptyButton,
  );

  container.append(h("div", { class: "settings-page" }, head, h("div", { class: "settings-body" }, list)));

  async function load(): Promise<void> {
    clear(list);
    const entries = await listTrash().catch((error) => {
      toast(String(error), "error");
      return null;
    });
    if (entries === null) {
      emptyButton.disabled = true;
      subline.textContent = "Could not read the Trash.";
      list.append(
        h(
          "div",
          { class: "empty-state compact" },
          icon("info", 38),
          h("h2", {}, "The Trash could not be read"),
          h("p", {}, "Close and reopen VGallery, then try again."),
          h(
            "div",
            { class: "inline-actions" },
            h("button", { class: "btn btn-primary", type: "button", onclick: () => void load() }, "Try again"),
          ),
        ),
      );
      return;
    }
    emptyButton.disabled = entries.length === 0;
    subline.textContent =
      entries.length === 0
        ? "Nothing here. Deleted photos and videos land here first."
        : `${entries.length} ${plural(entries.length, "item")} · ${formatBytes(entries.reduce((sum, entry) => sum + entry.size, 0))}`;

    if (entries.length === 0) {
      list.append(
        h(
          "div",
          { class: "empty-state compact" },
          icon("trash", 38),
          h("h2", {}, "Trash is empty"),
          h("p", {}, "Delete something and it will wait here for 30 days."),
        ),
      );
      return;
    }

    for (const entry of entries) {
      list.append(renderRow(entry));
    }
  }

  function renderRow(entry: TrashEntry): HTMLElement {
    const expired = Date.now() / 1000 - entry.trashedAt > THIRTY_DAYS;
    const thumb = h("div", { class: "trash-thumb" });

    if (entry.kind === "video") {
      thumb.append(icon("videos", 22));
    } else {
      void makeThumbnail(entry.stored, 120)
        .then((path) => {
          if (path) {
            const img = h("img", { src: assetUrl(path), alt: entry.name, loading: "lazy" });
            thumb.append(img);
          } else {
            thumb.append(icon("photos", 22));
          }
        })
        .catch(() => thumb.append(icon("photos", 22)));
    }

    const actions = h(
      "div",
      { class: "trash-actions" },
      h(
        "button",
        {
          class: "btn btn-ghost",
          type: "button",
          onclick: async () => {
            try {
              const restored = await restoreTrash([entry.id]);
              if (restored > 0) {
                toast(`Restored ${entry.name}`);
                await ctx.onChanged();
                await load();
              } else {
                toast("Could not restore it — a file with that name already exists.", "error");
              }
            } catch (error) {
              toast(String(error), "error");
            }
          },
        },
        icon("restore", 16),
        "Restore",
      ),
      h(
        "button",
        {
          class: "btn btn-ghost danger-text",
          type: "button",
          onclick: async () => {
            const ok = await askDialog({
              title: "Delete forever?",
              body: h("p", {}, `“${entry.name}” will be gone for good. This cannot be undone.`),
              actions: [
                { label: "Cancel", value: false },
                { label: "Delete forever", value: true, danger: true, primary: true },
              ],
            });
            if (!ok) return;
            try {
              await deleteForever([entry.id]);
              toast(`${entry.name} deleted forever`);
              await ctx.onChanged();
              await load();
            } catch (error) {
              toast(String(error), "error");
            }
          },
        },
        icon("trash", 16),
        "Delete forever",
      ),
    );

    return h(
      "article",
      { class: "trash-row" },
      thumb,
      h(
        "div",
        { class: "trash-copy" },
        h("div", { class: "trash-name" }, entry.name, expired ? h("span", { class: "badge badge-warn" }, "Expired") : null),
        h("p", { class: "trash-path", title: entry.original }, entry.original),
        h("p", { class: "trash-meta" }, `${formatBytes(entry.size)} · deleted ${formatAge(entry.trashedAt)}`),
      ),
      actions,
    );
  }

  await load();
}
