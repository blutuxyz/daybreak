# Daybreak 2.0.0 — decisions and contract

Closed source from 2.0.0. Android first; desktop follows the same API.

## Decisions (from the developer)

- **Name in texts:** "Daybreak", never "the developer". No technical leaks in user-facing legal text (no `update-server`, endpoints, file names, Gradle or GitHub names). Payment provider (Stripe) is named, because the law requires it. Contact is an email (TODO: developer supplies it; use `LegalTerms.CONTACT_EMAIL` as one constant until then).
- **Ko-fi:** removed from both apps (button, popup, unlock code, `SupportStore`, `SupportApi`, hide-button sheets). Ko-fi stays only as a plain "Donate" link on the web page served by the update server. It does not buy a plan.
- **Plans:** Free, Plus (2.99 EUR/month), Pro (5.99 EUR/month). Prices and Stripe price ids live in the admin panel. Sold through Stripe Checkout opened in the browser. The admin panel can grant, extend and revoke a plan for an account without Stripe.
- **Daybreak account:** random account id plus a restore code (no email, no password). Up to **3 WebUntis accounts** can be bound to one Daybreak account. The plan and synced features belong to the account, not to the install. Several devices can sign in to one account with the restore code.
- **Existing users (any 1.x install):** on first start after the update they are asked to migrate to a Daybreak account (create one, or sign in with a restore code). Their old install id and Ko-fi `paid` status are carried over.
- **Old Ko-fi payers:** keep the supporter extras for good (grandfathered, stored as a server-side flag on the account). They also get a popup on first launch after migrating: 3 months of Pro. If dismissed the first time, a reminder stays pinned at the top of More for 2 weeks.
- **Dawn:** the assistant is called "Dawn". Existing users see a full-screen "Daybreak AI is now Dawn" page once (rework `DawnIntro.kt`, `DawnIntroStore.kt`): graphic of the answer screen, buttons "Try now" and "Later".
- **Update screen:** whenever an update is available (every launch and every check), show a full-screen "A new version is available. Download it?" page like `UpdateRequiredScreen`, with "Download now" and "Later" (Later closes it until the next launch or check). A version below the minimum stays forced.
- **Assistant limits:** less transparent. The app shows no budgets, cents, counts or limit numbers; only a plain "come back later" message that looks like any outage. Admin panel gets per-model, per-tier visibility: available, locked (shown but greyed out, tapping shows the plans) or hidden.

## Free / Plus / Pro (draft, developer confirms)

| | Free | Plus | Pro |
|---|---|---|---|
| Timetable, absences, messages, exams, homework, search, notifications | yes | yes | yes |
| Basic widget | yes | yes | yes |
| Dawn: photo and PDF attachments | yes | yes | yes |
| Dawn: questions per day, models | few, basic model | more, more models | most, all models |
| App name and icon, calendar export | no | yes | yes |
| Widget customisation, share timetable as picture | no | yes | yes |
| Dawn: reasoning picker, message drafts | no | no | yes |
| Haptics studio, accent palettes, AMOLED | no | no | yes |
| Sync of Daybreak-only data across devices (settings, lesson colours, notes, homework ticks) | no | yes | yes |

Nothing that is a free basic function today gets locked. Feature flags are one enum (`Entitlement`) checked in one place, on both apps.

## Server API contract (update-server, all JSON)

Header on every call: `X-Daybreak-Install` (random id). Account calls also carry `Authorization: Bearer <accountToken>`.

- `POST /v1/account` → `{accountId, restoreCode, token}` creates an account. Body may carry `{legacyInstallId, legacyUnlockCode}` for migration (server marks the account grandfathered if the old Ko-fi code is paid in `support-payments.json`).
- `POST /v1/account/restore` `{restoreCode}` → `{accountId, token}` signs a device in.
- `GET /v1/account` → `{accountId, plan: "free"|"plus"|"pro", planUntil, grandfathered, webuntis: [{id, label}], proTrial: {offered, until}}`.
- `POST /v1/account/webuntis` `{server, school, studentId}` binds one (max 3, 409 beyond) and `DELETE /v1/account/webuntis/:id` unbinds. Only a salted hash of `school + studentId` is stored plus a short label chosen by the user. Proof by WebUntis token verification is a later step (needs checking against a real school); until then it is a guard that warns when a binding is shared by another account.
- `POST /v1/account/trial/accept` starts the 3 months of Pro for grandfathered accounts.
- `POST /v1/billing/checkout` `{plan}` → `{url}` (Stripe Checkout, `client_reference_id` = accountId) and `POST /v1/billing/portal` → `{url}`. `POST /v1/stripe/webhook` (signature checked) updates the plan. Env: `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, price ids in admin settings.
- `GET /v1/sync` / `PUT /v1/sync` (Plus and up): one JSON document per account with a version for conflict handling.
- `GET /v1/assistant/models` returns, per model, `state: available|locked` for the caller's plan (hidden ones are left out). `POST /v1/assistant` rejects locked models.
- **As built (server package, tested):** `POST /v1/account` answers 201 with `{accountId, plan, planUntil, grandfathered, supporterExtras, webuntis, proTrial:{offered,accepted,running,until}, restoreCode, token, legacy}` (`legacy` is `none|granted|claimed|unpaid`). The restore code looks like `DA-XXXX-XXXX-XXXX` and is shown once, so the app must store it. `POST /v1/account/restore` answers the same view plus `token`, 404 `unknown_code`. `POST /v1/account/webuntis` takes `{server, school, studentId, label}`, where the apps send the school server, the token's `tenant_id` as `school` and its `user_id` as `studentId` (read locally from the claims of `getAuthToken`, see `TokenIdentity`; not the login name, not the e-mail, not the timetable element id), answers 201 `{binding, sharedWithOthers, account}` or 409 `limit` / `duplicate`. `POST /v1/account/trial/accept` answers 409 `not_offered` / `already_accepted`. `POST /v1/billing/checkout` `{plan}` answers `{url}` or 503 `payments_off`; `/v1/billing/portal` answers 409 `no_subscription` without one. `GET/PUT /v1/sync` answers 402 `plan_required` on Free; `PUT` takes `{baseVersion, data}` and answers 409 `{error:'conflict', version, data}` when stale, 413 when over 256 KB. Old 1.x routes (`/v1/support/status`, `/v1/kofi`) stay for apps that have not migrated.
- **Assistant as built:** `GET /v1/assistant/models`, `POST /v1/assistant` and `/title` take the same optional `Authorization: Bearer <accountToken>`. Without it the caller is Free (a 1.x app's paid unlock code counts as Plus). Budgets belong to the account, not the device. `models` lists every model with `state: "available"` or `state: "locked"` (a locked one also has `locked: true`) and leaves hidden ones out; `showEffort` is false below the plan chosen in the admin panel (default Pro); `defaultModel` is always one the plan may use. A request for a locked model silently gets the default, and the budget error stays the plain `limit` code.
- Web: `/subscribe` page (plans, buy, Ko-fi donate link), `/admin` gets Accounts (search, grant plan, extend, revoke, unbind WebUntis) and the assistant tier table.

## Work packages

1. **Server** (`update-server/`): accounts, bindings, plans, Stripe, admin, `/subscribe`, assistant tiers, hidden limits. Tests with `npm test`.
2. **Legal and docs**: rewrite `legal_*` strings (de and en) and the desktop texts, raise `LegalTerms.VERSION` and desktop `LEGAL_VERSION`, README and CLAUDE.md wording for closed source, mention accounts, sync, Stripe, WebUntis hash.
3. **Android app**: account and migration flow, Dawn full-screen intro, update full-screen page, remove Ko-fi, entitlements and locked-state UI, account screen with the 3 WebUntis slots, old-supporter 3-month popup and 2-week pinned banner, sync client.
4. **Desktop app**: the same after 3 is settled.
