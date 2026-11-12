import { randomBytes } from 'node:crypto';
import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { parseTag } from './version.js';

/**
 * What the admin page sets and the app reads: the oldest app version that may still be used, and
 * messages for everyone ("WebUntis is down", "Timetable changes this week"). Both live in one small
 * JSON file in DATA_DIR, so they survive redeploys together with the APKs and the Ko-fi ledger.
 */

export const LEVELS = ['info', 'warning', 'critical'];
const MAX_TEXT = 500;
const ID = /^[0-9a-f]{12}$/;
export const MESSAGE_ID_PATTERN = ID;

const clean = (value) => String(value ?? '').trim();

/**
 * Switches the developer can flip without a new release. They are read by the app on every start.
 * `updates` turns off the in-app update feature completely (checks, notifications, the update screen and
 * the required-version gate). `requiredVersion` only turns off the gate. `messages` hides the pop-ups.
 * `disabledFeatures` hides single app features; the app knows these ids (see FEATURES).
 */
export const SWITCHES = ['updates', 'requiredVersion', 'messages'];
export const FEATURES = [
  { id: 'messages.send', label: 'Writing messages to teachers' },
  { id: 'support.popup', label: 'Ko-fi support pop-up' },
  { id: 'reminders', label: 'Morning summary and evening reminders' },
  { id: 'assistant', label: 'AI assistant' },
];

export function defaultSwitches() {
  return { updates: true, requiredVersion: true, messages: true, disabledFeatures: [] };
}

/** Reads stored switches; anything missing or unknown falls back to "on" / "not disabled". */
export function readSwitches(value) {
  const stored = value && typeof value === 'object' ? value : {};
  const known = new Set(FEATURES.map((feature) => feature.id));
  return {
    updates: stored.updates !== false,
    requiredVersion: stored.requiredVersion !== false,
    messages: stored.messages !== false,
    disabledFeatures: Array.isArray(stored.disabledFeatures)
      ? stored.disabledFeatures.filter((id) => known.has(id))
      : [],
  };
}

/** Reads the admin form: a checked box arrives as "on", an unchecked one not at all. */
export function switchesFromForm(input) {
  const on = (name) => input[name] === 'on' || input[name] === true;
  return {
    updates: on('updates'),
    requiredVersion: on('requiredVersion'),
    messages: on('messages'),
    disabledFeatures: FEATURES.map((feature) => feature.id).filter((id) => on(`feature:${id}`)),
  };
}

export function emptyState() {
  return { version: 1, minVersion: null, switches: defaultSwitches(), messages: [] };
}

/** Reads "1.2.0" or "v1.2.0". Empty means "no minimum". Returns { minVersion } or { error }. */
export function parseMinVersion(text) {
  const value = clean(text);
  if (value === '') return { minVersion: null };
  const parsed = parseTag(value);
  if (!parsed) return { error: 'bad_version' };
  return { minVersion: { name: parsed.versionName, code: parsed.versionCode } };
}

/** Pictures that ship inside the app; the sheet names one and the app draws it. */
export const PRESET_IMAGES = ["playstore"];
export const SHEET_STYLES = ["plain", "brand", "warning", "critical"];
export const IMAGE_SHAPES = ["icon", "banner"];
export const PLATFORMS = ["all", "android", "desktop"];
export const INSTALL_SOURCES = ["any", "sideloaded", "play"];
export const BUTTON_ACTIONS = ["dismiss", "url"];
export const BUTTON_STYLES = ["filled", "tonal", "text"];
const MAX_BUTTONS = 3;

/** A repeated form field, as a list (one value arrives as a plain string). */
const rows = (input, key) => (input[key] === undefined ? [] : Array.isArray(input[key]) ? input[key] : [input[key]]);
const pick = (value, allowed, fallback) => (allowed.includes(clean(value)) ? clean(value) : fallback);

/**
 * Reads the part of a message that makes it a sheet: a title, a picture, how it looks, who sees it and up to
 * three buttons (label, what it does, a small note under it). Returns { sheet } (null when the admin left all of
 * it at the defaults, so the message stays a plain one) or { error }. A link must be https.
 */
export function normalizeSheet(input, { imageExists = () => true } = {}) {
  const titleDe = clean(input.titleDe);
  const titleEn = clean(input.titleEn);
  if (titleDe.length > 80 || titleEn.length > 80) return { error: "bad_title" };

  const kind = pick(input.imageKind, ["none", "preset", "upload"], "none");
  let image = null;
  if (kind === "preset") {
    const value = clean(input.imagePreset);
    if (!PRESET_IMAGES.includes(value)) return { error: "bad_image" };
    image = { kind, value, shape: pick(input.imageShape, IMAGE_SHAPES, "icon") };
  } else if (kind === "upload") {
    const value = clean(input.imageUpload);
    if (!ID.test(value) || !imageExists(value)) return { error: "bad_image" };
    image = { kind, value, shape: pick(input.imageShape, IMAGE_SHAPES, "icon") };
  }

  const buttons = [];
  const labelsDe = rows(input, "buttonDe");
  const count = Math.max(labelsDe.length, rows(input, "buttonEn").length);
  for (let index = 0; index < count; index += 1) {
    const cell = (key) => clean(rows(input, key)[index]);
    const de = cell("buttonDe");
    const en = cell("buttonEn");
    if (!de && !en) continue;
    if (de.length > 40 || en.length > 40) return { error: "bad_button" };
    const action = pick(cell("buttonAction"), BUTTON_ACTIONS, "dismiss");
    const url = cell("buttonUrl");
    if (action === "url" && !/^https:\/\/\S{3,500}$/.test(url)) return { error: "bad_url" };
    const infoDe = cell("buttonInfoDe");
    const infoEn = cell("buttonInfoEn");
    if (infoDe.length > 200 || infoEn.length > 200) return { error: "bad_button" };
    buttons.push({
      de,
      en,
      action,
      url: action === "url" ? url : "",
      style: pick(cell("buttonStyle"), BUTTON_STYLES, buttons.length === 0 ? "filled" : "tonal"),
      closes: cell("buttonCloses") !== "no",
      infoDe,
      infoEn,
    });
    if (buttons.length > MAX_BUTTONS) return { error: "too_many_buttons" };
  }

  const dismissible = input.dismissible === "on" || input.dismissible === true;
  const dismissibleGiven = input.dismissibleGiven !== undefined;
  const sheet = {
    titleDe,
    titleEn,
    image,
    style: pick(input.style, SHEET_STYLES, "plain"),
    // The form always sends dismissibleGiven, so an unchecked box means "not dismissible"; callers without a form mean yes.
    dismissible: dismissibleGiven ? dismissible : true,
    platform: pick(input.platform, PLATFORMS, "all"),
    installSource: pick(input.installSource, INSTALL_SOURCES, "any"),
    buttons,
  };
  // A page that cannot be swiped away needs a button that closes it.
  if (!sheet.dismissible && !buttons.some((button) => button.closes)) return { error: "needs_button" };

  const plain =
    !titleDe && !titleEn && !image && buttons.length === 0 && sheet.style === "plain" &&
    sheet.platform === "all" && sheet.installSource === "any" && sheet.dismissible;
  return { sheet: plain ? null : sheet };
}

/**
 * Checks what the admin typed for a message. Needs a level, text in German or English (or both), and an
 * optional end time with an offset, such as 2026-11-07T18:00:00Z. Returns { message } or { error }.
 */
export function normalizeMessage(input, { now = Date.now(), id = newId(), imageExists } = {}) {
  const level = clean(input.level);
  if (!LEVELS.includes(level)) return { error: 'bad_level' };
  const de = clean(input.de);
  const en = clean(input.en);
  const sheet = normalizeSheet(input, { imageExists });
  if (sheet.error) return { error: sheet.error };
  // A sheet may carry its words in the title and the buttons alone.
  if (!de && !en && !(sheet.sheet?.titleDe || sheet.sheet?.titleEn)) return { error: 'bad_text' };
  if (de.length > MAX_TEXT || en.length > MAX_TEXT) return { error: 'bad_text' };
  let until = null;
  const endsAt = clean(input.until);
  if (endsAt) {
    const time = Date.parse(endsAt);
    if (Number.isNaN(time)) return { error: 'bad_until' };
    until = new Date(time).toISOString();
  }
  return {
    message: {
      id, level, de, en, enabled: true, until, createdAt: new Date(now).toISOString(),
      ...(sheet.sheet ? { sheet: sheet.sheet } : {}),
    },
  };
}

/** What the app gets: the minimum version and the messages that are switched on and not over yet. */
export function publicStatus(state, now = Date.now()) {
  const switches = readSwitches(state.switches);
  return {
    minVersionName: state.minVersion?.name ?? null,
    minVersionCode: state.minVersion?.code ?? null,
    switches: { updates: switches.updates, requiredVersion: switches.requiredVersion, messages: switches.messages },
    disabledFeatures: switches.disabledFeatures,
    messages: state.messages
      .filter((message) => message.enabled && (!message.until || Date.parse(message.until) > now))
      .map(({ id, level, de, en, until, sheet }) => ({ id, level, de, en, until, ...(sheet ? { sheet } : {}) })),
  };
}

function newId() {
  return randomBytes(6).toString('hex');
}

/**
 * The stored state. Writes go one after another and replace the file in one step, so a crash never
 * leaves half a file. A file that exists but cannot be read is an error, not an empty state: the
 * app then keeps what it knew instead of forgetting the minimum version.
 */
export class AnnouncementStore {
  constructor({ config }) {
    this.file = join(config.dataDir, 'announcements.json');
    this.state = undefined;
    this.queue = Promise.resolve();
  }

  async read() {
    if (this.state) return this.state;
    let text;
    try {
      text = await readFile(this.file, 'utf8');
    } catch (error) {
      if (error?.code !== 'ENOENT') throw error;
      this.state = emptyState();
      return this.state;
    }
    const parsed = JSON.parse(text);
    this.state = {
      version: 1,
      minVersion: parsed.minVersion ?? null,
      switches: readSwitches(parsed.switches),
      messages: Array.isArray(parsed.messages) ? parsed.messages : [],
    };
    return this.state;
  }

  /** The answer for the app. */
  async status(now = Date.now()) {
    return publicStatus(await this.read(), now);
  }

  /** A copy of everything, for the admin page. */
  async snapshot() {
    return structuredClone(await this.read());
  }

  setMinVersion(text) {
    return this.#change((state) => {
      const result = parseMinVersion(text);
      if (result.error) return result;
      state.minVersion = result.minVersion;
      return { saved: true };
    });
  }

  setSwitches(input) {
    return this.#change((state) => {
      state.switches = switchesFromForm(input);
      return { saved: true };
    });
  }

  /** Stores a message made by the server itself (the one-click demo); no form is read. */
  addPreparedMessage(message) {
    return this.#change((state) => {
      state.messages.unshift(message);
      return { saved: true };
    });
  }

  addMessage(input, options) {
    return this.#change((state) => {
      const result = normalizeMessage(input, options);
      if (result.error) return result;
      state.messages.unshift(result.message);
      return { saved: true };
    });
  }

  toggleMessage(id) {
    return this.#change((state) => {
      const message = ID.test(id) ? state.messages.find((entry) => entry.id === id) : undefined;
      if (!message) return { error: 'not_found' };
      message.enabled = !message.enabled;
      return { saved: true };
    });
  }

  removeMessage(id) {
    return this.#change((state) => {
      const before = state.messages.length;
      state.messages = state.messages.filter((entry) => entry.id !== id);
      if (!ID.test(id) || before === state.messages.length) return { error: 'not_found' };
      return { saved: true };
    });
  }

  /** Applies [change] to a copy of the state and writes it only when [change] does not report an error. */
  #change(change) {
    const task = this.queue.then(async () => {
      const state = structuredClone(await this.read());
      const result = change(state);
      if (result.error) return result;
      await mkdir(dirname(this.file), { recursive: true });
      const temporary = `${this.file}.tmp`;
      await writeFile(temporary, `${JSON.stringify(state, null, 2)}\n`, 'utf8');
      await rename(temporary, this.file);
      this.state = state;
      return result;
    });
    this.queue = task.catch(() => {});
    return task;
  }
}
