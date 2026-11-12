import { createHash, randomBytes, randomInt } from 'node:crypto';
import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';

/** The same alphabet as the old unlock codes: no 0, 1, I, L or O, so a code survives being read out. */
const ALPHABET = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';

export const PLANS = ['free', 'plus', 'pro'];
const RANK = { free: 0, plus: 1, pro: 2 };

/** How many WebUntis accounts one Daybreak account may bind. */
export const MAX_WEBUNTIS = 3;
/** How many devices may be signed in to one account at once; the oldest sign-in is dropped beyond this. */
const MAX_TOKENS = 12;
/** The largest synced document, in bytes of JSON. */
export const MAX_SYNC_BYTES = 256 * 1024;
/** Months of Pro offered to people who paid through Ko-fi before. */
export const TRIAL_MONTHS = 3;

const sha256 = (text) => createHash('sha256').update(text).digest('hex');

/** A restore code looks like DA-K7M2-9QXP-4HTR. */
export function makeRestoreCode() {
  const group = () => Array.from({ length: 4 }, () => ALPHABET[randomInt(ALPHABET.length)]).join('');
  return `DA-${group()}-${group()}-${group()}`;
}

/** The code as stored and shown, from user input; undefined if it is not a valid code. */
export function normalizeRestoreCode(text) {
  if (typeof text !== 'string') return undefined;
  const compact = text.toUpperCase().replace(/[\s-]+/g, '');
  if (!/^DA[2-9A-HJKMNP-Z]{12}$/.test(compact)) return undefined;
  const body = compact.slice(2);
  return `DA-${body.slice(0, 4)}-${body.slice(4, 8)}-${body.slice(8)}`;
}

/** The plan an account has right now: the better of its paid plan and a running trial. */
export function effectivePlan(account, now) {
  let best = 'free';
  let until = null;
  const consider = (plan, end) => {
    if (end !== null && end <= now) return;
    if (RANK[plan] > RANK[best] || (plan === best && (end === null || (until !== null && end > until)))) {
      best = plan;
      until = end;
    }
  };
  if (account.paid) consider(account.paid.plan, account.paid.until ? Date.parse(account.paid.until) : null);
  if (account.trial?.until) consider('pro', Date.parse(account.trial.until));
  return { plan: best, until: until === null ? null : new Date(until).toISOString() };
}

/** What the app may know about an account. No hashes, tokens or Stripe ids. */
export function publicView(account, now) {
  const { plan, until } = effectivePlan(account, now);
  const trialRunning = Boolean(account.trial?.until && Date.parse(account.trial.until) > now);
  return {
    accountId: account.id,
    plan,
    planUntil: until,
    grandfathered: Boolean(account.grandfathered),
    // Supporter extras stay for good for people who paid through Ko-fi, and come with any plan.
    supporterExtras: Boolean(account.grandfathered) || plan !== 'free',
    webuntis: account.webuntis.map(({ id, label }) => ({ id, label })),
    proTrial: {
      offered: Boolean(account.grandfathered) && !account.trial?.acceptedAt,
      accepted: Boolean(account.trial?.acceptedAt),
      running: trialRunning,
      until: account.trial?.until ?? null,
    },
  };
}

const DEFAULT_SETTINGS = {
  plus: { priceId: '', label: 'Plus', monthlyCents: 299 },
  pro: { priceId: '', label: 'Pro', monthlyCents: 599 },
};

/**
 * The accounts: who they are, their plan, the WebUntis accounts they bind and the synced document. One
 * JSON file, written atomically. Restore codes and sign-in tokens are kept only as hashes; a WebUntis
 * account is kept only as a salted hash of server, school and student id plus a label the user chose.
 */
export class AccountStore {
  constructor({ config, now = () => Date.now() }) {
    this.file = join(config.dataDir, 'accounts.json');
    this.now = now;
    this.data = undefined;
    this.queue = Promise.resolve();
    this.byToken = new Map();
    this.byRestore = new Map();
  }

  async #load() {
    if (this.data) return this.data;
    let parsed;
    try {
      parsed = JSON.parse(await readFile(this.file, 'utf8'));
    } catch {
      parsed = undefined;
    }
    const data = {
      version: 1,
      salt: typeof parsed?.salt === 'string' && parsed.salt ? parsed.salt : randomBytes(24).toString('hex'),
      settings: { ...DEFAULT_SETTINGS, ...(parsed?.settings ?? {}) },
      legacyClaims: parsed?.legacyClaims && typeof parsed.legacyClaims === 'object' ? parsed.legacyClaims : {},
      accounts: parsed?.accounts && typeof parsed.accounts === 'object' ? parsed.accounts : {},
    };
    this.byToken.clear();
    this.byRestore.clear();
    for (const account of Object.values(data.accounts)) this.#index(account);
    this.data = data;
    return data;
  }

  #index(account) {
    this.byRestore.set(account.restoreHash, account.id);
    for (const token of account.tokens) this.byToken.set(token.hash, account.id);
  }

  /** Runs [change] on the data, then writes the file. Changes run one at a time. */
  #mutate(change) {
    const task = this.queue.then(async () => {
      const data = await this.#load();
      const result = await change(data);
      if (result?.save === false) return result.value;
      await mkdir(dirname(this.file), { recursive: true });
      const temporary = `${this.file}.tmp`;
      await writeFile(temporary, JSON.stringify(data), 'utf8');
      await rename(temporary, this.file);
      return result?.save === true ? result.value : result;
    });
    this.queue = task.catch(() => {});
    return task;
  }

  #newToken(account) {
    const token = randomBytes(32).toString('base64url');
    account.tokens.push({ hash: sha256(token), at: new Date(this.now()).toISOString() });
    while (account.tokens.length > MAX_TOKENS) {
      const dropped = account.tokens.shift();
      this.byToken.delete(dropped.hash);
    }
    this.byToken.set(sha256(token), account.id);
    return token;
  }

  /**
   * Creates an account. A 1.x supporter passes [legacyUnlockCode]: if the Ko-fi ledger has that code
   * paid and no other account used it yet, the account is grandfathered and offered the Pro trial.
   */
  create({ legacyUnlockCode, isLegacyPaid } = {}) {
    return this.#mutate(async (data) => {
      const id = randomBytes(9).toString('base64url');
      const restoreCode = makeRestoreCode();
      const account = {
        id,
        createdAt: new Date(this.now()).toISOString(),
        restoreHash: sha256(restoreCode),
        tokens: [],
        paid: null,
        trial: null,
        grandfathered: false,
        stripe: {},
        webuntis: [],
        sync: { version: 0, data: null },
      };
      let legacy = 'none';
      if (legacyUnlockCode) {
        if (data.legacyClaims[legacyUnlockCode]) legacy = 'claimed';
        else if (isLegacyPaid && (await isLegacyPaid(legacyUnlockCode))) {
          data.legacyClaims[legacyUnlockCode] = id;
          account.grandfathered = true;
          legacy = 'granted';
        } else legacy = 'unpaid';
      }
      const token = this.#newToken(account);
      data.accounts[id] = account;
      this.#index(account);
      return { save: true, value: { account: publicView(account, this.now()), restoreCode, token, legacy } };
    });
  }

  /** Signs a device in with a restore code. Returns undefined for an unknown code. */
  restore(restoreCode) {
    const code = normalizeRestoreCode(restoreCode);
    if (!code) return Promise.resolve(undefined);
    return this.#mutate(async (data) => {
      const id = this.byRestore.get(sha256(code));
      const account = id ? data.accounts[id] : undefined;
      if (!account) return { save: false, value: undefined };
      const token = this.#newToken(account);
      return { save: true, value: { account: publicView(account, this.now()), token } };
    });
  }

  /** The account a sign-in token belongs to, as the app may see it; undefined for an unknown token. */
  async authenticate(token) {
    if (typeof token !== 'string' || token.length < 20 || token.length > 100) return undefined;
    const data = await this.#load();
    const id = this.byToken.get(sha256(token));
    return id ? data.accounts[id] : undefined;
  }

  async get(id) {
    return (await this.#load()).accounts[id];
  }

  view(account) {
    return publicView(account, this.now());
  }

  async settings() {
    return (await this.#load()).settings;
  }

  saveSettings(settings) {
    return this.#mutate((data) => {
      data.settings = settings;
      return { save: true, value: settings };
    });
  }

  /** The hash that identifies a WebUntis student without saying who it is. */
  async #webuntisHash({ server, school, studentId }) {
    const { salt } = await this.#load();
    return sha256(`${salt}|${server}|${school}|${studentId}`);
  }

  /**
   * Binds a WebUntis account. Returns { binding, sharedWithOthers } or { error }: "bad_request",
   * "limit" (already three), "duplicate" (this account has it). [sharedWithOthers] tells that another
   * Daybreak account has bound the same student, which the app may warn about.
   */
  bindWebuntis(accountId, { server, school, studentId, label }) {
    const cleanServer = String(server ?? '').trim().toLowerCase().replace(/^https?:\/\//, '').replace(/\/.*$/, '');
    const cleanSchool = String(school ?? '').trim().toLowerCase();
    const cleanId = Number(studentId);
    if (!cleanServer || !cleanSchool || !Number.isSafeInteger(cleanId) || cleanId <= 0) {
      return Promise.resolve({ error: 'bad_request' });
    }
    const cleanLabel = String(label ?? '').trim().slice(0, 40) || 'WebUntis';
    return this.#mutate(async (data) => {
      const account = data.accounts[accountId];
      if (!account) return { save: false, value: { error: 'not_found' } };
      const hash = await this.#webuntisHash({ server: cleanServer, school: cleanSchool, studentId: cleanId });
      if (account.webuntis.some((entry) => entry.hash === hash)) return { save: false, value: { error: 'duplicate' } };
      if (account.webuntis.length >= MAX_WEBUNTIS) return { save: false, value: { error: 'limit' } };
      const binding = { id: randomBytes(6).toString('base64url'), hash, label: cleanLabel, addedAt: new Date(this.now()).toISOString() };
      const sharedWithOthers = Object.values(data.accounts).some(
        (other) => other.id !== accountId && other.webuntis.some((entry) => entry.hash === hash),
      );
      account.webuntis.push(binding);
      return { save: true, value: { binding: { id: binding.id, label: binding.label }, sharedWithOthers } };
    });
  }

  unbindWebuntis(accountId, bindingId) {
    return this.#mutate((data) => {
      const account = data.accounts[accountId];
      const index = account ? account.webuntis.findIndex((entry) => entry.id === bindingId) : -1;
      if (index === -1) return { save: false, value: false };
      account.webuntis.splice(index, 1);
      return { save: true, value: true };
    });
  }

  /** Starts the Pro trial offered to grandfathered accounts. */
  acceptTrial(accountId) {
    return this.#mutate((data) => {
      const account = data.accounts[accountId];
      if (!account) return { save: false, value: { error: 'not_found' } };
      if (!account.grandfathered) return { save: false, value: { error: 'not_offered' } };
      if (account.trial?.acceptedAt) return { save: false, value: { error: 'already_accepted' } };
      const start = new Date(this.now());
      const end = new Date(start);
      end.setUTCMonth(end.getUTCMonth() + TRIAL_MONTHS);
      account.trial = { acceptedAt: start.toISOString(), until: end.toISOString() };
      return { save: true, value: { account: publicView(account, this.now()) } };
    });
  }

  /** Sets the paid plan by hand (admin) or from Stripe. [until] null means no end. */
  setPaid(accountId, { plan, until = null, source }) {
    if (!PLANS.includes(plan) || plan === 'free') return Promise.resolve({ error: 'bad_plan' });
    return this.#mutate((data) => {
      const account = data.accounts[accountId];
      if (!account) return { save: false, value: { error: 'not_found' } };
      account.paid = { plan, until: until ? new Date(until).toISOString() : null, source };
      return { save: true, value: { account: publicView(account, this.now()) } };
    });
  }

  /** Marks an account as an old supporter by hand (admin): it keeps the extras and is offered the Pro trial. */
  setGrandfathered(accountId, value) {
    return this.#mutate((data) => {
      const account = data.accounts[accountId];
      if (!account) return { save: false, value: false };
      account.grandfathered = Boolean(value);
      return { save: true, value: true };
    });
  }

  /** Ends the paid plan only if it came from [source], so a Stripe cancellation never removes a plan given by hand. */
  clearPaidFrom(accountId, source) {
    return this.#mutate((data) => {
      const account = data.accounts[accountId];
      if (!account || account.paid?.source !== source) return { save: false, value: false };
      account.paid = null;
      return { save: true, value: true };
    });
  }

  clearPaid(accountId) {
    return this.#mutate((data) => {
      const account = data.accounts[accountId];
      if (!account) return { save: false, value: { error: 'not_found' } };
      account.paid = null;
      return { save: true, value: { account: publicView(account, this.now()) } };
    });
  }

  /** Remembers the Stripe ids of an account, so later subscription events can find it. */
  setStripe(accountId, stripe) {
    return this.#mutate((data) => {
      const account = data.accounts[accountId];
      if (!account) return { save: false, value: false };
      account.stripe = { ...account.stripe, ...stripe };
      return { save: true, value: true };
    });
  }

  async findByStripe({ customerId, subscriptionId }) {
    const accounts = Object.values((await this.#load()).accounts);
    return accounts.find(
      (account) =>
        (customerId && account.stripe?.customerId === customerId) ||
        (subscriptionId && account.stripe?.subscriptionId === subscriptionId),
    );
  }

  /** The synced document: { version, data }. */
  async getSync(accountId) {
    const account = (await this.#load()).accounts[accountId];
    return account ? { version: account.sync.version, data: account.sync.data } : undefined;
  }

  /**
   * Replaces the synced document if [baseVersion] is the current version, so two devices cannot overwrite
   * each other without noticing. Returns { version } or { error: 'conflict', version, data }.
   */
  putSync(accountId, { baseVersion, data: document }) {
    return this.#mutate((data) => {
      const account = data.accounts[accountId];
      if (!account) return { save: false, value: { error: 'not_found' } };
      if (baseVersion !== account.sync.version) {
        return { save: false, value: { error: 'conflict', version: account.sync.version, data: account.sync.data } };
      }
      account.sync = { version: account.sync.version + 1, data: document };
      return { save: true, value: { version: account.sync.version } };
    });
  }

  /** The account whose id starts with [prefix] (admin lookup), or undefined unless exactly one. */
  async find(prefix) {
    const text = String(prefix ?? '').trim();
    if (text.length < 4) return undefined;
    const matches = Object.values((await this.#load()).accounts).filter((account) => account.id.startsWith(text));
    return matches.length === 1 ? matches[0] : undefined;
  }

  async count() {
    return Object.keys((await this.#load()).accounts).length;
  }
}
