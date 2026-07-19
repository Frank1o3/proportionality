#!/usr/bin/env python3

import json
import sys
from pathlib import Path


LANG_DIR = (
    Path(__file__).resolve().parent.parent
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "proportionality"
    / "lang"
)

REFERENCE_LANG = "en_us.json"


def load_json(path: Path) -> dict:
    try:
        with path.open("r", encoding="utf-8") as file:
            data = json.load(file)
    except json.JSONDecodeError as exc:
        print(f"ERROR: Invalid JSON in {path}")
        print(f"       {exc}")
        raise SystemExit(1)

    if not isinstance(data, dict):
        print(f"ERROR: {path} must contain a JSON object.")
        raise SystemExit(1)

    return data


def main() -> int:
    if not LANG_DIR.exists():
        print(f"ERROR: Language directory does not exist:")
        print(f"       {LANG_DIR}")
        return 1

    reference_path = LANG_DIR / REFERENCE_LANG

    if not reference_path.exists():
        print(f"ERROR: Reference language file not found:")
        print(f"       {reference_path}")
        return 1

    reference = load_json(reference_path)
    reference_keys = set(reference)

    print(f"Reference language: {REFERENCE_LANG}")
    print(f"Reference keys: {len(reference_keys)}")
    print()

    errors = 0

    language_files = sorted(LANG_DIR.glob("*.json"))

    for language_file in language_files:
        if language_file.name == REFERENCE_LANG:
            continue

        language = load_json(language_file)
        language_keys = set(language)

        missing = reference_keys - language_keys
        extra = language_keys - reference_keys

        empty_values = [
            key
            for key, value in language.items()
            if isinstance(value, str) and not value.strip()
        ]

        if missing or extra or empty_values:
            errors += 1

            print(f"ERROR: {language_file.name}")

            if missing:
                print("  Missing keys:")
                for key in sorted(missing):
                    print(f"    - {key}")

            if extra:
                print("  Unexpected keys:")
                for key in sorted(extra):
                    print(f"    - {key}")

            if empty_values:
                print("  Empty translations:")
                for key in sorted(empty_values):
                    print(f"    - {key}")

            print()
        else:
            print(
                f"OK: {language_file.name} "
                f"({len(language_keys)} keys)"
            )

    print()

    if errors:
        print(
            f"Validation failed: {errors} language file(s) "
            f"have localization issues."
        )
        return 1

    print(
        f"Localization validation passed: "
        f"{len(language_files)} language files checked."
    )

    return 0


if __name__ == "__main__":
    sys.exit(main())