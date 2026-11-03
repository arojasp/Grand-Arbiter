#!/usr/bin/env python3
"""
Google Play feature graphic (1024 x 500, PNG, no alpha) in the Tinta palette.

Uses the same H2 mark as the app icon (imported from build_icons.py) so both stay in sync.
Fonts: Inter / Inter Display (OFL), installed on the system; cairosvg finds them by family name.

  branding/play-feature-graphic.png          dark (default listing)
  branding/play-feature-graphic-light.png    light alternative

Usage: python tools/branding/build_feature_graphic.py   (needs shapely, cairosvg, pillow)
"""
from __future__ import annotations

import io
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_icons import BRAND, mark, path_data  # noqa: E402

W, H = 1024, 500
TITLE = "Grand Arbiter"
TAGLINE = "The judge's companion at the table"
DOCS = "\u2002·\u2002".join(["CR", "MTR", "IPG", "JAR", "Oracle"])

THEMES = {
    "dark": dict(bg="#121212", ink="#EDEDED", text="#EDEDED", tagline="#A3A3A3", docs="#8C8C8C"),
    "light": dict(bg="#FAFAF7", ink="#171717", text="#171717", tagline="#5E5E58", docs="#8A8A84"),
}

# The mark: 108-unit icon space, centred on (54, 54); placed left of centre.
MARK_SCALE, MARK_CX, MARK_CY = 4.0, 212, 250
TEXT_X = 400


def feature_svg(t: dict) -> str:
    d = path_data(mark())
    tx, ty = MARK_CX - 54 * MARK_SCALE, MARK_CY - 54 * MARK_SCALE
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<rect width="{W}" height="{H}" fill="{t["bg"]}"/>
<g transform="translate({tx} {ty}) scale({MARK_SCALE})"><path d="{d}" fill="{t["ink"]}" fill-rule="evenodd"/></g>
<text x="{TEXT_X}" y="230" font-family="Inter Display" font-weight="700" font-size="76" letter-spacing="-1.5" fill="{t["text"]}">{TITLE}</text>
<text x="{TEXT_X + 2}" y="288" font-family="Inter" font-weight="400" font-size="30" fill="{t["tagline"]}">{TAGLINE.replace("'", "&#8217;")}</text>
<text x="{TEXT_X + 2}" y="336" font-family="Inter" font-weight="500" font-size="21" letter-spacing="0.5" fill="{t["docs"]}">{DOCS}</text>
</svg>'''


def main() -> int:
    import cairosvg
    from PIL import Image

    BRAND.mkdir(parents=True, exist_ok=True)
    for name, t in THEMES.items():
        png = cairosvg.svg2png(bytestring=feature_svg(t).encode(), output_width=W, output_height=H)
        out = BRAND / ("play-feature-graphic.png" if name == "dark" else f"play-feature-graphic-{name}.png")
        Image.open(io.BytesIO(png)).convert("RGB").save(out, optimize=True)   # Play: no alpha
        print("wrote", out.relative_to(BRAND.parent))
    return 0


if __name__ == "__main__":
    sys.exit(main())
