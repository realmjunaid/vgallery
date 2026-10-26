import {
  assetUrl,
  listVideoTracks,
  readSubtitle,
  type MediaItem,
  type TrackOption,
  type VideoTracks,
} from "../lib/api";
import { h, hasOverlay, iconButton, openSheet, toast } from "../lib/dom";
import { icon } from "../lib/icons";

export interface FeedOptions {
  items: MediaItem[];
  startIndex?: number;
  title: string;
  autoplaySeconds: number;
  initialAutoplay?: boolean;
  onClose: () => void;
  onInfo: (item: MediaItem) => void;
  onOpen: (item: MediaItem) => void;
}

export interface FeedHandle {
  destroy: () => void;
}

const MOTION_MS = 5_000;
const TRACK_KEY = "vgallery.tracks.v1";

/** Last subtitle / extra audio choice per video path. */
interface TrackPref {
  sub?: string | null;
  audio?: string | null;
}

function loadTrackPrefs(): Record<string, TrackPref> {
  try {
    const raw = localStorage.getItem(TRACK_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as Record<string, TrackPref>;
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

function saveTrackPrefs(prefs: Record<string, TrackPref>): void {
  try {
    localStorage.setItem(TRACK_KEY, JSON.stringify(prefs));
  } catch {
    /* storage full: the choice just is not remembered */
  }
}

/**
 * The reels-style feed, now as a continuous strip: every item keeps its own
 * size (capped to the window), so a screen can show one picture and the top of
 * the next, and a small scroll reveals the rest instead of paging.
 */
export function openFeed(options: FeedOptions): FeedHandle {
  const { items, title, autoplaySeconds } = options;
  const start = Math.min(Math.max(0, options.startIndex ?? 0), Math.max(0, items.length - 1));

  let index = start;
  let autoplayOn = options.initialAutoplay ?? false;
  let timer = 0;
  let hintTimer = 0;
  let scrollTimer = 0;
  let destroyed = false;

  const progressBar = h("span", { class: "feed-progress-bar" });
  const counter = h("span", { class: "feed-counter" }, `${start + 1} / ${items.length}`);
  const zoomHint = h(
    "span",
    { class: "feed-zoom-hint", "aria-hidden": "true" },
    "Ctrl + scroll or pinch to zoom",
  );
  const zoomBadge = h(
    "button",
    {
      class: "feed-zoom hidden",
      type: "button",
      title: "Reset zoom (Ctrl+0)",
      "aria-label": "Reset feed zoom",
    },
    "1×",
  );
  const autoplayButton = iconButton(autoplayOn ? "pause" : "play", "Autoplay", () => toggleAutoplay());
  const backButton = iconButton("back", "Back", () => destroy());
  const tracksButton = iconButton("captions", "Subtitles and audio", () => void openTracksSheet(), "feed-tracks");
  tracksButton.classList.add("hidden");
  const infoButton = iconButton("info", "Details", () =>
    options.onInfo(items[index]),
  );

  const track = h("div", { class: "feed-track" });
  const feed = h("div", { class: "feed", tabindex: "0" }, track);

  const frames: HTMLElement[] = [];
  const slots: HTMLElement[] = items.map((item, i) => {
    const frame = h("div", { class: "feed-frame" });
    const slot = h(
      "div",
      {
        class: "feed-item",
        dataset: { index: String(i) },
        "aria-label": item.name,
      },
      frame,
    );
    track.append(slot);
    frames.push(frame);
    return slot;
  });

  const topbar = h(
    "header",
    { class: "feed-top" },
    backButton,
    h(
      "div",
      { class: "feed-heading" },
      h("strong", {}, title),
      h("span", {}, `${items.length} ${items.length === 1 ? "item" : "items"}`),
    ),
    h("div", { class: "feed-tools" }, zoomHint, zoomBadge, counter, tracksButton, autoplayButton, infoButton),
  );

  const overlay = h(
    "div",
    { class: "feed-overlay" },
    h("div", { class: "feed-progress" }, progressBar),
    topbar,
    feed,
  );

  document.body.classList.add("feed-open");
  document.getElementById("overlay-root")?.append(overlay);
  feed.focus({ preventScroll: true });

  // --- layout ------------------------------------------------------------
  //
  // Every item gets the same card, before the picture loads, so a portrait and
  // a square never come out bigger or smaller than each other. The photo keeps
  // its own ratio inside that card (cover, centred) instead of resizing the card.

  const SIDE_INSET = 72;

  /** One box for the whole feed. Ratio of the file does not change it. */
  function mediaBox(): { width: number; height: number } {
    const availableWidth = Math.max(280, feed.clientWidth - SIDE_INSET);
    const availableHeight = Math.max(280, feed.clientHeight);
    // Tall enough that the next card still peeks in, short of filling the window.
    const height = Math.round(Math.min(availableHeight * 0.58, 680));
    const width = Math.round(Math.min(availableWidth, height * 0.82, 560));
    return { width: Math.max(240, width), height: Math.max(280, height) };
  }

  function sizeFrame(i: number): void {
    const frame = frames[i];
    if (!frame) return;
    const box = mediaBox();
    frame.style.width = `${box.width}px`;
    frame.style.height = `${box.height}px`;
  }

  function sizeAllFrames(): void {
    for (let i = 0; i < frames.length; i += 1) sizeFrame(i);
  }

  /** Resize one frame without moving the picture the reader is looking at. */
  function resizeFrame(i: number): void {
    if (i === index) {
      sizeFrame(i);
      return;
    }
    const anchor = slots[index];
    const before = anchor.getBoundingClientRect().top;
    sizeFrame(i);
    const after = anchor.getBoundingClientRect().top;
    if (i < index && after !== before) feed.scrollTop += (after - before) / feedScale;
  }

  // --- media -------------------------------------------------------------

  interface SlotState {
    media: HTMLElement | null;
    kind: string | null;
    tracks: VideoTracks | null;
    subUrl: string | null;
    subEl: HTMLTrackElement | null;
    audio: HTMLAudioElement | null;
    audioCleanup: (() => void) | null;
    audioVolume: number;
  }
  const states: SlotState[] = items.map(() => ({
    media: null,
    kind: null,
    tracks: null,
    subUrl: null,
    subEl: null,
    audio: null,
    audioCleanup: null,
    audioVolume: 1,
  }));
  const trackPrefs = loadTrackPrefs();

  function ensureMedia(i: number): HTMLElement | null {
    const slot = slots[i];
    const frame = frames[i];
    if (!slot || !frame) return null;
    if (states[i].media) return states[i].media;
    const item = items[i];
    let media: HTMLElement;

    if (item.kind === "video") {
      const video = h("video", {
        class: "feed-media",
        src: assetUrl(item.path),
        controls: true,
        preload: "metadata",
        playsinline: true,
      });
      video.addEventListener("play", () => slot.classList.add("is-playing"));
      video.addEventListener("pause", () => slot.classList.remove("is-playing"));
      video.addEventListener("timeupdate", () => {
        if (i !== index) return;
        if (video.duration > 0) setProgress(video.currentTime / video.duration);
      });
      video.addEventListener("ended", () => {
        if (i !== index) return;
        slot.classList.remove("is-playing");
        if (autoplayOn) advance();
      });
      video.addEventListener("loadedmetadata", () => {
        if (item.width > 0 && item.height > 0) return;
        if (!video.videoWidth || !video.videoHeight) return;
        item.width = video.videoWidth;
        item.height = video.videoHeight;
        resizeFrame(i);
      });
      media = video;
      frame.append(video);
      if (!slot.querySelector(".feed-play")) {
        const play = h(
          "button",
          { class: "feed-play", type: "button", "aria-label": "Play" },
          icon("play", 34),
        );
        play.addEventListener("click", (event) => {
          event.stopPropagation();
          toggleVideo(video);
        });
        slot.append(play);
      }
    } else {
      const img = h("img", {
        class: "feed-media",
        src: assetUrl(item.path),
        alt: item.name,
        draggable: false,
        decoding: "async",
      });
      slot.classList.add("is-loading");
      img.addEventListener("load", () => {
        slot.classList.remove("is-loading");
        if (item.width > 0 && item.height > 0) return;
        if (!img.naturalWidth || !img.naturalHeight) return;
        item.width = img.naturalWidth;
        item.height = img.naturalHeight;
        resizeFrame(i);
      });
      img.addEventListener("error", () => {
        slot.classList.remove("is-loading");
        slot.classList.add("is-error");
      });
      frame.append(img);
      media = img;
    }

    states[i].media = media;
    states[i].kind = item.kind;
    if (item.kind === "video") void hydrateTracks(i);
    return media;
  }

  function videoAt(i: number): HTMLVideoElement | null {
    const media = states[i]?.media;
    return media instanceof HTMLVideoElement ? media : null;
  }

  function pauseOthers(except: number): void {
    for (let i = 0; i < states.length; i += 1) {
      if (i === except) continue;
      const video = videoAt(i);
      if (video && !video.paused) video.pause();
    }
  }

  /** Drop a far-away picture or video; its frame keeps the layout steady. */
  function releaseSlot(i: number): void {
    if (i === index) return;
    const state = states[i];
    if (!state.media) return;
    if (state.media instanceof HTMLVideoElement) state.media.pause();
    releaseTracks(i);
    state.media.remove();
    state.media = null;
    state.kind = null;
    slots[i].classList.remove("is-error", "is-playing", "is-loading");
  }

  function toggleVideo(video: HTMLVideoElement): void {
    if (video.paused) {
      pauseOthers(index);
      void video.play().catch(() => toast("This video cannot play in the app. Try opening it externally."));
    } else {
      video.pause();
    }
  }

  // --- subtitles and extra audio -----------------------------------------

  function remember(path: string, patch: TrackPref): void {
    trackPrefs[path] = { ...(trackPrefs[path] ?? {}), ...patch };
    saveTrackPrefs(trackPrefs);
  }

  function findTrack(list: TrackOption[], path: string | null | undefined): TrackOption | null {
    if (!path) return null;
    return list.find((track) => track.path === path) ?? null;
  }

  /** Read the sidecar files that sit next to this video, once per slot. */
  async function loadTracks(i: number): Promise<VideoTracks> {
    const state = states[i];
    if (state.tracks) return state.tracks;
    const item = items[i];
    if (!item || item.kind !== "video") return { subtitles: [], audio: [] };
    let tracks: VideoTracks = { subtitles: [], audio: [] };
    try {
      tracks = await listVideoTracks(item.path);
    } catch {
      /* an unreadable folder just means no extra tracks */
    }
    state.tracks = tracks;
    return tracks;
  }

  function clearSubtitle(i: number): void {
    const state = states[i];
    state.subEl?.remove();
    state.subEl = null;
    if (state.subUrl) {
      URL.revokeObjectURL(state.subUrl);
      state.subUrl = null;
    }
  }

  /** Show `track` as a WebVTT text track, or clear subtitles when null. */
  async function applySubtitle(i: number, track: TrackOption | null, persist = true): Promise<void> {
    const state = states[i];
    const video = videoAt(i);
    clearSubtitle(i);

    if (track && video) {
      try {
        const vtt = await readSubtitle(track.path);
        if (destroyed || states[i] !== state || state.media !== video) return;
        const url = URL.createObjectURL(new Blob([vtt], { type: "text/vtt" }));
        state.subUrl = url;
        const el = document.createElement("track");
        el.kind = "subtitles";
        el.label = track.label;
        el.srclang = track.lang || "und";
        el.src = url;
        el.default = true;
        video.append(el);
        state.subEl = el;
        const show = () => {
          const list = video.textTracks;
          for (let t = 0; t < list.length; t += 1) {
            list[t].mode = t === list.length - 1 ? "showing" : "disabled";
          }
        };
        el.addEventListener("load", show);
        el.addEventListener("error", () => {
          toast(`Could not read ${track.name}`, "error");
          clearSubtitle(i);
          updateTracksButton();
        });
        show();
        window.setTimeout(show, 80);
      } catch (error) {
        toast(String(error), "error");
      }
    }

    if (persist) remember(items[i].path, { sub: track ? track.path : null });
    updateTracksButton();
  }

  function stopExternalAudio(i: number): void {
    const state = states[i];
    state.audioCleanup?.();
    state.audioCleanup = null;
    if (state.audio) {
      state.audio.pause();
      state.audio.removeAttribute("src");
      state.audio.load();
      state.audio = null;
    }
    const video = videoAt(i);
    if (video) video.volume = state.audioVolume || 1;
  }

  /**
   * Play a sidecar audio file under the video, with the video's own soundtrack
   * silenced. The two are kept in step on play, pause, seek and drift.
   */
  function applyAudio(i: number, track: TrackOption | null, persist = true): void {
    const state = states[i];
    const video = videoAt(i);
    const previous = state.audio ? state.audioVolume : video && video.volume > 0 ? video.volume : 1;
    stopExternalAudio(i);
    state.audioVolume = previous || 1;

    if (track && video) {
      const audio = new Audio(assetUrl(track.path));
      audio.preload = "auto";
      audio.volume = state.audioVolume;
      audio.muted = video.muted;
      state.audio = audio;

      let syncing = false;
      const syncGain = () => {
        if (syncing) return;
        syncing = true;
        if (video.volume > 0) state.audioVolume = video.volume;
        // The video stays silent so its own track never doubles up.
        video.volume = 0;
        if (state.audio) {
          state.audio.volume = state.audioVolume;
          state.audio.muted = video.muted;
        }
        syncing = false;
      };

      const syncTime = (force: boolean) => {
        const current = state.audio;
        if (!current) return;
        if (force || Math.abs(current.currentTime - video.currentTime) > 0.35) {
          try {
            current.currentTime = video.currentTime;
          } catch {
            /* not seekable yet */
          }
        }
        if (!video.paused && current.paused) {
          void current.play().catch(() => {
            /* leave it paused rather than looping an error toast */
          });
        }
      };

      const onPlay = () => {
        syncGain();
        syncTime(true);
        void state.audio?.play().catch(() => toast("This audio track cannot play in the app.", "error"));
      };
      const onPause = () => state.audio?.pause();
      const onSeeking = () => syncTime(true);
      const onTime = () => syncTime(false);
      const onEnded = () => state.audio?.pause();
      const onVolume = () => syncGain();

      video.addEventListener("play", onPlay);
      video.addEventListener("pause", onPause);
      video.addEventListener("seeking", onSeeking);
      video.addEventListener("timeupdate", onTime);
      video.addEventListener("ended", onEnded);
      video.addEventListener("volumechange", onVolume);
      state.audioCleanup = () => {
        video.removeEventListener("play", onPlay);
        video.removeEventListener("pause", onPause);
        video.removeEventListener("seeking", onSeeking);
        video.removeEventListener("timeupdate", onTime);
        video.removeEventListener("ended", onEnded);
        video.removeEventListener("volumechange", onVolume);
      };

      syncGain();
      if (!video.paused) {
        syncTime(true);
        void audio.play().catch(() => toast("This audio track cannot play in the app.", "error"));
      }
    } else if (video) {
      video.volume = state.audioVolume;
    }

    if (persist) remember(items[i].path, { audio: track ? track.path : null });
    updateTracksButton();
  }

  function releaseTracks(i: number): void {
    clearSubtitle(i);
    stopExternalAudio(i);
  }

  function updateTracksButton(): void {
    const item = items[index];
    if (!item || item.kind !== "video") {
      tracksButton.classList.add("hidden");
      return;
    }
    tracksButton.classList.remove("hidden");
    const pref = trackPrefs[item.path];
    const captions = Boolean(pref?.sub);
    const alternate = Boolean(pref?.audio);
    tracksButton.classList.toggle("is-active", captions);
    tracksButton.title = alternate
      ? "Subtitles and audio · alternate audio track playing"
      : "Subtitles and audio (T)";
    tracksButton.replaceChildren(icon("captions", 19));
    if (alternate) tracksButton.append(h("span", { class: "feed-track-dot" }));
  }

  async function hydrateTracks(i: number): Promise<void> {
    const item = items[i];
    if (!item) return;
    const tracks = await loadTracks(i);
    if (destroyed) return;
    const pref = trackPrefs[item.path];
    if (!pref) {
      if (i === index) updateTracksButton();
      return;
    }
    const subtitle = findTrack(tracks.subtitles, pref.sub);
    if (subtitle) await applySubtitle(i, subtitle, false);
    const audio = findTrack(tracks.audio, pref.audio);
    if (audio) applyAudio(i, audio, false);
    if (i === index) updateTracksButton();
  }

  async function cycleSubtitle(): Promise<void> {
    const i = index;
    const item = items[i];
    if (!item || item.kind !== "video") return;
    const tracks = await loadTracks(i);
    if (tracks.subtitles.length === 0) {
      toast("No subtitle files next to this video (movie.srt, movie.en.srt).");
      return;
    }
    const current = trackPrefs[item.path]?.sub ?? null;
    if (current === null) {
      await applySubtitle(i, tracks.subtitles[0]);
      toast(`Subtitles: ${tracks.subtitles[0].label}`);
      return;
    }
    const at = tracks.subtitles.findIndex((t) => t.path === current);
    const next = at + 1 < tracks.subtitles.length ? tracks.subtitles[at + 1] : null;
    await applySubtitle(i, next);
    toast(next ? `Subtitles: ${next.label}` : "Subtitles off");
  }

  interface RowOption {
    value: string;
    label: string;
    meta?: string;
  }

  function trackGroup(
    title: string,
    rows: RowOption[],
    current: string | null,
    onPick: (value: string | null) => void,
  ): HTMLElement {
    const list = h("div", { class: "track-list" });
    for (const option of rows) {
      const active = (current ?? "") === option.value;
      const check = h("span", { class: "track-check" }, active ? icon("check", 13) : null);
      const row = h(
        "button",
        {
          class: `track-option ${active ? "active" : ""}`.trim(),
          type: "button",
          onclick: () => {
            for (const child of list.children) {
              child.classList.remove("active");
              child.querySelector(".track-check")?.replaceChildren();
            }
            row.classList.add("active");
            check.replaceChildren(icon("check", 13));
            onPick(option.value === "" ? null : option.value);
          },
        },
        check,
        h(
          "span",
          { class: "track-text" },
          h("strong", {}, option.label),
          option.meta ? h("small", { title: option.meta }, option.meta) : null,
        ),
      );
      list.append(row);
    }
    return h("div", { class: "track-group" }, h("h3", {}, title), list);
  }

  async function openTracksSheet(): Promise<void> {
    const i = index;
    const item = items[i];
    if (!item || item.kind !== "video") return;
    const tracks = await loadTracks(i);
    if (destroyed) return;
    const pref = trackPrefs[item.path] ?? {};

    openSheet(`Subtitles & audio`, (close) => {
      const body = h("div", { class: "sheet-body tracks" });
      const nodes: Node[] = [h("p", { class: "sheet-note" }, item.name)];
      nodes.push(
        trackGroup(
          "Subtitles",
          [
            { value: "", label: "Off" },
            ...tracks.subtitles.map((track) => ({
              value: track.path,
              label: track.label,
              meta: track.name,
            })),
          ],
          pref.sub ?? null,
          (value) => {
            void applySubtitle(i, findTrack(tracks.subtitles, value));
            pref.sub = value;
          },
        ),
      );
      if (tracks.subtitles.length === 0) {
        nodes.push(
          h(
            "p",
            { class: "track-note" },
            "No subtitle files next to this video. Put a .srt or .vtt with the same name — movie.srt, or movie.en.srt for a language.",
          ),
        );
      }
      nodes.push(
        trackGroup(
          "Audio",
          [
            { value: "", label: "Original", meta: "the track inside the video" },
            ...tracks.audio.map((track) => ({
              value: track.path,
              label: track.label,
              meta: track.name,
            })),
          ],
          pref.audio ?? null,
          (value) => {
            applyAudio(i, findTrack(tracks.audio, value));
            pref.audio = value;
          },
        ),
      );
      if (tracks.audio.length === 0) {
        nodes.push(
          h(
            "p",
            { class: "track-note" },
            "Only the audio inside the video is available. Windows' web engine cannot switch tracks inside an MP4/MKV, so put an extra audio file next to it — movie.bn.m4a, movie.hi.m4a — and it plays in sync.",
          ),
        );
      }
      nodes.push(h("p", { class: "track-note muted" }, "Your choice is remembered for this video."));
      nodes.push(
        h(
          "div",
          { class: "sheet-actions spread" },
          h(
            "button",
            {
              class: "btn btn-ghost",
              type: "button",
              onclick: () => {
                void applySubtitle(i, null);
                pref.sub = null;
              },
            },
            "Subtitles off",
          ),
          h("button", { class: "btn btn-primary", type: "button", onclick: close }, "Done"),
        ),
      );
      body.append(...nodes);
      return body;
    });
  }

  // --- global feed zoom ---------------------------------------------------
  //
  // Google Drive PDF-viewer style, matching the Android FeedZoomState: one
  // scale for the whole feed, so a pinch (or Ctrl + wheel) enlarges every
  // visible item together. The feed keeps owning vertical movement, so
  // scrolling still works at any zoom; only the sideways offset is clamped.

  const MIN_FEED_ZOOM = 1;
  const MAX_FEED_ZOOM = 10;
  const DOUBLE_CLICK_ZOOM = 2;
  let feedScale = 1;
  let feedOffsetX = 0;
  let zoomTransition = 0;

  /** The feed's centre in client pixels: the point the scale is anchored to. */
  function feedCenterX(): number {
    const rect = overlay.getBoundingClientRect();
    return rect.left + feed.clientWidth / 2;
  }

  function clampFeedOffset(): void {
    const limit = Math.max(0, (feed.clientWidth * (feedScale - 1)) / 2);
    feedOffsetX = Math.min(limit, Math.max(-limit, feedOffsetX));
  }

  function applyFeedZoom(animate: boolean): void {
    if (feedScale <= MIN_FEED_ZOOM + 0.001) {
      feedScale = MIN_FEED_ZOOM;
      feedOffsetX = 0;
      window.clearTimeout(zoomTransition);
      feed.style.transition = "";
      feed.style.transform = "";
      feed.classList.remove("is-zoomed", "is-panning");
      zoomBadge.classList.add("hidden");
      zoomBadge.textContent = "1×";
      return;
    }
    feed.classList.add("is-zoomed");
    zoomBadge.classList.remove("hidden");
    zoomHint.classList.add("hidden");
    zoomBadge.textContent = `${feedScale.toFixed(feedScale < 2 ? 2 : 1)}×`;
    window.clearTimeout(zoomTransition);
    if (animate) {
      feed.style.transition = "transform 0.16s ease-out";
      zoomTransition = window.setTimeout(() => {
        feed.style.transition = "";
      }, 180);
    } else {
      feed.style.transition = "";
    }
    feed.style.transform = `translate3d(${feedOffsetX}px, 0px, 0px) scale(${feedScale})`;
  }

  /** Zoom so the spot under `clientX` stays put, like the Android pinch math. */
  function zoomFeedTo(target: number, clientX: number, animate: boolean): void {
    const next = Math.min(MAX_FEED_ZOOM, Math.max(MIN_FEED_ZOOM, target));
    if (Math.abs(next - feedScale) < 0.001) return;
    // Keep the point under the pointer fixed: measure it from the centre, then
    // scale the existing offset by the same factor as the content.
    const pivot = clientX - feedCenterX();
    const factor = next / feedScale;
    feedOffsetX = pivot * (1 - factor) + feedOffsetX * factor;
    feedScale = next;
    clampFeedOffset();
    applyFeedZoom(animate);
  }

  function panFeed(dx: number): void {
    if (feedScale <= MIN_FEED_ZOOM + 0.001) return;
    feedOffsetX += dx;
    clampFeedOffset();
    applyFeedZoom(false);
  }

  /** Back to fit: the badge and Ctrl+0 both land here. */
  function resetFeedZoom(animate: boolean): void {
    if (feedScale <= MIN_FEED_ZOOM + 0.001) return;
    feedScale = MIN_FEED_ZOOM;
    feedOffsetX = 0;
    applyFeedZoom(animate);
  }

  // Trackpad pinch arrives as Ctrl + wheel in WebView2; Ctrl + wheel on a
  // mouse does the same. Both zoom the whole feed around the pointer.
  feed.addEventListener(
    "wheel",
    (event) => {
      if (!event.ctrlKey || destroyed) return;
      event.preventDefault();
      stopWheel();
      const unit = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? 400 : 1;
      const factor = Math.exp(-event.deltaY * unit * 0.0012);
      zoomFeedTo(feedScale * factor, event.clientX, true);
    },
    { passive: false },
  );

  // Double-click a picture: fit <-> 2x, anchored where it was clicked.
  feed.addEventListener("dblclick", (event) => {
    if (destroyed) return;
    const target = event.target as HTMLElement;
    if (!target.closest(".feed-frame")) return;
    if (target.closest("video, button")) return;
    event.preventDefault();
    stopWheel();
    const zoomedIn = feedScale > MIN_FEED_ZOOM + 0.001;
    zoomFeedTo(zoomedIn ? MIN_FEED_ZOOM : DOUBLE_CLICK_ZOOM, event.clientX, true);
  });

  // The badge in the top bar is a one-click way back to fit.
  zoomBadge.addEventListener("click", (event) => {
    event.stopPropagation();
    resetFeedZoom(true);
  });

  // --- pinch and sideways pan --------------------------------------------

  const points = new Map<number, { x: number; y: number }>();
  let panning = false;
  let panLastX = 0;
  let panMoved = 0;
  let pinchStartDistance = 0;
  let pinchStartScale = 1;
  let pinchStartOffset = 0;
  let suppressClick = false;
  let suppressTimer = 0;

  /** A pan ends with a click; swallow it so it does not toggle autoplay. */
  function suppressNextClick(): void {
    suppressClick = true;
    window.clearTimeout(suppressTimer);
    suppressTimer = window.setTimeout(() => {
      suppressClick = false;
    }, 400);
  }

  function beginPinch(): void {
    const list = [...points.values()];
    if (list.length < 2) return;
    const [a, b] = list;
    pinchStartDistance = Math.hypot(a.x - b.x, a.y - b.y) || 1;
    pinchStartScale = feedScale;
    pinchStartOffset = feedOffsetX;
    panning = false;
    feed.classList.remove("is-panning");
    for (const id of points.keys()) {
      try {
        feed.setPointerCapture(id);
      } catch {
        /* the pointer may already be gone */
      }
    }
  }

  feed.addEventListener("pointerdown", (event) => {
    if (destroyed) return;
    const target = event.target as HTMLElement;
    if (target.closest("button, video")) return;
    if (event.pointerType === "mouse" && event.button !== 0) return;
    points.set(event.pointerId, { x: event.clientX, y: event.clientY });

    if (points.size >= 2) {
      beginPinch();
      event.preventDefault();
      return;
    }

    if (feedScale > MIN_FEED_ZOOM + 0.001) {
      panning = true;
      panLastX = event.clientX;
      panMoved = 0;
      feed.classList.add("is-panning");
      try {
        feed.setPointerCapture(event.pointerId);
      } catch {
        /* synthetic pointers cannot be captured */
      }
    }
  });

  feed.addEventListener("pointermove", (event) => {
    if (!points.has(event.pointerId)) return;
    points.set(event.pointerId, { x: event.clientX, y: event.clientY });

    if (points.size >= 2) {
      const [a, b] = [...points.values()];
      const distance = Math.hypot(a.x - b.x, a.y - b.y) || 1;
      const next = Math.min(
        MAX_FEED_ZOOM,
        Math.max(MIN_FEED_ZOOM, pinchStartScale * (distance / pinchStartDistance)),
      );
      const pivot = (a.x + b.x) / 2 - feedCenterX();
      const factor = next / pinchStartScale;
      feedOffsetX = pivot * (1 - factor) + pinchStartOffset * factor;
      feedScale = next;
      clampFeedOffset();
      applyFeedZoom(false);
      suppressNextClick();
      event.preventDefault();
      return;
    }

    if (!panning) return;
    const dx = event.clientX - panLastX;
    panLastX = event.clientX;
    if (dx === 0) return;
    panFeed(dx);
    panMoved += Math.abs(dx);
    if (panMoved > 4) {
      suppressNextClick();
      event.preventDefault();
    }
  });

  function endPointer(event: PointerEvent): void {
    points.delete(event.pointerId);
    try {
      feed.releasePointerCapture(event.pointerId);
    } catch {
      /* already released */
    }
    const rest = [...points.values()][0];
    if (rest) {
      // One finger left after a pinch continues as a sideways pan.
      panning = feedScale > MIN_FEED_ZOOM + 0.001;
      if (panning) {
        panLastX = rest.x;
        panMoved = 0;
      }
      return;
    }
    panning = false;
    pinchStartDistance = 0;
    feed.classList.remove("is-panning");
  }
  feed.addEventListener("pointerup", endPointer);
  feed.addEventListener("pointercancel", endPointer);

  // Two fingers never scroll the page: the pinch owns that gesture. One
  // finger still scrolls vertically (touch-action: pan-y on the feed).
  feed.addEventListener(
    "touchmove",
    (event) => {
      if (event.touches.length >= 2) event.preventDefault();
    },
    { passive: false },
  );

  feed.addEventListener(
    "click",
    (event) => {
      if (!suppressClick) return;
      suppressClick = false;
      event.preventDefault();
      event.stopPropagation();
    },
    true,
  );

  // --- progress ----------------------------------------------------------

  function resetProgress(): void {
    progressBar.style.transition = "none";
    progressBar.style.width = "0%";
  }

  function setProgress(fraction: number): void {
    progressBar.style.transition = "none";
    progressBar.style.width = `${Math.min(1, Math.max(0, fraction)) * 100}%`;
  }

  function animateProgress(durationMs: number): void {
    progressBar.style.transition = "none";
    progressBar.style.width = "0%";
    requestAnimationFrame(() => {
      progressBar.style.transition = `width ${durationMs}ms linear`;
      progressBar.style.width = "100%";
    });
  }

  // --- playback ----------------------------------------------------------

  function dwellMs(item: MediaItem): number {
    if (item.kind === "video") return Number.POSITIVE_INFINITY;
    if (item.kind === "motion") return MOTION_MS;
    return autoplaySeconds * 1000;
  }

  function startCurrent(): void {
    window.clearTimeout(timer);
    const item = items[index];
    if (!item) return;
    const media = ensureMedia(index);
    if (!media) return;

    if (media instanceof HTMLVideoElement) {
      media.currentTime = 0;
      pauseOthers(index);
      void media.play().catch(() => {
        /* WebView blocked autoplay; the play button is still there. */
      });
      return;
    }

    if (!autoplayOn) {
      resetProgress();
      return;
    }
    const dwell = dwellMs(item);
    if (!Number.isFinite(dwell)) return;
    animateProgress(dwell);
    timer = window.setTimeout(() => advance(), dwell);
  }

  function advance(): void {
    if (!autoplayOn) return;
    if (index >= items.length - 1) {
      autoplayOn = false;
      updateAutoplayButton();
      resetProgress();
      toast("End of the feed");
      return;
    }
    scrollToIndex(index + 1, true);
  }

  function toggleAutoplay(): void {
    autoplayOn = !autoplayOn;
    updateAutoplayButton();
    window.clearTimeout(timer);
    if (!autoplayOn) {
      resetProgress();
      const video = videoAt(index);
      video?.pause();
      return;
    }
    startCurrent();
  }

  function updateAutoplayButton(): void {
    autoplayButton.replaceChildren(icon(autoplayOn ? "pause" : "play", 19));
    autoplayButton.classList.toggle("is-active", autoplayOn);
    autoplayButton.title = autoplayOn ? "Pause autoplay" : "Autoplay";
  }

  // --- navigation --------------------------------------------------------

  function scrollTopFor(i: number): number {
    const slot = slots[Math.min(slots.length - 1, Math.max(0, i))];
    if (!slot) return 0;
    const feedTop = feed.getBoundingClientRect().top;
    // Screen distances are scaled while the feed is zoomed; scrollTop is not.
    const top =
      (slot.getBoundingClientRect().top - feedTop) / feedScale + feed.scrollTop;
    const max = Math.max(0, feed.scrollHeight - feed.clientHeight);
    // Centre the item, so whatever its height it is the one under the middle.
    const target = top - (feed.clientHeight - slot.offsetHeight) / 2;
    return Math.min(max, Math.max(0, target));
  }

  function scrollToIndex(next: number, smooth: boolean): void {
    stopWheel();
    const clamped = Math.min(items.length - 1, Math.max(0, next));
    const top = scrollTopFor(clamped);
    if (smooth) feed.scrollTo({ top, behavior: "smooth" });
    else feed.scrollTop = top;
  }

  // --- smooth scrolling --------------------------------------------------
  //
  // No snapping: a wheel notch glides a short distance and stops where it
  // lands, so the next picture comes up gradually instead of replacing the
  // screen. Trackpads already send small deltas and keep their own feel.

  let wheelTarget = 0;
  let wheelFrame = 0;
  let wheelGliding = false;

  function cancelFrame(): void {
    if (wheelFrame) cancelAnimationFrame(wheelFrame);
    wheelFrame = 0;
  }

  function stopWheel(): void {
    wheelGliding = false;
    wheelTarget = feed.scrollTop;
    cancelFrame();
  }

  /** Ease the scroll position towards `target`; returns false when done. */
  function glideTo(target: number, factor: number): boolean {
    const max = Math.max(0, feed.scrollHeight - feed.clientHeight);
    const goal = Math.min(max, Math.max(0, target));
    const distance = goal - feed.scrollTop;
    if (Math.abs(distance) < 0.6) {
      feed.scrollTop = goal;
      return false;
    }
    feed.scrollTop += distance * factor;
    return true;
  }

  function runWheel(): void {
    cancelFrame();
    const step = () => {
      wheelFrame = 0;
      if (destroyed) return;
      if (glideTo(wheelTarget, 0.24)) wheelFrame = requestAnimationFrame(step);
      else wheelGliding = false;
    };
    wheelFrame = requestAnimationFrame(step);
  }

  feed.addEventListener(
    "wheel",
    (event: WheelEvent) => {
      if (destroyed) return;
      // Ctrl + wheel is the feed zoom, handled above.
      if (event.ctrlKey) return;
      // While zoomed, a sideways trackpad swipe pans the enlarged feed.
      if (
        feedScale > MIN_FEED_ZOOM + 0.001 &&
        Math.abs(event.deltaX) > Math.abs(event.deltaY)
      ) {
        event.preventDefault();
        stopWheel();
        panFeed(-event.deltaX);
        return;
      }
      // Horizontal swipes belong to the trackpad, not the feed.
      if (Math.abs(event.deltaY) <= Math.abs(event.deltaX)) return;
      event.preventDefault();
      const unit =
        event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? feed.clientHeight : 1;
      const max = Math.max(0, feed.scrollHeight - feed.clientHeight);
      if (!wheelGliding) {
        wheelTarget = feed.scrollTop;
        wheelGliding = true;
      }
      const raw = event.deltaY * unit;
      // A mouse notch is ~120px, which would barely move the feed; real
      // trackpad ticks are tiny, so they get only a small boost.
      const scale = Math.abs(raw) >= 60 ? 2 : 1.15;
      const delta = Math.max(
        -feed.clientHeight,
        Math.min(feed.clientHeight, raw * scale),
      );
      wheelTarget = Math.min(max, Math.max(0, wheelTarget + delta));
      runWheel();
    },
    { passive: false },
  );

  /** The item under the middle of the screen is the active one. */
  function visibleIndex(): number {
    const rect = feed.getBoundingClientRect();
    const center = rect.top + rect.height / 2;
    let best = index;
    let bestDistance = Number.POSITIVE_INFINITY;
    for (let i = 0; i < slots.length; i += 1) {
      const box = slots[i].getBoundingClientRect();
      if (box.bottom < rect.top || box.top > rect.bottom) continue;
      const distance = Math.abs((box.top + box.bottom) / 2 - center);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = i;
      }
    }
    return best;
  }

  function setIndex(next: number): void {
    if (next === index || next < 0 || next >= items.length) return;
    const previous = index;
    index = next;
    window.clearTimeout(timer);
    resetProgress();
    counter.textContent = `${index + 1} / ${items.length}`;
    const previousVideo = videoAt(previous);
    previousVideo?.pause();
    ensureMedia(index);
    updateTracksButton();
    if (autoplayOn) startCurrent();
  }

  feed.addEventListener("scroll", () => {
    window.clearTimeout(scrollTimer);
    scrollTimer = window.setTimeout(() => {
      if (destroyed) return;
      setIndex(visibleIndex());
    }, 80);
  });

  feed.addEventListener("click", (event) => {
    const target = event.target as HTMLElement;
    if (target === feed || target === track || target.classList.contains("feed-item")) {
      toggleAutoplay();
    }
  });

  const onKey = (event: KeyboardEvent) => {
    if (destroyed) return;
    // A details/sheet dialog on top of the feed owns the keyboard.
    if (hasOverlay()) return;
    const target = event.target as HTMLElement | null;
    if (target?.closest("input, textarea, select")) return;
    if ((event.ctrlKey || event.metaKey) && event.key === "0") {
      event.preventDefault();
      resetFeedZoom(true);
      return;
    }
    if (target?.closest("button, video")) {
      // Escape still closes; other keys belong to the focused control.
      if (event.key !== "Escape") return;
    }
    if (event.key === "Escape") {
      event.preventDefault();
      destroy();
    } else if (event.key === "ArrowDown" || event.key === "PageDown") {
      event.preventDefault();
      scrollToIndex(index + 1, true);
    } else if (event.key === "ArrowUp" || event.key === "PageUp") {
      event.preventDefault();
      scrollToIndex(index - 1, true);
    } else if (event.key === " ") {
      event.preventDefault();
      toggleAutoplay();
    } else if (event.key === "+" || event.key === "=") {
      event.preventDefault();
      zoomFeedTo(feedScale * 1.25, feedCenterX(), true);
    } else if (event.key === "-" || event.key === "_") {
      event.preventDefault();
      zoomFeedTo(feedScale / 1.25, feedCenterX(), true);
    } else if (event.key.toLowerCase() === "i") {
      options.onInfo(items[index]);
    } else if (event.key.toLowerCase() === "o") {
      options.onOpen(items[index]);
    } else if (event.key.toLowerCase() === "t") {
      event.preventDefault();
      void openTracksSheet();
    } else if (event.key.toLowerCase() === "c") {
      event.preventDefault();
      void cycleSubtitle();
    }
  };
  document.addEventListener("keydown", onKey);

  // --- boot --------------------------------------------------------------

  sizeAllFrames();

  // Mount pictures one screen above and below; their boxes keep the layout
  // steady while they are away.
  const observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        const i = Number((entry.target as HTMLElement).dataset.index);
        if (!Number.isInteger(i)) continue;
        if (entry.isIntersecting) ensureMedia(i);
        else releaseSlot(i);
      }
    },
    { root: feed, rootMargin: "120% 0px 120% 0px" },
  );
  for (const slot of slots) observer.observe(slot);

  let resizeTimer = 0;
  function relayout(): void {
    if (destroyed) return;
    const anchor = slots[index];
    const feedTop = feed.getBoundingClientRect().top;
    const before = anchor.getBoundingClientRect().top - feedTop;
    sizeAllFrames();
    const after = anchor.getBoundingClientRect().top - feedTop;
    if (after !== before) feed.scrollTop += (after - before) / feedScale;
    clampFeedOffset();
    applyFeedZoom(false);
  }
  const onResize = () => {
    window.clearTimeout(resizeTimer);
    resizeTimer = window.setTimeout(relayout, 120);
  };
  window.addEventListener("resize", onResize);

  hintTimer = window.setTimeout(() => zoomHint.classList.add("hidden"), 7000);
  updateAutoplayButton();
  updateTracksButton();
  ensureMedia(start);
  requestAnimationFrame(() => {
    feed.scrollTop = scrollTopFor(start);
    // Opening a video should start it, like tapping a reel does on the phone.
    if (autoplayOn || items[start]?.kind === "video") startCurrent();
  });

  let cleanupTimer = 0;
  function destroy(): void {
    if (destroyed) return;
    destroyed = true;
    window.clearTimeout(timer);
    window.clearTimeout(hintTimer);
    window.clearTimeout(scrollTimer);
    window.clearTimeout(cleanupTimer);
    window.clearTimeout(resizeTimer);
    window.clearTimeout(zoomTransition);
    window.clearTimeout(suppressTimer);
    window.removeEventListener("resize", onResize);
    observer.disconnect();
    stopWheel();
    document.removeEventListener("keydown", onKey);
    document.body.classList.remove("feed-open");
    for (let i = 0; i < states.length; i += 1) {
      const state = states[i];
      if (state.media instanceof HTMLVideoElement) state.media.pause();
      releaseTracks(i);
    }
    overlay.classList.add("closing");
    cleanupTimer = window.setTimeout(() => overlay.remove(), 160);
    options.onClose();
  }

  return { destroy };
}
