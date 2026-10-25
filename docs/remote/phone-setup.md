> Historical development notes. For a new installation, follow [Cloudflare setup](../CLOUDFLARE_SETUP.md). Personal deployment instructions below are not setup requirements.

# Phone-only setup

## Cloudflare setup is complete

Deployed on 2026-10-20. No additional dashboard setup or paid upgrade is needed.

- Base URL: https://focusrelay.YOUR_SUBDOMAIN.workers.dev
- Health check: https://focusrelay.YOUR_SUBDOMAIN.workers.dev/health
- MCP URL: https://focusrelay.YOUR_SUBDOMAIN.workers.dev/mcp
- Worker and D1 database: `mindful-remote`.
- D1 ID: `YOUR_D1_DATABASE_ID`.
- Three distinct encrypted credentials are configured; values are supplied only
  in the owner's private connection file. No credentials are embedded in the APK.
- Expired authorization and rate-limit records are cleaned daily.

For future deployments, use the existing database and preserve the configured
secrets. Do not create another database, re-run initial account setup, or rotate
credentials unless that is intentional. The Wrangler database binding is now set.

## Update from Preview 2/3 to 1.3.0

Install the supplied signed `Mindful_Remote_1_3_0.apk` over the current app without
uninstalling. The final APK uses the same personal certificate/package and version
code 130. Existing URL/token, opt-in selection and Android permissions are preserved
by an in-place update; open Mindful after installation.

Open **ChatGPT remote control → App access → Select all apps → Save app access**
if you want to expand opt-in. Essential phone controls remain excluded. Newly
installed apps require **Refresh apps**, selection and save.

Tap **Allow precise scheduled blocks** and grant Android's **Alarms & reminders**
access for prompt scheduled transitions. A received future rule should show local
scheduler `exact`; `inexact` means Android can delay it. The full
[Samsung checklist](SAMSUNG_TESTS.md) covers the required real-device tests.

No new URL/token or Cloudflare account is needed. The D1 migration and Worker v0.2.0 deployment were completed on 2026-10-20;
no Cloudflare dashboard work is required for this update.
Refresh ChatGPT's MCP tool discovery if it still exposes only the old four tools;
a new login is only needed if the connection itself asks for authorization.

## Install and pair the phone

The preview APK has been built and supplied. Your private setup file contains
the relay URL and device token; keep its contents out of chat and GitHub.

1. If you installed the first preview, uninstall **Mindful Remote Preview** once
   before installing Preview 2: the first build used a temporary debug key.
   Download and install `Mindful_Remote_Preview_2.apk`. If Android asks, temporarily allow your browser
   to install unknown apps. This preview installs separately from official
   Mindful; it does not import its data.
2. Complete Mindful's onboarding. Open Settings → General → **ChatGPT remote
   control**.
3. Grant Usage Access, Display over other apps, notifications and unrestricted
   battery use using the buttons on that screen. Keep the connection notification
   enabled.
4. Paste the Relay URL and Device token from `Mindful_Remote_Private_Setup.txt`.
   The phone URL has no `/mcp` suffix.
5. Select the apps ChatGPT may control and enable remote control.
6. Wait for Connected. The relay has not received a block until an actual command
   is sent; use a short two-minute block for the first test.

## Connect ChatGPT

First verify which custom integration controls your account exposes. The
developer cannot guarantee account-specific availability from source code alone.
In the available custom MCP/app/plugin connection screen:

1. Add your Worker URL ending with `/mcp`.
2. Choose OAuth if prompted. Use the server's discovery/DCR configuration.
3. Review the permissions, then authorize on your own Worker page using
   OWNER_SECRET. Only enter that secret when the browser address is your Worker.
4. Return to ChatGPT and invoke the Mindful connection. Ask it to list apps, then
   block one selected app for two minutes.
5. Check status until the phone acknowledges the returned revision before saying
   the block is active.

The OAuth implementation supports ChatGPT's documented `chatgpt.com` connector
callbacks. If the account shows a different callback, update its exact allowlist
after checking official documentation; do not disable callback validation.

If native custom MCP connection is unavailable on the account, the REST API can
also be described as a Custom GPT Action using bearer API-key authentication.
That fallback must be verified against the account before use; it is not an
automatic substitute and is not yet configured in this preview.

## Connect Claude

Use the same MCP URL with **Sign in now** and **Register automatically**.
No manually supplied OAuth client ID or client secret is needed. Published
identity (CIMD) is not implemented; select automatic registration (DCR).
Authorize on the Worker using the existing owner secret, then return to Claude.
The consent page identifies Claude. Both clients control the same opted-in phone
and shared rules; neither connection requires an APK update or new credentials.

The exact Claude callback is `https://claude.ai/api/mcp/auth_callback`, as documented
by [Anthropic](https://support.anthropic.com/en/articles/11503834-building-custom-integrations-via-remote-mcp-servers).
Other Claude paths, query strings, fragments, credentials and lookalike domains
are rejected. Existing ChatGPT callbacks remain supported.

## Foreground/overlay update 1.3.1

Install `Mindful_Remote_1_3_1.apk` over the current app without uninstalling.
Version code 131 retains the same package/signing identity and saved pairing.
This updates Android tracking/overlay behavior only; the relay is unchanged.
After installation, open Mindful once and test local blocking for Reddit and
Coca-Cola, then remote blocking. A block must follow the target app promptly
after delivery, never appear over Mindful itself, and clear on expiry. Repeat
with accessibility enabled and disabled, and across lock/unlock. Remote receipt
can still take a sync cycle; status alone cannot prove a visible blocking screen.

## Overlay cleanup update 1.3.2

Install `Mindful_Remote_1_3_2.apk` over the supplied 1.3.1 APK. It uses the same
package and personal signing certificate, with higher version code 132. If an
installation conflict occurs, retain the installed app and capture the error
rather than clearing data.

Do not rely on readiness reports as proof that a blocking window was removed.
For a controlled short test, confirm Home/Recents and the notification shade
remain available, then unblock and confirm the overlay disappears without a
restart. Test local and remote blocks separately. If navigation is obstructed,
force-stop Mindful; do not run longer blocks until the result is understood.


### 1.4.0 design preview

This preview is a separate installation (`com.mindful.android.remote.design`, label
Mindful Design Preview). The previous personal signing key was unavailable in the
current workspace, so this build cannot update 1.3.2 in place. Keep the working
installation. Grant Usage Access and Display over other apps to the design preview
before testing local rules. Exact alarms and unrestricted battery access help
scheduled boundaries. Avoid enabling overlapping rules in both installations
while comparing them. Remote pairing is optional; the existing working app can
remain the paired remote client during local UI tests.

The Blocking tab contains Quick Block and today’s local timeline. Add Rule opens
Schedule/Usage Limit, followed by Timing → Apps → Review. Enable multiple rules
independently. Usage budgets count all selected apps together by default; turn off
Share the allowance to give each app its own budget. Earlier usage today counts.
Schedules retain their displayed timezone; daily usage budgets follow the phone’s
local day. Stopping one rule leaves every other applicable restriction intact.
Settings links to remote control and the original dashboard/settings.

Test a short Quick Block first, Home/Recents, stopping the block, two overlapping
rules on the same app, independent pause/delete, scheduled start/end, midnight
usage reset and lock/unlock. Automated checks do not substitute for Samsung
overlay, Doze or reboot tests. Force-stop disables Android background work until
you reopen the app. Local rule storage is separate from the original database
export/import. Activity retains the most recent 200 visible blocking events.

