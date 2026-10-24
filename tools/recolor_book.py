"""Recolors a copy of the vanilla book texture for the Codex.

Edit TINT_DARK / TINT_LIGHT to change the palette, then run:
    python3 tools/recolor_book.py
The original texture is kept untouched as book_original.png.
"""
from PIL import Image

SRC = "src/main/resources/assets/eldritch/textures/item/book_original.png"
DST = "src/main/resources/assets/eldritch/textures/item/codex.png"

TINT_DARK = (38, 12, 62)     # shadows
TINT_LIGHT = (196, 140, 255)  # highlights


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def main():
    img = Image.open(SRC).convert("RGBA")
    out = Image.new("RGBA", img.size)
    px = img.load()
    po = out.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, a = px[x, y]
            if a == 0:
                po[x, y] = (0, 0, 0, 0)
                continue
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            rgb = mix(TINT_DARK, TINT_LIGHT, lum)
            po[x, y] = (rgb[0], rgb[1], rgb[2], a)
    out.save(DST)
    print("wrote", DST)


if __name__ == "__main__":
    main()
