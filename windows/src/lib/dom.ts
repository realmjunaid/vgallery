import { icon, type IconName } from "./icons";

type Props = Record<string, unknown>;
type Child = Node | string | number | null | undefined | false;

/** Tiny hyperscript: attributes, events (onclick), dataset, class and style. */
export function h<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  props: Props = {},
  ...children: Child[]
): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value === undefined || value === null || value === false) continue;
    if (key === "class") {
      el.className = String(value);
    } else if (key === "dataset") {
      Object.assign(el.dataset, value as Record<string, string>);
    } else if (key === "style" && typeof value === "object") {
      Object.assign(el.style, value as Record<string, string>);
    } else if (key === "html") {
      el.innerHTML = String(value);
    } else if (key.startsWith("on") && typeof value === "function") {
      el.addEventListener(key.slice(2).toLowerCase(), value as EventListener);
    } else if (key in el) {
      (el as unknown as Record<string, unknown>)[key] = value;
    } else {
      el.setAttribute(key, String(value));
    }
  }
  for (const child of children) {
    if (child === null || child === undefined || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return el;
}

export function clear(el: Element): void {
  while (el.firstChild) el.removeChild(el.firstChild);
}

export function iconButton(
  name: IconName,
  label: string,
  onClick: (event: MouseEvent) => void,
  extraClass = "",
): HTMLButtonElement {
  const button = h(
    "button",
    {
      class: `icon-btn ${extraClass}`.trim(),
      type: "button",
      title: label,
      "aria-label": label,
      onclick: onClick,
    },
    icon(name, 19),
  );
  return button;
}

export function toast(message: string, kind: "info" | "error" = "info"): void {
  const root = document.getElementById("toast-root");
  if (!root) return;
  const node = h("div", { class: `toast ${kind === "error" ? "toast-error" : ""}` }, message);
  root.append(node);
  requestAnimationFrame(() => node.classList.add("show"));
  window.setTimeout(() => {
    node.classList.remove("show");
    window.setTimeout(() => node.remove(), 220);
  }, 2800);
}

export interface DialogAction {
  label: string;
  value: boolean;
  danger?: boolean;
  primary?: boolean;
}

let overlaySeq = 0;

function focusablesIn(root: HTMLElement): HTMLElement[] {
  return [...root.querySelectorAll<HTMLElement>(
    'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
  )].filter((node) => node.offsetParent !== null || node === document.activeElement);
}

/** Keep Tab inside a modal, the way a native Windows dialog behaves. */
function trapTab(root: HTMLElement, event: KeyboardEvent): void {
  if (event.key !== "Tab") return;
  const items = focusablesIn(root);
  if (items.length === 0) {
    event.preventDefault();
    return;
  }
  const first = items[0];
  const last = items[items.length - 1];
  const active = document.activeElement as HTMLElement | null;
  if (event.shiftKey && (active === first || !root.contains(active))) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && (active === last || !root.contains(active))) {
    event.preventDefault();
    first.focus();
  }
}

function restoreFocus(node: HTMLElement | null): void {
  if (!node || !node.isConnected) return;
  try {
    node.focus({ preventScroll: true });
  } catch {
    /* the original control is gone with its view */
  }
}

export function askDialog(options: {
  title: string;
  body?: Node | string;
  actions: DialogAction[];
  onDismissValue?: boolean;
}): Promise<boolean> {
  const root = document.getElementById("overlay-root") ?? document.body;
  return new Promise((resolve) => {
    const previous = document.activeElement as HTMLElement | null;
    const titleId = `dialog-title-${(overlaySeq += 1)}`;
    let settled = false;
    const finish = (value: boolean) => {
      if (settled) return;
      settled = true;
      document.removeEventListener("keydown", onKey);
      backdrop.remove();
      restoreFocus(previous);
      resolve(value);
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        event.stopPropagation();
        finish(options.onDismissValue ?? false);
      } else {
        trapTab(dialog, event);
      }
    };
    const buttons = options.actions.map((action) =>
      h(
        "button",
        {
          class: `btn ${action.primary ? "btn-primary" : "btn-ghost"} ${action.danger ? "btn-danger" : ""}`.trim(),
          type: "button",
          onclick: () => finish(action.value),
        },
        action.label,
      ),
    );
    const dialog = h(
      "div",
      { class: "dialog", role: "dialog", "aria-modal": "true", "aria-labelledby": titleId },
      h("h2", { class: "dialog-title", id: titleId }, options.title),
      options.body ? h("div", { class: "dialog-body" }, options.body) : null,
      h("div", { class: "dialog-actions" }, ...buttons),
    );
    const backdrop = h(
      "div",
      {
        class: "modal-backdrop",
        onclick: (event: MouseEvent) => {
          if (event.target === backdrop) finish(options.onDismissValue ?? false);
        },
      },
      dialog,
    );
    document.addEventListener("keydown", onKey);
    root.append(backdrop);
    requestAnimationFrame(() => backdrop.classList.add("show"));
    const primary = backdrop.querySelector<HTMLButtonElement>(".btn-primary");
    (primary ?? buttons[0])?.focus({ preventScroll: true });
  });
}

/** True while a dialog or sheet owns the screen. */
export function hasOverlay(): boolean {
  return document.querySelector(".modal-backdrop") !== null;
}

/** A modal that owns arbitrary content. Returns close() and the content node. */
export function openSheet(
  title: string,
  build: (close: () => void) => Node,
  options: { wide?: boolean } = {},
): () => void {
  const root = document.getElementById("overlay-root") ?? document.body;
  const previous = document.activeElement as HTMLElement | null;
  const titleId = `sheet-title-${(overlaySeq += 1)}`;
  let closed = false;
  const close = () => {
    if (closed) return;
    closed = true;
    document.removeEventListener("keydown", onKey);
    backdrop.classList.remove("show");
    window.setTimeout(() => backdrop.remove(), 160);
    restoreFocus(previous);
  };
  const onKey = (event: KeyboardEvent) => {
    // A confirmation dialog on top of this sheet owns Escape until it closes.
    if (document.querySelectorAll(".modal-backdrop").length > 1) return;
    if (event.key === "Escape") {
      event.preventDefault();
      close();
    } else {
      trapTab(dialog, event);
    }
  };
  const dialog = h(
    "div",
    {
      class: `sheet ${options.wide ? "sheet-wide" : ""}`.trim(),
      role: "dialog",
      "aria-modal": "true",
      "aria-labelledby": titleId,
      tabindex: "-1",
    },
    h(
      "div",
      { class: "sheet-head" },
      h("h2", { id: titleId }, title),
      iconButton("close", "Close", close),
    ),
    build(close),
  );
  const backdrop = h(
    "div",
    {
      class: "modal-backdrop",
      onclick: (event: MouseEvent) => {
        if (event.target === backdrop) close();
      },
    },
    dialog,
  );
  document.addEventListener("keydown", onKey);
  root.append(backdrop);
  requestAnimationFrame(() => backdrop.classList.add("show"));
  dialog.focus({ preventScroll: true });
  return close;
}
