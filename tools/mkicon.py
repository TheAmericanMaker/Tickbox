#!/usr/bin/env python3
"""Generates the Tickbox launcher mark: a rounded tick box with the check knocked out.

One filled path with evenOdd fill, so the same drawable serves as the adaptive
foreground and as the Android 13 monochrome silhouette. Geometry is computed rather
than eyeballed because the adaptive-icon safe zone is a circle, not a square: the
guaranteed-visible area on a 108dp canvas is a 66dp circle (radius 33 from centre).
"""
import math

CANVAS = 108.0
C = CANVAS / 2          # 54, the centre
SAFE_R = 33.0           # guaranteed-visible radius

# --- the box -------------------------------------------------------------------
BOX_MIN, BOX_MAX = 30.0, 78.0
CORNER = 11.0

# --- the check -----------------------------------------------------------------
# Elbow at B; short arm down-right from A, long arm up-right to C.
A = (41.0, 55.0)
B = (49.0, 63.0)
CC = (67.0, 45.0)
THICK = 8.0


def unit(p, q):
    dx, dy = q[0] - p[0], q[1] - p[1]
    n = math.hypot(dx, dy)
    return dx / n, dy / n


def offset_polyline(points, half):
    """Closed outline of a thick polyline, mitred at the joint."""
    def normal(p, q):
        ux, uy = unit(p, q)
        return -uy, ux           # left-hand normal

    n1 = normal(points[0], points[1])
    n2 = normal(points[1], points[2])

    def side(sign):
        s = sign * half
        a = (points[0][0] + n1[0] * s, points[0][1] + n1[1] * s)
        c = (points[2][0] + n2[0] * s, points[2][1] + n2[1] * s)
        # mitre: intersect the two offset lines at the elbow
        d1 = unit(points[0], points[1])
        d2 = unit(points[1], points[2])
        p1 = (points[1][0] + n1[0] * s, points[1][1] + n1[1] * s)
        p2 = (points[1][0] + n2[0] * s, points[1][1] + n2[1] * s)
        den = d1[0] * (-d2[1]) - d1[1] * (-d2[0])
        if abs(den) < 1e-9:
            return [a, p1, c]
        t = ((p2[0] - p1[0]) * (-d2[1]) - (p2[1] - p1[1]) * (-d2[0])) / den
        elbow = (p1[0] + d1[0] * t, p1[1] + d1[1] * t)
        return [a, elbow, c]

    left = side(+1)
    right = side(-1)
    return left + right[::-1]


def fmt(v):
    return f"{v:g}"


def rounded_square_path():
    x0 = y0 = BOX_MIN
    x1 = y1 = BOX_MAX
    r = CORNER
    return (
        f"M{fmt(x0 + r)},{fmt(y0)}"
        f"L{fmt(x1 - r)},{fmt(y0)}"
        f"A{fmt(r)},{fmt(r)} 0 0 1 {fmt(x1)},{fmt(y0 + r)}"
        f"L{fmt(x1)},{fmt(y1 - r)}"
        f"A{fmt(r)},{fmt(r)} 0 0 1 {fmt(x1 - r)},{fmt(y1)}"
        f"L{fmt(x0 + r)},{fmt(y1)}"
        f"A{fmt(r)},{fmt(r)} 0 0 1 {fmt(x0)},{fmt(y1 - r)}"
        f"L{fmt(x0)},{fmt(y0 + r)}"
        f"A{fmt(r)},{fmt(r)} 0 0 1 {fmt(x0 + r)},{fmt(y0)}"
        "Z"
    )


def check_path(poly):
    d = f"M{fmt(poly[0][0])},{fmt(poly[0][1])}"
    for p in poly[1:]:
        d += f"L{fmt(p[0])},{fmt(p[1])}"
    return d + "Z"


poly = offset_polyline([A, B, CC], THICK / 2)

# --- safety check: does anything stray outside the guaranteed circle? ----------
def radial(p):
    return math.hypot(p[0] - C, p[1] - C)

corner_centres = [(BOX_MIN + CORNER, BOX_MIN + CORNER), (BOX_MAX - CORNER, BOX_MIN + CORNER),
                  (BOX_MIN + CORNER, BOX_MAX - CORNER), (BOX_MAX - CORNER, BOX_MAX - CORNER)]
box_extent = max(radial(p) for p in corner_centres) + CORNER
check_extent = max(radial(p) for p in poly)
print(f"box max radius   {box_extent:.1f}  (safe {SAFE_R})  -> {'OK' if box_extent <= SAFE_R else 'OUTSIDE'}")
print(f"check max radius {check_extent:.1f}  (safe {SAFE_R})  -> {'OK' if check_extent <= SAFE_R else 'OUTSIDE'}")

VECTOR = f'''<?xml version="1.0" encoding="utf-8"?>
<!--
  Tickbox launcher mark: a tick box with the check knocked out of it.

  One path, evenOdd fill, so this same drawable is the adaptive foreground *and* the
  Android 13 monochrome layer — a themed icon tints the box and lets the check show
  through, which is the right silhouette without a second file to keep in sync.

  Filled rather than stroked on purpose: a 6dp outline goes muddy at launcher sizes,
  where this stays legible down to about 24dp.

  Geometry is generated, not hand-tuned — the adaptive safe zone is a 66dp circle on
  the 108dp canvas, so the box sits at radius {box_extent:.1f} and the check at
  {check_extent:.1f}, both inside 33. Regenerate with tools/mkicon.py if you change it.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <path
        android:fillColor="#FFFFFF"
        android:fillType="evenOdd"
        android:pathData="{rounded_square_path()}{check_path(poly)}" />
</vector>
'''

import sys, os
out = sys.argv[1] if len(sys.argv) > 1 else '.'
with open(os.path.join(out, 'ic_launcher_foreground.xml'), 'w') as f:
    f.write(VECTOR)
print('wrote ic_launcher_foreground.xml')

# --- matching 512x512 PNG for F-Droid -----------------------------------------
# Rendered the way a launcher shows it, not as the raw canvas: adaptive icons are
# masked, so only the central 72dp of the 108dp canvas is visible. Cropping to that
# and applying a rounded mask makes the store icon match the phone.
try:
    from PIL import Image, ImageDraw
    SUPER = 4                    # supersample, then downscale, for clean edges
    VISIBLE = 72.0               # what a launcher actually shows
    S = 512
    big = int(CANVAS * SUPER * 6)
    k = big / CANVAS
    img = Image.new('RGBA', (big, big), (0x3D, 0x6B, 0x4F, 255))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([BOX_MIN * k, BOX_MIN * k, BOX_MAX * k, BOX_MAX * k],
                        radius=CORNER * k, fill=(255, 255, 255, 255))
    d.polygon([(p[0] * k, p[1] * k) for p in poly], fill=(0x3D, 0x6B, 0x4F, 255))

    inset = (CANVAS - VISIBLE) / 2 * k
    img = img.crop((int(inset), int(inset), int(big - inset), int(big - inset)))
    img = img.resize((S, S), Image.LANCZOS)

    # squircle-ish mask, close to what most launchers apply
    mask = Image.new('L', (S * SUPER, S * SUPER), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, S * SUPER - 1, S * SUPER - 1],
                                           radius=int(S * SUPER * 0.22), fill=255)
    mask = mask.resize((S, S), Image.LANCZOS)
    out_img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    out_img.paste(img, (0, 0), mask)
    out_img.save(os.path.join(out, 'icon.png'))
    print('wrote icon.png 512x512 (masked, as a launcher shows it)')
except ImportError:
    print('PIL missing - skipped PNG')
