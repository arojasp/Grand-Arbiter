#!/usr/bin/env python3
"""
Build the card SEED shipped in the APK from the Scryfall bulk files (stdlib only, streaming).

Input : cards/source/oracle-cards.jsonl.gz, rulings.jsonl.gz, scryfall-meta.json
        (python tools/cards/fetch_scryfall.py)
Output: app/src/main/assets/cards/oracle-cards.jsonl.gz   Scryfall card objects, unused fields removed
        app/src/main/assets/cards/rulings.jsonl.gz        Scryfall ruling objects
        app/src/main/assets/cards/seed.json               {"oracleUpdatedAt", "rulingsUpdatedAt"}
        (The Android Gradle Plugin gunzips .gz assets when packaging: in the APK they are
        cards/oracle-cards.jsonl and cards/rulings.jsonl. Kept as .gz here to keep git small.)

The seed keeps SCRYFALL'S OWN FORMAT on purpose: the app reads the seed and the weekly download
from Scryfall with the same parser, filter and mapper (data/cards/scryfall/). The judge-app
filter (no art series, no digital-only cards, paper formats only) lives ONLY in Kotlin, so the
seed and an update can never disagree. This script only strips fields to keep the APK small.

Usage: python tools/cards/build_cards.py
"""
from __future__ import annotations

import gzip
import io
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "cards" / "source"
OUT = ROOT / "app" / "src" / "main" / "assets" / "cards"

# Must cover every field the Kotlin model (ScryfallCard / ScryfallFace) reads.
CARD_FIELDS = ("oracle_id", "name", "layout", "mana_cost", "cmc", "type_line", "oracle_text",
               "power", "toughness", "loyalty", "defense", "hand_modifier", "life_modifier",
               "colors", "color_identity", "keywords", "legalities", "game_changer", "reserved",
               "games", "set", "set_type", "card_faces", "image_uris", "artist")
FACE_FIELDS = ("name", "mana_cost", "type_line", "oracle_text", "power", "toughness",
               "loyalty", "defense", "colors", "image_uris", "artist")
RULING_FIELDS = ("oracle_id", "source", "published_at", "comment")
LEGAL_FORMATS = ("standard", "pioneer", "modern", "legacy", "vintage", "pauper", "commander",
                 "oathbreaker", "duel", "premodern", "oldschool", "predh", "paupercommander")


def jsonl(path: Path):
    with gzip.open(path, "rt", encoding="utf-8") as f:
        for line in f:
            line = line.strip().rstrip(",")
            if line and line not in ("[", "]"):
                yield json.loads(line)


# normal = whole card; art_crop = the art only (shown in the app with the artist's name, as
# Scryfall's guidelines require for art crops).
IMAGE_SIZES = ("normal", "art_crop")


def images(d: dict) -> dict | None:
    uris = d.get("image_uris") or {}
    kept = {k: uris[k] for k in IMAGE_SIZES if uris.get(k)}
    return kept or None


def strip(d: dict, fields) -> dict:
    out = {}
    for k in fields:
        v = d.get(k)
        if k == "image_uris":
            v = images(d)
        elif k == "card_faces" and v:
            v = [strip(f, FACE_FIELDS) for f in v]
        elif k == "legalities" and v:
            v = {f: s for f, s in v.items() if f in LEGAL_FORMATS}
        if v not in (None, "", [], {}, False):
            out[k] = v
    return out


def write(name: str, rows) -> tuple[int, int]:
    path = OUT / name
    n = 0
    raw = 0
    # mtime=0: same data -> byte-identical file, so a rebuild doesn't churn git with 6 MB blobs.
    with gzip.GzipFile(path, "wb", compresslevel=9, mtime=0) as raw_file, \
            io.TextIOWrapper(raw_file, encoding="utf-8", newline="\n") as f:
        for r in rows:
            line = json.dumps(r, ensure_ascii=False, separators=(",", ":")) + "\n"
            f.write(line)
            raw += len(line.encode())
            n += 1
    print(f"  {name:22} {n:6} rows  {raw / 1e6:5.1f} MB raw  {path.stat().st_size / 1e6:5.1f} MB gz")
    return n, path.stat().st_size


def main() -> int:
    meta = json.loads((SRC / "scryfall-meta.json").read_text(encoding="utf-8"))
    OUT.mkdir(parents=True, exist_ok=True)
    print(f"Writing seed to {OUT.relative_to(ROOT)}")
    write("oracle-cards.jsonl.gz", (strip(c, CARD_FIELDS) for c in jsonl(SRC / "oracle-cards.jsonl.gz")
                                    if c.get("oracle_id") or c.get("card_faces")))
    write("rulings.jsonl.gz", (strip(r, RULING_FIELDS) for r in jsonl(SRC / "rulings.jsonl.gz")))
    seed = {"oracleUpdatedAt": meta["oracle_cards"]["updated_at"],
            "rulingsUpdatedAt": meta["rulings"]["updated_at"]}
    (OUT / "seed.json").write_text(json.dumps(seed, indent=2) + "\n", encoding="utf-8")
    print(f"  seed.json              {seed}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
