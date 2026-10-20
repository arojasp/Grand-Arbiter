"""Play's "What's new" files: every expected locale present, non-empty, at most 500 characters."""
import sys
from pathlib import Path

LIMIT = 500
LOCALES = ("en-US", "es-ES")
root = Path(__file__).resolve().parents[2] / "distribution" / "whatsnew"
errors = []
for locale in LOCALES:
    f = root / f"whatsnew-{locale}"
    if not f.exists():
        errors.append(f"{f.name}: missing")
        continue
    text = f.read_text(encoding="utf-8").strip()
    print(f"{f.name}: {len(text)} characters")
    if not text:
        errors.append(f"{f.name}: empty")
    elif len(text) > LIMIT:
        errors.append(f"{f.name}: {len(text)} characters, Play allows {LIMIT}")
for e in errors:
    print(f"::error::{e}")
sys.exit(1 if errors else 0)
