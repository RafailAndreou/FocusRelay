# FocusRelay

An Android app blocker with independent schedules, daily usage allowances, Quick Block and optional AI remote control through a self-hosted Cloudflare relay.

FocusRelay is a modified edition of [Mindful](https://github.com/akaMrNagar/Mindful) by Pawan Nagar and contributors. It is distributed under **GNU GPL version 2**. Original copyright notices remain. FocusRelay changes are by Rafail Andreou; this project is independent of the upstream maintainers.

## What it does

- Quick Block with separate app selection and timer controls.
- Multiple overlapping schedules and shared or per-app daily allowances.
- Blocking, Rules, Activity and Settings tabs; real app icons and compact overflow counts.
- Optional OAuth MCP connection for AI clients to control explicitly allowed apps.
- Device-local enforcement of received rules, with acknowledgment and permission diagnostics.

Remote control is optional. Local features work without a Cloudflare account. Each relay serves **one owner and one phone**; deploy your own instance. No shared hosted service or credentials are included.

## Start here

1. [Build and install the Android app](docs/BUILD.md).
2. [Deploy Cloudflare and connect your phone and AI client](docs/CLOUDFLARE_SETUP.md).
3. Read [privacy](PRIVACY.md), [security](SECURITY.md) and [attribution/licenses](NOTICE.md).

Android permissions and manufacturer battery settings affect blocking. A force-stop stops background enforcement until the app is reopened. This is a personal productivity tool, without a tamper-proof enforcement guarantee. First test a short block and check Home, Recents and expiry.

## Source and licensing

This repository supplies the changed files and build scripts as an overlay on upstream commit `b6eb68d500463c54e1127b6cc7ad540f02ae0c8e`. The build script checks that exact commit before copying files. [Build instructions](docs/BUILD.md) explain how to reconstruct the full source. Cloud builds also produce a complete application source archive; distribute that archive beside any APK from the same build.

See [LICENSE](LICENSE), [NOTICE.md](NOTICE.md), [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [modification record](MODIFICATIONS.md). You may use, modify and redistribute under GPL v2, including commercially, subject to its terms. No additional restrictions are imposed.

The current development version is **1.6.0**. GitHub Actions produces a debug preview for testing, not a stable release-signed download. A separately maintained release key is needed for installable long-term updates. Never commit that key.

Historical implementation notes live under [docs/remote](docs/remote/README.md); the new setup guide above is authoritative for new deployments.
