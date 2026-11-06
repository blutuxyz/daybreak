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

/**
 * Checks what the admin typed for a message. Needs a level, text in German or English (or both), and an
 * optional end time with an offset, such as 2026-11-07T18:00:00Z. Returns { message } or { error }.
 */
export function normalizeMessage(input, { now = Date.now(), id = newId() } = {}) {
  const level = clean(input.level);
  if (!LEVELS.includes(level)) return { error: 'bad_level' };
  const de = clean(input.de);
  const en = clean(input.en);
  if (!de && !en) return { error: 'bad_text' };
  if (de.length > MAX_TEXT || en.length > MAX_TEXT) return { error: 'bad_text' };
  let until = null;
  const endsAt = clean(input.until);
  if (endsAt) {
    const time = Date.parse(endsAt);
    if (Number.isNaN(time)) return { error: 'bad_until' };
    until = new Date(time).toISOString();
  }
  return {
    message: { id, level, de, en, enabled: true, until, createdAt: new Date(now).toISOString() },
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
      .map(({ id, level, de, en, until }) => ({ id, level, de, en, until })),
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
