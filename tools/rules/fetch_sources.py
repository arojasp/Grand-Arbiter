#!/usr/bin/env python3
"""
Download the rules sources into rules/source/ (needs network; stdlib only).

  cr.txt        current Comprehensive Rules TXT (URL found via Academy Ruins /link/cr)
  mtr.pdf       current official MTR PDF (URL found via Academy Ruins /link/mtr)

PDF links (MTR/IPG/JAR) come from the official WPN rules-documents page; Academy Ruins' /link
redirects are only a fallback. Its parsed /mtr JSON is not used: on 2026-10-07 it was still the
2025-11-10 MTR, and its /link/mtr pointed to a URL that returned 404.
  sources.json  where each file came from, its SHA-256, and the current IPG/JAR PDF links

  ipg.pdf, jar.pdf  current official IPG / JAR PDFs (text extracted by extract_pdf_docs; the IPG
                    penalties are also kept by hand in ipg-infractions.json and cross-checked)

Usage: python tools/rules/fetch_sources.py
Then:  python tools/rules/extract_mtr.py       (PDF -> rules/source/mtr.txt)
       python tools/rules/build_bundle.py      and review `git diff rules/source`.
"""
from __future__ import annotations

import datetime as dt
import hashlib
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "rules" / "source"
API = "https://api.academyruins.com"
WPN_DOCS = "https://wpn.wizards.com/en/rules-documents"
UA = {"User-Agent": "GrandArbiter-rules-fetch/1.0 (+https://github.com/; build-time only)"}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):  # report the Location instead of following it
        return None


def resolve_link(doc: str) -> str:
    """Academy Ruins /link/<doc> answers 307 -> official document URL."""
    opener = urllib.request.build_opener(NoRedirect)
    try:
        opener.open(urllib.request.Request(f"{API}/link/{doc}", headers=UA), timeout=30)
    except urllib.error.HTTPError as e:
        if e.code in (301, 302, 303, 307, 308) and e.headers.get("Location"):
            return e.headers["Location"]
        raise
    raise RuntimeError(f"/link/{doc} did not redirect")


def wpn_pdf_links() -> dict[str, str]:
    """Official PDF links from the WPN rules page (newest first on the page). English versions preferred."""
    html = get(WPN_DOCS).decode("utf-8", errors="replace").replace("\\/", "/")
    urls = re.findall(r"https://media\.wizards\.com/[^\"'\s<>]+?\.pdf", html, re.I)
    found: dict[str, str] = {}
    for doc in ("mtr", "ipg", "jar"):
        cands = [u for u in dict.fromkeys(urls) if re.search(rf"_{doc}_", u, re.I)]
        english = [u for u in cands if re.search(r"_EN\.pdf$", u, re.I)]
        if english or cands:
            found[doc] = (english or cands)[0]
    return found


def get(url: str) -> bytes:
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return r.read()


def save(name: str, data: bytes) -> str:
    path = SRC / name
    old = path.read_bytes() if path.exists() else None
    path.write_bytes(data)
    status = "new" if old is None else ("unchanged" if old == data else "CHANGED")
    print(f"  {name:10} {len(data) // 1024:6} KB  {status}")
    return hashlib.sha256(data).hexdigest()


def main() -> int:
    SRC.mkdir(parents=True, exist_ok=True)
    sources_path = SRC / "sources.json"
    previous = json.loads(sources_path.read_text(encoding="utf-8")) if sources_path.exists() else {}
    now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    sources: dict = {"fetchedAt": now}

    print("Resolving official document links…")
    links: dict[str, str] = {}
    try:
        links.update(wpn_pdf_links())
    except Exception as e:
        print(f"  WPN page FAILED ({e}); falling back to Academy Ruins links", file=sys.stderr)
    for doc in ("cr", "mtr", "ipg", "jar"):
        if doc not in links:
            try:
                links[doc] = resolve_link(doc)
            except Exception as e:  # keep going: one broken link shouldn't block the rest
                print(f"  {doc:4} FAILED: {e}", file=sys.stderr)
                continue
            print(f"  {doc:4} {links[doc]}  (via Academy Ruins)")
        else:
            print(f"  {doc:4} {links[doc]}  (via WPN)")

    print("Downloading…")
    if "cr" not in links or not links["cr"].lower().endswith(".txt"):
        print("  CR: no .txt link found. Download the TXT from https://magic.wizards.com/en/rules "
              "and save it as rules/source/cr.txt", file=sys.stderr)
    else:
        sources["cr"] = {"url": links["cr"], "sha256": save("cr.txt", get(links["cr"]))}

    if "mtr" not in links or not links["mtr"].lower().endswith(".pdf"):
        print("  MTR: no PDF link found. Download it from https://wpn.wizards.com/en/rules-documents "
              "and save it as rules/source/mtr.pdf", file=sys.stderr)
    else:
        try:
            pdf = get(links["mtr"])
        except urllib.error.HTTPError as e:
            raise SystemExit(f"MTR: {links['mtr']} -> HTTP {e.code}. Download the MTR PDF from "
                             f"{WPN_DOCS} and save it as rules/source/mtr.pdf")
        if not pdf.startswith(b"%PDF"):
            raise SystemExit(f"MTR: {links['mtr']} did not return a PDF")
        sources["mtr"] = {"url": links["mtr"], "sha256": save("mtr.pdf", pdf)}

    for doc in ("ipg", "jar"):
        if doc not in links:
            print(f"  {doc.upper()}: no PDF link found. Download it from {WPN_DOCS} "
                  f"and save it as rules/source/{doc}.pdf", file=sys.stderr)
            continue
        old = previous.get(doc, {}).get("url") or previous.get(doc, {}).get("pdf")
        if old and old != links[doc]:
            print(f"  !! {doc.upper()} has a NEW official PDF: {links[doc]}\n"
                  f"     (was {old}). Re-check rules/source/{doc}-infractions.json by hand." if doc == "ipg" else
                  f"  !! {doc.upper()} has a NEW official PDF: {links[doc]} (was {old}).")
        try:
            pdf = get(links[doc])
        except urllib.error.HTTPError as e:
            print(f"  {doc.upper()}: {links[doc]} -> HTTP {e.code}. Download it from {WPN_DOCS} "
                  f"and save it as rules/source/{doc}.pdf", file=sys.stderr)
            continue
        if not pdf.startswith(b"%PDF"):
            print(f"  {doc.upper()}: {links[doc]} did not return a PDF", file=sys.stderr)
            continue
        sources[doc] = {"url": links[doc], "sha256": save(f"{doc}.pdf", pdf)}

    sources_path.write_text(json.dumps(sources, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {sources_path.relative_to(ROOT)}. Next: python tools/rules/extract_mtr.py, then build_bundle.py")
    return 0


if __name__ == "__main__":
    sys.exit(main())
