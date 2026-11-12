import { createHmac, timingSafeEqual } from 'node:crypto';

/** How far a webhook's timestamp may be from now, in seconds (Stripe's own default). */
const TOLERANCE_SECONDS = 300;
/** Days of access kept after a period ends while Stripe still retries a payment. */
const GRACE_DAYS = 3;

/**
 * Checks the Stripe-Signature header of a webhook. The header is "t=<time>,v1=<hmac>[,v1=…]"; the
 * HMAC-SHA256 is over "<time>.<raw body>" with the webhook secret.
 */
export function verifyStripeSignature({ payload, header, secret, now = Date.now() }) {
  if (typeof payload !== 'string' || typeof header !== 'string' || !secret) return false;
  const parts = header.split(',').map((part) => part.trim().split('='));
  const time = parts.find(([key]) => key === 't')?.[1];
  const signatures = parts.filter(([key]) => key === 'v1').map(([, value]) => value);
  if (!time || signatures.length === 0 || !/^\d+$/.test(time)) return false;
  if (Math.abs(now / 1000 - Number(time)) > TOLERANCE_SECONDS) return false;
  const expected = createHmac('sha256', secret).update(`${time}.${payload}`).digest('hex');
  const expectedBuffer = Buffer.from(expected);
  return signatures.some((given) => {
    const givenBuffer = Buffer.from(given);
    return givenBuffer.length === expectedBuffer.length && timingSafeEqual(givenBuffer, expectedBuffer);
  });
}

/** A form body as Stripe wants it: nested keys like line_items[0][price]. */
function form(fields) {
  const body = new URLSearchParams();
  for (const [key, value] of Object.entries(fields)) if (value !== undefined && value !== '') body.set(key, String(value));
  return body;
}

/** The Stripe calls this server makes, over plain fetch (no SDK). */
export class StripeClient {
  constructor({ config, fetchImpl = fetch }) {
    this.secretKey = config.stripeSecretKey;
    this.baseUrl = config.stripeApiUrl;
    this.fetch = fetchImpl;
  }

  get configured() {
    return Boolean(this.secretKey);
  }

  async #post(path, fields) {
    const response = await this.fetch(`${this.baseUrl}${path}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${this.secretKey}`, 'Content-Type': 'application/x-www-form-urlencoded' },
      body: form(fields),
    });
    const json = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(`stripe ${path} ${response.status} ${json?.error?.type ?? ''}`.trim());
    return json;
  }

  /** A Checkout page for a subscription. The account id travels with it, so the webhook can find the account. */
  async checkout({ accountId, plan, priceId, customerId, successUrl, cancelUrl }) {
    const session = await this.#post('/v1/checkout/sessions', {
      mode: 'subscription',
      'line_items[0][price]': priceId,
      'line_items[0][quantity]': 1,
      client_reference_id: accountId,
      customer: customerId,
      'subscription_data[metadata][accountId]': accountId,
      'subscription_data[metadata][plan]': plan,
      'metadata[accountId]': accountId,
      'metadata[plan]': plan,
      success_url: successUrl,
      cancel_url: cancelUrl,
    });
    return session.url;
  }

  /**
   * Cancels a subscription at once (used when an account is deleted). A subscription Stripe no longer knows
   * (404) counts as cancelled. Any other failure throws, so the account is not deleted while still billed.
   */
  async cancelSubscription(subscriptionId) {
    const response = await this.fetch(`${this.baseUrl}/v1/subscriptions/${encodeURIComponent(subscriptionId)}`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${this.secretKey}` },
    });
    if (response.ok || response.status === 404) return true;
    throw new Error(`stripe cancel ${response.status}`);
  }

  /** The Stripe page where a customer changes the card or cancels. */
  async portal({ customerId, returnUrl }) {
    const session = await this.#post('/v1/billing_portal/sessions', { customer: customerId, return_url: returnUrl });
    return session.url;
  }
}

const ACTIVE = new Set(['active', 'trialing', 'past_due']);

/**
 * Turns a verified Stripe event into a change of an account's plan. [priceToPlan] maps a Stripe price id
 * to "plus" or "pro" (the prices are set on the admin page); the subscription's own metadata wins.
 * Returns what happened, for the log and the tests.
 */
export async function handleStripeEvent({ event, accounts, priceToPlan, now = Date.now() }) {
  const object = event?.data?.object;
  if (!object) return { handled: false };

  if (event.type === 'checkout.session.completed') {
    const accountId = object.client_reference_id ?? object.metadata?.accountId;
    if (!accountId || !(await accounts.get(accountId))) return { handled: false, reason: 'no_account' };
    await accounts.setStripe(accountId, { customerId: object.customer, subscriptionId: object.subscription });
    return { handled: true, accountId };
  }

  if (!event.type?.startsWith('customer.subscription.')) return { handled: false };
  const account =
    (object.metadata?.accountId ? await accounts.get(object.metadata.accountId) : undefined) ??
    (await accounts.findByStripe({ customerId: object.customer, subscriptionId: object.id }));
  if (!account) return { handled: false, reason: 'no_account' };
  await accounts.setStripe(account.id, { customerId: object.customer, subscriptionId: object.id, status: object.status });

  if (event.type === 'customer.subscription.deleted' || !ACTIVE.has(object.status)) {
    await accounts.clearPaidFrom(account.id, 'stripe');
    return { handled: true, accountId: account.id, plan: 'free' };
  }
  const priceId = object.items?.data?.[0]?.price?.id;
  const plan = object.metadata?.plan ?? (await priceToPlan(priceId));
  if (plan !== 'plus' && plan !== 'pro') return { handled: false, reason: 'unknown_plan' };
  const periodEnd = Number(object.current_period_end ?? object.items?.data?.[0]?.current_period_end);
  const until = Number.isFinite(periodEnd) ? periodEnd * 1000 + GRACE_DAYS * 86_400_000 : now + 35 * 86_400_000;
  await accounts.setPaid(account.id, { plan, until, source: 'stripe' });
  return { handled: true, accountId: account.id, plan };
}
