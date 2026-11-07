#!/usr/bin/env python3
"""Składa zrzuty z gry (docs/img/game/, robione przez ./gradlew runShots) w galerie do README.

Wycina postać ze środka każdego zdjęcia, powiększa i podpisuje nazwą zestawu.
Użycie: python3 -I tools/make_game_gallery.py
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GAME = os.path.join(ROOT, "docs", "img", "game")
OUT = os.path.join(ROOT, "docs", "img")
LABELS = {"catboy": "catboy", "fox": "lisek", "devil": "diabełek", "angel": "aniołek", "fairy": "wróżka",
          "bear": "miś", "bunny": "króliczek", "puppy": "puppy", "wolf": "wilczek", "femboy": "femboy"}


def font(size):
    p = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    return ImageFont.truetype(p, size) if os.path.exists(p) else ImageFont.load_default()


def crop_player(path):
    im = Image.open(path).convert("RGB")
    w, h = im.size
    cw, ch = int(w * 0.17), int(h * 0.56)
    cx, cy = w // 2, int(h * 0.56)
    return im.crop((cx - cw // 2, cy - ch // 2, cx + cw // 2, cy + ch // 2))


def sheet(prefix, names, cols, out_name, scale=2):
    tiles = []
    for n in names:
        f = os.path.join(GAME, f"{prefix}-{n}.png")
        if os.path.exists(f):
            tiles.append((LABELS.get(n, n), crop_player(f)))
    if not tiles:
        return
    tw, th = tiles[0][1].size
    tw, th = tw * scale, th * scale
    lab = 30
    rows = (len(tiles) + cols - 1) // cols
    img = Image.new("RGB", (cols * tw, rows * (th + lab)), (24, 20, 28))
    d = ImageDraw.Draw(img)
    f = font(20)
    for i, (label, t) in enumerate(tiles):
        r, c = divmod(i, cols)
        x, y = c * tw, r * (th + lab)
        img.paste(t.resize((tw, th), Image.NEAREST), (x, y))
        d.text((x + tw / 2, y + th + lab / 2), label, font=f, fill=(255, 143, 190), anchor="mm")
    img.save(os.path.join(OUT, out_name))
    print("zapisano", out_name, img.size)


def main():
    sheet("front", ["catboy", "fox", "devil", "angel", "fairy", "bear", "bunny", "puppy", "wolf", "femboy"], 5, "game-kits.png")
    sheet("back", ["angel", "devil", "fairy", "fox"], 4, "game-back.png")
    for i in range(4):
        f = os.path.join(GAME, f"gui-{i}.png")
        if os.path.exists(f):
            Image.open(f).convert("RGB").save(os.path.join(OUT, f"game-gui-{i}.png"))


if __name__ == "__main__":
    sys.exit(main())
