# Cloudflare setup: from an empty account to a connected phone

This deploys **your own** single-owner, single-phone relay. You do not need the developer's Worker URL, device token, signing key or account. Local app blocking works without these steps. Commands run in a terminal on Windows/macOS/Linux; Node.js 24, Git and a Cloudflare account are required. Review Cloudflare's current plans and usage limits in your account before deployment.

## 1. Get the source and install relay tooling

```sh
git clone https://github.com/RafailAndreou/FocusRelay.git
cd FocusRelay/relay
npm ci
npx wrangler login
npx wrangler whoami
```

Wrangler opens your browser for Cloudflare sign-in. Select the account where you want to host the relay. If you have several accounts, set account_id in your local Wrangler configuration to that account's ID.

## 2. Make a private deployment configuration

Copy wrangler.jsonc to wrangler.local.jsonc (PowerShell: `Copy-Item wrangler.jsonc wrangler.local.jsonc`; macOS/Linux: `cp wrangler.jsonc wrangler.local.jsonc`). The local file is gitignored.

Keep main, compatibility_date, triggers and migrations_dir. Choose a Worker name such as focusrelay in the top-level name field. Keep the database **binding** named DB; the code requires it.

```sh
npx wrangler d1 create focusrelay --config wrangler.local.jsonc
```

Copy the returned database_id into the existing d1_databases entry in wrangler.local.jsonc and set database_name to focusrelay. Replace the all-zero example ID. If Wrangler offers to add a binding, use DB and make sure the config has only one DB entry. An ID is a configuration reference, not an authentication secret.

## 3. Create the database tables

```sh
npx wrangler d1 migrations apply focusrelay --remote --config wrangler.local.jsonc
```

Confirm application of both migrations (0001_initial and 0002_remote_planning). Do this before pairing a phone. For an existing installation, migrations apply only pending changes; do not manually run the ALTER TABLE twice or create a new database when updating.

## 4. Generate three different secrets

Run this locally **three times**, saving each different result in your password manager under the indicated role:

```sh
node -e "console.log(require('node:crypto').randomBytes(32).toString('base64url'))"
```

| Name | Where it belongs |
|---|---|
| OWNER_SECRET | Owner's OAuth consent page; also authenticates the private REST API |
| DEVICE_TOKEN | Android Settings → Remote control → Device token |
| AUTH_SECRET | Cloudflare only; signs OAuth grants, never entered on the phone or AI client |

Use a separate random value for each; each must have at least 32 characters. Do not paste values into source files, issues or screenshots. The commands above generate secrets on your machine; no sample credential in this guide is usable.

## 5. Upload secrets and deploy

Run each command and paste the corresponding value into Wrangler's interactive prompt:

```sh
npx wrangler secret put OWNER_SECRET --config wrangler.local.jsonc
npx wrangler secret put DEVICE_TOKEN --config wrangler.local.jsonc
npx wrangler secret put AUTH_SECRET --config wrangler.local.jsonc
npx wrangler deploy --config wrangler.local.jsonc
```

If Wrangler asks to create the Worker while uploading the first secret, allow it. Deploy the source afterward. Record the workers.dev URL printed by deploy, such as `https://focusrelay.YOUR_SUBDOMAIN.workers.dev`. If workers.dev is not configured, complete the subdomain setup in Cloudflare Workers & Pages and deploy again.

Open that URL with `/health` appended. Confirm a healthy response. A health response does not prove a phone is connected; it only checks the relay endpoint. Worker observability is off in the example. Never log authorization headers or credential values while troubleshooting.

## 6. Install and pair FocusRelay

1. Build/install the app using [BUILD.md](BUILD.md), or install a trusted maintainer-signed release when one is available.
2. Complete onboarding. Grant Usage Access and Display over other apps.
3. Open **Settings → Remote control**. Grant notifications and unrestricted battery use; allow **Alarms & reminders** for precise schedules when offered.
4. Enter your Worker base URL **without /mcp** and your DEVICE_TOKEN.
5. Open App access, select the apps remote clients may control, save and enable remote control.
6. Wait for Connected/last successful sync. Keep the foreground notification enabled. Avoid leaving another blocker installation active while testing.

The phone polls for updates, so delivery can take a sync cycle. Essential phone controls are excluded from remote selection. Disabling remote control and opting apps out remain local actions.

## 7. Connect an AI client

Use the client's custom remote MCP integration settings, if available for your account. Names/availability differ by client and subscription.

1. Add your Worker URL **with /mcp** (for example `https://focusrelay.YOUR_SUBDOMAIN.workers.dev/mcp`).
2. Choose OAuth with automatic client registration (DCR). Do not invent a static client ID/secret; this server registers the client.
3. On your own Worker's consent page, review the permissions and enter OWNER_SECRET. Verify the hostname before entering it. Never put OWNER_SECRET into the chat itself.
4. Return to the client, ask it to list permitted apps, then test a 30-second block on a nonessential app.
5. Check get_status for the acknowledged revision and fresh permission/service readiness, and confirm the actual phone behavior. Check Home, Recents, expiry and manual unblock before trying longer blocks.

The existing OAuth callback allowlist supports documented ChatGPT callbacks and Claude's exact `https://claude.ai/api/mcp/auth_callback`. Use automatic registration in Claude. Do not disable callback or PKCE validation if a different client fails; support for other callbacks requires an audited code change. This is a remote MCP server; it is not installed by merely pasting a URL into an ordinary chat.

## 8. Troubleshooting

| Symptom | Check |
|---|---|
| Relay setup incomplete | All three secrets exist, differ, and meet minimum length; deployment has DB binding |
| SQL missing-table/column error | Correct database ID and both migrations applied remotely |
| Phone unauthorized | DEVICE_TOKEN exactly matches the Worker secret; URL has no /mcp |
| OAuth denied/invalid redirect | Client uses a supported exact callback and automatic registration |
| Accepted but not applied | Phone connectivity, remote enabled, allowlist saved, permissions, battery settings and revision acknowledgment |
| Schedule transitions late | Exact-alarm permission and Android battery/Doze constraints; acknowledgement is not a timing guarantee |
| APK will not update | Signing certificate or package differs; do not uninstall before preserving the working setup |

## 9. Updating and removing your deployment

Pull new source, run npm ci, apply pending D1 migrations and redeploy with the same local config. Keep your database and secrets. Do not change credentials merely because the app's name changes. A deliberately rotated DEVICE_TOKEN must also be updated on the phone; rotating AUTH_SECRET requires reconnecting AI clients.

To stop using the service, disable remote control on the phone and disconnect AI integrations. The account owner can remove the Worker and database through Cloudflare after backing up anything needed. Deleting cloud resources is separate from uninstalling the Android app.

Official references, checked 2026-10-26: [D1 getting started](https://developers.cloudflare.com/d1/get-started/), [D1 migrations](https://developers.cloudflare.com/d1/reference/migrations/), [Worker secrets](https://developers.cloudflare.com/workers/configuration/secrets/), [Wrangler commands](https://developers.cloudflare.com/workers/wrangler/commands/).
