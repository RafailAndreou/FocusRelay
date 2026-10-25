#!/usr/bin/env python3
"""Package full reconstructed application source plus relay and build notices.
Copyright 2026 Rafail Andreou. SPDX-License-Identifier: GPL-2.0-only
"""
from pathlib import Path
import json
import subprocess
import sys
import tarfile

ROOT = Path(__file__).resolve().parent.parent
EXPECTED = 'b6eb68d500463c54e1127b6cc7ad540f02ae0c8e'
EXCLUDED = {'.git', '.dart_tool', '.gradle', 'build', 'node_modules', '.wrangler', '__pycache__'}
PRIVATE_NAMES = {'local.properties', 'key.properties', '.dev.vars', '.env', 'wrangler.local.jsonc', 'secrets.json', 'credentials.json'}
PRIVATE_SUFFIXES = {'.jks', '.keystore', '.p12', '.pfx', '.pem', '.key', '.apk', '.aab', '.zip', '.sqlite', '.sqlite3', '.db'}

def permitted(path):
    return not (set(path.parts) & EXCLUDED or path.name in PRIVATE_NAMES or path.name.startswith(('.dev.vars.', '.env.')) or path.suffix.lower() in PRIVATE_SUFFIXES or 'Private_Setup' in path.name or 'Private_Signing' in path.name)

def main():
    source = Path(sys.argv[1]).resolve()
    actual = subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
    if actual != EXPECTED:
        raise SystemExit('Unexpected upstream version')
    manifest = json.loads((ROOT / 'scripts/overlay-files.json').read_text())
    for name in manifest:
        if (ROOT / name).read_bytes() != (source / name).read_bytes():
            raise SystemExit(f'Overlay not applied: {name}')
    output = ROOT / 'build/focusrelay-complete-source.tar.gz'
    output.parent.mkdir(exist_ok=True)
    with tarfile.open(output, 'w:gz') as archive:
        for path in sorted(source.rglob('*')):
            rel = path.relative_to(source)
            if path.is_file() and not path.is_symlink() and permitted(rel):
                archive.add(path, arcname=Path('FocusRelay/application') / rel, recursive=False)
        # Include overlay as well: its manifest/scripts reproduce the build.
        tracked = subprocess.check_output(['git', '-C', str(ROOT), 'ls-files', '-z']).decode().split('\0')
        for name in sorted(filter(None, tracked)):
            rel = Path(name)
            path = ROOT / rel
            if path.is_file() and not path.is_symlink() and permitted(rel) and rel.parts[0] != 'upstream':
                archive.add(path, arcname=Path('FocusRelay/overlay') / rel, recursive=False)
    print(output)

if __name__ == '__main__':
    main()
