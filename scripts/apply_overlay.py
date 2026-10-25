#!/usr/bin/env python3
"""Copy explicitly listed source changes into a pinned upstream checkout."""
from pathlib import Path
import json
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
EXPECTED = "b6eb68d500463c54e1127b6cc7ad540f02ae0c8e"


def main():
    target = Path(sys.argv[1]).resolve()
    actual = subprocess.check_output(["git", "-C", str(target), "rev-parse", "HEAD"], text=True).strip()
    if actual != EXPECTED:
        raise SystemExit(f"Refusing to patch unexpected upstream commit: {actual}")
    for name in json.loads((ROOT / "scripts/overlay-files.json").read_text()):
        relative = Path(name)
        if relative.is_absolute() or ".." in relative.parts:
            raise SystemExit("Invalid overlay path")
        source = ROOT / relative
        if not source.is_file():
            raise SystemExit(f"Missing source file: {name}")
        destination = target / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)
    print("Applied FocusRelay overlay to the verified upstream commit")


if __name__ == "__main__":
    main()

