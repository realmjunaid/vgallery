const NS = "http://www.w3.org/2000/svg";

export type IconName =
  | "search"
  | "settings"
  | "photos"
  | "videos"
  | "grid"
  | "list"
  | "back"
  | "play"
  | "pause"
  | "trash"
  | "restore"
  | "folder"
  | "close"
  | "info"
  | "external"
  | "check"
  | "plus"
  | "minus"
  | "refresh"
  | "share"
  | "clock"
  | "sparkle"
  | "expand"
  | "windows"
  | "heart"
  | "captions"
  | "audio";

/** Inner markup for a 24x24 icon. Static, author-controlled strings only. */
const PATHS: Record<IconName, string> = {
  search:
    '<circle cx="11" cy="11" r="7"/><path d="m16.5 16.5 4 4"/>',
  settings:
    '<path d="M4 7h10"/><path d="M18 7h2"/><circle cx="16" cy="7" r="2"/><path d="M4 17h4"/><path d="M12 17h8"/><circle cx="10" cy="17" r="2"/>',
  photos:
    '<rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="8.5" cy="9.5" r="1.6"/><path d="m4 17 4.5-4.5a2 2 0 0 1 2.8 0L20 21"/>',
  videos:
    '<rect x="3" y="4" width="18" height="16" rx="3"/><path d="M10 9.2v5.6a.6.6 0 0 0 .9.5l4.6-2.8a.6.6 0 0 0 0-1l-4.6-2.8a.6.6 0 0 0-.9.5Z" fill="currentColor" stroke="none"/>',
  grid:
    '<rect x="3.5" y="3.5" width="7" height="7" rx="2"/><rect x="13.5" y="3.5" width="7" height="7" rx="2"/><rect x="3.5" y="13.5" width="7" height="7" rx="2"/><rect x="13.5" y="13.5" width="7" height="7" rx="2"/>',
  list:
    '<path d="M4 6h16"/><path d="M4 12h16"/><path d="M4 18h16"/>',
  back: '<path d="M15 5l-7 7 7 7"/>',
  play: '<path d="M8 5.5v13a.7.7 0 0 0 1.06.6l10.2-6.5a.7.7 0 0 0 0-1.2L9.06 4.9A.7.7 0 0 0 8 5.5Z" fill="currentColor" stroke="none"/>',
  pause:
    '<rect x="7" y="5" width="3.4" height="14" rx="1.2" fill="currentColor" stroke="none"/><rect x="13.6" y="5" width="3.4" height="14" rx="1.2" fill="currentColor" stroke="none"/>',
  trash:
    '<path d="M4 7h16"/><path d="M9 7V5.5A1.5 1.5 0 0 1 10.5 4h3A1.5 1.5 0 0 1 15 5.5V7"/><path d="M6 7l.8 12a2 2 0 0 0 2 1.9h6.4a2 2 0 0 0 2-1.9L18 7"/><path d="M10 11v6"/><path d="M14 11v6"/>',
  restore:
    '<path d="M4.5 9A8 8 0 1 1 4 14"/><path d="M4 4v5h5"/>',
  folder:
    '<path d="M3.5 7.5A2.5 2.5 0 0 1 6 5h3.6c.6 0 1.2.3 1.6.8l1 1.2H18a2.5 2.5 0 0 1 2.5 2.5v7A2.5 2.5 0 0 1 18 19H6a2.5 2.5 0 0 1-2.5-2.5Z"/>',
  close: '<path d="M6 6l12 12"/><path d="M18 6 6 18"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5"/><circle cx="12" cy="8" r=".9" fill="currentColor" stroke="none"/>',
  external:
    '<path d="M14 4h6v6"/><path d="M20 4 11 13"/><path d="M18 14v4.5A1.5 1.5 0 0 1 16.5 20h-11A1.5 1.5 0 0 1 4 18.5v-11A1.5 1.5 0 0 1 5.5 6H10"/>',
  check: '<path d="m5 12.5 4.5 4.5L19 7"/>',
  plus: '<path d="M12 5v14"/><path d="M5 12h14"/>',
  minus: '<path d="M5 12h14"/>',
  refresh:
    '<path d="M20 11a8 8 0 1 0-2.3 5.7"/><path d="M20 4.5V11h-6.5"/>',
  share:
    '<circle cx="6" cy="12" r="2.4"/><circle cx="17.5" cy="6" r="2.4"/><circle cx="17.5" cy="18" r="2.4"/><path d="m8.2 10.9 7.1-3.8"/><path d="m8.2 13.1 7.1 3.8"/>',
  clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 1.8"/>',
  sparkle:
    '<path d="M12 3.5l1.9 4.9 5.1 1.6-3.7 3.5.9 5.2-4.2-2.5-4.2 2.5.9-5.2L4.9 10l5.2-1.6Z"/>',
  expand:
    '<path d="M4 9V5.5A1.5 1.5 0 0 1 5.5 4H9"/><path d="M15 4h3.5A1.5 1.5 0 0 1 20 5.5V9"/><path d="M20 15v3.5a1.5 1.5 0 0 1-1.5 1.5H15"/><path d="M9 20H5.5A1.5 1.5 0 0 1 4 18.5V15"/>',
  windows:
    '<path d="M4 6.5 11 5.5v6H4Z"/><path d="M12.5 5.3 20 4v7.5h-7.5Z"/><path d="M4 12.5h7v6L4 17.5Z"/><path d="M12.5 12.5H20V20l-7.5-1.1Z"/>',
  heart:
    '<path d="M12 19.5s-7.5-4.4-7.5-9.2A4.3 4.3 0 0 1 12 7a4.3 4.3 0 0 1 7.5 3.3c0 4.8-7.5 9.2-7.5 9.2Z"/>',
  captions:
    '<rect x="3" y="4.5" width="18" height="15" rx="3.2"/><path d="M10.4 10.2a2.4 2.4 0 1 0 0 3.6"/><path d="M16.6 10.2a2.4 2.4 0 1 0 0 3.6"/>',
  audio:
    '<path d="M4 9.6h3.2L12 6.2v11.6L7.2 14.4H4Z"/><path d="M15.4 9.2a4 4 0 0 1 0 5.6"/><path d="M18 6.6a7.6 7.6 0 0 1 0 10.8"/>',
};

export function icon(name: IconName, size = 20, strokeWidth = 1.8): SVGSVGElement {
  const svg = document.createElementNS(NS, "svg");
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("width", String(size));
  svg.setAttribute("height", String(size));
  svg.setAttribute("fill", "none");
  svg.setAttribute("stroke", "currentColor");
  svg.setAttribute("stroke-width", String(strokeWidth));
  svg.setAttribute("stroke-linecap", "round");
  svg.setAttribute("stroke-linejoin", "round");
  svg.setAttribute("aria-hidden", "true");
  svg.innerHTML = PATHS[name];
  return svg;
}
