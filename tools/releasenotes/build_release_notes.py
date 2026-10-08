#!/usr/bin/env python3
"""
Release notes: Wizards' HTML article per set -> app/src/main/assets/rules/releasenotes/<CODE>.json

Input: the index app/src/main/assets/rules/release-notes.json (code, name, url, newest first).
For each set it reads tools/releasenotes/source/<slug>.html (gitignored), downloading it first with
--download (missing ones) or --refresh (all). Run on your PC: the cloud/VM can't reach Wizards.

Output, one file per set, same inline markup as the rules bundle (**bold**, *italic*,
[text](https://…), "\\" escapes) in "p"/"heading"/"list"; card name, lines and notes are PLAIN
text, drawn with mana symbols ({W}) like Oracle text:
  {"schemaVersion": 1, "code", "name", "url", "lastModified": "2026-06-22",
   "sections": [{"title": "General Notes", "blocks": [
       {"type": "heading", "text"} | {"type": "p", "text"} | {"type": "list", "items": [...]} |
       {"type": "card", "name", "lines": ["{1}{W}", "Instant", ...], "notes": [...]}]}]}
and the index gets each set's "sha" (first 16 hex of the file's SHA-256), so the app only
downloads sets that changed. Merging to main publishes them (no app update).

Usage: python tools/releasenotes/build_release_notes.py [--download | --refresh] [--only FRA]
"""
from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
import sys
import urllib.request
from datetime import datetime
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(__file__).resolve().parent / "source"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "rules"
INDEX = ASSETS / "release-notes.json"
OUT = ASSETS / "releasenotes"
USER_AGENT = "GrandArbiter/1.0 (release notes pipeline; +https://github.com/arojasp/Grand-Arbiter)"
SCHEMA_VERSION = 1


def escape(text: str) -> str:
    """Raw text -> inline markup text (the app's InlineMarkup escapes)."""
    return re.sub(r"([\\*\[\]])", r"\\\1", text)


class ArticleParser(HTMLParser):
    """Turns <div class="article-body"> into sections of blocks. Everything else is ignored."""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.depth = 0              # div depth inside article-body; 0 = outside
        self.in_body = False
        self.sections: list[dict] = []
        self.intro: list[str] = []  # paragraphs before the first <h2> (compiled by, last modified…)
        self.buf: list[str] = []    # inline markup being built
        self.capture: str | None = None   # "h2" | "h3" | "p" | "card" | "li"
        self.list_items: list[str] | None = None
        self.card: dict | None = None
        self.link: str | None = None
        self.skip = 0               # inside <cig-card> (card image embed)
        self.card_paragraphs = 0    # courier <p> seen: every one must become a card (validation)

    # ---- helpers
    def _section(self) -> dict:
        if not self.sections:
            self.sections.append({"title": "", "blocks": []})
        return self.sections[-1]

    def _text(self) -> str:
        t = "".join(self.buf)
        self.buf = []
        return re.sub(r"[ \t\r\f\v]+", " ", t).replace(" \n", "\n").replace("\n ", "\n").strip()

    def _close_p(self) -> None:
        """
        HTML closes an open <p> at the next <p>, <ul>, <hr>, <h2>, <div>…: Wizards' pages rely on
        it (J. Jonah Jameson's card ends with </li>, Byway Barterer's never closes).
        """
        if self.capture in ("p", "card"):
            self.handle_endtag("p")

    def _end_card(self) -> None:
        if self.card is not None:
            self._section()["blocks"].append(self.card)
            self.card = None

    # ---- parser callbacks
    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if not self.in_body:
            if tag == "div" and "article-body" in (a.get("class") or ""):
                self.in_body, self.depth = True, 1
            return
        if tag in ("p", "ul", "ol", "hr", "h2", "h3", "div"):
            self._close_p()
        if tag == "div":
            self.depth += 1
        if self.skip or tag == "cig-card":
            self.skip += tag == "cig-card"
            return
        if tag in ("h2", "h3"):
            self._end_card()
            self.capture = tag
        elif tag == "p":
            courier = "courier" in (a.get("style") or "").lower()
            if courier:
                self.card_paragraphs += 1
                self._end_card()
            self.capture = "card" if courier else "p"
        elif tag == "ul":
            self.list_items = []
        elif tag == "li":
            self.capture = "li"
        elif tag == "hr":
            self._end_card()
        elif tag == "br":
            self.buf.append("\n")
        elif tag in ("em", "i"):
            self.buf.append("*")
        elif tag in ("strong", "b"):
            self.buf.append("**")
        elif tag == "a":
            href = a.get("href") or ""
            if href.startswith("/"):
                href = "https://magic.wizards.com" + href
            self.link = href if href.startswith("https://") else None
            if self.link:
                self.buf.append("[")
        elif tag == "mana-icon":
            self.buf.append("{" + (a.get("mana") or "?").upper() + "}")

    def handle_endtag(self, tag):
        if not self.in_body:
            return
        if tag == "div":
            self._close_p()
            self.depth -= 1
            if self.depth == 0:
                self._end_card()
                self.in_body = False
            return
        if tag == "cig-card":
            self.skip = max(0, self.skip - 1)
            return
        if self.skip:
            return
        if tag in ("em", "i"):
            self.buf.append("*")
        elif tag in ("strong", "b"):
            self.buf.append("**")
        elif tag == "a" and self.link:
            self.buf.append(f"]({self.link})")
            self.link = None
        elif tag in ("h2", "h3") and self.capture == tag:
            text = self._text()
            if tag == "h2":
                # "Returning Mechanic: Preparation Cards<br>Returning Keyword Action: Prepare": one line
                self.sections.append({"title": " / ".join(l.strip() for l in strip_markup(text).split("\n") if l.strip()), "blocks": []})
            elif text:
                self._section()["blocks"].append({"type": "heading", "text": text})
            self.capture = None
        elif tag == "p" and self.capture in ("p", "card"):
            text = self._text()
            if self.capture == "card":
                # Card text is PLAIN (no markup): the app draws it with its mana symbols, like Oracle text.
                lines = [strip_markup(l) for l in text.split("\n") if l.strip()]
                if lines:
                    self.card = {"type": "card", "name": lines[0], "lines": lines[1:], "notes": []}
            elif text:
                if not self.sections:
                    self.intro.append(text)
                else:
                    self._end_card()
                    self._section()["blocks"].append({"type": "p", "text": text})
            self.capture = None
        elif tag == "li" and self.capture == "li":
            if self.list_items is not None:
                self.list_items.append(self._text())
            self.capture = None
        elif tag == "ul" and self.list_items is not None:
            items = [i for i in self.list_items if i]
            if self.card is not None:
                self.card["notes"].extend(strip_markup(i) for i in items)   # plain, like the card text
            elif items:
                self._section()["blocks"].append({"type": "list", "items": items})
            self.list_items = None

    def handle_data(self, data):
        if self.in_body and not self.skip and self.capture:
            self.buf.append(escape(data.replace("\n", " ")))


def strip_markup(text: str) -> str:
    text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", text)
    text = re.sub(r"(?<!\\)\*", "", text)
    return re.sub(r"\\(.)", r"\1", text).strip()


def last_modified(intro: list[str]) -> str | None:
    for p in intro:
        # "Document last modified June 22, 2026" or "Document last modified Date: June 23, 2025"
        m = re.search(r"last modified\s*(?:date:\s*)?([A-Z][a-z]+ \d{1,2}, \d{4})", strip_markup(p), re.I)
        if m:
            return datetime.strptime(m.group(1), "%B %d, %Y").date().isoformat()
    return None


def section_title(title: str, set_name: str) -> str:
    """
    Short, readable section titles for the app's quick jumps: older pages shout ("GENERAL NOTES"),
    use non-breaking spaces and repeat the set ("Magic: The Gathering | Marvel's Spider-Man Main Set
    Card-Specific Notes" -> "Main Set Card-Specific Notes").
    """
    t = re.sub(r"\s+", " ", title.replace("\xa0", " ")).strip()
    if t.isupper():
        t = " ".join("-".join(part[:1] + part[1:].lower() for part in word.split("-")) for word in t.split(" "))
    for prefix in ("Magic: The Gathering | ", "Magic: The Gathering ", set_name + " "):
        if t.lower().startswith(prefix.lower()) and len(t) > len(prefix):
            t = t[len(prefix):]
    return t[:1].upper() + t[1:]


def slug(url: str) -> str:
    return url.rstrip("/").rsplit("/", 1)[-1]


def download(url: str, target: Path) -> None:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "text/html"})
    with urllib.request.urlopen(req, timeout=60) as r:
        target.write_bytes(r.read())
    print(f"  downloaded {target.name}")


def build(entry: dict, html_text: str) -> tuple[dict, list[str]]:
    p = ArticleParser()
    p.feed(html_text)
    problems = []
    sections = [s for s in p.sections if s["title"] and s["blocks"]]
    for s in sections:
        s["title"] = section_title(section_title(s["title"], entry["name"]), entry["name"])
    # The trademark line at the end of the article is not content.
    for s in sections:
        s["blocks"] = [b for b in s["blocks"] if not (b["type"] == "p" and "property of Wizards of the Coast" in b["text"])]
    cards = [b for s in sections for b in s["blocks"] if b["type"] == "card"]
    # Counted by the same HTML tokenizer: Wizards' markup has quirks a regex misses
    # (Bloomburrow: style="margin-le<br />ft:.5in;font-family:courier").
    expected_cards = p.card_paragraphs
    if not sections:
        problems.append("no sections found (did the page layout change?)")
    if len(cards) != expected_cards:
        problems.append(f"{len(cards)} cards parsed, {expected_cards} in the HTML")
    if any(not c["name"] for c in cards):
        problems.append("a card without a name")
    if not any(s["title"].lower().startswith("general notes") for s in sections):
        problems.append("no 'General Notes' section")
    doc = {
        "schemaVersion": SCHEMA_VERSION,
        "code": entry["code"],
        "name": entry["name"],
        "url": entry["url"],
        "lastModified": last_modified(p.intro),
        "sections": sections,
    }
    return doc, problems


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--download", action="store_true", help="download the sets whose HTML is missing")
    ap.add_argument("--refresh", action="store_true", help="download every set again (Wizards revises them)")
    ap.add_argument("--only", help="just this set code")
    args = ap.parse_args()

    index = json.loads(INDEX.read_text(encoding="utf-8"))
    SOURCE.mkdir(exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    failed = 0
    for entry in index["sets"]:
        if args.only and entry["code"] != args.only.upper():
            continue
        src = SOURCE / f"{slug(entry['url'])}.html"
        print(f"{entry['code']} {entry['name']}")
        if args.refresh or (args.download and not src.exists()):
            download(entry["url"], src)
        if not src.exists():
            print(f"  missing {src.name}: run with --download")
            failed += 1
            continue
        doc, problems = build(entry, src.read_text(encoding="utf-8"))
        if problems:
            failed += 1
            for pr in problems:
                print(f"  ERROR: {pr}")
            continue      # never publish a half-parsed set: the previous file stays
        text = json.dumps(doc, ensure_ascii=False, separators=(",", ":")) + "\n"
        (OUT / f"{entry['code']}.json").write_text(text, encoding="utf-8", newline="\n")
        # Same as the app's ReleaseNotesParser.sha16 (which also normalizes CRLF -> LF).
        entry["sha"] = hashlib.sha256(text.replace("\r\n", "\n").encode("utf-8")).hexdigest()[:16]
        cards = sum(1 for s in doc["sections"] for b in s["blocks"] if b["type"] == "card")
        print(f"  {len(doc['sections'])} sections, {cards} cards, last modified {doc['lastModified']}, {len(text) // 1024} KB")

    INDEX.write_text(json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print("index updated" + (f"; {failed} set(s) not built" if failed else ""))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
