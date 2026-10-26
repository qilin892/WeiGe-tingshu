#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
图标定稿图：与 res/drawable/ic_launcher_foreground.xml 同一套坐标。

用法：python tools/render_icon.py  → preview/icon-final.png
"""
from PIL import Image, ImageDraw

S = 6
DARK = (10, 9, 8)
CREAM = (237, 231, 219)
ORANGE = (255, 122, 24)
ORANGE_DIM = (179, 84, 16)

# 与 vector 里一致的几何
COVER = (30, 24, 74, 84, 8)          # x1,y1,x2,y2,radius
TICKS = [(67.5, 31), (67.5, 37), (67.5, 43)]
TICK_END = 73.5
W = [(38, 46), (44.5, 72), (52, 46), (59.5, 72), (66, 46)]
W_W = 6.0


def px(v):
    return int(round(v * S))


def draw_icon(d):
    x1, y1, x2, y2, r = COVER
    d.rounded_rectangle((px(x1), px(y1), px(x2), px(y2)), radius=px(r), fill=ORANGE)
    for (x, y) in TICKS:
        d.line([(px(x), px(y)), (px(TICK_END), px(y))], fill=ORANGE_DIM, width=px(2.6))

    p = [(px(x), px(y)) for x, y in W]
    d.line(p, fill=DARK, width=px(W_W), joint="curve")
    rr = W_W / 2
    for pt in (p[0], p[-1]):
        d.ellipse((pt[0] - px(rr), pt[1] - px(rr), pt[0] + px(rr), pt[1] + px(rr)), fill=DARK)


def masked(icon, r=36):
    out = Image.new("RGB", icon.size, CREAM)
    mask = Image.new("L", icon.size, 0)
    ImageDraw.Draw(mask).ellipse((px(54 - r), px(54 - r), px(54 + r), px(54 + r)), fill=255)
    out.paste(icon, (0, 0), mask)
    return out


def main():
    icon = Image.new("RGB", (px(108), px(108)), DARK)
    draw_icon(ImageDraw.Draw(icon))
    m = masked(icon)

    pad = 28
    sizes = [(96, "桌面 96dp"), (72, "圆形遮罩"), (48, "小图标 48dp"), (32, "列表 32dp")]
    width = pad + sum(px(s) + pad for s, _ in sizes)
    out = Image.new("RGB", (width, px(96) + pad * 2 + 8), CREAM)

    x = pad
    for size, _ in sizes:
        img = m if size == 72 else icon
        out.paste(img.resize((px(size), px(size)), Image.LANCZOS), (x, pad + 4))
        x += px(size) + pad

    out.save("preview/icon-final.png")
    print("saved preview/icon-final.png", out.size)


if __name__ == "__main__":
    main()
