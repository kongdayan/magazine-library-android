#!/usr/bin/env python3
"""Validate Android string resources against the English source.

Checks every locale declared in res/xml/locales_config.xml:
  * the key set matches values/strings.xml exactly;
  * format placeholders match per key (order may change, count may not);
  * strings marked translatable="false" are byte-identical;
  * no locale leaves a translated string empty.

Run from the repository root: python3 scripts/check-translations.py
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
DEFAULT_FILE = RES / "values" / "strings.xml"
LOCALES_CONFIG = RES / "xml" / "locales_config.xml"
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[a-zA-Z%]")
LOCALE_DIRS = {
    "en": "values",
    "zh-CN": "values-zh-rCN",
    "ja": "values-ja",
    "ko": "values-ko",
    "ru": "values-ru",
}


def parse(path: Path) -> dict[str, tuple[str, bool]]:
    tree = ET.parse(path)
    values: dict[str, tuple[str, bool]] = {}
    for node in tree.getroot().findall("string"):
        name = node.get("name")
        if not name:
            continue
        if name in values:
            raise SystemExit(f"{path}: duplicate string name {name}")
        translatable = node.get("translatable", "true") != "false"
        values[name] = ("".join(node.itertext()), translatable)
    return values


def placeholders(text: str) -> list[str]:
    return sorted(PLACEHOLDER.findall(text))


def main() -> int:
    if not DEFAULT_FILE.exists():
        print(f"missing {DEFAULT_FILE}", file=sys.stderr)
        return 1
    default = parse(DEFAULT_FILE)
    configured = []
    for node in ET.parse(LOCALES_CONFIG).getroot().findall("locale"):
        tag = next((value for key, value in node.attrib.items()
                    if key == "name" or key.endswith("}name")), None)
        if tag:
            configured.append(tag)
    missing_dirs = [tag for tag in configured if tag not in LOCALE_DIRS]
    if missing_dirs:
        print(f"locales_config has unmapped locales: {missing_dirs}", file=sys.stderr)
        return 1

    errors: list[str] = []
    for tag in configured:
        if tag == "en":
            continue
        path = RES / LOCALE_DIRS[tag] / "strings.xml"
        if not path.exists():
            errors.append(f"{tag}: missing {path.relative_to(ROOT)}")
            continue
        localized = parse(path)
        required = {key for key, (_, translatable) in default.items() if translatable}
        missing = sorted(required - set(localized))
        extra = sorted(set(localized) - set(default))
        for key in missing:
            errors.append(f"{tag}: missing key {key}")
        for key in extra:
            errors.append(f"{tag}: unknown key {key}")
        for key, (_, translatable) in default.items():
            if not translatable and key in localized:
                errors.append(f"{tag}: {key} is not translatable and must be omitted")
        for key, (source_text, translatable) in default.items():
            if key not in localized:
                continue
            target_text, _ = localized[key]
            if not translatable and target_text != source_text:
                errors.append(f"{tag}: {key} must not be translated")
            if not target_text.strip():
                errors.append(f"{tag}: {key} is empty")
            expected = placeholders(source_text)
            actual = placeholders(target_text)
            if expected != actual:
                errors.append(
                    f"{tag}: {key} placeholders {actual} do not match {expected}"
                )
        print(f"{tag}: {len(localized)} strings OK")

    if errors:
        print("\nTranslation check failed:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    print(f"all {len(configured)} locales match {len(default)} English strings")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
