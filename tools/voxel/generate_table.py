#!/usr/bin/env python3
"""Generates the voxel table + net asset used by MatchArenaRenderer.

Writes assets/models/table/{table.obj, table.mtl, table.png}.

Style: abandoned / old / rusty — 0.125-unit voxels, clustered wear (rust
blooms around damage epicenters and along edges), holes punched through the
tabletop, a chipped border line, rust creeping up the legs, a torn sagging
net. Same-color cell runs are merged so the mesh stays inside libGDX's
16-bit index limits.

The model is authored directly in GAMEPLAY units so the loader's per-axis fit
resolves to identity:
  - length 14 along local X (the loader rotates 90° about Y so X -> world Z)
  - width 6 along local Z
  - play surface top at y = 2.0 (MatchWorld3D.TABLE_TOP_Y)
  - net top at y = 2.5 (NET_TOP_Y) — also the bbox top (the posts reach it)
  - legs reach y = 0 (the floor)

Colors come from the game's 16-color palette (core .. config/Palette.java,
docs/art-style.md) so the retro filter quantizes them to themselves.

NOTE: libGDX's ObjLoader samples vt v top-based (no flip) — v here follows
that convention, not the standard OBJ bottom-based one.

Run from the repo root:  python3 tools/voxel/generate_table.py
"""

import math
import os
import random
import struct
import zlib

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "assets", "models", "table")
SEED = 20260610

# ── Palette (game palette entries) ──────────────────────────────────────────
COLORS = [
    "4A5A3C",  # 0  GREEN_DIM — table base green
    "7B8C5A",  # 1  GREEN     — light green dither
    "E8DCC0",  # 2  TEXT      — chalk white lines
    "3B342E",  # 3  BORDER    — dark wood
    "4D4036",  # 4  BORDER_HI — mid wood
    "6B5B4B",  # 5  TEXT_DIM  — pale wood / dust
    "1A1014",  # 6  BG2       — net mesh / grime
    "D89F66",  # 7  WARM      — net tape
    "8C5C36",  # 8  WARM_DIM  — rust
    "0A0608",  # 9  BG        — hole interior
    "5E1F1F",  # 10 RED_DIM   — deep rust
    "7A2A2A",  # 11 RED_GLOW  — rust bloom
]
GREEN, GREEN_LT, CHALK = 0, 1, 2
WOOD_DK, WOOD, WOOD_PALE = 3, 4, 5
GRIME, TAPE, RUST, HOLE_DK, RUST_DK, RUST_RD = 6, 7, 8, 9, 10, 11

GRID = 4
CELL_PX = 16

# Geometry (gameplay units)
LEN, WID = 14.0, 6.0
HX, HZ = LEN / 2, WID / 2
SURF_Y = 2.0
NET_TOP = 2.5
SLAB = 0.25
CELL = 0.125                  # voxel size everywhere
NCX, NCZ = int(LEN / CELL), int(WID / CELL)     # 112 x 48 top cells

rng = random.Random(SEED)

verts, uvs, faces = [], [], []
_vcache = {}

for ci in range(len(COLORS)):
    cx, cy = ci % GRID, ci // GRID
    uvs.append(((cx + 0.5) / GRID, (cy + 0.5) / GRID))   # top-based v (libGDX)

NORMALS = {"+x": 1, "-x": 2, "+y": 3, "-y": 4, "+z": 5, "-z": 6}


def vid(p):
    key = (round(p[0], 4), round(p[1], 4), round(p[2], 4))
    if key not in _vcache:
        verts.append(key)
        _vcache[key] = len(verts)
    return _vcache[key]


def quad(p1, p2, p3, p4, color, normal):
    vt = color + 1
    vn = NORMALS[normal]
    faces.append([(vid(p1), vt, vn), (vid(p2), vt, vn), (vid(p3), vt, vn), (vid(p4), vt, vn)])


def box(x0, y0, z0, x1, y1, z1, color, skip=()):
    if "+y" not in skip:
        quad((x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0), color, "+y")
    if "-y" not in skip:
        quad((x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1), color, "-y")
    if "+x" not in skip:
        quad((x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1), color, "+x")
    if "-x" not in skip:
        quad((x0, y0, z1), (x0, y1, z1), (x0, y1, z0), (x0, y0, z0), color, "-x")
    if "+z" not in skip:
        quad((x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1), color, "+z")
    if "-z" not in skip:
        quad((x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0), color, "-z")


def cell_hash(ix, iz, salt=0):
    """Deterministic per-cell noise in [0, 1)."""
    h = (ix * 73856093) ^ (iz * 19349663) ^ ((SEED + salt) * 83492791)
    h = (h ^ (h >> 13)) * 0x5BD1E995 & 0xFFFFFFFF
    return ((h ^ (h >> 15)) & 0xFFFF) / 65536.0


# ── Wear field: edges + damage epicenters + noise ───────────────────────────
EPICENTERS = [(rng.uniform(-HX, HX), rng.uniform(-HZ, HZ), rng.uniform(0.7, 2.0))
              for _ in range(12)]


def wear(x, z, ix, iz):
    edge = max(0.0, 1.0 - min(HX - abs(x), HZ - abs(z)) / 1.0) * 0.45
    blob = 0.0
    for bx, bz, r in EPICENTERS:
        d = math.hypot(x - bx, z - bz)
        if d < r:
            blob = max(blob, (1.0 - d / r) * 0.85)
    return min(1.0, edge + blob + cell_hash(ix, iz) * 0.30)


# ── Tabletop ─────────────────────────────────────────────────────────────────
center_col = NCZ // 2
top_color = [[None] * NCZ for _ in range(NCX)]   # None = hole

for ix in range(NCX):
    for iz in range(NCZ):
        x = -HX + (ix + 0.5) * CELL
        z = -HZ + (iz + 0.5) * CELL
        w = wear(x, z, ix, iz)
        h = cell_hash(ix, iz, 7)
        border = ix < 2 or ix >= NCX - 2 or iz < 2 or iz >= NCZ - 2
        line = iz == center_col or iz == center_col - 1

        if border:
            # chipped chalk border — fades to dust, sometimes worn through
            c = CHALK if w < 0.52 else (WOOD_PALE if w < 0.72 else GREEN)
        elif line:
            c = CHALK if w < 0.45 else (WOOD_PALE if w < 0.62 else GREEN)
        elif w > 0.86 and h > 0.5:
            c = None                                   # hole, clustered
        elif w > 0.64:
            c = (RUST, RUST_DK, GRIME, RUST_RD)[int(h * 4)]
        elif w > 0.50 and h > 0.6:
            c = WOOD_PALE                              # dusty faded patch
        elif h < 0.16:
            c = GREEN_LT
        elif h > 0.93:
            c = GRIME
        else:
            c = GREEN
        top_color[ix][iz] = c

# emit top faces with run-length merging along x
for iz in range(NCZ):
    ix = 0
    while ix < NCX:
        c = top_color[ix][iz]
        if c is None:
            ix += 1
            continue
        run = ix
        while run < NCX and top_color[run][iz] == c:
            run += 1
        x0, x1 = -HX + ix * CELL, -HX + run * CELL
        z0, z1 = -HZ + iz * CELL, -HZ + (iz + 1) * CELL
        quad((x0, SURF_Y, z0), (x0, SURF_Y, z1), (x1, SURF_Y, z1), (x1, SURF_Y, z0), c, "+y")
        ix = run

# hole interiors: dark walls toward solid neighbours + a sunken floor
HOLE_FLOOR = SURF_Y - 0.125
for ix in range(NCX):
    for iz in range(NCZ):
        if top_color[ix][iz] is not None:
            continue
        x0, x1 = -HX + ix * CELL, -HX + (ix + 1) * CELL
        z0, z1 = -HZ + iz * CELL, -HZ + (iz + 1) * CELL
        quad((x0, HOLE_FLOOR, z0), (x0, HOLE_FLOOR, z1),
             (x1, HOLE_FLOOR, z1), (x1, HOLE_FLOOR, z0), HOLE_DK, "+y")
        if ix + 1 >= NCX or top_color[ix + 1][iz] is not None:
            quad((x1, HOLE_FLOOR, z1), (x1, SURF_Y, z1), (x1, SURF_Y, z0), (x1, HOLE_FLOOR, z0), GRIME, "-x")
        if ix - 1 < 0 or top_color[ix - 1][iz] is not None:
            quad((x0, HOLE_FLOOR, z0), (x0, SURF_Y, z0), (x0, SURF_Y, z1), (x0, HOLE_FLOOR, z1), GRIME, "+x")
        if iz + 1 >= NCZ or top_color[ix][iz + 1] is not None:
            quad((x0, HOLE_FLOOR, z1), (x0, SURF_Y, z1), (x1, SURF_Y, z1), (x1, HOLE_FLOOR, z1), GRIME, "-z")
        if iz - 1 < 0 or top_color[ix][iz - 1] is not None:
            quad((x1, HOLE_FLOOR, z0), (x1, SURF_Y, z0), (x0, SURF_Y, z0), (x0, HOLE_FLOOR, z0), GRIME, "+z")

# ── Slab rim (pixelated, rust-streaked) + plain underside ───────────────────
RIM_ROWS = int(SLAB / CELL)                       # 2 rows
y_base = SURF_Y - SLAB


def rim_color(i, row, salt):
    h = cell_hash(i, row, salt)
    if h > 0.84:
        return (RUST, RUST_DK)[int(h * 9) % 2]
    # plank pattern: 4-cell runs so the merger can collapse them
    return WOOD if (i // 4 + row) % 2 == 0 else WOOD_DK


def rim_strip(fixed_axis, sign, count, salt, normal):
    for row in range(RIM_ROWS):
        y0r, y1r = y_base + row * CELL, y_base + (row + 1) * CELL
        i = 0
        while i < count:
            c = rim_color(i, row, salt)
            run = i
            while run < count and rim_color(run, row, salt) == c:
                run += 1
            a0, a1 = -(count * CELL) / 2 + i * CELL, -(count * CELL) / 2 + run * CELL
            if fixed_axis == "z":
                zf = sign * HZ
                if sign > 0:
                    quad((a0, y0r, zf), (a1, y0r, zf), (a1, y1r, zf), (a0, y1r, zf), c, "+z")
                else:
                    quad((a1, y0r, zf), (a0, y0r, zf), (a0, y1r, zf), (a1, y1r, zf), c, "-z")
            else:
                xf = sign * HX
                if sign > 0:
                    quad((xf, y0r, a0), (xf, y1r, a0), (xf, y1r, a1), (xf, y0r, a1), c, "+x")
                else:
                    quad((xf, y0r, a1), (xf, y1r, a1), (xf, y1r, a0), (xf, y0r, a0), c, "-x")
            i = run


rim_strip("z", 1, NCX, 11, "+z")
rim_strip("z", -1, NCX, 12, "-z")
rim_strip("x", 1, NCZ, 13, "+x")
rim_strip("x", -1, NCZ, 14, "-x")
quad((-HX, y_base, -HZ), (HX, y_base, -HZ), (HX, y_base, HZ), (-HX, y_base, HZ), WOOD_DK, "-y")

# ── Legs (rust creeping up from the floor) + cross beams ─────────────────────
LEG = 0.5
LEG_CELLS = int(LEG / CELL)                       # 4
LEG_H = SURF_Y - SLAB
LEG_ROWS = int(LEG_H / CELL)                      # 14


def leg_face_color(col, row, salt):
    # row 0 at the floor — rust probability decays with height
    rust_p = 0.65 * max(0.0, 1.0 - row / 7.0) + 0.08
    h = cell_hash(col, row, salt)
    if h < rust_p:
        return (RUST, RUST_DK, GRIME)[int(h * 13) % 3]
    return WOOD if h < 0.85 else WOOD_DK


leg_salt = 100
for sx in (-1, 1):
    for sz in (-1, 1):
        cx, cz = sx * (HX - 1.5), sz * (HZ - 1.25)
        x0, x1 = cx - LEG / 2, cx + LEG / 2
        z0, z1 = cz - LEG / 2, cz + LEG / 2
        leg_salt += 17
        for row in range(LEG_ROWS):
            y0r, y1r = row * CELL, (row + 1) * CELL
            for salt_off, faceset in ((0, "z"), (1, "x")):
                col = 0
                while col < LEG_CELLS:
                    c = leg_face_color(col, row, leg_salt + salt_off)
                    run = col
                    while run < LEG_CELLS and leg_face_color(run, row, leg_salt + salt_off) == c:
                        run += 1
                    a0, a1 = col * CELL, run * CELL
                    if faceset == "z":
                        quad((x0 + a0, y0r, z1), (x0 + a1, y0r, z1), (x0 + a1, y1r, z1), (x0 + a0, y1r, z1), c, "+z")
                        quad((x0 + a1, y0r, z0), (x0 + a0, y0r, z0), (x0 + a0, y1r, z0), (x0 + a1, y1r, z0), c, "-z")
                    else:
                        quad((x1, y0r, z0 + a0), (x1, y1r, z0 + a0), (x1, y1r, z0 + a1), (x1, y0r, z0 + a1), c, "+x")
                        quad((x0, y0r, z0 + a1), (x0, y1r, z0 + a1), (x0, y1r, z0 + a0), (x0, y0r, z0 + a0), c, "-x")
                    col = run
for sx in (-1, 1):
    cx = sx * (HX - 1.5)
    box(cx - 0.125, 0.45, -(HZ - 1.25), cx + 0.125, 0.75, HZ - 1.25, WOOD_DK)

# ── Net: torn weave, sagging tape, rusty posts ───────────────────────────────
NT = 0.08
ncols = int((WID - 0.25) / CELL)                  # 46
nrows = int((NET_TOP - SURF_Y) / CELL)            # 4
z_start = -(ncols * CELL) / 2

# one big tear region
tear_c = rng.randint(8, ncols - 8)
tear_r = rng.uniform(2.0, 3.4)

for r in range(nrows - 1):                        # weave rows (tape handled below)
    for cidx in range(ncols):
        h = cell_hash(cidx, r, 55)
        if r > 0 and (r + cidx) % 2 != 0:
            continue                              # weave pattern hole
        if math.hypot(cidx - tear_c, (r - 1) * 2.2) < tear_r and r > 0:
            continue                              # the tear
        if r > 0 and h < 0.16:
            continue                              # random missing threads
        c = GRIME if h > 0.12 else RUST_DK
        zb = z_start + cidx * CELL
        yb = SURF_Y + r * CELL
        box(-NT / 2, yb, zb, NT / 2, yb + CELL, zb + CELL, c)

# sagging tape: dips one cell in the middle, a few segments missing
for cidx in range(ncols):
    h = cell_hash(cidx, 9, 56)
    if h < 0.07:
        continue                                  # missing tape chunk
    sag = CELL * (math.sin(math.pi * cidx / (ncols - 1)) ** 2) * 0.9
    yb = NET_TOP - CELL - sag
    zb = z_start + cidx * CELL
    c = TAPE if h < 0.86 else RUST
    box(-NT / 2 - 0.01, yb, zb, NT / 2 + 0.01, yb + CELL, zb + CELL, c)

# posts (pixelated, rusty) — they reach NET_TOP and define the bbox top
for sz in (-1, 1):
    zb = sz * HZ
    z0p, z1p = (zb - 0.125, zb) if sz > 0 else (zb, zb + 0.125)
    post_rows = int((NET_TOP - (SURF_Y - SLAB)) / CELL)   # 6
    for row in range(post_rows):
        y0r = SURF_Y - SLAB + row * CELL
        h = cell_hash(row, sz, 57)
        c = (WOOD_PALE, RUST, RUST_DK, WOOD)[int(h * 4)]
        box(-0.0625, y0r, z0p, 0.0625, y0r + CELL, z1p, c)

# ── Write palette texture ────────────────────────────────────────────────────
def write_png(path):
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
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(bytes(rows), 9)) + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


# ── Write OBJ + MTL ──────────────────────────────────────────────────────────
os.makedirs(OUT_DIR, exist_ok=True)
write_png(os.path.join(OUT_DIR, "table.png"))

with open(os.path.join(OUT_DIR, "table.mtl"), "w") as f:
    f.write("# generated by tools/voxel/generate_table.py\n"
            "newmtl voxel\nKd 1.000 1.000 1.000\nmap_Kd table.png\n")

NORMAL_VECS = ["1 0 0", "-1 0 0", "0 1 0", "0 -1 0", "0 0 1", "0 0 -1"]
with open(os.path.join(OUT_DIR, "table.obj"), "w") as f:
    f.write("# voxel table+net — generated by tools/voxel/generate_table.py\n"
            "mtllib table.mtl\no voxel_table\n")
    for v in verts:
        f.write(f"v {v[0]} {v[1]} {v[2]}\n")
    for u in uvs:
        f.write(f"vt {u[0]:.5f} {u[1]:.5f}\n")
    for n in NORMAL_VECS:
        f.write(f"vn {n}\n")
    f.write("usemtl voxel\n")
    for q in faces:
        f.write("f " + " ".join(f"{v}/{t}/{n}" for v, t, n in q) + "\n")

idx = 6 * len(faces)
print(f"verts={len(verts)} quads={len(faces)} (tris={2 * len(faces)}, indices={idx})")
assert idx <= 32767, "exceeds 16-bit index budget — merge more aggressively"
assert len(verts) <= 32767, "vertex count exceeds 16-bit budget"
print(f"wrote {os.path.abspath(OUT_DIR)}")
