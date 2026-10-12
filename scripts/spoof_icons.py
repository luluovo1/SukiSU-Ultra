#!/usr/bin/env python3
"""Perturb launcher icon assets in spoofed builds.

Every spoofed build otherwise ships byte-identical icon artwork, which is
a stable fingerprint: detectors can hash the icon (file bytes, decoded
bitmap, or the rendered adaptive-icon bitmap) and match it across package
renames. This script makes every layer differ on each build while staying
visually identical:

- mipmap webp/png bitmaps: flip the lowest bit of a few random pixels
- adaptive-icon vector layers (foreground / monochrome): render at a
  random 99.3-99.8% alpha, so the composed bitmap hash changes
- the launcher background color: shift one channel by 1 level

Only files under res/ are touched; the script is invoked from the spoofed
randomizer step (normal builds stay pristine).

Usage: spoof_icons.py <res-dir>
"""

import glob
import os
import random
import re
import sys


def tweak_bitmap(path: str) -> bool:
    from PIL import Image

    img = Image.open(path).convert('RGBA')
    w, h = img.size
    px = img.load()
    for _ in range(random.randint(3, 8)):
        x, y = random.randrange(w), random.randrange(h)
        r, g, b, a = px[x, y]
        if a == 0:
            continue
        # Flip the lowest bit of two channels: guaranteed to differ by 1
        # level, invisible to the eye.
        r ^= 1
        b ^= 1
        px[x, y] = (r, g, b, a)
    if path.lower().endswith('.webp'):
        img.save(path, 'WEBP', lossless=True, quality=100, method=4)
    else:
        img.save(path, 'PNG', optimize=True)
    return True


def tweak_vector(path: str) -> bool:
    src = open(path, encoding='utf-8').read()
    if '<vector' not in src:
        return False
    alpha = f'0.99{random.randint(3, 8)}'
    out, n = re.subn(
        r'(<vector\b[^>]*?)\s+android:alpha="[^"]*"',
        lambda m: f'{m.group(1)} android:alpha="{alpha}"',
        src,
        count=1,
    )
    if n == 0:
        out, n = re.subn(
            r'(<vector\b[^>]*?)(\s*/?>)',
            lambda m: f'{m.group(1)} android:alpha="{alpha}"{m.group(2)}',
            src,
            count=1,
        )
    if n == 0:
        return False
    open(path, 'w', encoding='utf-8', newline='').write(out)
    return True


def tweak_color(path: str) -> bool:
    src = open(path, encoding='utf-8').read()

    def shift(match: re.Match) -> str:
        head, color = match.group(1), match.group(2)
        if len(color) == 9:  # #AARRGGBB
            g = int(color[5:7], 16)
            g = g - 1 if g > 0 else g + 1
            return f'{head}#{color[1:5]}{g:02X}{color[7:9]}<'
        if len(color) == 7:  # #RRGGBB
            g = int(color[3:5], 16)
            g = g - 1 if g > 0 else g + 1
            return f'{head}#{color[1:3]}{g:02X}{color[5:7]}<'
        return match.group(0)

    out, n = re.subn(
        r'(name="ic_launcher_background">)(#[0-9A-Fa-f]{6,8})<',
        shift,
        src,
    )
    if n == 0:
        return False
    open(path, 'w', encoding='utf-8', newline='').write(out)
    return True


def main() -> None:
    res = sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/res'
    random.seed()

    bitmaps = sorted(set(
        glob.glob(os.path.join(res, 'mipmap-*', 'ic_launcher*.webp'))
        + glob.glob(os.path.join(res, 'mipmap-*', 'ic_launcher*.png'))
    ))
    vectors = sorted(glob.glob(os.path.join(res, 'drawable', 'ic_launcher_*.xml')))
    colors = sorted(glob.glob(os.path.join(res, 'values', 'colors.xml')))

    if not bitmaps and not vectors:
        raise SystemExit('spoof_icons: no launcher icon assets found')

    n_bmp = sum(1 for p in bitmaps if tweak_bitmap(p))
    n_vec = sum(1 for p in vectors if tweak_vector(p))
    n_col = sum(1 for p in colors if tweak_color(p))
    print(
        f'spoof_icons: bitmaps {n_bmp}/{len(bitmaps)}, '
        f'vectors {n_vec}/{len(vectors)}, colors {n_col}/{len(colors)}'
    )


if __name__ == '__main__':
    main()
