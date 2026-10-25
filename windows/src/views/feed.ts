import {
  assetUrl,
  listVideoTracks,
  readSubtitle,
  type MediaItem,
  type TrackOption,
  type VideoTracks,
} from "../lib/api";
import { h, iconButton, openSheet, toast } from "../lib/dom";
import { formatBytes, kindLabel } from "../lib/format";
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
 * The reels-style vertical feed. One item per screen, scroll-snapped, with the
 * playing item held in the middle and the next one gliding up into the middle.
 */
export function openFeed(options: FeedOptions): FeedHandle {
  const { items, title, autoplaySeconds } = options;
  const start = Math.min(Math.max(0, options.startIndex ?? 0), Math.max(0, items.length - 1));

  let index = start;
  let autoplayOn = options.initialAutoplay ?? false;
  let timer = 0;
  let captionTimer = 0;
  let scrollTimer = 0;
  let destroyed = false;

  const progressBar = h("span", { class: "feed-progress-bar" });
  const counter = h("span", { class: "feed-counter" }, `${start + 1} / ${items.length}`);
  const autoplayButton = iconButton(autoplayOn ? "pause" : "play", "Autoplay", () => toggleAutoplay());
  const backButton = iconButton("back", "Back", () => destroy());
  const tracksButton = iconButton("captions", "Subtitles and audio", () => void openTracksSheet(), "feed-tracks");
  tracksButton.classList.add("hidden");
  const infoButton = iconButton("info", "Details", () =>
    options.onInfo(items[index]),
  );

  const caption = h(
    "div",
    { class: "feed-caption" },
    h("strong", {}, items[start]?.name ?? ""),
    h("span", {}, `${formatBytes(items[start]?.size ?? 0)} · ${kindLabel(items[start]?.kind ?? "image")}`),
  );

  const track = h("div", { class: "feed-track" });
  const feed = h("div", { class: "feed", tabindex: "0" }, track);

  const slots: HTMLElement[] = items.map((item, i) => {
    const slot = h("div", {
      class: "feed-item",
      dataset: { index: String(i) },
      "aria-label": item.name,
    });
    track.append(slot);
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
    h("div", { class: "feed-tools" }, counter, tracksButton, autoplayButton, infoButton),
  );

  const overlay = h(
    "div",
    { class: "feed-overlay" },
    h("div", { class: "feed-progress" }, progressBar),
    topbar,
    feed,
    caption,
  );

  document.body.classList.add("feed-open");
  document.getElementById("overlay-root")?.append(overlay);
  feed.focus({ preventScroll: true });

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

  function mediaFor(i: number): HTMLElement | null {
    const slot = slots[i];
    if (!slot) return null;
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
      media = video;
      slot.append(video);
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
    } else {
      const img = h("img", {
        class: "feed-media",
        src: assetUrl(item.path),
        alt: item.name,
        draggable: false,
      });
      slot.classList.add("is-loading");
      img.addEventListener("load", () => slot.classList.remove("is-loading"));
      img.addEventListener("error", () => {
        slot.classList.remove("is-loading");
        slot.classList.add("is-error");
      });
      slot.append(img);
      media = img;
    }

    states[i].media = media;
    states[i].kind = item.kind;
    setupZoom(slot, media);
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

  function releaseFar(center: number): void {
    for (let i = 0; i < states.length; i += 1) {
      if (Math.abs(i - center) <= 3) continue;
      const state = states[i];
      if (!state.media) continue;
      if (state.media instanceof HTMLVideoElement) state.media.pause();
      releaseTracks(i);
      state.media.remove();
      state.media = null;
      state.kind = null;
      slots[i].classList.remove("is-error", "is-playing", "is-loading");
    }
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

  // --- zoom --------------------------------------------------------------

  function setupZoom(slot: HTMLElement, media: HTMLElement): void {
    if (!(media instanceof HTMLImageElement)) return;
    let scale = 1;
    let x = 0;
    let y = 0;
    let dragging = false;
    let lastX = 0;
    let lastY = 0;

    const apply = () => {
      media.style.transform = `translate(${x}px, ${y}px) scale(${scale})`;
      media.style.cursor = scale > 1 ? (dragging ? "grabbing" : "grab") : "";
      slot.classList.toggle("is-zoomed", scale > 1);
    };

    slot.addEventListener(
      "wheel",
      (event) => {
        if (!event.ctrlKey) return;
        event.preventDefault();
        scale = Math.min(6, Math.max(1, scale * (event.deltaY < 0 ? 1.15 : 1 / 1.15)));
        if (scale === 1) {
          x = 0;
          y = 0;
        }
        apply();
      },
      { passive: false },
    );

    slot.addEventListener("dblclick", () => {
      scale = scale > 1 ? 1 : 2;
      x = 0;
      y = 0;
      apply();
    });

    slot.addEventListener("pointerdown", (event) => {
      if (scale <= 1) return;
      dragging = true;
      lastX = event.clientX;
      lastY = event.clientY;
      slot.setPointerCapture(event.pointerId);
      apply();
    });
    slot.addEventListener("pointermove", (event) => {
      if (!dragging) return;
      x += event.clientX - lastX;
      y += event.clientY - lastY;
      lastX = event.clientX;
      lastY = event.clientY;
      apply();
    });
    const stopDrag = (event: PointerEvent) => {
      if (!dragging) return;
      dragging = false;
      try {
        slot.releasePointerCapture(event.pointerId);
      } catch {
        /* pointer already released */
      }
      apply();
    };
    slot.addEventListener("pointerup", stopDrag);
    slot.addEventListener("pointercancel", stopDrag);
  }

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
    const media = mediaFor(index);
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

  function scrollToIndex(next: number, smooth: boolean): void {
    const clamped = Math.min(items.length - 1, Math.max(0, next));
    stopWheel();
    feed.scrollTo({ top: clamped * feed.clientHeight, behavior: smooth ? "smooth" : "auto" });
  }

  // --- smooth scrolling --------------------------------------------------
  //
  // The feed used to jump a whole screen per wheel notch (CSS scroll-snap with
  // `scroll-snap-stop: always`). Instead we drive the scroll ourselves with a
  // short eased glide, so the wheel and the touchpad move continuously, and we
  // settle on the nearest item once the wheel goes quiet.

  let wheelTarget = 0;
  let wheelFrame = 0;
  let wheelSettle = 0;
  let wheelGliding = false;
  let wheelDirection = 1;

  function cancelFrame(): void {
    if (wheelFrame) cancelAnimationFrame(wheelFrame);
    wheelFrame = 0;
  }

  function stopWheel(): void {
    window.clearTimeout(wheelSettle);
    wheelSettle = 0;
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
      if (glideTo(wheelTarget, 0.22)) wheelFrame = requestAnimationFrame(step);
      else wheelGliding = false;
    };
    wheelFrame = requestAnimationFrame(step);
  }

  /** After the wheel stops, slide the rest of the way to the item it was heading for. */
  function settleWheel(): void {
    const height = feed.clientHeight || 1;
    const last = Math.max(0, items.length - 1);
    wheelGliding = false;
    const raw = feed.scrollTop / height;
    const base = Math.floor(raw);
    const fraction = raw - base;
    // A nudge of a third of a screen is enough to move on; less falls back,
    // which is what makes a one-notch scroll feel like a page turn.
    let target = base;
    if (wheelDirection > 0 && fraction >= 0.3) target = base + 1;
    if (wheelDirection < 0 && fraction > 0.7) target = base + 1;
    const nearest = Math.min(last, Math.max(0, target)) * height;
    wheelTarget = nearest;
    if (Math.abs(feed.scrollTop - nearest) < 2) return;
    const from = feed.scrollTop;
    const start = performance.now();
    const duration = 260;
    const ease = (t: number) => 1 - Math.pow(1 - t, 3);
    cancelFrame();
    const step = (now: number) => {
      if (destroyed) return;
      const t = Math.min(1, (now - start) / duration);
      feed.scrollTop = from + (nearest - from) * ease(t);
      if (t < 1) wheelFrame = requestAnimationFrame(step);
      else wheelFrame = 0;
    };
    wheelFrame = requestAnimationFrame(step);
  }

  feed.addEventListener(
    "wheel",
    (event: WheelEvent) => {
      if (destroyed) return;
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
      // A mouse notch is ~120px, which would barely move the feed; a real
      // trackpad ticks are tiny, so they get only a small boost.
      const scale = Math.abs(raw) >= 60 ? 2.5 : 1.15;
      // A single notch should feel like a nudge, a fast spin like a throw.
      const delta = Math.max(
        -feed.clientHeight,
        Math.min(feed.clientHeight, raw * scale),
      );
      wheelDirection = delta >= 0 ? 1 : -1;
      wheelTarget = Math.min(max, Math.max(0, wheelTarget + delta));
      runWheel();
      window.clearTimeout(wheelSettle);
      wheelSettle = window.setTimeout(settleWheel, 170);
    },
    { passive: false },
  );

  function setIndex(next: number, fromScroll: boolean): void {
    if (next === index) return;
    const previous = index;
    index = next;
    window.clearTimeout(timer);
    resetProgress();
    counter.textContent = `${index + 1} / ${items.length}`;
    caption.replaceChildren(
      h("strong", {}, items[index].name),
      h("span", {}, `${formatBytes(items[index].size)} · ${kindLabel(items[index].kind)}`),
    );
    caption.classList.add("show");
    window.clearTimeout(captionTimer);
    captionTimer = window.setTimeout(() => caption.classList.remove("show"), 2600);
    const previousVideo = videoAt(previous);
    previousVideo?.pause();
    mediaFor(index);
    releaseFar(index);
    updateTracksButton();
    if (autoplayOn) startCurrent();
  }

  feed.addEventListener("scroll", () => {
    window.clearTimeout(scrollTimer);
    scrollTimer = window.setTimeout(() => {
      if (destroyed) return;
      const height = feed.clientHeight || 1;
      const next = Math.round(feed.scrollTop / height);
      if (next !== index) setIndex(next, true);
    }, 90);
  });

  feed.addEventListener("click", (event) => {
    const target = event.target as HTMLElement;
    if (target === feed || target === track || target.classList.contains("feed-item")) {
      toggleAutoplay();
    }
  });

  const onKey = (event: KeyboardEvent) => {
    if (destroyed) return;
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

  caption.classList.add("show");
  captionTimer = window.setTimeout(() => caption.classList.remove("show"), 2800);
  updateAutoplayButton();
  updateTracksButton();
  mediaFor(start);
  releaseFar(start);
  requestAnimationFrame(() => {
    feed.scrollTop = start * feed.clientHeight;
    if (autoplayOn) startCurrent();
  });

  let cleanupTimer = 0;
  function destroy(): void {
    if (destroyed) return;
    destroyed = true;
    window.clearTimeout(timer);
    window.clearTimeout(captionTimer);
    window.clearTimeout(scrollTimer);
    window.clearTimeout(cleanupTimer);
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
