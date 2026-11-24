#!/usr/bin/env python3
#
#    Copyright (c) 2024-2026 SOPTIM AG
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#
"""Renders the app icons and the splash screen.

Draws the mark itself (BLUE, LETTERS and mark()): a copy of frontend/static/favicon.svg, which it
does not read. A change to the mark has to be made in both.

Writes the icons and the splash screen of the desktop app (desktop/icons/) and the icons of the web
app manifest (frontend/static/icons/). The results are committed; run this again only when the
mark changes.

Needs Inkscape on the PATH and Pillow (pip install pillow). The mark sets its letters in
Helvetica Neue or Arial; without either, fontconfig substitutes Liberation Sans.
"""

import io
import pathlib
import subprocess
import tempfile

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
DESKTOP_ICONS = ROOT / "desktop" / "icons"
WEB_ICONS = ROOT / "frontend" / "static" / "icons"

BLUE = "#2a6ebb"
LETTERS = (
    '<text x="32" y="32" text-anchor="middle" dominant-baseline="central"'
    ' font-family="Helvetica Neue, Arial, sans-serif" font-weight="700"'
    ' font-size="{size}" letter-spacing="-1" fill="#ffffff">RA</text>'
)


def mark(inset=0.0, radius=12, letters=28, full_bleed=False):
    """The mark: a blue rounded square with the letters RA, optionally inset on a clear canvas."""
    if full_bleed:
        square = f'<rect x="0" y="0" width="64" height="64" fill="{BLUE}"/>'
    else:
        square = (
            f'<rect x="2" y="2" width="60" height="60" rx="{radius}" ry="{radius}" '
            f'fill="{BLUE}"/>'
        )
    body = square + LETTERS.format(size=letters)
    if inset:
        scale = 1 - 2 * inset
        body = f'<g transform="translate({64 * inset} {64 * inset}) scale({scale})">{body}</g>'
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">{body}</svg>'


SPLASH = f"""<svg xmlns="http://www.w3.org/2000/svg" width="480" height="240" viewBox="0 0 480 240">
  <rect width="480" height="240" fill="#ffffff"/>
  <rect x="0.5" y="0.5" width="479" height="239" fill="none" stroke="#e0e0e0"/>
  <g transform="translate(40 72) scale(1.5)">
    <rect x="2" y="2" width="60" height="60" rx="12" ry="12" fill="{BLUE}"/>
    {LETTERS.format(size=28)}
  </g>
  <text x="152" y="112" font-family="Helvetica Neue, Arial, sans-serif" font-size="34"
        fill="#303030"><tspan font-weight="700" fill="#1f75cb" letter-spacing="2">RDFA</tspan><tspan
        font-weight="600">rchitect</tspan></text>
  <text x="152" y="148" font-family="Helvetica Neue, Arial, sans-serif" font-size="15"
        fill="#787878">Starting…</text>
</svg>
"""


def render(svg, size, height=None):
    with tempfile.TemporaryDirectory() as tmp:
        source = pathlib.Path(tmp) / "icon.svg"
        target = pathlib.Path(tmp) / "icon.png"
        source.write_text(svg, encoding="utf-8")
        command = [
            "inkscape",
            str(source),
            "--export-type=png",
            f"--export-filename={target}",
            f"--export-width={size}",
        ]
        if height:
            command.append(f"--export-height={height}")
        subprocess.run(command, check=True, capture_output=True)
        return Image.open(io.BytesIO(target.read_bytes())).convert("RGBA")


def main():
    DESKTOP_ICONS.mkdir(parents=True, exist_ok=True)
    WEB_ICONS.mkdir(parents=True, exist_ok=True)

    plain = mark()
    # macOS draws app icons inside a grid that leaves a margin around the artwork.
    padded = mark(inset=0.1)
    maskable = mark(full_bleed=True, letters=22)

    render(plain, 512).save(DESKTOP_ICONS / "rdfarchitect.png")

    ico_sizes = [16, 24, 32, 48, 64, 128, 256]
    largest = render(plain, 256)
    largest.save(
        DESKTOP_ICONS / "rdfarchitect.ico",
        sizes=[(s, s) for s in ico_sizes],
        append_images=[render(plain, s) for s in ico_sizes[:-1]],
    )

    render(padded, 1024).save(DESKTOP_ICONS / "rdfarchitect.icns")

    render(SPLASH, 480, 240).convert("RGB").save(DESKTOP_ICONS / "splash.png")

    render(plain, 192).save(WEB_ICONS / "icon-192.png")
    render(plain, 512).save(WEB_ICONS / "icon-512.png")
    render(maskable, 512).save(WEB_ICONS / "icon-maskable-512.png")
    render(mark(full_bleed=True), 180).convert("RGB").save(WEB_ICONS / "apple-touch-icon.png")


if __name__ == "__main__":
    main()
