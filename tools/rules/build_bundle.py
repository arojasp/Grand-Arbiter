#!/usr/bin/env python3
"""
Build app/src/main/assets/rules/bundle.json from the files in rules/source/.

Offline and stdlib-only: run fetch_sources.py first (needs network), then this.
The real gate is the Kotlin test BundledRulesAssetTest (strict decode + RulesBundleValidator);
the checks here are a fast mirror so problems show up before Gradle.

Sources:
  rules/source/cr.txt        official Comprehensive Rules TXT (Wizards)
  rules/source/mtr.txt, ipg.txt, jar.txt   official PDFs -> text by extract_pdf_docs.py
  rules/source/ipg-infractions.json        IPG penalties checked by a judge (cross-checked with the PDF)
  rules/source/sources.json  URLs + hashes written by fetch_sources.py

Usage: python tools/rules/build_bundle.py [--bundle-version 2026100701] [--check]
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "rules" / "source"
OUT = ROOT / "app" / "src" / "main" / "assets" / "rules" / "bundle.json"
# Small file the app downloads first (from GitHub, branch main) to know whether there is a newer
# bundle before downloading the whole thing. Must always match bundle.json: BundledRulesAssetTest checks.
MANIFEST = OUT.with_name("manifest.json")
REPORT = ROOT / "rules" / "build-report.md"
SCHEMA_VERSION = 1


class Report:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.info: list[str] = []

    def error(self, msg: str) -> None: self.errors.append(msg)
    def warn(self, msg: str) -> None: self.warnings.append(msg)
    def note(self, msg: str) -> None: self.info.append(msg)


# ---------------------------------------------------------------- markup

def esc(text: str) -> str:
    """Escape characters that mean something in our markup."""
    return (text.replace("\\", "\\\\").replace("*", "\\*")
            .replace("[", "\\[").replace("]", "\\]"))


def link(label: str, target_id: str) -> str:
    return f"[{label}](ref:{target_id})"


# ---------------------------------------------------------------- CR (official TXT)

CR_CHAPTER = re.compile(r"^(\d)\. (\S.*)$")
CR_SECTION = re.compile(r"^(\d{3})\. (\S.*)$")
CR_RULE = re.compile(r"^(\d{3}\.\d+)\.? (\S.*)$")
CR_SUBRULE = re.compile(r"^(\d{3}\.\d+[a-z]+)\.? (\S.*)$")   # "\.?": the official CR has typos like "119.1d."
CR_EXAMPLE = re.compile(r"^Example: (\S.*)$")
CR_RULE_REF = re.compile(r"(?<![\d.,])(\d{3}(?:\.\d+[a-z]?)?)(?![\d])(?!,\d)")   # not "999,999"
CR_SECTION_ID = re.compile(r"cr/\d{3}")
# a bare "NNN" is a reference only inside "rule(s)/section(s) ..." lists, e.g. "rules 100.1 and 101"
CR_REF_CONTEXT = re.compile(
    r"\b(?:rules?|sections?)\s+(?:\[?\d{3}(?:\.\d+[a-z]?)?(?:\]\(ref:[^)]*\))?(?:,|\s+and|\s+or|\s+through|\s*[–-])?\s*)*$", re.I)


def read_text(path: Path) -> str:
    raw = path.read_bytes()
    for enc in ("utf-8-sig", "cp1252"):
        try:
            text = raw.decode(enc)
            break
        except UnicodeDecodeError:
            continue
    else:
        raise SystemExit(f"cannot decode {path}")
    return text.replace("\r\n", "\n").replace("\r", "\n")


def link_cr_refs(text: str, ids: set[str], rep: Report, where: str) -> str:
    """Escape, then turn rule numbers into links. Bare 3-digit numbers only after 'rule(s)'/'section'."""
    out, last = [], 0
    escaped = esc(text)
    for m in CR_RULE_REF.finditer(escaped):
        num = m.group(1)
        if "." not in num and not CR_REF_CONTEXT.search("".join(out[-3:]) + escaped[last:m.start()]):
            continue  # "100 cards" is not a reference
        target = f"cr/{num}"
        out.append(escaped[last:m.start()])
        if target in ids:
            out.append(link(num, target))
        else:
            out.append(num)
            rep.warn(f"{where}: reference to {num} not found in the CR (left as plain text)")
        last = m.end()
    out.append(escaped[last:])
    return "".join(out)


def parse_cr(path: Path, rep: Report) -> dict:
    text = read_text(path)
    m = re.search(r"effective as of (\w+ \d{1,2}, \d{4})", text)
    if not m:
        raise SystemExit("CR: effective date line not found")
    effective = dt.datetime.strptime(m.group(1), "%B %d, %Y").date().isoformat()

    lines = [ln.strip() for ln in text.split("\n")]
    try:
        contents = lines.index("Contents")
        toc_glossary = lines.index("Glossary", contents)
        body_start = next(i for i in range(toc_glossary + 1, len(lines)) if CR_CHAPTER.match(lines[i]))
        body_end = lines.index("Glossary", body_start)
        credits = lines.index("Credits", body_end)
    except (ValueError, StopIteration) as e:
        raise SystemExit(f"CR: document layout not recognised ({e})")

    # pass 1: classify lines into raw sections
    raw: list[dict] = []          # {id, parentId, number, title, paras: [str], examples: [str]}
    seen_ids: set[str] = set()
    chapter = section = rule = None
    unclassified: list[str] = []
    for ln in lines[body_start:body_end]:
        if not ln:
            continue
        if (mm := CR_SUBRULE.match(ln)):
            num, body = mm.groups()
            parent = f"cr/{num.rstrip('abcdefghijklmnopqrstuvwxyz')}"
            if not rule or rule["id"] != parent:
                rep.warn(f"cr/{num}: subrule without its rule {parent} right before it")
            cur = {"id": f"cr/{num}", "parentId": parent if parent in seen_ids else (section and section["id"]),
                   "number": num, "title": None, "paras": [body], "examples": []}
            raw.append(cur); seen_ids.add(cur["id"])
        elif (mm := CR_RULE.match(ln)):
            num, body = mm.groups()
            parent = f"cr/{num.split('.')[0]}"
            rule = {"id": f"cr/{num}", "parentId": parent, "number": num, "title": None, "paras": [body], "examples": []}
            raw.append(rule); seen_ids.add(rule["id"])
        elif (mm := CR_SECTION.match(ln)) and chapter and mm.group(1)[0] == chapter["number"]:
            section = {"id": f"cr/{mm.group(1)}", "parentId": chapter["id"], "number": mm.group(1),
                       "title": mm.group(2), "paras": [], "examples": []}
            raw.append(section); seen_ids.add(section["id"])
            rule = None
        elif (mm := CR_CHAPTER.match(ln)) and (chapter is None or int(mm.group(1)) > int(chapter["number"])):
            chapter = {"id": f"cr/{mm.group(1)}", "parentId": None, "number": mm.group(1),
                       "title": mm.group(2), "paras": [], "examples": []}
            raw.append(chapter); seen_ids.add(chapter["id"])
            section = rule = None
        elif (mm := CR_EXAMPLE.match(ln)):
            if raw:
                raw[-1]["examples"].append(mm.group(1))
            else:
                unclassified.append(ln)
        else:
            unclassified.append(ln)
            if raw and raw[-1]["paras"]:
                raw[-1]["paras"].append(ln)     # keep the text, flag it for review

    if unclassified:
        rep.warn(f"CR: {len(unclassified)} line(s) didn't match any pattern and were kept as extra paragraphs. "
                 f"First ones: {unclassified[:5]}")

    ids = {r["id"] for r in raw}
    dupes = {i for i in ids if sum(1 for r in raw if r["id"] == i) > 1}
    for d in sorted(dupes):
        rep.error(f"CR: duplicate id {d}")

    # pass 2: blocks with escaped text + links
    sections = []
    for r in raw:
        blocks = [{"type": "p", "text": link_cr_refs(p, ids, rep, r["id"])} for p in r["paras"]]
        blocks += [{"type": "example", "text": link_cr_refs(e, ids, rep, r["id"])} for e in r["examples"]]
        sec = {"id": r["id"], "number": r["number"]}
        if r["parentId"]:
            sec["parentId"] = r["parentId"]
        if r["title"]:
            sec["title"] = esc(r["title"])
        if blocks:
            sec["blocks"] = blocks
        sections.append(sec)

    # glossary
    glossary, block = [], []
    for ln in lines[body_end + 1:credits] + [""]:
        if ln:
            block.append(ln)
        elif block:
            term, definition = block[0], "\n".join(block[1:])
            if definition:
                glossary.append({"term": term, "definition": link_cr_refs(definition, ids, rep, f"glossary '{term}'")})
            else:
                rep.warn(f"CR glossary: term without definition: {term!r}")
            block = []
    seen = set()
    for g in glossary:
        if g["term"] in seen:
            rep.error(f"CR glossary: duplicate term {g['term']!r}")
        seen.add(g["term"])

    ex_counts = [len(r["examples"]) for r in raw]
    many = [r["id"] for r in raw if len(r["examples"]) >= 4]
    rep.note(f"CR: effective {effective}; {sum(1 for r in raw if r['parentId'] is None)} chapters, "
             f"{sum(1 for r in raw if CR_SECTION_ID.fullmatch(r['id']))} sections, "
             f"{sum(1 for r in raw if r['number'] and '.' in r['number'])} rules+subrules, "
             f"{sum(ex_counts)} examples (max {max(ex_counts, default=0)} on one rule), {len(glossary)} glossary terms")
    if many:
        rep.note(f"CR: {len(many)} rule(s) with 4+ examples (Academy Ruins keeps at most 3): {many[:10]}")

    return {"id": "cr", "title": "Comprehensive Rules", "effectiveDate": effective,
            "sections": sections, "glossary": glossary}


# ---------------------------------------------------------------- MTR / IPG / JAR (rules/source/<doc>.txt from extract_pdf_docs.py)

TXT_DOCS = {
    "mtr": "Magic: The Gathering Tournament Rules",
    "ipg": "Magic Infraction Procedure Guide",
    "jar": "Judging at Regular Rules Enforcement Level",
}
SECTION_REF = re.compile(r"\b([Ss]ections?)\s+(\d+\.\d+)\b")
APPENDIX_REF = re.compile(r"\bAppendix ([A-Z])\b")
CR_REF = re.compile(r"\b([Rr]ules?)\s+(\d{3}\.\d+[a-z]?)\b")
SYNTAX_ESCAPE = re.compile(r"^\\([#\-|@\d\\])")
CELL_SPLIT = re.compile(r"(?<!\\)\|")
HEADING_ROLES = {"definition": "definition", "examples": "examples", "example": "examples",
                 "philosophy": "philosophy", "additional remedy": "additional_remedy", "upgrade": "upgrade",
                 "downgrade": "downgrade"}
LETTERED_EXAMPLE = re.compile(r"^([A-Z])\.\s+(.+)$")
# IPG: "**Upgrade**: text" / "**Upgrade:** text" are inline in the PDF; they become a labelled part
GRADE_LEAD_IN = re.compile(r"^\*\*(Upgrade|Downgrade)(:?)\*\*(:?)\s*(.+)$")


def link_refs(text: str, doc: str, ids: set[str], rep: Report, where: str) -> str:
    """Text is already markup (escaped by extract_pdf_docs.py); only add links."""
    def section(m: re.Match) -> str:
        target = f"{doc}/{m.group(2)}"
        if doc != "mtr" and "Tournament Rules" in m.string[m.end():m.end() + 40]:
            target = f"mtr/{m.group(2)}"          # "section 5.2 of the Magic Tournament Rules"
        if target in ids:
            return f"{m.group(1)} {link(m.group(2), target)}"
        rep.warn(f"{where}: reference to section {m.group(2)} not found in the {doc.upper()} (left as plain text)")
        return m.group(0)

    def appendix(m: re.Match) -> str:
        target = f"{doc}/appendix-{m.group(1).lower()}"
        return link(m.group(0), target) if target in ids else m.group(0)

    def cr_rule(m: re.Match) -> str:
        target = f"cr/{m.group(2)}"
        return f"{m.group(1)} {link(m.group(2), target)}" if target in ids else m.group(0)

    text = SECTION_REF.sub(section, text)
    text = APPENDIX_REF.sub(appendix, text)
    return CR_REF.sub(cr_rule, text)


def parse_txt_doc(doc: str, path: Path, rep: Report, other_ids: set[str]) -> dict:
    lines = path.read_text(encoding="utf-8").split("\n")
    effective = next((ln.split(" ", 1)[1].strip() for ln in lines if ln.startswith("@effective ")), None)
    if not effective:
        raise SystemExit(f"{doc.upper()}: @effective line missing in {path.name} (re-run extract_pdf_docs.py)")
    ids = {f"{doc}/{ln[2:].split(' | ')[0]}" for ln in lines if ln.startswith("# ")} | other_ids

    sections, cur, in_examples = [], None, False

    def blocks() -> list:
        return cur.setdefault("blocks", [])

    for ln in lines:
        if not ln.strip() or (ln.startswith("@") and not ln.startswith(("@penalty ", "@parts "))):
            continue
        if ln.startswith("# "):
            parts = ln[2:].split(" | ")
            local, title = parts[0], parts[1]
            cur = {"id": f"{doc}/{local}"}
            if len(parts) > 2:
                cur["parentId"] = f"{doc}/{parts[2]}"
            elif re.fullmatch(r"\d+\.\d+", local):
                cur["parentId"] = f"{doc}/{local.split('.')[0]}"
            if re.fullmatch(r"[\d.]+", local):
                cur["number"] = local
            cur["title"] = title
            sections.append(cur)
            in_examples = False
            continue
        if cur is None:
            rep.error(f"{doc.upper()}: content before the first header: {ln[:60]!r}")
            continue
        where = cur["id"]
        if ln.startswith("@penalty "):
            cur["_penalty"] = ln.split(" ", 1)[1].strip()
        elif ln.startswith("@parts "):
            cur["_parts"] = [p for p in ln.split(" ", 1)[1].strip().split(",") if p]
        elif ln.startswith("## "):
            text = ln[3:]
            role = HEADING_ROLES.get(text.strip().lower()) if doc == "ipg" else None
            blocks().append({"type": "heading", "text": text, **({"role": role} if role else {})})
            in_examples = role == "examples"
        elif ln.startswith("- "):
            item = link_refs(ln[2:], doc, ids, rep, where)
            if blocks() and blocks()[-1]["type"] == "list" and not blocks()[-1].get("ordered"):
                blocks()[-1]["items"].append(item)
            else:
                blocks().append({"type": "list", "items": [item]})
        elif re.match(r"^\d+\.\s", ln):
            item = link_refs(re.sub(r"^\d+\.\s+", "", ln), doc, ids, rep, where)
            if blocks() and blocks()[-1]["type"] == "list" and blocks()[-1].get("ordered"):
                blocks()[-1]["items"].append(item)
            else:
                blocks().append({"type": "list", "ordered": True, "items": [item]})
        elif ln.startswith("|= ") or ln.startswith("|  "):
            cells = [c.strip().replace("\\|", "|") for c in CELL_SPLIT.split(ln[3:])]
            cells = [re.sub(r"(?<!\\)\\n", "\n", link_refs(c, doc, ids, rep, where)) for c in cells]
            if ln.startswith("|= "):
                blocks().append({"type": "table", "header": cells, "rows": []})
            elif blocks() and blocks()[-1]["type"] == "table":
                blocks()[-1]["rows"].append(cells)
            else:
                rep.error(f"{where}: table row without a header")
        else:
            text = SYNTAX_ESCAPE.sub(r"\1", ln)
            if doc == "ipg" and (m := GRADE_LEAD_IN.match(text)) and (m.group(2) or m.group(3)):
                role = m.group(1).lower()
                last_heading = next((b for b in reversed(blocks()) if b["type"] == "heading"), None)
                if not (blocks() and last_heading is not None and last_heading.get("role") == role
                        and blocks()[-1]["type"] == "p"):
                    blocks().append({"type": "heading", "text": m.group(1), "role": role})
                blocks().append({"type": "p", "text": link_refs(m.group(4), doc, ids, rep, where)})
                in_examples = False
            elif in_examples and (m := LETTERED_EXAMPLE.match(text)):
                blocks().append({"type": "example", "label": m.group(1), "text": link_refs(m.group(2), doc, ids, rep, where)})
            else:
                blocks().append({"type": "p", "text": link_refs(text, doc, ids, rep, where)})

    for sec in sections:
        for b in sec.get("blocks", []):
            if b["type"] == "table" and any(len(r) != len(b["header"]) for r in b["rows"]):
                rep.error(f"{sec['id']}: table rows don't match the header width")

    tables = sum(1 for x in sections for b in x.get("blocks", []) if b["type"] == "table")
    rep.note(f"{doc.upper()}: effective {effective}; {len(sections)} sections, {tables} tables")
    return {"id": doc, "title": TXT_DOCS[doc], "effectiveDate": effective, "sections": sections}


# ---------------------------------------------------------------- IPG infractions: three sources must agree

PENALTY_ENUM = {"no penalty": "none", "none": "none", "warning": "warning", "game loss": "game_loss",
                "match loss": "match_loss", "disqualification": "disqualification"}
CATEGORY_BY_CHAPTER = {"2": "game_play_error", "3": "tournament_error", "4": "unsporting_conduct"}
INFRACTIONS_JSON = SRC / "ipg-infractions.json"


def apply_ipg_infractions(ipg: dict, rep: Report) -> None:
    """
    1) penalty printed next to each infraction header in the PDF   (sec["_penalty"], by extract_pdf_docs)
    2) Appendix A "Penalty Quick Reference" table in the same PDF
    3) rules/source/ipg-infractions.json, checked by a judge ("verified": true)
    Any disagreement is an error. Unverified entries are warnings.
    """
    secs = {s["id"]: s for s in ipg["sections"]}
    from_header = {s["id"].split("/")[1]: s.pop("_penalty") for s in ipg["sections"] if "_penalty" in s}

    # 2) Appendix A table: "Missed Trigger | None", group rows have an empty penalty
    quick: dict[str, str] = {}
    table = next((b for b in secs.get("ipg/appendix-a", {}).get("blocks", []) if b["type"] == "table"), None)
    if not table:
        rep.error("IPG: Appendix A (Penalty Quick Reference) table not found")
    else:
        for name, pen in (r[:2] for r in table["rows"]):
            if pen.strip():
                quick[norm_title(name)] = pen.strip()

    # 3) hand-checked file; create it (unverified) on first run
    if not INFRACTIONS_JSON.exists():
        seed = {"_comment": "One entry per IPG infraction. A judge checks each penalty against the official PDF "
                            "and sets verified to true. The build fails if this disagrees with the PDF."}
        for num, pen in sorted(from_header.items(), key=lambda kv: [int(x) for x in kv[0].split(".")]):
            seed[num] = {"title": secs[f"ipg/{num}"]["title"], "penalty": pen, "verified": False}
        INFRACTIONS_JSON.write_text(json.dumps(seed, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        rep.warn(f"created {INFRACTIONS_JSON.relative_to(ROOT)} from the PDF: every entry needs a judge's check")
    curated = {k: v for k, v in json.loads(INFRACTIONS_JSON.read_text(encoding="utf-8")).items() if not k.startswith("_")}

    for num in sorted(set(from_header) | set(curated), key=lambda n: [int(x) for x in n.split(".")]):
        sid = f"ipg/{num}"
        sec = secs.get(sid)
        if not sec:
            rep.error(f"ipg-infractions.json: {num} is not a section of the current IPG")
            continue
        header = from_header.get(num)
        entry = curated.get(num)
        title = sec["title"]
        short = norm_title(title.split("—")[-1])
        in_quick = quick.get(short) or quick.get(norm_title(title))
        values = {"PDF header": header, "Appendix A": in_quick, "ipg-infractions.json": entry and entry.get("penalty")}
        enums = {k: PENALTY_ENUM.get((v or "").strip().lower()) for k, v in values.items()}
        missing = [k for k, v in values.items() if not v]
        if missing:
            rep.error(f"{sid} {title!r}: penalty missing in {', '.join(missing)}")
            continue
        if None in enums.values() or len(set(enums.values())) != 1:
            rep.error(f"{sid} {title!r}: sources disagree: {values}")
            continue
        if not entry.get("verified"):
            rep.warn(f"{sid} {title!r}: penalty {header!r} not yet verified by a judge in ipg-infractions.json")
        category = CATEGORY_BY_CHAPTER.get(num.split(".")[0])
        if not category:
            rep.error(f"{sid}: infraction outside chapters 2-4")
            continue
        sec["infraction"] = {"category": category, "penalty": enums["PDF header"]}
    for sec in ipg["sections"]:
        expected = sec.pop("_parts", None)
        if "infraction" in sec:
            roles = [b.get("role") for b in sec.get("blocks", []) if b["type"] == "heading"]
            if expected is None:
                rep.error(f"{sec['id']}: no @parts line (re-run extract_pdf_docs.py)")
            elif sorted(set(expected)) != sorted({r for r in roles if r}):
                rep.error(f"{sec['id']}: parts in the PDF {expected} but labelled {[r for r in roles if r]}")
            if "definition" not in roles:
                rep.error(f"{sec['id']}: infraction without a Definition")
            labels = [b.get("label") for b in sec.get("blocks", []) if b["type"] == "example"]
            if labels != [chr(ord("A") + i) for i in range(len(labels))]:
                rep.error(f"{sec['id']}: example labels not A, B, C…: {labels} (merged or missing example?)")
            if roles and roles[0] != "definition":
                rep.error(f"{sec['id']}: infraction doesn't start with its Definition ({roles[:3]})")
    parts = {r: sum(1 for x in ipg["sections"] if "infraction" in x for b in x.get("blocks", [])
                    if b["type"] == "heading" and b.get("role") == r)
             for r in ("definition", "examples", "philosophy", "additional_remedy", "upgrade", "downgrade")}
    rep.note(f"IPG parts: {parts}")
    rep.note(f"IPG: {sum(1 for s in ipg['sections'] if 'infraction' in s)} infractions, penalties cross-checked "
             f"(PDF header / Appendix A / ipg-infractions.json)")


def norm_title(s: str) -> str:
    return re.sub(r"\s+", " ", s.replace("—", "-").replace("–", "-")).strip().lower()


# ---------------------------------------------------------------- checks (mirror of RulesBundleValidator)

ID_RE = re.compile(r"^(cr|mtr|ipg|jar)/[a-z0-9][a-z0-9.\-]*$")
LINK_RE = re.compile(r"(?<!\\)\[([^\]]+)\]\(([^)]+)\)")
EMPH_RE = re.compile(r"(?<!\\)(\*\*|\*)(?=\S)(.+?)(?<=\S)(?<!\\)\1")
BARE_STAR = re.compile(r"(?<!\\)\*")


def block_texts(b: dict) -> list[str]:
    if b["type"] == "list":
        return b["items"]
    if b["type"] == "table":
        return b["header"] + [c for r in b["rows"] for c in r]
    return [b["text"]]


def check(bundle: dict, rep: Report) -> None:
    all_ids = {s["id"] for d in bundle["documents"] for s in d["sections"]}
    for d in bundle["documents"]:
        seen, parents = set(), {s.get("parentId") for s in d["sections"]}
        for s in d["sections"]:
            sid = s["id"]
            if not ID_RE.match(sid) or not sid.startswith(d["id"] + "/"):
                rep.error(f"{sid}: bad id")
            if sid in seen:
                rep.error(f"{sid}: duplicate id")
            if s.get("parentId") and s["parentId"] not in seen:
                rep.error(f"{sid}: parent {s['parentId']} missing or after child")
            seen.add(sid)
            if not s.get("blocks") and sid not in parents and not s.get("title"):
                rep.error(f"{sid}: empty leaf section without a title")
            elif not s.get("blocks") and sid not in parents:
                rep.note(f"{sid}: heading with no content in the source ({s['title']})")
            for b in s.get("blocks", []):
                if b["type"] == "table" and any(not c.strip() for c in b["header"]):
                    rep.error(f"{sid}: table header with a blank cell")
                for t in block_texts(b):
                    if not t.strip() and b["type"] != "table":
                        rep.error(f"{sid}: blank text in {b['type']}")
                    for target in (m.group(2) for m in LINK_RE.finditer(t)):
                        if target.startswith("ref:") and target[4:] not in all_ids:
                            rep.error(f"{sid}: broken ref {target}")
                    if BARE_STAR.search(EMPH_RE.sub(r"\2", LINK_RE.sub(r"\1", t))):
                        rep.error(f"{sid}: unescaped '*'")


# ---------------------------------------------------------------- main

def next_bundle_version() -> int:
    """yyyyMMddNN, always above the bundle currently in assets (the app only imports a HIGHER version)."""
    today = int(dt.date.today().strftime("%Y%m%d") + "01")
    try:
        current = json.loads(OUT.read_text(encoding="utf-8"))["bundleVersion"]
    except (FileNotFoundError, KeyError, ValueError):
        return today
    return max(today, current + 1)


def current_min_version_code() -> int:
    """minVersionCode is set by hand (--min-version-code) and survives every rebuild."""
    try:
        return int(json.loads(MANIFEST.read_text(encoding="utf-8")).get("minVersionCode", 0))
    except (FileNotFoundError, ValueError):
        return 0


def write_manifest(bundle: dict, min_version_code: int | None = None) -> None:
    manifest = {
        "schemaVersion": bundle["schemaVersion"],
        "bundleVersion": bundle["bundleVersion"],
        "generatedAt": bundle.get("generatedAt"),
        "documents": {d["id"]: d["effectiveDate"] for d in bundle["documents"]},
        # Apps with a lower versionCode get the mandatory in-app update (once merged to main).
        "minVersionCode": current_min_version_code() if min_version_code is None else min_version_code,
    }
    MANIFEST.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"Wrote {MANIFEST.relative_to(ROOT)}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--bundle-version", type=int,
                    help="yyyyMMddNN; default: today's date + 01")
    ap.add_argument("--check", action="store_true", help="build and report, but don't write the asset")
    ap.add_argument("--manifest-only", action="store_true",
                    help="only (re)write manifest.json from the existing bundle.json (no rebuild, no version bump)")
    ap.add_argument("--min-version-code", type=int,
                    help="force apps below this versionCode to update (kept in later rebuilds); 0 = none")
    args = ap.parse_args()
    if args.manifest_only:
        write_manifest(json.loads(OUT.read_text(encoding="utf-8")), args.min_version_code)
        return 0

    rep = Report()
    sources = json.loads((SRC / "sources.json").read_text(encoding="utf-8")) if (SRC / "sources.json").exists() else {}

    docs = []
    if (SRC / "cr.txt").exists():
        cr = parse_cr(SRC / "cr.txt", rep)
        cr["sourceUrl"] = sources.get("cr", {}).get("url", "https://magic.wizards.com/en/rules")
        cr["sourceSha256"] = hashlib.sha256((SRC / "cr.txt").read_bytes()).hexdigest()
        docs.append(cr)
    else:
        rep.error("rules/source/cr.txt missing: run fetch_sources.py")
    cr_ids = {sec["id"] for d in docs for sec in d["sections"]}
    for name in ("mtr", "ipg", "jar"):
        txt = SRC / f"{name}.txt"
        if not txt.exists():
            rep.error(f"rules/source/{name}.txt missing: run fetch_sources.py, then extract_pdf_docs.py")
            continue
        doc = parse_txt_doc(name, txt, rep, cr_ids)
        cr_ids |= {sec["id"] for sec in doc["sections"]}          # later docs may link into earlier ones
        if name == "ipg":
            apply_ipg_infractions(doc, rep)
        doc["sourceUrl"] = sources.get(name, {}).get("url", "https://wpn.wizards.com/en/rules-documents")
        if (SRC / f"{name}.pdf").exists():
            doc["sourceSha256"] = hashlib.sha256((SRC / f"{name}.pdf").read_bytes()).hexdigest()
        docs.append(doc)

    # fixed key order inside each document for stable diffs
    order = ["id", "title", "effectiveDate", "sourceUrl", "sourceSha256", "sections", "glossary"]
    docs = [{k: d[k] for k in order if k in d} for d in docs]

    version = args.bundle_version or next_bundle_version()
    bundle = {
        "schemaVersion": SCHEMA_VERSION,
        "bundleVersion": version,
        "generatedAt": dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "documents": docs,
    }
    check(bundle, rep)

    lines = [f"# Rules bundle build report", "", f"bundleVersion {version}", ""]
    lines += ["## Errors"] + [f"- {e}" for e in rep.errors or ["none"]] + [""]
    lines += ["## Summary"] + [f"- {i}" for i in rep.info] + [""]
    lines += [f"## Warnings ({len(rep.warnings)})"] + [f"- {w}" for w in rep.warnings or ["none"]]
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print("\n".join(lines[:4] + ["## Errors"] + [f"- {e}" for e in rep.errors[:30] or ['none']]
                    + ["", "## Summary"] + [f"- {i}" for i in rep.info]
                    + ["", f"{len(rep.warnings)} warning(s): see {REPORT.relative_to(ROOT)}"]))
    if rep.errors:
        print("\nNOT written: fix the errors above.", file=sys.stderr)
        return 1
    if args.check:
        return 0
    OUT.parent.mkdir(parents=True, exist_ok=True)
    # one section per line: readable git diffs when a rule changes
    with OUT.open("w", encoding="utf-8", newline="\n") as f:
        head = {k: v for k, v in bundle.items() if k != "documents"}
        f.write("{\n")
        for k, v in head.items():
            f.write(f"  {json.dumps(k)}: {json.dumps(v, ensure_ascii=False)},\n")
        f.write('  "documents": [\n')
        for di, d in enumerate(docs):
            f.write("    {\n")
            keys = list(d.keys())
            for ki, k in enumerate(keys):
                comma = "," if ki < len(keys) - 1 else ""
                if k in ("sections", "glossary"):
                    f.write(f"      {json.dumps(k)}: [\n")
                    for ii, item in enumerate(d[k]):
                        c = "," if ii < len(d[k]) - 1 else ""
                        f.write(f"        {json.dumps(item, ensure_ascii=False)}{c}\n")
                    f.write(f"      ]{comma}\n")
                else:
                    f.write(f"      {json.dumps(k)}: {json.dumps(d[k], ensure_ascii=False)}{comma}\n")
            f.write("    }" + ("," if di < len(docs) - 1 else "") + "\n")
        f.write("  ]\n}\n")
    print(f"\nWrote {OUT.relative_to(ROOT)} ({OUT.stat().st_size // 1024} KB)")
    write_manifest(bundle, args.min_version_code)
    return 0


if __name__ == "__main__":
    sys.exit(main())
