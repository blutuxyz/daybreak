import { timingSafeEqual } from 'node:crypto';
import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';

/**
 * Unlock codes look like DB-K7M2-9QXP. The alphabet has no 0, 1, I, L or O, so a code survives being
 * read out or typed into a message on Ko-fi.
 */
const CODE_BODY = '[2-9A-HJKMNP-Z]{4}';
const CODE_IN_TEXT = new RegExp(`\\bDB[\\s-]*(${CODE_BODY})[\\s-]*(${CODE_BODY})\\b`, 'i');
const CODE_EXACT = new RegExp(`^DB-${CODE_BODY}-${CODE_BODY}$`);

/** The code as the app writes it, from user input; undefined if it is not a valid code. */
export function normalizeCode(text) {
  if (typeof text !== 'string') return undefined;
  const match = CODE_IN_TEXT.exec(text.trim().toUpperCase());
  const code = match ? `DB-${match[1]}-${match[2]}` : undefined;
  return code && CODE_EXACT.test(code) ? code : undefined;
}

/** Finds an unlock code inside the free text a supporter left with a payment. */
export function extractCode(message) {
  return normalizeCode(message);
}

/**
 * Reads the body Ko-fi posts to a webhook: a form with one field "data" that holds JSON.
 * Returns undefined when it is not that.
 */
export function parseKofiBody(body) {
  const raw = typeof body === 'string' ? body : body?.data;
  if (typeof raw !== 'string') return undefined;
  try {
    const parsed = JSON.parse(raw);
    return parsed && typeof parsed === 'object' ? parsed : undefined;
  } catch {
    return undefined;
  }
}

/** Compares two secrets without leaking how many leading characters matched. */
export function sameSecret(given, expected) {
  if (typeof given !== 'string' || typeof expected !== 'string') return false;
  const a = Buffer.from(given);
  const b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}

/**
 * The payments that unlocked a code. It keeps only what is needed to answer "was this code paid":
 * the code, the Ko-fi transaction id (so a repeated webhook is counted once), the amount and the
 * time. No name, no e-mail address and no message text are stored.
 */
export class SupportLedger {
  constructor({ config }) {
    this.file = join(config.dataDir, 'support-payments.json');
    this.entries = undefined;
    this.queue = Promise.resolve();
  }

  async #load() {
    if (this.entries) return this.entries;
    try {
      const parsed = JSON.parse(await readFile(this.file, 'utf8'));
      this.entries = parsed && typeof parsed.payments === 'object' && parsed.payments ? parsed.payments : {};
    } catch {
      this.entries = {};
    }
    return this.entries;
  }

  async isPaid(code) {
    return Boolean((await this.#load())[code]);
  }

  /**
   * Records a payment that was granted by hand on the admin page, for a code whose Ko-fi payment never
   * arrived. The record says so (source "admin"). Returns false if the code is already paid.
   */
  grant({ code, at = new Date() }) {
    return this.record({ code, transactionId: `admin-${at.getTime()}`, amount: 0, currency: 'manual', source: 'admin', at });
  }

  /** Remembers a payment for [code]. Returns false if this transaction was already recorded. */
  record({ code, transactionId, amount, currency, source, at = new Date() }) {
    const task = this.queue.then(async () => {
      const entries = await this.#load();
      const known = Object.values(entries).some((entry) => entry.transactionId === transactionId);
      if ((known && transactionId) || entries[code]) return false;
      entries[code] = { transactionId, amount, currency, at: at.toISOString(), ...(source ? { source } : {}) };
      await mkdir(dirname(this.file), { recursive: true });
      const temporary = `${this.file}.tmp`;
      await writeFile(temporary, JSON.stringify({ version: 1, payments: entries }), 'utf8');
      await rename(temporary, this.file);
      return true;
    });
    this.queue = task.catch(() => {});
    return task;
  }
}

/** A small fixed-window counter per key, so the status endpoint cannot be used to guess codes. */
export class RateLimiter {
  constructor({ limit, windowMs, now = () => Date.now() }) {
    this.limit = limit;
    this.windowMs = windowMs;
    this.now = now;
    this.windows = new Map();
  }

  /** True if the call is allowed. */
  allow(key) {
    const time = this.now();
    const window = this.windows.get(key);
    if (!window || time - window.start >= this.windowMs) {
      this.windows.set(key, { start: time, count: 1 });
      if (this.windows.size > 10_000) this.#sweep(time);
      return true;
    }
    window.count += 1;
    return window.count <= this.limit;
  }

  #sweep(time) {
    for (const [key, window] of this.windows) {
      if (time - window.start >= this.windowMs) this.windows.delete(key);
    }
  }
}
