"""Zwo FPS menu icons: 16x16 pixel art drawn from code, auto-outlined.

Usage: python tools/draw_icons.py <out_textures_dir> <preview_png>
"""
import math
import sys
from PIL import Image

N = 16
OUT = (32, 18, 46, 255)  # outline: deep purple-black


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


class Icon:
    def __init__(self):
        self.px = {}

    def put(self, x, y, c):
        if 0 <= x < N and 0 <= y < N:
            self.px[(x, y)] = hexc(c) if isinstance(c, str) else c

    def grid(self, rows, palette, ox=0, oy=0):
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in palette:
                    self.put(ox + x, oy + y, palette[ch])

    def disk(self, cx, cy, r, c, r_in=-1.0):
        for y in range(N):
            for x in range(N):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if r_in < d <= r:
                    self.put(x, y, c)

    def image(self, outline=True):
        img = Image.new('RGBA', (N, N), (0, 0, 0, 0))
        for (x, y), c in self.px.items():
            img.putpixel((x, y), c)
        if outline:
            for y in range(N):
                for x in range(N):
                    if (x, y) in self.px:
                        continue
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        if (x + dx, y + dy) in self.px:
                            img.putpixel((x, y), OUT)
                            break
        return img


def leve():
    # Feather: light = lightweight.
    i = Icon()
    i.grid([
        "............LLL.",
        "..........LLLMM.",
        ".........LLLMMW.",
        "........LLLMMWM.",
        ".......LLLMMWMD.",
        "......LLLMMWMD..",
        ".....LLLMMWMD...",
        "....LLMMMWMD....",
        "....LLMMWMD.....",
        "...LLMMWMD......",
        "...LMMWDD.......",
        "...LMWD.........",
        "....W...........",
        "...W............",
        "..W.............",
        "................",
    ], {'L': '#b6f28f', 'M': '#5fc24a', 'D': '#2f7d2a', 'W': '#f4ffe6'})
    return i


def medio():
    # Gauge with the needle in the middle.
    i = Icon()
    i.grid([
        "................",
        "................",
        "......YYYY......",
        "....GGYYYYRR....",
        "...GGFFFFFFRR...",
        "..GGFFFWFFFFRR..",
        "..GFFFFWFFFFFR..",
        ".GGFFFFWFFFFFRR.",
        ".GFFFFFWFFFFFFR.",
        ".GFFFFHWHFFFFFR.",
        ".GFFFFHHHFFFFFR.",
        ".BBBBBBBBBBBBBB.",
        ".BbbbbbbbbbbbbB.",
        "................",
        "................",
        "................",
    ], {'G': '#5fc24a', 'Y': '#ffd23f', 'R': '#ef4b4b', 'F': '#2b2140', 'W': '#ffffff',
        'H': '#b8b0c8', 'B': '#8a5cd6', 'b': '#5b3a9b'})
    return i


def pvp():
    # Two crossed swords, hilts at the bottom corners.
    i = Icon()
    blade_l, blade_d = '#eef4f7', '#9fb0bb'
    gold, brown, pom = '#f2b233', '#7a4a24', '#ffe08a'
    def sword(m):
        f = (lambda x: x) if m == 0 else (lambda x: 15 - x)
        for k in range(8):
            i.put(f(14 - k), 1 + k, blade_l)
            i.put(f(13 - k), 1 + k, blade_d)
        for (x, y) in ((4, 8), (5, 9), (6, 10)):
            i.put(f(x), y, gold)
        for (x, y) in ((4, 10), (3, 11)):
            i.put(f(x), y, brown)
        i.put(f(2), 12, pom)
    sword(0)
    sword(1)
    return i


def auto():
    # Cyan gear with an "A".
    i = Icon()
    cx = cy = 8.0
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            a = (math.atan2(dy, dx) + math.pi) / (2 * math.pi) * 8
            tooth = (a % 1.0) < 0.5
            outer = 7.2 if tooth else 5.6
            if d <= outer:
                i.put(x, y, '#3fd0e0' if d > 4.2 else '#1f5f86')
                if 5.0 < d <= outer and tooth and dy < 0:
                    i.put(x, y, '#9cf3fb')
    i.grid([
        ".WW.",
        "W..W",
        "WWWW",
        "W..W",
    ], {'W': '#ffffff'}, ox=6, oy=6)
    return i


def off():
    # Power symbol.
    i = Icon()
    red, light = '#ef4b4b', '#ff9a8a'
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8.5
            d = math.hypot(dx, dy)
            ang = math.degrees(math.atan2(dx, -dy))  # 0 = up
            if 4.4 < d <= 6.6 and abs(ang) > 38:
                i.put(x, y, light if (dx < 0 and dy < 0) else red)
    for y in range(1, 9):
        i.put(7, y, light)
        i.put(8, y, red)
    return i


def status():
    # Monitor showing an FPS bar chart.
    i = Icon()
    i.grid([
        "................",
        ".FFFFFFFFFFFFFF.",
        ".FSSSSSSSSSSSSF.",
        ".FSSSSSSSSSgSSF.",
        ".FSSSSSSSgSgSSF.",
        ".FSSSSSgSgSgSSF.",
        ".FSSSySgSgSgSSF.",
        ".FSySySgSgSgSSF.",
        ".FSySySgSgSgSSF.",
        ".FSSSSSSSSSSSSF.",
        ".FFFFFFFFFFFFFF.",
        ".......ff.......",
        "......ffff......",
        ".....PPPPPP.....",
        "................",
        "................",
    ], {'F': '#5a5466', 'S': '#3a2366', 'g': '#5fe36b', 'y': '#ffd23f', 'f': '#5a5466', 'P': '#8a5cd6'})
    return i


def cloud(i, oy=0):
    i.grid([
        "......WWW.......",
        "....WWWWWWW.....",
        "..WWWWWWWWWWWW..",
        ".WWWWWWWWWWWWWW.",
        ".GGGGGGGGGGGGGG.",
    ], {'W': '#f4f6fb', 'G': '#b9c2d6'}, oy=oy)


def rain_on():
    i = Icon()
    cloud(i, oy=2)
    for (x, y) in ((3, 9), (2, 11), (7, 9), (6, 11), (5, 13), (11, 9), (10, 11), (9, 13), (13, 11), (12, 13)):
        i.put(x, y, '#4aa8ff')
    return i


def rain_off():
    i = Icon()
    i.disk(11.5, 5.0, 3.6, '#ffd23f')
    for (x, y) in ((11, 0), (15, 4), (15, 6), (8, 1)):
        i.put(x, y, '#ffe680')
    cloud(i, oy=6)
    return i


def tips():
    # Light bulb.
    i = Icon()
    i.disk(8.0, 6.0, 5.2, '#ffd23f')
    i.disk(8.0, 6.0, 5.2, '#ffd23f')
    for (x, y) in ((5, 4), (5, 5), (6, 3), (7, 3)):
        i.put(x, y, '#fff4b8')
    for (x, y) in ((6, 8), (6, 7), (7, 6), (8, 6), (9, 7), (9, 8)):
        i.put(x, y, '#e08a00')
    i.grid([
        "......YY......",
        "....GGGGGG....",
        "....gggggg....",
        "....GGGGGG....",
        ".....gggg.....",
    ], {'Y': '#ffd23f', 'G': '#c9c5d3', 'g': '#8f899c'}, ox=1, oy=10)
    return i


def area():
    # Radar with detected entities.
    i = Icon()
    i.disk(8, 8, 7.2, '#123d24')
    i.disk(8, 8, 7.2, '#2fae5a', r_in=6.3)
    i.disk(8, 8, 3.9, '#2fae5a', r_in=3.1)
    for k in range(1, 7):
        i.put(8 + k // 1 if False else 8 + int(k * 0.75), 8 - k, '#7cff9e')
    for (x, y) in ((4, 5), (11, 10), (5, 11)):
        i.put(x, y, '#ff5a5a')
    i.put(8, 8, '#ffffff')
    return i


ICONS = {
    'leve': leve, 'medio': medio, 'pvp': pvp, 'auto': auto, 'off': off,
    'status': status, 'rain_on': rain_on, 'rain_off': rain_off, 'tips': tips, 'area': area,
}


def main():
    out_dir, preview = sys.argv[1], sys.argv[2]
    import os
    os.makedirs(out_dir, exist_ok=True)
    scale, pad = 12, 8
    cols = len(ICONS)
    sheet = Image.new('RGBA', (cols * (N * scale + pad) + pad, N * scale + 2 * pad), (198, 198, 198, 255))
    for idx, (name, fn) in enumerate(ICONS.items()):
        img = fn().image()
        img.save(os.path.join(out_dir, name + '.png'))
        big = img.resize((N * scale, N * scale), Image.NEAREST)
        x0 = pad + idx * (N * scale + pad)
        slot = Image.new('RGBA', big.size, (139, 139, 139, 255))
        slot.alpha_composite(big)
        sheet.paste(slot, (x0, pad))
    sheet.save(preview)


if __name__ == '__main__':
    main()
