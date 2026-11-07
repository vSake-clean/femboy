#!/usr/bin/env python3
"""Rysuje płaskie podglądy (2.5D) ze skinów PNG do galerii w README.

Nie uruchamia Minecrafta – składa front postaci wprost z tekstury 64x64
(głowa, tułów, ręce, nogi + warstwy nakładki), tak jak obrazki w docs/img.
Użycie: python3 -I tools/render_skins.py
"""
import os
import random
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKINS = os.path.join(ROOT, "skins")
OUT = os.path.join(ROOT, "docs", "img")

# prostokąty "front" w teksturze 64x64: (x, y, w, h)
FRONT = {
    "head": (8, 8, 8, 8),
    "body": (20, 20, 8, 12),
    "rarm": (44, 20, 4, 12),
    "larm": (36, 52, 4, 12),
    "rleg": (4, 20, 4, 12),
    "lleg": (20, 52, 4, 12),
}
OVER = {  # warstwy nakładki (kapelusz, bluza, rękawy, nogawki)
    "head": (40, 8, 8, 8),
    "body": (20, 36, 8, 12),
    "rarm": (44, 36, 4, 12),
    "larm": (52, 52, 4, 12),
    "rleg": (4, 36, 4, 12),
    "lleg": (4, 52, 4, 12),
}


def crop(img, box):
    x, y, w, h = box
    return img.crop((x, y, x + w, y + h))


def avatar(path, scale=6):
    """Składa front postaci jako obrazek RGBA (16 wide * 20 tall w px tekstury)."""
    tex = Image.open(path).convert("RGBA")
    if tex.height == 32:  # stary format 64x32 → podwój dół z górnych kończyn
        big = Image.new("RGBA", (64, 64))
        big.paste(tex, (0, 0))
        for dst, src in (((16, 48), (0, 16)), ((32, 48), (40, 16))):
            big.paste(tex.crop((src[0], src[1], src[0] + 16, src[1] + 16)), dst)
        tex = big
    slim = path.endswith("_slim.png")
    aw = 3 if slim else 4
    # szerokość: ręka + ciało(8) + ręka ; wysokość: głowa(8)+tułów(12)
    W, H = aw + 8 + aw, 8 + 12
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    def place(part, dx, dy):
        base = crop(tex, FRONT[part])
        over = crop(tex, OVER[part])
        if part in ("rarm", "larm") and slim:
            base = base.crop((0, 0, 3, 12))
            over = over.crop((0, 0, 3, 12))
        canvas.alpha_composite(base, (dx, dy))
        canvas.alpha_composite(over, (dx, dy))

    bx = aw  # start ciała po prawej ręce
    place("rarm", bx - aw, 8)
    place("larm", bx + 8, 8)
    place("rleg", bx, 20 - 0)  # nogi pod tułowiem (ale H=20, więc doklejamy niżej)
    # powiększ płótno o nogi
    full = Image.new("RGBA", (W, H + 12), (0, 0, 0, 0))
    full.alpha_composite(canvas, (0, 0))

    def place_full(part, dx, dy):
        base = crop(tex, FRONT[part])
        over = crop(tex, OVER[part])
        full.alpha_composite(base, (dx, dy))
        full.alpha_composite(over, (dx, dy))

    place_full("body", bx, 8)
    place_full("rleg", bx, 20)
    place_full("lleg", bx + 4, 20)
    place_full("head", bx, 0)
    place_full("rarm", bx - aw, 8)
    place_full("larm", bx + 8, 8)
    if slim:  # dorysuj chude ręce na wierzchu (żeby nie wystawały)
        pass
    out = full.resize((full.width * scale, full.height * scale), Image.NEAREST)
    return out


def grid(paths, cols, scale=6, pad=10, bg=(255, 255, 255, 255)):
    avs = [avatar(p, scale) for p in paths]
    cw = max(a.width for a in avs)
    ch = max(a.height for a in avs)
    rows = (len(avs) + cols - 1) // cols
    W = cols * cw + (cols + 1) * pad
    H = rows * ch + (rows + 1) * pad
    img = Image.new("RGBA", (W, H), bg)
    for i, a in enumerate(avs):
        r, c = divmod(i, cols)
        x = pad + c * (cw + pad) + (cw - a.width) // 2
        y = pad + r * (ch + pad) + (ch - a.height) // 2
        img.alpha_composite(a, (x, y))
    return img


def pick(folder, n, seed):
    files = sorted(
        os.path.join(SKINS, folder, f)
        for f in os.listdir(os.path.join(SKINS, folder))
        if f.endswith(".png")
    )
    random.Random(seed).shuffle(files)
    return files[:n]


def main():
    os.makedirs(OUT, exist_ok=True)
    fem = pick("femboy", 16, 7)
    egi = pick("egirl", 16, 7)
    mix = []
    for a, b in zip(fem, egi):
        mix.append(a)
        mix.append(b)
    grid(mix, 8, scale=6).save(os.path.join(OUT, "gallery-big.png"))
    grid(pick("femboy", 9, 3), 9, scale=7).save(os.path.join(OUT, "femboy-row2.png"))
    grid(pick("egirl", 9, 5), 9, scale=7).save(os.path.join(OUT, "egirl-row2.png"))
    grid(pick("catboy", 9, 2), 9, scale=7).save(os.path.join(OUT, "catboy-row.png"))
    print("zapisano: gallery-big.png, femboy-row2.png, egirl-row2.png, catboy-row.png →", OUT)


if __name__ == "__main__":
    sys.exit(main())
