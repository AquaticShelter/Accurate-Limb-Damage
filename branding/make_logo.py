"""Generates the mod logo and banner. Run: python make_logo.py  (needs Pillow; Poppins for the banner text)

The figure is drawn on a 128x128 pixel grid and scaled up with nearest-neighbour, so it stays blocky and crisp
at any size. The zone colours are the same ones the in-game HUD uses for low / medium / high / critical damage.
"""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import os

GRID = 128
SS = 4  # supersampling for the smooth parts (ring, rounded corners)

BG_TOP = (28, 31, 38)
BG_BOT = (11, 12, 15)
GREY = (124, 130, 138)
RED = (255, 59, 48)
ORANGE = (255, 159, 28)
YELLOW = (255, 217, 61)
WHITE = (242, 244, 247)


def figure_grid():
    """128x128 RGBA of the blocky figure: 16 x 32 cells of 3 grid pixels, a 2 px gap between parts."""
    img = Image.new("RGBA", (GRID, GRID), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    C, GAP = 3, 2
    ox, oy = (GRID - 16 * C) // 2, (GRID - 32 * C) // 2

    def part(x, y, w, h, color, alpha=255):
        x0, y0 = ox + x * C, oy + y * C
        d.rectangle([x0, y0, x0 + w * C - GAP - 1, y0 + h * C - GAP - 1], fill=color + (alpha,))

    part(4, 0, 8, 8, YELLOW)            # head
    part(4, 8, 8, 6, RED)               # chest
    part(4, 14, 8, 6, ORANGE)           # abdomen
    part(0, 8, 4, 12, GREY, 235)        # arms
    part(12, 8, 4, 12, GREY, 235)
    part(4, 20, 4, 10, GREY, 235)       # legs
    part(8, 20, 4, 10, GREY, 235)
    part(4, 30, 4, 2, GREY, 235)        # feet
    part(8, 30, 4, 2, GREY, 235)
    return img


def background(size):
    s = size * SS
    bg = Image.new("RGBA", (s, s))
    px = bg.load()
    for y in range(s):
        t = y / (s - 1)
        c = tuple(int(BG_TOP[i] + (BG_BOT[i] - BG_TOP[i]) * t) for i in range(3)) + (255,)
        for x in range(s):
            px[x, y] = c
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.19), fill=255)
    bg.putalpha(mask)
    d = ImageDraw.Draw(bg)
    d.rounded_rectangle([s * 0.012, s * 0.012, s * 0.988, s * 0.988], radius=int(s * 0.18),
                        outline=(255, 255, 255, 26), width=max(2, int(s * 0.006)))
    return bg


def logo(size=512):
    s = size * SS
    img = background(size)
    d = ImageDraw.Draw(img)
    cell = s / GRID

    # corner brackets, like a HUD frame
    m = int(s * 0.115)
    L = int(s * 0.12)
    w = max(2, int(s * 0.012))
    col = (255, 255, 255, 70)
    for (cx, cy, sx, sy) in [(m, m, 1, 1), (s - m, m, -1, 1), (m, s - m, 1, -1), (s - m, s - m, -1, -1)]:
        d.line([(cx, cy), (cx + sx * L, cy)], fill=col, width=w)
        d.line([(cx, cy), (cx, cy + sy * L)], fill=col, width=w)

    # figure
    fig = figure_grid().resize((s, s), Image.NEAREST)
    img = Image.alpha_composite(img, fig)

    # soft glow behind the chest, then the hit ring
    glow = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    gx, gy = (GRID / 2) * cell, ((GRID - 96) // 2 + 11 * 3) * cell
    ImageDraw.Draw(glow).ellipse([gx - 20 * cell, gy - 20 * cell, gx + 20 * cell, gy + 20 * cell], fill=RED + (70,))
    glow = glow.filter(ImageFilter.GaussianBlur(cell * 8))
    img = Image.alpha_composite(img, glow)

    d = ImageDraw.Draw(img)
    r = 13.5 * cell
    rw = max(2, int(cell * 0.9))
    d.ellipse([gx - r, gy - r, gx + r, gy + r], outline=WHITE + (190,), width=rw)
    tick_in, tick_out = r + cell * 2.5, r + cell * 9
    for dx, dy in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
        d.line([(gx + dx * tick_in, gy + dy * tick_in), (gx + dx * tick_out, gy + dy * tick_out)],
               fill=WHITE + (190,), width=rw)
    return img.resize((size, size), Image.LANCZOS)


def banner(w=1280, h=640):
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        t = y / (h - 1)
        c = tuple(int(BG_TOP[i] * 0.8 + (BG_BOT[i] * 0.8 - BG_TOP[i] * 0.8) * t) for i in range(3)) + (255,)
        for x in range(w):
            px[x, y] = c
    # faint strips on the right, nodding at the HUD's strip layout
    strips = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sd = ImageDraw.Draw(strips)
    for i in range(6):
        y0 = 110 + i * 70
        sd.rectangle([w - 380, y0, w, y0 + 44], fill=(255, 255, 255, 9))
    img = Image.alpha_composite(img, strips)
    d = ImageDraw.Draw(img)
    icon = logo(400)
    img.alpha_composite(icon, (120, (h - 400) // 2))

    def font(name, size):
        for base in ("/usr/share/fonts/truetype/google-fonts/", ""):
            try:
                return ImageFont.truetype(base + name, size)
            except OSError:
                pass
        return ImageFont.load_default()

    big = font("Poppins-Bold.ttf", 92)
    mid = font("Poppins-Medium.ttf", 34)
    small = font("Poppins-Regular.ttf", 25)
    x = 600
    d.rectangle([x, 196, x + 8, 196 + 96], fill=RED)
    d.text((x + 30, 178), "LIMB", font=big, fill=WHITE)
    d.text((x + 30, 270), "DAMAGE", font=big, fill=WHITE)
    d.text((x, 400), "Hit zones, death recap, training dummy", font=small, fill=(190, 196, 204))
    d.text((x, 450), "Forge 1.20.1  /  TACZ ready", font=mid, fill=RED)
    return img


if __name__ == "__main__":
    out = os.path.dirname(os.path.abspath(__file__))
    root = os.path.dirname(out)
    big = logo(512)
    big.save(os.path.join(out, "logo-512.png"))
    logo(256).save(os.path.join(root, "src", "main", "resources", "logo.png"))
    banner().convert("RGB").save(os.path.join(out, "banner-1280x640.png"))
    print("done")
