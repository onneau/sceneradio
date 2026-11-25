#!/usr/bin/env python3
"""Maakt het app-icoon van SceneRadio: een S uit het Scopex-lettertype in felle chroom (wit, cyaan, blauw) met een
donkere rand, op een paars-naar-magenta achtergrond met Amiga-koperbalken. De S is bewust licht en heeft een rand:
zo valt hij niet weg tegen de achtergrond en blijft hij ook op klein formaat een S.

Gebruik (vanuit de hoofdmap van het project, vereist Pillow):
    python3 tools/make_icon.py

Schrijft:
    app/src/main/res/drawable-nodpi/ic_launcher_r.png      de S (432 x 432, 108 dp op xxxhdpi; de bestandsnaam is
                                                           historisch en blijft zo, zodat de verwijzingen kloppen)
    app/src/main/res/drawable/ic_launcher_foreground.xml   verwijst naar die PNG
    app/src/main/res/drawable/ic_launcher_background.xml   paarse achtergrond met vier koperbalken (vector)
    app/src/main/res/drawable-xhdpi/tv_banner.png          de banner voor Android TV (640 x 360): dezelfde opzet, breder
De adaptive-icon bestanden in mipmap-anydpi-v26 blijven ongewijzigd.
"""
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
SHEET = ROOT / "app/src/main/assets/fonts/scopex.png"
CELL_W, CELL_H = 32, 35          # Scopex: 16 x 16 tekens, index = tekencode
SIZE = 432                       # 108 dp * 4
SCALE = 6                        # elk beeldpunt van de S wordt 6 x 6: past binnen het veilige midden van het icoon
BASE = "#000014"
LETTER = "S"
SKY = [(0.0, "#1A0536"), (0.5, "#5B1A8F"), (1.0, "#B02FB0")]   # paars verloop van boven naar beneden
SKY_BANDS = 18                   # het verloop in 18 banden, zoals een Amiga-palet (6 dp per band)
OUTLINE = (0x00, 0x00, 0x0F)     # rand om de S
SHADOW = (0x00, 0x0A, 0x2E)      # schaduw: de rand, 1 beeldpunt van de S rechtsonder
# Chroom van boven naar beneden: wit, cyaan, blauw, donkere horizon, en weer licht naar de onderrand.
CHROME = [(0.0, "#FFFFFF"), (0.22, "#B8F6FF"), (0.46, "#3FA0F0"), (0.5, "#0F3C96"),
          (0.56, "#1F66C8"), (0.8, "#7FEAFF"), (1.0, "#FFFFFF")]
SHADE = [6, 5, 3, 2, 0, 0, 2, 3, 5, 6]   # kleurindex per regel van een balk (0 = lichtst, 6 = donkerst)
GOLD = ["FFF3B0", "FFE070", "FFC22E", "E0A020", "B87818", "8A5410", "5A3208"]
COPPER = ["FFD2A8", "F0A070", "D07838", "B05A22", "8A4012", "62290A", "3C1606"]
LINE = 3                         # dikte van een balkregel in beeldpunten van de 432-versie
BARS = [(60, GOLD), (94, COPPER), (SIZE - 94, GOLD), (SIZE - 60, COPPER)]   # middelpunt (px), kleurreeks


def scopex_letter():
    sheet = Image.open(SHEET).convert("RGBA")
    px = sheet.load()
    # hoogte van het lettertype = laagste ingekleurde regel over alle tekens (zoals de app het ook bepaalt)
    height = 0
    for code in range(256):
        ox, oy = (code % 16) * CELL_W, (code // 16) * CELL_H
        for y in range(CELL_H):
            if any(px[ox + x, oy + y][3] for x in range(CELL_W)):
                height = max(height, y + 1)
    code = ord(LETTER)
    ox, oy = (code % 16) * CELL_W, (code // 16) * CELL_H
    cols = [x for x in range(CELL_W) if any(px[ox + x, oy + y][3] for y in range(CELL_H))]
    return sheet.crop((ox + min(cols), oy, ox + max(cols) + 1, oy + height))


def hexcolor(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def gradient(stops, t):
    """Kleur op positie t (0..1) in een lijst (positie, kleur)."""
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            a, b = hexcolor(c0), hexcolor(c1)
            f = (t - t0) / (t1 - t0) if t1 > t0 else 0
            return tuple(round(a[i] + (b[i] - a[i]) * f) for i in range(3))
    return hexcolor(stops[-1][1])


def foreground():
    glyph = scopex_letter()
    alpha = glyph.split()[3]
    w, h = glyph.size

    # S in chroom: elke rij een eigen kleur uit het verloop.
    fill = Image.new("RGBA", (w, h))
    pixels = fill.load()
    for y in range(h):
        colour = gradient(CHROME, y / (h - 1)) + (255,)
        for x in range(w):
            pixels[x, y] = colour
    fill.putalpha(alpha)

    # Rand: de S 1 beeldpunt dikker in alle richtingen, met een schaduw 1 beeldpunt rechtsonder.
    pad = 3
    size = (w + 2 * pad, h + 2 * pad)
    edge = Image.new("L", size, 0)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            shifted = Image.new("L", size, 0)
            shifted.paste(alpha, (pad + dx, pad + dy))
            edge = ImageChops.lighter(edge, shifted)
    shadow_mask = Image.new("L", size, 0)
    shadow_mask.paste(edge, (1, 1))

    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    shadow = Image.new("RGBA", size, SHADOW + (255,))
    shadow.putalpha(shadow_mask)
    layer.alpha_composite(shadow)
    outline = Image.new("RGBA", size, OUTLINE + (255,))
    outline.putalpha(edge)
    layer.alpha_composite(outline)
    layer.alpha_composite(fill, (pad, pad))

    big = layer.resize((layer.width * SCALE, layer.height * SCALE), Image.NEAREST)
    out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    out.alpha_composite(big, ((SIZE - big.width) // 2, (SIZE - big.height) // 2))
    return out


def background_xml():
    rows = [
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="108dp"',
        '    android:height="108dp"',
        '    android:viewportWidth="108"',
        '    android:viewportHeight="108">',
        f'    <path android:fillColor="{BASE}" android:pathData="M0,0h108v108h-108z"/>',
    ]
    dp = SIZE / 108.0
    band = 108 / SKY_BANDS
    for i in range(SKY_BANDS):
        r, g, b = gradient(SKY, i / (SKY_BANDS - 1))
        rows.append(
            f'    <path android:fillColor="#{r:02X}{g:02X}{b:02X}" '
            f'android:pathData="M0,{i * band:g}h108v{band:g}h-108z"/>'
        )
    for center, ramp in BARS:
        for i, shade in enumerate(SHADE):
            top = (center + (i - len(SHADE) // 2) * LINE) / dp
            rows.append(
                f'    <path android:fillColor="#{ramp[shade]}" '
                f'android:pathData="M0,{top:g}h108v{LINE / dp:g}h-108z"/>'
            )
    rows.append("</vector>")
    return "\n".join(rows) + "\n"


BANNER = (640, 360)              # Android TV: 320 x 180 dp op xhdpi


def banner():
    """De banner voor Android TV: dezelfde achtergrond, koperbalken en S als het icoon, op 16:9."""
    w, h = BANNER
    img = Image.new("RGBA", BANNER, hexcolor(BASE) + (255,))
    draw = ImageDraw.Draw(img)
    for i in range(SKY_BANDS):
        top, bottom = round(i * h / SKY_BANDS), round((i + 1) * h / SKY_BANDS)
        draw.rectangle([0, top, w, bottom], fill=gradient(SKY, i / (SKY_BANDS - 1)) + (255,))
    for center, ramp in ((50, GOLD), (78, COPPER), (h - 78, GOLD), (h - 50, COPPER)):
        for i, shade in enumerate(SHADE):
            top = center + (i - len(SHADE) // 2) * LINE
            draw.rectangle([0, top, w, top + LINE - 1], fill=hexcolor(ramp[shade]) + (255,))
    letter = foreground()
    img.paste(letter, ((w - SIZE) // 2, (h - SIZE) // 2), letter)
    return img


FOREGROUND_XML = """<?xml version="1.0" encoding="utf-8"?>
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/ic_launcher_r"
    android:gravity="fill" />
"""


def main():
    (RES / "drawable-nodpi").mkdir(parents=True, exist_ok=True)
    (RES / "drawable").mkdir(parents=True, exist_ok=True)
    foreground().save(RES / "drawable-nodpi/ic_launcher_r.png", optimize=True)
    (RES / "drawable/ic_launcher_foreground.xml").write_text(FOREGROUND_XML, encoding="utf-8")
    (RES / "drawable/ic_launcher_background.xml").write_text(background_xml(), encoding="utf-8")
    (RES / "drawable-xhdpi").mkdir(parents=True, exist_ok=True)
    banner().save(RES / "drawable-xhdpi/tv_banner.png", optimize=True)
    print("Icoon en TV-banner geschreven in", RES)


if __name__ == "__main__":
    main()
