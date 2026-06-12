#!/usr/bin/env python3
"""Generates the voxel ball, item props, and fly used by the match renderers.

Writes:
  assets/models/ball/ball.{obj,mtl,png}
  assets/models/items/<item>/item.{obj,mtl,png}   (9 items)
  assets/models/fly/fly.{obj,mtl,png}

Conventions shared with generate_table.py:
  - colors are exact entries of the game's 16-color palette, addressed via a
    64x64 palette PNG (every face's UVs point at one cell centre)
  - libGDX's ObjLoader samples vt v top-based (no flip) — v follows that
  - quads, CCW-outward winding, axis normals

Sizing: the ball and items are auto-fitted by their loaders (longest axis),
so their absolute scale is irrelevant. The FLY is authored at exact world
scale, centred at the origin, because MatchArenaRenderer resets fly instance
transforms every frame (no room for a fitting transform).

Run from the repo root:  python3 tools/voxel/generate_props.py
"""

import math
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "assets", "models")
SEED = 20260611

COLORS = [
    "4A5A3C",  # 0  GREEN_DIM
    "7B8C5A",  # 1  GREEN
    "E8DCC0",  # 2  TEXT   — chalk white
    "3B342E",  # 3  BORDER — dark wood
    "4D4036",  # 4  BORDER_HI — mid wood
    "6B5B4B",  # 5  TEXT_DIM — pale wood / dust
    "1A1014",  # 6  BG2 — grime / near-black
    "D89F66",  # 7  WARM — gold / tape
    "8C5C36",  # 8  WARM_DIM — rust / leather
    "0A0608",  # 9  BG — black
    "5E1F1F",  # 10 RED_DIM — deep red
    "7A2A2A",  # 11 RED_GLOW — red
    "A03B3B",  # 12 RED — bright red
]
GREEN, GREEN_LT, CHALK = 0, 1, 2
WOOD_DK, WOOD, WOOD_PALE = 3, 4, 5
GRIME, GOLD, RUST, BLACK, RED_DK, RED_MD, RED = 6, 7, 8, 9, 10, 11, 12

GRID, CELL_PX = 4, 16


def cell_hash(x, y, z, salt=0):
    h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ ((SEED + salt) * 2654435761)
    h = (h ^ (h >> 13)) * 0x5BD1E995 & 0xFFFFFFFF
    return ((h ^ (h >> 15)) & 0xFFFF) / 65536.0


class Vox:
    """Sparse voxel grid; meshes into neighbor-culled quads."""

    def __init__(self):
        self.cells = {}

    def set(self, x, y, z, c):
        self.cells[(x, y, z)] = c

    def box(self, x0, y0, z0, x1, y1, z1, c):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, c)

    def ellipsoid(self, cx, cy, cz, rx, ry, rz, c):
        for x in range(int(cx - rx - 1), int(cx + rx + 2)):
            for y in range(int(cy - ry - 1), int(cy + ry + 2)):
                for z in range(int(cz - rz - 1), int(cz + rz + 2)):
                    if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2 <= 1.05:
                        self.set(x, y, z, c)

    def disc_x(self, x0, x1, cy, cz, r, c):
        """Solid disc in the YZ plane, spanning x0..x1."""
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                if (y - cy) ** 2 + (z - cz) ** 2 <= r * r + 0.4:
                    for x in range(x0, x1 + 1):
                        self.set(x, y, z, c)

    def write(self, out_dir, name, cell_size, center=True):
        cells = self.cells
        xs = [k[0] for k in cells]
        ys = [k[1] for k in cells]
        zs = [k[2] for k in cells]
        if center:
            ox = (min(xs) + max(xs) + 1) / 2.0
            oy = (min(ys) + max(ys) + 1) / 2.0
            oz = (min(zs) + max(zs) + 1) / 2.0
        else:
            ox = oy = oz = 0.0

        verts, vcache, faces = [], {}, []

        def vid(x, y, z):
            key = (round((x - ox) * cell_size, 5),
                   round((y - oy) * cell_size, 5),
                   round((z - oz) * cell_size, 5))
            if key not in vcache:
                verts.append(key)
                vcache[key] = len(verts)
            return vcache[key]

        for (x, y, z), c in sorted(cells.items()):
            vt = c + 1
            if (x, y + 1, z) not in cells:
                faces.append(([vid(x, y + 1, z), vid(x, y + 1, z + 1),
                               vid(x + 1, y + 1, z + 1), vid(x + 1, y + 1, z)], vt, 3))
            if (x, y - 1, z) not in cells:
                faces.append(([vid(x, y, z), vid(x + 1, y, z),
                               vid(x + 1, y, z + 1), vid(x, y, z + 1)], vt, 4))
            if (x + 1, y, z) not in cells:
                faces.append(([vid(x + 1, y, z), vid(x + 1, y + 1, z),
                               vid(x + 1, y + 1, z + 1), vid(x + 1, y, z + 1)], vt, 1))
            if (x - 1, y, z) not in cells:
                faces.append(([vid(x, y, z + 1), vid(x, y + 1, z + 1),
                               vid(x, y + 1, z), vid(x, y, z)], vt, 2))
            if (x, y, z + 1) not in cells:
                faces.append(([vid(x, y, z + 1), vid(x + 1, y, z + 1),
                               vid(x + 1, y + 1, z + 1), vid(x, y + 1, z + 1)], vt, 5))
            if (x, y, z - 1) not in cells:
                faces.append(([vid(x + 1, y, z), vid(x, y, z),
                               vid(x, y + 1, z), vid(x + 1, y + 1, z)], vt, 6))

        os.makedirs(out_dir, exist_ok=True)
        write_palette_png(os.path.join(out_dir, name + ".png"))
        with open(os.path.join(out_dir, name + ".mtl"), "w") as f:
            f.write(f"# generated by tools/voxel/generate_props.py\n"
                    f"newmtl voxel\nKd 1.000 1.000 1.000\nmap_Kd {name}.png\n")
        with open(os.path.join(out_dir, name + ".obj"), "w") as f:
            f.write(f"# voxel prop — generated by tools/voxel/generate_props.py\n"
                    f"mtllib {name}.mtl\no {name}\n")
            for v in verts:
                f.write(f"v {v[0]} {v[1]} {v[2]}\n")
            for ci in range(len(COLORS)):
                cx, cy = ci % GRID, ci // GRID
                f.write(f"vt {(cx + 0.5) / GRID:.5f} {(cy + 0.5) / GRID:.5f}\n")
            for n in ("1 0 0", "-1 0 0", "0 1 0", "0 -1 0", "0 0 1", "0 0 -1"):
                f.write(f"vn {n}\n")
            f.write("usemtl voxel\n")
            for vids, vt, vn in faces:
                f.write("f " + " ".join(f"{v}/{vt}/{vn}" for v in vids) + "\n")
        assert len(verts) <= 32767 and len(faces) * 6 <= 32767, name
        print(f"{name:12s} cells={len(cells):5d} quads={len(faces):5d}")


def write_palette_png(path):
    size = GRID * CELL_PX
    rows = bytearray()
    for py in range(size):
        rows.append(0)
        for px in range(size):
            ci = (py // CELL_PX) * GRID + (px // CELL_PX)
            hexc = COLORS[ci] if ci < len(COLORS) else "000000"
            rows += bytes(int(hexc[i:i + 2], 16) for i in (0, 2, 4))

    def chunk(tag, payload):
        data = tag + payload
        return struct.pack(">I", len(payload)) + data + struct.pack(">I", zlib.crc32(data))

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
                + chunk(b"IDAT", zlib.compress(bytes(rows), 9)) + chunk(b"IEND", b""))


# ── Ball (replaces the Meshy egg; loader auto-fits) ──────────────────────────
def ball():
    v = Vox()
    v.ellipsoid(4, 4, 4, 4.2, 4.2, 4.2, CHALK)
    for (x, y, z) in list(v.cells):
        h = cell_hash(x, y, z, 1)
        if h < 0.10:
            v.cells[(x, y, z)] = WOOD_PALE        # scuffs
        elif h > 0.985:
            v.cells[(x, y, z)] = RUST             # old stains
    return v


# ── Items ─────────────────────────────────────────────────────────────────────
def paddle(blade_r, handle_len, rubber):
    v = Vox()
    bx, by = 8, 9                                  # blade centre
    for x in range(bx - 6, bx + 7):
        for y in range(by - 6, by + 7):
            d = math.hypot(x - bx, y - by)
            if d <= blade_r:
                c = rubber if d <= blade_r - 1.2 else WOOD_DK
                v.set(x, y, 0, c)
                v.set(x, y, 1, c if cell_hash(x, y, 7) > 0.1 else WOOD_PALE)
    v.box(bx - 1, by - 6 - handle_len, 0, bx + 1, by - int(blade_r) - 1, 1, WOOD_PALE)
    return v, bx, by


def wide_paddle():
    v, bx, by = paddle(6.2, 4, RED_MD)
    for s in (-1, 1):                              # outward arrows
        tip = bx + s * 11
        v.set(tip, by, 0, GOLD)
        v.set(tip - s, by + 1, 0, GOLD)
        v.set(tip - s, by - 1, 0, GOLD)
        v.set(tip - s, by, 0, GOLD)
        v.set(tip - 2 * s, by, 0, GOLD)
    return v


def tiny_paddle():
    v = Vox()
    bx, by = 8, 9
    for x in range(bx - 3, bx + 4):                # small blade
        for y in range(by - 3, by + 4):
            d = math.hypot(x - bx, y - by)
            if d <= 3.2:
                c = RED_DK if d <= 2.0 else WOOD_DK
                v.set(x, y, 0, c)
                v.set(x, y, 1, c)
    v.box(bx - 1, by - 7, 0, bx, by - 4, 1, WOOD_PALE)
    for s in (-1, 1):                              # inward arrows
        base = bx + s * 11
        v.set(base, by, 0, GOLD)
        v.set(base - s, by, 0, GOLD)
        tip = base - 2 * s
        v.set(tip, by, 0, GOLD)
        v.set(tip + s, by + 1, 0, GOLD)
        v.set(tip + s, by - 1, 0, GOLD)
    return v


def patch_kit():
    v = Vox()
    v.box(0, 0, 0, 10, 6, 7, CHALK)
    for x in range(11):                            # weathering
        for z in range(8):
            if cell_hash(x, 6, z, 2) < 0.08:
                v.set(x, 6, z, WOOD_PALE)
    for x in range(3, 8):                          # red cross on the lid
        v.set(x, 6, 3, RED)
        v.set(x, 6, 4, RED)
    for z in range(1, 7):
        v.set(5, 6, z, RED)
        v.set(6, 6, z, RED)
    v.box(4, 2, -1, 6, 4, -1, GRIME)               # clasp
    return v


def slow_mo():
    v = Vox()
    v.box(0, 0, 0, 6, 0, 6, WOOD_DK)               # base plate
    v.box(0, 8, 0, 6, 8, 6, WOOD_DK)               # top plate
    for (px, pz) in ((0, 0), (0, 6), (6, 0), (6, 6)):
        v.box(px, 1, pz, px, 7, pz, WOOD)          # pillars
    for y, r in ((1, 2.6), (2, 1.8), (3, 1.0)):    # bottom sand cone
        for x in range(1, 6):
            for z in range(1, 6):
                if math.hypot(x - 3, z - 3) <= r:
                    v.set(x, y, z, GOLD)
    v.set(3, 4, 3, GOLD)                           # neck grain
    for y, r in ((5, 1.0), (6, 1.8), (7, 2.6)):    # top glass outline
        for x in range(1, 6):
            for z in range(1, 6):
                d = math.hypot(x - 3, z - 3)
                if r - 0.9 <= d <= r:
                    v.set(x, y, z, CHALK)
    v.set(3, 5, 3, GOLD)                           # falling sand
    return v


def steal():
    v = Vox()
    profile = [2.4, 3.1, 3.4, 3.3, 2.9, 2.2, 1.4]  # sack silhouette
    for y, r in enumerate(profile):
        for x in range(0, 8):
            for z in range(0, 8):
                if math.hypot(x - 3.5, z - 3.5) <= r:
                    c = WOOD_PALE if cell_hash(x, y, z, 3) > 0.3 else WOOD
                    v.set(x, y, z, c)
    for x in range(3, 5):                          # tie band + puff
        for z in range(3, 5):
            v.set(x, 7, z, GRIME)
            v.set(x, 8, z, WOOD_PALE)
    v.set(3, 0, 7, GOLD)                           # dropped coin
    v.set(5, 0, 6, GOLD)
    return v


def fast_serve():
    v = Vox()
    rows = [(10, 4, 7), (9, 3, 6), (8, 2, 5), (7, 1, 6),   # upper body
            (6, 3, 8), (5, 4, 7), (4, 3, 6), (3, 2, 5),    # lower zag
            (2, 2, 4), (1, 1, 3), (0, 1, 2)]               # tip
    for y, x0, x1 in rows:
        for x in range(x0, x1 + 1):
            for z in (0, 1):
                v.set(x, y, z, GOLD if x > x0 else CHALK)  # pale leading edge
    return v


def punch():
    v = Vox()
    v.ellipsoid(4, 5, 3, 3.6, 3.0, 3.0, RED_MD)    # fist
    v.ellipsoid(8, 4, 3, 1.4, 1.4, 1.6, RED_MD)    # thumb
    for (x, y, z) in list(v.cells):
        if y <= 3 and cell_hash(x, y, z, 4) < 0.35:
            v.cells[(x, y, z)] = RED_DK            # shading
    v.box(2, 0, 1, 6, 1, 5, CHALK)                 # cuff
    return v


def fly_bait():
    v = Vox()
    for y in range(0, 6):                          # jar body
        for x in range(0, 7):
            for z in range(0, 7):
                if math.hypot(x - 3, z - 3) <= 3.2:
                    v.set(x, y, z, GOLD if y <= 2 else WOOD_PALE)
    v.box(1, 6, 1, 5, 6, 5, WOOD_DK)               # lid
    v.set(2, 7, 2, GRIME)                          # flies on the lid
    v.set(4, 7, 4, GRIME)
    v.set(6, 3, 3, GOLD)                           # drip
    v.set(6, 2, 3, GOLD)
    return v


def coin_flip():
    v = Vox()
    v.disc_x(0, 1, 5, 5, 4.6, GOLD)
    for (x, y, z) in list(v.cells):                # rim + face marking
        d = math.hypot(y - 5, z - 5)
        if d > 3.6:
            v.cells[(x, y, z)] = RUST
    for y in range(4, 7):                          # embossed slot
        v.set(0, y, 5, RUST)
        v.set(1, y, 5, RUST)
    return v


# ── Fly (exact world scale, centred — transforms are reset per frame) ───────
def fly():
    v = Vox()
    v.ellipsoid(4, 2, 3, 1.6, 1.4, 2.6, GRIME)     # abdomen
    v.ellipsoid(4, 2, 7, 1.2, 1.2, 1.2, GRIME)     # head
    v.set(3, 3, 8, RED_MD)                         # eyes
    v.set(5, 3, 8, RED_MD)
    for s in (-1, 1):                              # wings
        for dx in range(1, 4):
            for dz in range(0, 3):
                v.set(4 + s * dx, 4, 2 + dz, WOOD_PALE if dx < 3 else CHALK)
    return v


ITEMS = {
    "patch_kit": patch_kit, "wide_paddle": wide_paddle, "slow_mo": slow_mo,
    "steal": steal, "fast_serve": fast_serve, "tiny_paddle": tiny_paddle,
    "punch": punch, "fly_bait": fly_bait, "coin_flip": coin_flip,
}

if __name__ == "__main__":
    ball().write(os.path.join(ROOT, "ball"), "ball", 0.05)
    for name, fn in ITEMS.items():
        fn().write(os.path.join(ROOT, "items", name), "item", 0.05)
    # fly: max extent must be ~0.45 world units (FLY_RADIUS * 1.5)
    fly().write(os.path.join(ROOT, "fly"), "fly", 0.045)
    print("wrote", os.path.abspath(ROOT))
