#!/usr/bin/env python3
"""Check tracked publication files without printing suspected secret contents.
Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
Not a substitute for a full-history credential scanner and artifact review.
"""
from pathlib import Path
import re
import subprocess
import sys
ROOT = Path(__file__).resolve().parent.parent
patterns = [
    re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    re.compile(r'\b(?:ghp|gho|ghu|ghs|github_pat)_[A-Za-z0-9_]{20,}\b'),
    re.compile(r'\bAKIA[A-Z0-9]{16}\b'),
    re.compile(r'(?i)(?:OWNER_SECRET|DEVICE_TOKEN|AUTH_SECRET)\s*[:=]\s*[\x22\x27]([A-Za-z0-9_+/=-]{32,})[\x22\x27]'),
]

def main():
    paths = subprocess.check_output(['git', '-C', str(ROOT), 'ls-files', '-z']).decode().split('\0')
    failures = []
    for name in filter(None, paths):
        p = ROOT / name
        if not p.is_file():
            continue
        if p.suffix.lower() in {'.jks', '.keystore', '.p12', '.pfx', '.pem', '.key', '.apk', '.aab', '.db', '.sqlite'} or p.name in {'.env', '.dev.vars', 'key.properties', 'wrangler.local.jsonc'}:
            failures.append((name, 'private/binary artifact'))
        s = p.read_text(errors='replace')
        if any(pattern.search(s) for pattern in patterns):
            failures.append((name, 'potential credential'))
        if re.search(r'https://[^/\s]*\.workers\.dev', s) and re.search(r'rafailandreou123', s):
            failures.append((name, 'personal deployment'))
    for name, reason in failures:
        print(f'{name}: {reason}')
    if failures:
        raise SystemExit(1)
    print('Tracked-file publication checks passed; no matching private artifacts or credentials.')

if __name__ == '__main__':
    main()
