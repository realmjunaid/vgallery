import { assetUrl, makeThumbnail, type MediaItem } from "./api";
import { icon } from "./icons";

const loader = new IntersectionObserver(
  (entries) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue;
      const el = entry.target as HTMLElement;
      loader.unobserve(el);
      if (el instanceof HTMLImageElement) void loadThumb(el);
      else if (el instanceof HTMLVideoElement) {
        const src = el.dataset.src;
        if (src) {
          el.src = assetUrl(src);
          el.load();
        }
      }
    }
  },
  { rootMargin: "700px 0px" },
);

function failMedia(node: HTMLElement): void {
  const parent = node.parentElement;
  if (!parent) return;
  parent.classList.add("is-fallback");
  node.remove();
  if (!parent.querySelector(".thumb-glyph")) parent.append(fallbackGlyph());
}

async function loadThumb(img: HTMLImageElement): Promise<void> {
  const path = img.dataset.thumbPath;
  const size = Number(img.dataset.thumbSize ?? 480);
  if (!path) return;
  try {
    const thumb = await makeThumbnail(path, size);
    if (thumb) {
      img.src = assetUrl(thumb);
    } else {
      failMedia(img);
    }
  } catch {
    failMedia(img);
  }
}

/** A lazily-loaded thumbnail for a photo. The wrapper gets .is-fallback when decoding fails. */
export function thumbFor(item: MediaItem, size = 480): HTMLElement {
  const wrap = document.createElement("div");
  wrap.className = "thumb";

  if (item.kind === "video") {
    const video = document.createElement("video");
    video.className = "thumb-media";
    video.muted = true;
    video.playsInline = true;
    video.preload = "metadata";
    video.dataset.src = item.path;
    video.addEventListener("loadeddata", () => {
      // Nudge the first frame into view on WebView2.
      try {
        video.currentTime = 0.05;
      } catch {
        /* metadata only */
      }
    });
    video.addEventListener("error", () => wrap.classList.add("is-fallback"));
    loader.observe(video);
    wrap.append(video);
    return wrap;
  }

  const img = document.createElement("img");
  img.className = "thumb-media";
  img.alt = item.name;
  img.draggable = false;
  img.decoding = "async";
  img.dataset.thumbPath = item.path;
  img.dataset.thumbSize = String(size);
  img.addEventListener("error", () => img.classList.add("is-missing"));
  loader.observe(img);
  wrap.append(img);
  return wrap;
}

/** Small icon used when a thumbnail cannot be produced. */
export function fallbackGlyph(): HTMLElement {
  const span = document.createElement("span");
  span.className = "thumb-glyph";
  span.append(icon("photos", 28));
  return span;
}
