# Banner for the /fps title: drawn on a 160x13 GUI-pixel grid at 2x (320x26).
import sys
from PIL import Image, ImageDraw
W, H, K = 160, 13, 2
img = Image.new('RGBA', (W*K, H*K), (0,0,0,0))
d = ImageDraw.Draw(img)
def px(x, y, c, w=1, h=1):  # half-pixel units (2x grid)
    d.rectangle([x, y, x+w-1, y+h-1], fill=c)
G = W*K; HH = H*K
outline = (24, 8, 38, 255)
top, bot = (120, 52, 170), (58, 20, 92)
# body with cut corners (chamfer 3 half-px)
for y in range(HH):
    t = y/(HH-1)
    col = tuple(int(top[i]*(1-t) + bot[i]*t) for i in range(3)) + (255,)
    ins = max(0, 4 - y, y - (HH-5))
    px(ins, y, col, G - 2*ins, 1)
# outline
for y in range(HH):
    ins = max(0, 4 - y, y - (HH-5))
    px(ins, y, outline, 2); px(G-ins-2, y, outline, 2)
px(4, 0, outline, G-8, 2); px(4, HH-2, outline, G-8, 2)
for i in range(4):
    px(i, 4-i, outline, 2, 1); px(G-2-i, 4-i, outline, 2, 1)
    px(i, HH-5+i, outline, 2, 1); px(G-2-i, HH-5+i, outline, 2, 1)
# highlights
px(6, 2, (190, 120, 240, 255), G-12, 1)
px(6, HH-3, (36, 12, 60, 255), G-12, 1)
# speed chevrons at each end
def chev(x0, flip):
    c1, c2 = (240, 200, 90, 255), (200, 120, 40, 255)
    for k in range(3):
        bx = x0 + (k*9 if not flip else -k*9)
        for j in range(7):
            dx = j if j < 4 else 6 - j
            xx = bx + (dx if not flip else -dx)
            px(xx, 6 + j*2, c1 if k == 0 else c2, 3, 2)
chev(12, False); chev(G-15, True)
img.save(sys.argv[1])
# preview at GUI scale 3 over a chest-like background
bg = Image.new('RGBA', (176*3, 40*3), (198,198,198,255))
ban = img.resize((W*3, H*3), Image.NEAREST)
bg.alpha_composite(ban, (8*3, 3*3))
bg.save(sys.argv[2])
