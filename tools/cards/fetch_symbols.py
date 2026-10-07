#!/usr/bin/env python3
"""
Download Scryfall's card symbols (mana, tap, energy…) as SVG (stdlib only, needs network).

  cards/source/symbols/symbology.json   Scryfall's /symbology list (symbol, svg_uri, colors…)
  cards/source/symbols/svg/<name>.svg   one SVG per symbol, named by symbol_file_name()

Then: python tools/cards/build_symbols.py   (SVG -> Android vector drawables, offline in the APK)

The symbols are Wizards of the Coast's, used under the Fan Content Policy (free app + notice).
Usage: python tools/cards/fetch_symbols.py
"""
from __future__ import annotations

import json
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "cards" / "source" / "symbols"
UA = "GrandArbiter/1.0 (judge reference app; https://github.com/arojasp/Grand-Arbiter)"


def symbol_file_name(symbol: str) -> str:
    """'{W/U}' -> 'wu', '{2/W}' -> '2w', '{½}' -> 'half', '{∞}' -> 'infinity'. Must match build_symbols.py."""
    inner = symbol.strip("{}")
    special = {"½": "half", "∞": "infinity", "?": "question"}
    if inner in special:
        return special[inner]
    name = "".join(special.get(ch, ch) for ch in inner.lower() if ch not in "/")
    if not name.replace("_", "").isalnum() or not name.isascii():
        name = "u" + "_".join(f"{ord(ch):x}" for ch in inner)
    return name


def get(url: str, accept: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": accept})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def main() -> int:
    (OUT / "svg").mkdir(parents=True, exist_ok=True)
    data = json.loads(get("https://api.scryfall.com/symbology", "application/json"))
    symbols = data["data"]
    (OUT / "symbology.json").write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(symbols)} symbols")
    seen: dict[str, str] = {}
    for s in symbols:
        uri = s.get("svg_uri")
        if not uri:
            continue
        name = symbol_file_name(s["symbol"])
        if name in seen:
            print(f"  !! name clash: {s['symbol']} and {seen[name]} -> {name}", file=sys.stderr)
            return 1
        seen[name] = s["symbol"]
        (OUT / "svg" / f"{name}.svg").write_bytes(get(uri, "image/svg+xml,*/*"))
        time.sleep(0.1)   # Scryfall: 10 requests/second max
    print(f"Saved {len(seen)} SVGs to {(OUT / 'svg').relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
