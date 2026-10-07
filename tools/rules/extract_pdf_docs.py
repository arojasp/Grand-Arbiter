#!/usr/bin/env python3
"""
Extract the official MTR / IPG / JAR PDFs (rules/source/<doc>.pdf) into rules/source/<doc>.txt.

The .txt files are a small line-based format that build_bundle.py reads (stdlib only). They are
committed, so every new document version shows up in git as a readable diff.

  @effective 2026-02-27          effective date from the cover
  @penalty No Penalty            IPG: the penalty printed next to the infraction header
  # 2.3 | Pregame Procedures     section header: local id | title [| parent local id]
  ## Regular                     heading inside a section
  - text                         bullet item
  1. text                        numbered item
  |= a | b | c                   table header row      |  a | b | c   table row
                                 (in cells: \\| is a literal pipe, \\n a line break)
  anything else                  one paragraph per line
  \\- text                        a paragraph that merely starts like syntax

Text is already in bundle markup: literal * [ ] \\ are escaped, bold is **…**.

MTR and IPG headers are matched against the PDF's own table of contents and every TOC entry
must be found exactly once, in order. The JAR has no TOC: its centred bold lines are sections
and its left-aligned bold lines are the issues inside them.

Requires: pip install pdfplumber
Usage:    python tools/rules/extract_pdf_docs.py [mtr] [ipg] [jar]     (default: all three)
"""
from __future__ import annotations

import datetime as dt
import re
import sys
from pathlib import Path

try:
    import pdfplumber
except ImportError:
    raise SystemExit("pdfplumber is required: pip install pdfplumber")

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "rules" / "source"

TOC_LEADER = re.compile(r"\.{4,}\s*\d+\s*$")
TOC_SECTION = re.compile(r"^(\d+)\.\s+(.+?)\s*\.{4,}")
TOC_SUBSECTION = re.compile(r"^(\d+\.\d+)\.?\s+(.+?)\s*\.{4,}")
TOC_APPENDIX = re.compile(r"^Appendix ([A-Z])\s*[—–-]\s*(.+?)\s*\.{4,}")
TOC_PLAIN = re.compile(r"^(.+?)\s*\.{4,}")
BULLET = re.compile(r"^[••]\s*")
ORDERED = re.compile(r"^(\d+)\.\s+")
WORD_GAP = 0.18   # a horizontal gap wider than this x font size is a space (these PDFs have no space chars)
PENALTIES = ("No Penalty", "Warning", "Game Loss", "Match Loss", "Disqualification")
# IPG infraction parts, detected on the raw PDF line (independently of the paragraph logic) and
# written as "@parts …" so build_bundle.py can check that every part ended up labelled.
IPG_PARTS = {"Definition": "definition", "Examples": "examples", "Example": "examples", "Philosophy": "philosophy",
             "AdditionalRemedy": "additional_remedy", "Upgrade": "upgrade", "Downgrade": "downgrade"}


# ---------------------------------------------------------------- text helpers

def norm(s: str) -> str:
    """Comparison key for headers: case, spaces, dashes and quotes don't matter."""
    s = s.replace("–", "-").replace("—", "-").replace("’", "'")
    s = re.sub(r"\s*-\s*", "-", s)
    return re.sub(r"\s+", " ", s).strip().lower()


def slug(s: str, words: int = 6) -> str:
    parts = re.findall(r"[a-z0-9]+", s.lower().replace("’", "").replace("'", ""))
    return "-".join(parts[:words])


def esc(s: str) -> str:
    return s.replace("\\", "\\\\").replace("*", "\\*").replace("[", "\\[").replace("]", "\\]")


def is_bold(fontname: str) -> bool:
    return "Bold" in fontname


def plain(chars: list[dict]) -> str:
    buf, prev = [], None
    for c in chars:
        if prev is not None and c["x0"] - prev["x1"] > WORD_GAP * c.get("size", 11):
            buf.append(" ")
        buf.append(c["text"])
        prev = c
    return re.sub(r"\s+", " ", "".join(buf)).strip()


def line_markup(chars: list[dict], keep_bold: bool = True) -> str:
    """Rebuild a line from chars: bold runs become **…**, everything else is escaped."""
    runs: list[tuple[bool, str]] = []
    prev = None
    for ch in chars:
        b = keep_bold and is_bold(ch.get("fontname", "")) and "Italic" not in ch.get("fontname", "")
        gap = prev is not None and ch["x0"] - prev["x1"] > WORD_GAP * ch.get("size", 11)
        text = (" " if gap else "") + ch["text"]
        if ch["text"].strip() == "":
            b = runs[-1][0] if runs else False          # a space keeps the current style
        if runs and runs[-1][0] == b:
            runs[-1] = (b, runs[-1][1] + text)
        else:
            runs.append((b, text))
        prev = ch
    out = ""
    for b, seg in runs:
        core = seg.strip()
        if not core:
            out += seg
            continue
        lead, trail = seg[: len(seg) - len(seg.lstrip())], seg[len(seg.rstrip()):]
        out += lead + (f"**{esc(core)}**" if b else esc(core)) + trail
    out = re.sub(r"\s+", " ", out)
    out = re.sub(r"\*\*(\s*)\*\*", r"\1", out)                 # "**a** **b**" glued -> one run
    out = re.sub(r"([a-z])(https?://)", r"\1 \2", out)         # the PDFs have "located athttps://…"
    return out.strip()


def clean_table(raw: list[list]) -> list[list[str]]:
    """Normalise pdfplumber rows: keep in-cell line breaks, undo merged-cell artefacts."""
    rows = [[re.sub(r"[ \t]+", " ", (c or "")).strip() for c in r] for r in raw]
    rows = [r for r in rows if any(r)]
    if not rows:
        return rows
    counts = {sum(1 for c in r if c) for r in rows}
    if len(counts) == 1 and next(iter(counts)) >= 2:
        return [[c for c in r if c] for r in rows]          # merged cells only shifted things: compact
    width = max(len(r) for r in rows)
    keep = [i for i in range(width) if any(i < len(r) and r[i] for r in rows)]
    rows = [[r[i] if i < len(r) else "" for i in keep] for r in rows]
    if len(rows) > 2 and any(not c for c in rows[0]):     # two-row header ("Cards remaining" over 2 columns)
        top, sub = rows[0], rows[1]
        rows = [[f"{t} — {b}" if t and b else (t or b) for t, b in zip(top, sub)]] + rows[2:]
    return rows


def cell(c: str) -> str:
    return "\\n".join(esc(part.strip()).replace("|", "\\|") for part in c.split("\n"))


def find_effective(pdf, pattern: str) -> str:
    for page in pdf.pages[:3]:
        if (m := re.search(pattern + r" (\w+ \d{1,2}, \d{4})", page.extract_text() or "")):
            return dt.datetime.strptime(m.group(1), "%B %d, %Y").date().isoformat()
    raise SystemExit("effective date not found on the first pages")


# ---------------------------------------------------------------- page -> line/table stream

def page_stream(page) -> list[tuple]:
    tables = page.find_tables()
    boxes = [t.bbox for t in tables]

    def in_table(c):
        return any(b[0] - 1 <= c["x0"] and c["x1"] <= b[2] + 1 and b[1] - 1 <= c["top"] and c["bottom"] <= b[3] + 1
                   for b in boxes)

    stream = []
    for ln in page.extract_text_lines(return_chars=True):
        chars = [c for c in ln["chars"] if not in_table(c)]
        if not chars:
            continue
        txt = plain(chars)
        if re.fullmatch(r"\d{1,3}", txt) and (ln["top"] < 80 or ln["top"] > page.height - 80):
            continue                                         # page number
        bold_chars = [c for c in chars if c["text"].strip()]
        stream.append(("line", ln["top"], {
            "chars": chars, "text": txt, "x0": chars[0]["x0"], "x1": chars[-1]["x1"],
            "top": ln["top"], "bottom": ln["bottom"], "size": max(c.get("size", 11) for c in chars),
            "all_bold": bool(bold_chars) and all(is_bold(c["fontname"]) for c in bold_chars),
        }))
    for t in tables:
        stream.append(("table", t.bbox[1], t))
    stream.sort(key=lambda s: s[1])
    return stream


class Writer:
    """Turns classified lines into the .txt format: paragraphs, list items, headings, tables."""

    def __init__(self) -> None:
        self.out: list[str] = []
        self.para: list[str] = []
        self.item = False

    def flush(self) -> None:
        if self.para:
            joined = ""
            for piece in self.para:
                if joined.endswith("-") and not joined.endswith(" -"):
                    joined += piece                      # "state-" + "based"
                else:
                    joined += (" " if joined else "") + piece
            if not self.item and re.match(r"^(#|-|\||@|\\|\d+\.\s)", joined):
                joined = "\\" + joined                   # a paragraph that would look like syntax
            self.out.append(joined)
        self.para, self.item = [], False

    def emit(self, *lines: str) -> None:
        self.flush()
        self.out.extend(lines)

    def table(self, t) -> None:
        rows = clean_table(t.extract())
        if rows:
            self.emit("|= " + " | ".join(cell(c) for c in rows[0]),
                      *["|  " + " | ".join(cell(c) for c in r) for r in rows[1:]], "")

    def text(self) -> str:
        self.flush()
        return re.sub(r"\n{3,}", "\n\n", "\n".join(self.out)).strip() + "\n"


def flow_line(w: Writer, line: dict, prev: dict | None, body_x: float, heading_ok: bool) -> None:
    """Paragraph / list / heading logic shared by all documents."""
    gap = (line["top"] - prev["bottom"]) if prev else None
    new_block = gap is None or gap > 0.55 * line["size"]
    indented = line["x0"] > body_x + 20
    bullet = BULLET.match(line["text"])
    numbered = ORDERED.match(line["text"]) and indented
    markup = line_markup(line["chars"])
    if re.match(r"^\*\*[\d.]+\*\*:", markup):
        new_block = True                                    # IPG changelog entry "**2.3**: …"
    if re.match(r"^[A-H]\.\s+\S", line["text"]):
        new_block = True                                    # IPG lettered example "B. …" (tight spacing in the PDF)
    if heading_ok and line["all_bold"] and len(line["text"]) < 60 and not line["text"].endswith((".", ":")) \
            and not bullet and not numbered:
        w.emit(f"## {esc(line['text'])}", "")
    elif bullet or numbered:
        w.flush()
        w.item = True
        w.para = ["- " + BULLET.sub("", markup)] if bullet else [markup]
    elif gap is None and w.para and not w.item:
        last = w.para[-1]                                   # first line on a new page
        if last.endswith((".", ":", "”", ")")) and (line["text"][:1].isupper() or line["text"][:1] in "“("):
            w.flush(); w.para = [markup]
        else:
            w.para.append(markup)
    elif w.item and indented and (not new_block or gap is None):
        w.para.append(markup)                               # wrapped list item / item continues on next page
    elif new_block:
        w.flush(); w.para = [markup]
    else:
        w.para.append(markup)


# ---------------------------------------------------------------- MTR and IPG (TOC-driven)

def read_toc(pdf) -> tuple[list[dict], set[int]]:
    entries, toc_pages = [], set()
    for i, page in enumerate(pdf.pages[:8]):
        lines = [ln.strip() for ln in (page.extract_text() or "").split("\n")]
        if not any(TOC_LEADER.search(ln) for ln in lines):
            continue
        toc_pages.add(i)
        for ln in lines:
            if not TOC_LEADER.search(ln):
                continue
            if (m := TOC_APPENDIX.match(ln)):
                entries.append({"kind": "section", "id": f"appendix-{m.group(1).lower()}",
                                "title": f"Appendix {m.group(1)}—{m.group(2).strip()}", "appendix": True})
            elif (m := TOC_SUBSECTION.match(ln)):
                entries.append({"kind": "section", "id": m.group(1), "title": m.group(2).strip()})
            elif (m := TOC_SECTION.match(ln)):
                entries.append({"kind": "section", "id": m.group(1), "title": m.group(2).strip()})
            elif (m := TOC_PLAIN.match(ln)):
                title = m.group(1).strip()
                last_appendix = next((e for e in reversed(entries) if e.get("appendix")), None)
                if last_appendix and entries and (entries[-1] is last_appendix or entries[-1]["kind"] == "heading"):
                    entries.append({"kind": "heading", "title": title})        # sub-entry of an appendix
                else:
                    entries.append({"kind": "section", "id": slug(title), "title": title, "plain": True})
    if not entries:
        raise SystemExit("table of contents not found")
    return entries, toc_pages


def header_keys(e: dict) -> set[str]:
    t = e["title"]
    if e["kind"] == "heading" or e.get("plain") or e.get("appendix"):
        return {norm(t)}
    return {norm(f"{e['id']}. {t}"), norm(f"{e['id']} {t}"), norm(f"{e['id']}.{t}")}


def match_header(key: str, e: dict) -> str | None:
    """None if the line isn't this header; otherwise what follows the header text (IPG penalty)."""
    for h in header_keys(e):
        if key == h:
            return ""
        if key.startswith(h + " "):
            return key[len(h) + 1:]
    return None


def extract_toc_doc(name: str, effective_pattern: str) -> tuple[str, list[str]]:
    problems: list[str] = []
    w = Writer()
    with pdfplumber.open(SRC / f"{name}.pdf") as pdf:
        effective = find_effective(pdf, effective_pattern)
        toc, toc_pages = read_toc(pdf)
        w.out += [f"@effective {effective}", f"@source {name}.pdf", ""]
        toc_i, started, done, body_x = 0, False, False, None
        parts: dict[int, list[str]] = {}          # index of "@penalty" line in w.out -> parts seen
        for pi, page in enumerate(pdf.pages):
            if done:
                break
            if pi in toc_pages:
                continue                                      # contents pages (IPG: after the introduction)
            prev = None
            for kind, _, obj in page_stream(page):
                if kind == "table":
                    if started:
                        w.table(obj)
                    continue
                line, key = obj, norm(obj["text"])
                if key.startswith("all trademarks are property of wizards"):
                    done = True
                    break
                found, rest = None, None
                if toc_i < len(toc) and (rest := match_header(key, toc[toc_i])) is not None:
                    found = toc_i
                else:
                    for j in range(toc_i + 1, len(toc)):
                        if (rest := match_header(key, toc[j])) is not None:
                            # only trust a jump ahead for an exact, header-looking line
                            if started and (line["all_bold"] or re.match(r"^\d", line["text"])):
                                problems.append(f"TOC entry not found before page {pi + 1}: "
                                                f"{toc[toc_i].get('id', '')} {toc[toc_i]['title']!r}")
                                found = j
                            break
                if found is not None:
                    e = toc[found]
                    toc_i = found + 1
                    started = True
                    if e["kind"] == "section":
                        w.emit("", f"# {e['id']} | {esc(e['title'])}", "")
                        if rest:
                            penalty = next((p for p in PENALTIES if norm(p) == rest), None)
                            if penalty:
                                w.emit(f"@penalty {penalty}")
                                parts[len(w.out)] = []     # "@parts" goes right after "@penalty"
                            else:
                                problems.append(f"{e['id']}: unexpected text after the header: {rest!r}")
                    else:
                        w.emit("", f"## {esc(e['title'])}", "")
                    prev = line
                    continue
                if not started:
                    continue                                  # cover page title etc.
                if parts:
                    raw = line["text"].replace(" ", "")
                    for part, role in IPG_PARTS.items():
                        if raw == part or raw.startswith(part + ":") or raw.startswith(f"**{part}"):
                            current = parts[max(parts)]
                            if role not in current:
                                current.append(role)
                if body_x is None:
                    body_x = line["x0"]
                flow_line(w, line, prev, body_x, heading_ok=True)
                prev = line
    if toc_i < len(toc):
        problems += [f"TOC entry never found: {e.get('id', '')} {e['title']!r}" for e in toc[toc_i:]]
    for at in sorted(parts, reverse=True):              # insert from the end so indexes stay valid
        w.out.insert(at, "@parts " + ",".join(parts[at]))
    n = sum(1 for e in toc if e["kind"] == "section")
    print(f"{name.upper()} effective {effective}: {n} sections in the TOC, "
          f"{sum(1 for ln in w.out if ln.startswith('# '))} extracted")
    return w.text(), problems


# ---------------------------------------------------------------- JAR (no TOC)

def extract_jar() -> tuple[str, list[str]]:
    problems: list[str] = []
    w = Writer()
    with pdfplumber.open(SRC / "jar.pdf") as pdf:
        effective = find_effective(pdf, r"(?:Updated|Effective)")
        w.out += [f"@effective {effective}", "@source jar.pdf", ""]
        page_w = pdf.pages[0].width
        top_id, started, done, body_x = None, False, False, None
        pending_issue: list[dict] = []          # consecutive bold lines forming one issue title

        def close_issue():
            nonlocal pending_issue
            if pending_issue:
                title = " ".join(l["text"] for l in pending_issue)
                if not top_id:
                    problems.append(f"issue outside a section: {title!r}")
                w.emit("", f"# {slug(title)} | {esc(title)} | {top_id}", "")
                pending_issue = []

        for page in pdf.pages:
            if done:
                break
            prev = None
            for kind, _, obj in page_stream(page):
                if kind == "table":
                    close_issue(); w.table(obj); continue
                line = obj
                if norm(line["text"]).startswith("all trademarks are property of wizards"):
                    done = True
                    break
                if not started:
                    if re.search(r"(Updated|Effective) \w+ \d", line["text"]) or line["text"].startswith("Judging at Regular"):
                        continue                              # title block
                    started = True
                    w.emit("", "# introduction | Introduction", "")
                    top_id = None
                centred = abs((line["x0"] + line["x1"]) / 2 - page_w / 2) < 40 and line["x0"] > 120
                if line["all_bold"] and centred:
                    close_issue()
                    top_id = slug(line["text"])
                    w.emit("", f"# {top_id} | {esc(line['text'])}", "")
                    prev = line
                    continue
                if line["all_bold"] and not BULLET.match(line["text"]) and top_id:
                    gap = (line["top"] - prev["bottom"]) if prev else 99
                    if pending_issue and gap < 0.55 * line["size"]:
                        pending_issue.append(line)            # issue title wraps onto a second line
                    else:
                        close_issue()
                        pending_issue = [line]
                    prev = line
                    continue
                close_issue()
                if body_x is None:
                    body_x = line["x0"]
                flow_line(w, line, prev, body_x, heading_ok=False)
                prev = line
        close_issue()
    print(f"JAR effective {effective}: {sum(1 for ln in w.out if ln.startswith('# '))} sections")
    return w.text(), problems


# ---------------------------------------------------------------- main

def main() -> int:
    wanted = sys.argv[1:] or ["mtr", "ipg", "jar"]
    failed = False
    for name in wanted:
        if not (SRC / f"{name}.pdf").exists():
            print(f"{name}: rules/source/{name}.pdf missing (run fetch_sources.py)", file=sys.stderr)
            failed = True
            continue
        if name == "mtr":
            text, problems = extract_toc_doc("mtr", "Effective")
        elif name == "ipg":
            text, problems = extract_toc_doc("ipg", "Effective")
        elif name == "jar":
            text, problems = extract_jar()
        else:
            print(f"unknown document {name!r}", file=sys.stderr)
            return 2
        out = SRC / f"{name}.txt"
        out.write_text(text, encoding="utf-8", newline="\n")
        for p in problems:
            print(f"  PROBLEM ({name}): {p}", file=sys.stderr)
        failed |= bool(problems)
        print(f"  -> {out.relative_to(ROOT)}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
