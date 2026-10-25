<div align="center">

# FocusRelay

### Make room for what matters.

An open-source Android app for blocking distractions, building healthier digital habits, and managing your focus—with optional AI remote control.

[![Platform: Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](docs/BUILD.md)
[![License: GPL v2](https://img.shields.io/badge/License-GPL_v2-blue.svg)](LICENSE)
[![Self-hosted remote control](https://img.shields.io/badge/Remote_control-Self--hosted-8b9cf7)](docs/CLOUDFLARE_SETUP.md)

[Get started](#get-started) · [Features](#features) · [Connect your AI assistant](docs/CLOUDFLARE_SETUP.md) · [Privacy](PRIVACY.md)

</div>

---

**A quick pause, a study routine, or a daily limit—set up the kind of focus you need.**

FocusRelay brings your blocks and schedules into one place. Start a Quick Block, plan recurring distraction-free hours, or give selected apps a daily allowance. See today's plan on a timeline and manage each rule independently.

Want to control your blocks through an AI assistant? Connect your own relay and let an authorized MCP client manage the apps you choose. Local blocking works without an account or Cloudflare setup.

## Features

### Quick Block

Choose your distracting apps, set a timer, and take a break. Tap the card to change your app selection, or use its separate timer control to adjust the duration.

### Schedules that fit your day

Create named rules for studying, work, evenings, or any other routine. Pick the apps, hours and repeat days, then let the schedule handle the rest. Multiple schedules can run together with their own settings.

Pausing one rule leaves other applicable blocks in place.

### Daily app allowances

Decide how much time you want to spend on selected apps each day. Give each app its own allowance, or let a group share one budget—for example, 30 minutes across your social apps.

### A clear view of your focus

Four tabs keep the essentials within reach:

| Blocking | Rules | Activity | Settings |
|---|---|---|---|
| Quick Block and today's timeline | Manage schedules and allowances | Review usage and recent blocking activity | Permissions, appearance and remote control |

App icons help you recognize your selections at a glance. Compact icon groups show a **+N** count when more apps are selected.

### Optional AI remote control

Manage opted-in apps through a compatible AI assistant, including supported ChatGPT and Claude MCP connections. You can ask it to:

- List the apps you've allowed it to control.
- Block an app until a chosen time, or remove an immediate remote block.
- Create future or recurring blocks and reusable focus presets.
- Pause or resume remote rules and check the phone's connection status.

Each relay is self-hosted on **Cloudflare Workers + D1** and serves one owner and one phone. You choose which apps are available to remote clients; essential phone controls are excluded. Remote commands require a connection to reach the phone. Once received, rules are stored and enforced locally, subject to Android's background restrictions.

[Follow the step-by-step connection guide →](docs/CLOUDFLARE_SETUP.md)

### More tools for digital wellbeing

FocusRelay also retains Mindful's focus sessions, Bedtime, detailed usage statistics, app limits and notification scheduling, accessible from Settings.

## Get started

FocusRelay is currently a development preview. **A stable, maintainer-signed public APK release is not available yet.**

1. **Build or try a preview:** follow the [Android build guide](docs/BUILD.md). GitHub Actions can generate a test APK without a local Android development environment.
2. **Grant the required permissions:** complete onboarding and enable Usage Access and Display over other apps. Review notification, battery and alarm settings where prompted.
3. **Create your first block:** choose a nonessential app and try a short Quick Block. Check that you can use Home and Recents and that the block ends as expected.
4. **Connect remote control, if you want it:** follow the [Cloudflare and MCP setup guide](docs/CLOUDFLARE_SETUP.md). You can use local features without this step.

> GitHub Actions previews use a temporary debug signing certificate. They are for testing; installing one over a separately signed copy may fail. See the build guide before replacing an existing installation.

## Privacy and control

Your local usage and activity data stays on your phone. Enabling remote control shares selected app names/package IDs, remote rules and connection diagnostics with **your configured relay** and makes relevant information available to authorized AI clients. No device token or shared relay credentials are bundled with the public source.

You can choose remote app access and disable remote control from the phone. Read the [privacy policy](PRIVACY.md) and [security notes](SECURITY.md) for details.

Android and manufacturer battery settings can delay background work. Force-stopping the app stops background enforcement until it is reopened. FocusRelay is a personal productivity tool; it does not promise tamper-proof blocking.

## Feedback and contributions

Found a bug or have an idea? [Open an issue](https://github.com/RafailAndreou/FocusRelay/issues). For blocking problems, include your Android version, phone model, steps to reproduce, and whether the rule was local or remote. Keep tokens, signing keys and private diagnostics out of public reports.

To contribute, start with the [build guide](docs/BUILD.md). The [Cloudflare guide](docs/CLOUDFLARE_SETUP.md) covers the optional relay, and the [API documentation](docs/remote/API.md) describes remote commands. Follow [SECURITY.md](SECURITY.md) for vulnerability reporting.

## Built on Mindful

FocusRelay is an independent modified edition of **[Mindful](https://github.com/akaMrNagar/Mindful)**, created by **Pawan Nagar and contributors**. Thank you to the original developers and localization contributors whose work provides its foundation.

The FocusRelay changes are maintained by **Rafail Andreou** and add the redesigned blocking workspace, independent rules, app-icon summaries, remote control and related reliability fixes. This project is not an official Mindful release or endorsed by its maintainers. You can [support the original project](https://github.com/akaMrNagar/Mindful#donate) separately.

FocusRelay is distributed under **GNU GPL version 2**. Original notices are retained. See [LICENSE](LICENSE), [attribution](NOTICE.md), [third-party notices](THIRD_PARTY_NOTICES.md) and the [modification record](MODIFICATIONS.md).

For developers: this repository applies a source overlay to a pinned Mindful commit. The build guide explains reconstruction, and the workflow packages complete corresponding source to distribute alongside the matching APK.
