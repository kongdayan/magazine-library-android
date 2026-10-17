#!/usr/bin/env python3
"""Print the CHANGELOG section for a release tag, plus installation notes.

Usage: python3 scripts/release-notes.py v1.4.0 > RELEASE_NOTES.md
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: release-notes.py <tag>", file=sys.stderr)
        return 2
    version = sys.argv[1].lstrip("vV")
    changelog = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    match = re.search(
        rf"^##\s+{re.escape(version)}\b.*?$(.*?)(?=^##\s|\Z)",
        changelog,
        flags=re.MULTILINE | re.DOTALL,
    )
    if match:
        body = match.group(1).strip()
    else:
        body = "See CHANGELOG.md for the full history."
    print(body)
    print()
    print("### Install")
    print()
    print("Download `MagazineLibrary-universal.apk` below and open it on an Android 10+ device.")
    print("The app has no native libraries, so the universal APK runs on every supported "
          "device; ABI-named APKs are provided for tooling that expects them.")
    print()
    print("The APK is signed with the public community key, so it upgrades previous "
          "Magazine Library sideload builds. Verify downloads with `SHA256SUMS`.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
