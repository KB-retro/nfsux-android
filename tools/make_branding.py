# Renders the NFSU-X Carrerillas logo and app icons (Inter font, Pillow).
# Usage: python3 tools/make_branding.py <output-dir>
import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops
BLACK = '/usr/share/fonts/opentype/inter/InterDisplay-BlackItalic.otf'
XBOLD = '/usr/share/fonts/opentype/inter/Inter-ExtraBoldItalic.otf'

def gradient(W, H, top, bottom, y0, y1):
    g = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(g)
    for y in range(H):
        t = min(1, max(0, (y - y0) / max(1, y1 - y0)))
        d.line([(0, y), (W, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(top, bottom)) + (255,))
    return g

def stylise(W, H, fill_masks, plain_masks, outline=9, glow=10):
    shape = Image.new('L', (W, H), 0)
    for m, _ in fill_masks + plain_masks: shape = ImageChops.lighter(shape, m)
    out = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    g = shape.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.GaussianBlur(glow))
    out.paste((40, 170, 255, 255), (0, 0), g.point(lambda v: int(v * 0.55)))
    out.paste((8, 16, 28, 255), (0, 0), shape.filter(ImageFilter.MaxFilter(outline)))
    for m, fill in fill_masks + plain_masks: out.paste(fill, (0, 0), m)
    return out

def logo(path_big, path_small, preview):
    W, H = 1100, 300
    big = ImageFont.truetype(BLACK, 150); mid = ImageFont.truetype(XBOLD, 62)
    def m(fn):
        mk = Image.new('L', (W, H), 0); fn(ImageDraw.Draw(mk)); return mk
    t, s = 'NFSU-X', 'CARRERILLAS'
    tb = big.getbbox(t, anchor='ls'); x0 = (W - (tb[2] - tb[0])) // 2 - tb[0]; base = 160
    title = m(lambda d: d.text((x0, base), t, font=big, anchor='ls', fill=255))
    def stripes(d):
        xr = x0 + tb[0] - 22
        for y, l in ((72, 120), (102, 90), (132, 60)):
            d.polygon([(xr - l, y), (xr, y), (xr - 10, y + 14), (xr - l - 10, y + 14)], fill=255)
        d.polygon([(x0 + tb[0] + 30, base + 12), (x0 + tb[2] + 30, base + 12), (x0 + tb[2] + 24, base + 22), (x0 + tb[0] + 24, base + 22)], fill=255)
    stripe = m(stripes)
    sb = mid.getbbox(s, anchor='ls')
    sub = m(lambda d: d.text(((W - (sb[2] - sb[0])) // 2 - sb[0], 255), s, font=mid, anchor='ls', fill=255))
    blue = gradient(W, H, (150, 240, 255), (40, 120, 230), 30, 170)
    orange = gradient(W, H, (255, 214, 120), (255, 120, 40), 200, 260)
    out = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    shape = ImageChops.lighter(ImageChops.lighter(title, stripe), sub)
    g = shape.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.GaussianBlur(10))
    out.paste((40, 170, 255, 255), (0, 0), g.point(lambda v: int(v * 0.55)))
    out.paste((8, 16, 28, 255), (0, 0), shape.filter(ImageFilter.MaxFilter(9)))
    out.paste(blue, (0, 0), ImageChops.lighter(title, stripe))
    out.paste(orange, (0, 0), sub)
    out = out.crop(out.getbbox()); out.save(path_big)
    out.resize((500, round(out.height * 500 / out.width)), Image.LANCZOS).save(path_small)
    for bg, n in (((255, 255, 255), 'light'), ((13, 17, 23), 'dark')):
        p = Image.new('RGBA', (out.width + 40, out.height + 40), bg + (255,)); p.alpha_composite(out, (20, 20)); p.save(f'{preview}-{n}.png')

def wheel(S, R):
    """Tyre with tread blocks, alloy rim and five spokes, centred, outer radius R."""
    import math
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0)); d = ImageDraw.Draw(img); c = S / 2
    def circle(r, **kw): d.ellipse((c - r, c - r, c + r, c + r), **kw)
    circle(R, fill=(22, 24, 28, 255))                                   # tyre
    for i in range(36):                                                 # tread blocks
        a = 2 * math.pi * i / 36; a2 = a + 2 * math.pi / 36 * 0.55
        pts = [(c + R * 1.0 * math.cos(t), c + R * 1.0 * math.sin(t)) for t in (a, a2)]
        pts += [(c + R * 0.9 * math.cos(t), c + R * 0.9 * math.sin(t)) for t in (a2, a)]
        d.polygon(pts, fill=(48, 52, 60, 255))
    circle(R * 0.86, fill=(14, 15, 18, 255))                            # sidewall
    circle(R * 0.80, outline=(40, 44, 52, 255), width=max(2, int(R * 0.015)))
    rim = gradient(S, S, (235, 242, 248), (110, 125, 140), int(c - R * 0.72), int(c + R * 0.72))
    m = Image.new('L', (S, S), 0); md = ImageDraw.Draw(m)
    md.ellipse((c - R * 0.72, c - R * 0.72, c + R * 0.72, c + R * 0.72), fill=255)
    md.ellipse((c - R * 0.62, c - R * 0.62, c + R * 0.62, c + R * 0.62), fill=0)
    for i in range(5):                                                  # spokes
        a = 2 * math.pi * i / 5 - math.pi / 2; w = 0.16
        md.polygon([(c + R * 0.16 * math.cos(a - w * 2), c + R * 0.16 * math.sin(a - w * 2)),
                    (c + R * 0.64 * math.cos(a - w), c + R * 0.64 * math.sin(a - w)),
                    (c + R * 0.64 * math.cos(a + w), c + R * 0.64 * math.sin(a + w)),
                    (c + R * 0.16 * math.cos(a + w * 2), c + R * 0.16 * math.sin(a + w * 2))], fill=255)
    md.ellipse((c - R * 0.18, c - R * 0.18, c + R * 0.18, c + R * 0.18), fill=255)
    img.paste(rim, (0, 0), m)
    circle(R * 0.07, fill=(30, 34, 40, 255))                            # hub nut
    return img

def icon(size, foreground):
    # Adaptive foreground: 108dp canvas, art inside the central 66dp (61%).
    S = 1024
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0)) if foreground else gradient(S, S, (18, 44, 66), (6, 10, 16), 0, S)
    R = S * (0.30 if foreground else 0.44)
    img.alpha_composite(wheel(S, R))
    width = R * 2 * 1.02
    f = ImageFont.truetype(BLACK, 100)
    f = ImageFont.truetype(BLACK, int(100 * width / (f.getbbox('NFSU-X')[2] - f.getbbox('NFSU-X')[0])))
    text = Image.new('L', (S, S), 0); d = ImageDraw.Draw(text)
    d.text((S // 2, S // 2), 'NFSU-X', font=f, anchor='mm', fill=255)
    bb = text.getbbox(); h = bb[3] - bb[1]
    bar = Image.new('L', (S, S), 0); y = bb[3] + h * 0.18; th = h * 0.15
    ImageDraw.Draw(bar).polygon([(bb[0] + h * 0.2, y), (bb[2], y), (bb[2] - th * 0.6, y + th), (bb[0] + h * 0.2 - th * 0.6, y + th)], fill=255)
    shape = ImageChops.lighter(text, bar)
    g = shape.filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.GaussianBlur(20))
    img.paste((40, 170, 255, 255), (0, 0), g.point(lambda v: int(v * 0.7)))
    img.paste((8, 16, 28, 255), (0, 0), shape.filter(ImageFilter.MaxFilter(15 if foreground else 21)))
    img.paste(gradient(S, S, (150, 240, 255), (40, 120, 230), bb[1], bb[3]), (0, 0), text)
    img.paste(gradient(S, S, (255, 214, 120), (255, 120, 40), int(y), int(y + th)), (0, 0), bar)
    if not foreground:
        r = Image.new('L', (S, S), 0); ImageDraw.Draw(r).rounded_rectangle((0, 0, S - 1, S - 1), radius=190, fill=255)
        img.putalpha(ImageChops.multiply(img.getchannel('A'), r))
    return img.resize((size, size), Image.LANCZOS)

if __name__ == '__main__':
    out = sys.argv[1]
    logo(f'{out}/logo-readme.png', f'{out}/launcher_logo.png', f'{out}/preview')
    for d, n in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
        icon(n, False).save(f'{out}/ic_launcher-{d}.png')
        icon(n * 9 // 4, True).save(f'{out}/ic_launcher_foreground-{d}.png')
    icon(512, False).save(f'{out}/icon-preview.png')
    fg = icon(432, True); bg = Image.new('RGBA', (432, 432), (14, 34, 51, 255)); bg.alpha_composite(fg)
    c = Image.new('L', (432, 432), 0); ImageDraw.Draw(c).ellipse((54, 54, 378, 378), fill=255)
    prev = Image.new('RGBA', (432, 432), (255, 255, 255, 255)); prev.paste(bg, (0, 0), c); prev.save(f'{out}/adaptive-preview.png')
