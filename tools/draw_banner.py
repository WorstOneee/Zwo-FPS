# Title banner for the /fps menu (font zwofps:menu, char ).
# Drawn 1:1 on the GUI pixel grid: 160x13. Font glyphs must fit the client's 256x256 font
# atlas, so the texture must stay narrower than 256 px.
# Usage: python tools/draw_banner.py <out_png> [preview_png]
import sys
from PIL import Image, ImageDraw

W, H = 160, 13
img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

OUTLINE = (24, 8, 38, 255)
TOP, BOT = (122, 54, 172), (60, 22, 94)
HI, LO = (186, 118, 236, 255), (40, 14, 64, 255)
GOLD, AMBER = (244, 204, 92, 255), (206, 124, 42, 255)


def inset(y):  # chamfered corners
    return max(0, 2 - y, y - (H - 3))


# body gradient
for y in range(H):
    t = y / (H - 1)
    col = tuple(int(TOP[i] * (1 - t) + BOT[i] * t) for i in range(3)) + (255,)
    x0 = inset(y)
    d.line([(x0, y), (W - 1 - x0, y)], fill=col)
# outline
for y in range(H):
    x0 = inset(y)
    d.point((x0, y), OUTLINE)
    d.point((W - 1 - x0, y), OUTLINE)
d.line([(2, 0), (W - 3, 0)], fill=OUTLINE)
d.line([(2, H - 1), (W - 3, H - 1)], fill=OUTLINE)
d.point((1, 1), OUTLINE); d.point((W - 2, 1), OUTLINE)
d.point((1, H - 2), OUTLINE); d.point((W - 2, H - 2), OUTLINE)
# bevel
d.line([(3, 1), (W - 4, 1)], fill=HI)
d.line([(3, H - 2), (W - 4, H - 2)], fill=LO)


# speed chevrons at both ends: > > >  ...  < < <
def chevron(x, flip, col):
    for j in range(7):  # rows 3..9
        dx = j if j < 4 else 6 - j
        xx = x - dx if flip else x + dx
        d.point((xx, 3 + j), col)
        d.point((xx + (-1 if flip else 1), 3 + j), col)


for k, col in enumerate((GOLD, AMBER, AMBER)):
    chevron(6 + k * 5, False, col)
    chevron(W - 7 - k * 5, True, col)

img.save(sys.argv[1])

if len(sys.argv) > 2:  # preview over a chest-like background at GUI scale 3
    bg = Image.new('RGBA', (176 * 3, 22 * 3), (198, 198, 198, 255))
    bg.alpha_composite(img.resize((W * 3, H * 3), Image.NEAREST), (8 * 3, 3 * 3))
    bg.save(sys.argv[2])
