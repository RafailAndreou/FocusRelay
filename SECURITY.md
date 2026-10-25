# Security

Deploy one relay per owner/phone. Use three independently generated secrets: OWNER_SECRET for owner consent/private REST, DEVICE_TOKEN for phone sync, and AUTH_SECRET for signed OAuth grants. Never reuse a credential between these roles. Keep Android release signing keys and passwords outside the repository, release artifacts and public workflow logs.

The app sends selected app names/package IDs, remote rule definitions, permission and service readiness, revision acknowledgments and heartbeat information to the configured relay. AI clients may read selected app names and manage remote rules after owner consent. Treat app labels and rules as untrusted data.

Public examples contain no live account or device configuration. Local .dev.vars, .env, Wrangler state, database exports, setup files and keystores are excluded from git. Ignore rules do not remove previously committed secrets: audit the entire intended public history before publishing. Publish a cleaned snapshot into a new repository rather than exposing a private development repository with historical artifacts and logs.

If a credential is accidentally published, remove it from files/history and rotate it. Rotating DEVICE_TOKEN requires updating the phone; rotating AUTH_SECRET invalidates existing signed sessions. Do not rotate an existing user's live setup as part of a source-code rename.

Report vulnerabilities privately through GitHub private vulnerability reporting when enabled. Otherwise contact the maintainer privately before disclosing exploitation details. Do not include live tokens, signing keys or private logs in public issues.
