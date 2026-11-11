import { createHash, randomBytes } from 'node:crypto';
import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';

/**
 * What the assistant keeps on disk, in DATA_DIR:
 *   assistant-settings.json  the admin's choices: providers, models, routing, limits, logging
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

/**
 * The kinds of upstream a provider can be: OpenRouter (its own options), any server that speaks OpenAI's
 * chat completions, and any server that speaks Anthropic's messages API.
 */
export const PROVIDER_TYPES = [
  { id: 'openrouter', label: 'OpenRouter' },
  { id: 'openai', label: 'OpenAI-compatible (chat completions)' },
  { id: 'anthropic', label: 'Anthropic-compatible (messages)' },
];

/** The look of a model's tag next to the picker: a quiet info badge, or an alarming one. */
export const TAG_KINDS = [
  { id: 'info', label: 'Info' },
  { id: 'alarm', label: 'Alarm' },
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
  /** Name new chats with a short AI title (one small extra request per chat, counted in the budget). */
  aiTitles: true,
  /** Whether the app shows the reasoning-effort picker, which levels it offers, and the default ('' = the model's own). */
  showEffortSelector: false,
  /**
   * Live HTML previews: a website the assistant writes is shown as a card with a Preview and its code. Off, the
   * assistant answers with the page as an HTML code block, and the app shows no Preview.
   */
  livePreviews: true,
  efforts: ['low', 'medium', 'high'],
  defaultEffort: '',
  /**
   * The upstreams. An empty key of an OpenRouter provider falls back to OPENROUTER_API_KEY, and an empty base
   * URL to the OpenRouter address; other providers need both on this page.
   */
  providers: [{ id: 'openrouter', name: 'OpenRouter', type: 'openrouter', baseUrl: '', apiKey: '' }],
  /**
   * The models the app may offer. A disabled model stays in the picker but answers with the upstream error.
   * inputPrice and outputPrice are US dollars per million tokens, for providers that do not report a cost
   * (OpenRouter does); they count against the daily budget.
   */
  models: [
    { id: 'anthropic/claude-sonnet-5.5', label: 'Standard', provider: 'openrouter', enabled: true, description: '', tag: '', tagKind: 'info', inputPrice: 0, outputPrice: 0 },
    { id: 'anthropic/claude-haiku-5.5', label: 'Schnell / Fast', provider: 'openrouter', enabled: true, description: '', tag: '', tagKind: 'info', inputPrice: 0, outputPrice: 0 },
    { id: 'anthropic/claude-opus-5.5', label: 'Pro', provider: 'openrouter', enabled: true, description: '', tag: '', tagKind: 'info', inputPrice: 0, outputPrice: 0 },
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
const MAX_PROVIDERS = 10;
const MAX_MODELS = 40;
const LOG_PAGE_SIZE = 25;

/** The hash a user is stored and shown under. */
export function userKey(installId) {
  return createHash('sha256').update(`daybreak-assistant:${installId}`).digest('hex').slice(0, 16);
}

/** "2026-11-09" in UTC: the day a budget belongs to. */
export function dayOf(time) {
  return new Date(time).toISOString().slice(0, 10);
}

function cleanText(value, max) {
  return typeof value === 'string' ? value.trim().slice(0, max) : '';
}

function cleanPrice(value) {
  const number = Number(value);
  return Number.isFinite(number) && number >= 0 && number <= 10_000 ? number : 0;
}

function cleanProvider(provider) {
  if (!provider || typeof provider !== 'object') return null;
  const type = PROVIDER_TYPES.some((item) => item.id === provider.type) ? provider.type : 'openai';
  const id = cleanText(provider.id, 40) || `p${randomBytes(4).toString('hex')}`;
  const baseUrl = cleanText(provider.baseUrl, 300);
  return {
    id,
    name: cleanText(provider.name, 40) || type,
    type,
    baseUrl: /^https?:\/\//i.test(baseUrl) ? baseUrl : '',
    apiKey: cleanText(provider.apiKey, 300),
  };
}

function cleanModel(model, providers) {
  if (!model || typeof model !== 'object') return null;
  const id = cleanText(model.id, 120);
  if (!id) return null;
  const provider = providers.some((item) => item.id === model.provider) ? model.provider : providers[0]?.id ?? '';
  return {
    id,
    label: cleanText(model.label, 60) || id,
    provider,
    enabled: model.enabled !== false,
    description: cleanText(model.description, 300),
    tag: cleanText(model.tag, 24),
    tagKind: model.tagKind === 'alarm' ? 'alarm' : 'info',
    inputPrice: cleanPrice(model.inputPrice),
    outputPrice: cleanPrice(model.outputPrice),
  };
}

/** Reads stored settings over the defaults; anything broken falls back to the default. */
export function readSettings(value) {
  const stored = value && typeof value === 'object' ? value : {};
  const settings = { ...DEFAULT_SETTINGS };
  if (typeof stored.enabled === 'boolean') settings.enabled = stored.enabled;
  if (Array.isArray(stored.providers)) {
    const providers = stored.providers.map(cleanProvider).filter(Boolean).slice(0, MAX_PROVIDERS);
    settings.providers = providers;
  }
  if (Array.isArray(stored.models)) {
    settings.models = stored.models.map((model) => cleanModel(model, settings.providers)).filter(Boolean).slice(0, MAX_MODELS);
  } else {
    settings.models = DEFAULT_SETTINGS.models.map((model) => ({ ...model }));
  }
  if (typeof stored.defaultModel === 'string') settings.defaultModel = stored.defaultModel;
  if (!settings.models.some((model) => model.id === settings.defaultModel)) settings.defaultModel = settings.models[0]?.id ?? '';
  if (PROVIDER_SORTS.some((sort) => sort.id === stored.providerSort)) settings.providerSort = stored.providerSort;
  if (HIDDEN_ROUTINGS.some((routing) => routing.id === stored.hiddenRouting)) settings.hiddenRouting = stored.hiddenRouting;
  if (Array.isArray(stored.efforts)) settings.efforts = EFFORTS.filter((effort) => stored.efforts.includes(effort));
  if (typeof stored.defaultEffort === 'string') settings.defaultEffort = EFFORTS.includes(stored.defaultEffort) ? stored.defaultEffort : '';
  for (const key of ['allowFallbacks', 'denyDataCollection', 'logContent', 'showModelSwitcher', 'showEffortSelector', 'aiTitles', 'livePreviews']) {
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
 * Reads the settings form of the admin page. Providers and models are rows: the same field repeated once per
 * row, in the same order. A blank provider key keeps the key that is stored. Returns { value } or { error }.
 */
export function parseSettingsForm(body, current = DEFAULT_SETTINGS) {
  const form = body && typeof body === 'object' ? body : {};
  const rows = (key) => (form[key] === undefined ? [] : Array.isArray(form[key]) ? form[key] : [form[key]]);
  const cell = (key, index) => String(rows(key)[index] ?? '');

  const providerCount = rows('providerName').length;
  if (providerCount > MAX_PROVIDERS) return { error: 'too_many_providers' };
  const providers = [];
  for (let index = 0; index < providerCount; index += 1) {
    const type = PROVIDER_TYPES.some((item) => item.id === cell('providerType', index)) ? cell('providerType', index) : 'openai';
    const baseUrl = cell('providerBase', index).trim();
    if (baseUrl && !/^https?:\/\//i.test(baseUrl)) return { error: 'bad_url' };
    const name = cell('providerName', index).trim();
    const baseUrlTyped = cell('providerBase', index).trim();
    const typedKey = cell('providerKey', index).trim();
    // A blank row of the table is not a provider.
    if (!name && !baseUrlTyped && !typedKey && !cell('providerId', index)) continue;
    const id = cell('providerId', index) || `p${randomBytes(4).toString('hex')}`;
    const existing = current.providers?.find((item) => item.id === id);
    providers.push({
      id,
      name: cell('providerName', index).trim(),
      type,
      baseUrl,
      apiKey: cell('providerKey', index).trim() || existing?.apiKey || '',
    });
  }

  const modelCount = rows('modelId').length;
  if (modelCount > MAX_MODELS) return { error: 'too_many_models' };
  const models = [];
  for (let index = 0; index < modelCount; index += 1) {
    const id = cell('modelId', index).trim();
    // A blank row of the table is not a model.
    if (!id) continue;
    if (!/^[\w.\-:/]{3,120}$/.test(id)) return { error: 'bad_model' };
    for (const key of ['modelInputPrice', 'modelOutputPrice']) {
      const number = Number(cell(key, index) || 0);
      if (!Number.isFinite(number) || number < 0 || number > 10_000) return { error: 'bad_number' };
    }
    models.push({
      id,
      label: cell('modelLabel', index).trim() || id,
      provider: cell('modelProvider', index),
      enabled: cell('modelEnabled', index) !== 'off',
      description: cell('modelDescription', index).trim(),
      tag: cell('modelTag', index).trim(),
      tagKind: cell('modelTagKind', index) === 'alarm' ? 'alarm' : 'info',
      inputPrice: Number(cell('modelInputPrice', index) || 0),
      outputPrice: Number(cell('modelOutputPrice', index) || 0),
    });
  }

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
      livePreviews: form.livePreviews !== undefined,
      aiTitles: form.aiTitles !== undefined,
      efforts: String(form.efforts ?? '').split(/[\s,]+/).filter(Boolean),
      defaultEffort: String(form.defaultEffort ?? ''),
      hiddenRouting: form.hiddenRouting,
      providers,
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

  /** The newest entries that are still kept, up to limit. */
  async logs({ limit = 200 } = {}) {
    const state = await this.#load();
    const cutoff = this.now() - state.settings.logRetentionDays * 86_400_000;
    return state.log.filter((item) => Date.parse(item.at) >= cutoff).slice(0, limit);
  }

  /** One page of the log, newest first: { entries, total, page, pages }. */
  async logPage({ page = 1, pageSize = LOG_PAGE_SIZE } = {}) {
    const state = await this.#load();
    const cutoff = this.now() - state.settings.logRetentionDays * 86_400_000;
    const all = state.log.filter((item) => Date.parse(item.at) >= cutoff);
    const pages = Math.max(1, Math.ceil(all.length / pageSize));
    const current = Math.min(Math.max(1, Math.floor(Number(page)) || 1), pages);
    const start = (current - 1) * pageSize;
    return { entries: all.slice(start, start + pageSize), total: all.length, page: current, pages, pageSize };
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
