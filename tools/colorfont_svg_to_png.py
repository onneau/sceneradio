#!/usr/bin/env python3
"""Zet een SVG-spritesheet van Stone Oakvalley's Amiga ColorFonts Generator (SOCFG) om naar een PNG.

De SVG bestaat uit één <rect> per pixel (`x`, `y`, `fill`). Het resultaat is een transparante PNG met
hetzelfde 16x16 rooster (teken = rij * 16 + kolom). De app leest ze uit app/src/main/assets/fonts/.

Gebruik:  python3 tools/colorfont_svg_to_png.py spritesheet.svg uitvoer.png
Lettertypes in gebruik (Original-palet):
  metallix.png  Metallix 36.16c, cel 36x36   (scroller, artiest en titel)
  forest.png    Forest 32.8c,    cel 25x32   (overige tekst)
  scopex.png    Scopexfont 35.16c, cel 32x35 (zendernaam)
"""
import re
import sys

from PIL import Image

RECT = re.compile(r'<rect width="[\d.]+" height="[\d.]+" x="(\d+)" y="(\d+)" fill="#([0-9a-fA-F]{6})"')


def convert(svg_path, png_path):
    text = open(svg_path, encoding="utf-8").read()
    size = re.search(r'width="(\d+)" height="(\d+)"', text)
    image = Image.new("RGBA", (int(size.group(1)), int(size.group(2))), (0, 0, 0, 0))
    pixels = image.load()
    for x, y, color in RECT.findall(text):
        pixels[int(x), int(y)] = (int(color[0:2], 16), int(color[2:4], 16), int(color[4:6], 16), 255)
    image.save(png_path, optimize=True)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    convert(sys.argv[1], sys.argv[2])
