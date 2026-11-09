import { createHash, randomBytes } from 'node:crypto';
import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';

/**
 * What the assistant keeps on disk, in DATA_DIR:
 *   assistant-settings.json  the admin's choices: models, provider routing, limits, logging
 *   assistant-usage.json     the money each user spent per day (only the last few days)
 *   assistant-log.json       the last requests, for the admin page (kept for logRetentionDays)
 *
 * A "user" is the random install id the app makes on first use, stored only as a hash. There is no
 * account and no name.
 */

export const PROVIDER_SORTS = [
  { id: 'default', label: 'Default (OpenRouter balances price and uptime)' },
  { id: 'price', label: 'Cheapest provider first' },
  { id: 'throughput', label: 'Fastest output (highest throughput) first' },
  { id: 'latency', label: 'Quickest first token (lowest latency) first' },
];

/** Which model a user gets while the model switcher is hidden. */
export const HIDDEN_ROUTINGS = [
  { id: 'fixed', label: 'Always the default model' },
  { id: 'random', label: 'A random model from the list, per question' },
];

/** Reasoning levels OpenRouter passes on as reasoning.effort. Not every model takes every level. */
export const EFFORTS = ['minimal', 'low', 'medium', 'high', 'xhigh', 'max'];

export const DEFAULT_SETTINGS = Object.freeze({
  /**
   * The server-side switch. Off, every request fails with the upstream error. It is also off while the
   * client-side switch (feature "assistant" under Switches) is off; that one hides the assistant in the app.
   */
  enabled: true,
  /** Whether the app shows the model picker. Hidden, users get the default or a random model. */
  showModelSwitcher: true,
  hiddenRouting: 'fixed',
  /** Whether the app shows the reasoning-effort picker, which levels it offers, and the default ('' = the model's own). */
  /** Name new chats with a short AI title (one small extra request per chat, counted in the budget). */
  aiTitles: true,
  showEffortSelector: false,
  efforts: ['low', 'medium', 'high'],
  defaultEffort: '',
  models: [
    { id: 'anthropic/claude-sonnet-5.5', label: 'Standard' },
    { id: 'anthropic/claude-haiku-5.5', label: 'Schnell / Fast' },
    { id: 'anthropic/claude-opus-5.5', label: 'Pro' },
  ],
  defaultModel: 'anthropic/claude-sonnet-5.5',
  providerSort: 'default',
  allowFallbacks: true,
  /** Ask providers not to store or train on the prompts (OpenRouter data_collection: deny). */
  denyDataCollection: true,
  /** Daily budget per user in US cents (OpenRouter reports cost in credits, 1 credit = 1 USD). */
  freeCentsPerDay: 10,
  paidCentsPerDay: 20,
  /** Requests per IP address and minute. */
  assistantPerMinute: 10,
  updateChecksPerMinute: 30,
  downloadsPerHour: 20,
  /** Whether the log keeps the text of questions and answers, and for how many days. */
  logContent: true,
  logRetentionDays: 7,
});

const MAX_LOG_ENTRIES = 1000;
const USAGE_KEEP_DAYS = 3;

/** The hash a user is stored and shown under. */
export function userKey(installId) {
  return createHash('sha256').update(`daybreak-assistant:${installId}`).digest('hex').slice(0, 16);
}

/** "2026-11-09" in UTC: the day a budget belongs to. */
export function dayOf(time) {
  return new Date(time).toISOString().slice(0, 10);
}

/** Reads stored settings over the defaults; anything broken falls back to the default. */
export function readSettings(value) {
  const stored = value && typeof value === 'object' ? value : {};
  const settings = { ...DEFAULT_SETTINGS };
  if (typeof stored.enabled === 'boolean') settings.enabled = stored.enabled;
  if (Array.isArray(stored.models)) {
    settings.models = stored.models
      .filter((model) => model && typeof model.id === 'string' && model.id.trim())
      .map((model) => ({ id: model.id.trim(), label: typeof model.label === 'string' && model.label.trim() ? model.label.trim() : model.id.trim() }));
  }
  if (typeof stored.defaultModel === 'string') settings.defaultModel = stored.defaultModel;
  if (!settings.models.some((model) => model.id === settings.defaultModel)) settings.defaultModel = settings.models[0]?.id ?? '';
  if (PROVIDER_SORTS.some((sort) => sort.id === stored.providerSort)) settings.providerSort = stored.providerSort;
  if (HIDDEN_ROUTINGS.some((routing) => routing.id === stored.hiddenRouting)) settings.hiddenRouting = stored.hiddenRouting;
  if (Array.isArray(stored.efforts)) settings.efforts = EFFORTS.filter((effort) => stored.efforts.includes(effort));
  if (typeof stored.defaultEffort === 'string') settings.defaultEffort = EFFORTS.includes(stored.defaultEffort) ? stored.defaultEffort : '';
  for (const key of ['allowFallbacks', 'denyDataCollection', 'logContent', 'showModelSwitcher', 'showEffortSelector', 'aiTitles']) {
    if (typeof stored[key] === 'boolean') settings[key] = stored[key];
  }
  for (const key of ['freeCentsPerDay', 'paidCentsPerDay', 'assistantPerMinute', 'updateChecksPerMinute', 'downloadsPerHour', 'logRetentionDays']) {
    const number = Number(stored[key]);
    if (stored[key] !== undefined && Number.isFinite(number) && number >= 0) settings[key] = number;
  }
  // The privacy texts in the app promise at most 30 days.
  settings.logRetentionDays = Math.min(settings.logRetentionDays, 30);
  return settings;
}

/**
 * Reads the settings form of the admin page. Models are one per line, "id" or "id | label".
 * Returns { value } or { error }.
 */
export function parseSettingsForm(body) {
  const form = body && typeof body === 'object' ? body : {};
  const models = String(form.models ?? '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [id, ...label] = line.split('|');
      return { id: id.trim(), label: label.join('|').trim() || id.trim() };
    });
  if (models.length > 20) return { error: 'too_many_models' };
  if (models.some((model) => !/^[\w.\-:/]{3,120}$/.test(model.id))) return { error: 'bad_model' };
  const numbers = {};
  for (const [key, max] of [
    ['freeCentsPerDay', 100_000],
    ['paidCentsPerDay', 100_000],
    ['assistantPerMinute', 10_000],
    ['updateChecksPerMinute', 10_000],
    ['downloadsPerHour', 10_000],
    ['logRetentionDays', 30],
  ]) {
    const number = Number(form[key]);
    if (!Number.isFinite(number) || number < 0 || number > max) return { error: 'bad_number' };
    numbers[key] = number;
  }
  const defaultModel = models.some((model) => model.id === form.defaultModel) ? form.defaultModel : models[0]?.id ?? '';
  return {
    value: readSettings({
      enabled: form.enabled !== undefined,
      showModelSwitcher: form.showModelSwitcher !== undefined,
      showEffortSelector: form.showEffortSelector !== undefined,
      aiTitles: form.aiTitles !== undefined,
      efforts: String(form.efforts ?? '').split(/[\s,]+/).filter(Boolean),
      defaultEffort: String(form.defaultEffort ?? ''),
      hiddenRouting: form.hiddenRouting,
      models,
      defaultModel,
      providerSort: form.providerSort,
      allowFallbacks: form.allowFallbacks !== undefined,
      denyDataCollection: form.denyDataCollection !== undefined,
      logContent: form.logContent !== undefined,
      ...numbers,
    }),
  };
}

async function readJson(file) {
  try {
    return JSON.parse(await readFile(file, 'utf8'));
  } catch {
    return undefined;
  }
}

async function writeJsonAtomic(file, value) {
  await mkdir(dirname(file), { recursive: true });
  const temp = `${file}.${randomBytes(4).toString('hex')}.tmp`;
  await writeFile(temp, JSON.stringify(value));
  await rename(temp, file);
}

export class AssistantStore {
  constructor({ config, now = () => Date.now() }) {
    this.dir = config.dataDir;
    this.now = now;
    this.settingsFile = join(this.dir, 'assistant-settings.json');
    this.usageFile = join(this.dir, 'assistant-usage.json');
    this.logFile = join(this.dir, 'assistant-log.json');
    this.cache = undefined;
    this.queue = Promise.resolve();
  }

  async #load() {
    if (this.cache) return this.cache;
    const [settings, usage, log] = await Promise.all([
      readJson(this.settingsFile),
      readJson(this.usageFile),
      readJson(this.logFile),
    ]);
    this.cache = {
      settings: readSettings(settings),
      usage: usage && typeof usage === 'object' && !Array.isArray(usage) ? usage : {},
      log: Array.isArray(log) ? log : [],
    };
    return this.cache;
  }

  /** Writes one after the other, so two requests never interleave a file. */
  #persist(file, pick) {
    this.queue = this.queue.then(() => writeJsonAtomic(file, pick(this.cache))).catch(() => {});
    return this.queue;
  }

  async settings() {
    return (await this.#load()).settings;
  }

  /** The settings without waiting: the defaults until they were read once. For the rate limiters. */
  currentSettings() {
    return this.cache?.settings ?? DEFAULT_SETTINGS;
  }

  async saveSettings(settings) {
    const state = await this.#load();
    state.settings = readSettings(settings);
    await this.#persist(this.settingsFile, (cache) => cache.settings);
    return state.settings;
  }

  /** Cents the user spent today. */
  async spentToday(user) {
    const state = await this.#load();
    return state.usage[dayOf(this.now())]?.[user] ?? 0;
  }

  async addSpend(user, cents) {
    if (!(cents > 0)) return;
    const state = await this.#load();
    const today = dayOf(this.now());
    state.usage[today] ??= {};
    state.usage[today][user] = (state.usage[today][user] ?? 0) + cents;
    // Old days are of no use: budgets are per day.
    const cutoff = dayOf(this.now() - USAGE_KEEP_DAYS * 86_400_000);
    for (const day of Object.keys(state.usage)) if (day < cutoff) delete state.usage[day];
    await this.#persist(this.usageFile, (cache) => cache.usage);
  }

  /** Today's totals for the admin page: users, requests are in the log. */
  async usageToday() {
    const state = await this.#load();
    const today = state.usage[dayOf(this.now())] ?? {};
    const values = Object.values(today);
    return { users: values.length, cents: values.reduce((sum, value) => sum + value, 0) };
  }

  async addLog(entry) {
    const state = await this.#load();
    const id = randomBytes(6).toString('hex');
    state.log.unshift({ id, at: new Date(this.now()).toISOString(), ...entry });
    const cutoff = this.now() - state.settings.logRetentionDays * 86_400_000;
    state.log = state.log.filter((item) => Date.parse(item.at) >= cutoff).slice(0, MAX_LOG_ENTRIES);
    await this.#persist(this.logFile, (cache) => cache.log);
    return id;
  }

  async logs({ limit = 200 } = {}) {
    const state = await this.#load();
    const cutoff = this.now() - state.settings.logRetentionDays * 86_400_000;
    return state.log.filter((item) => Date.parse(item.at) >= cutoff).slice(0, limit);
  }

  async log(id) {
    return (await this.#load()).log.find((item) => item.id === id);
  }

  async clearLogs() {
    const state = await this.#load();
    state.log = [];
    await this.#persist(this.logFile, (cache) => cache.log);
  }
}
