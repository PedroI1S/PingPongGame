#!/usr/bin/env python3
"""Generates the voxel bunker arena + life bulb used by MatchArenaRenderer.

Writes:
  assets/models/arena/arena.{obj,mtl,png}   — the room, AUTHORED IN WORLD UNITS
  assets/models/arena/bulb.{obj,mtl,png}    — one life bulb (centred, world scale)

The arena is authored directly in gameplay coordinates (no fitting transform):
floor top at y=0, table at the origin, P1 side +z. Camera sits at
(0, 4.5, 13.5), so the room spans x ±11, z ±14, ceiling y=9.

Composed from several voxel grids at different cell sizes (1.0 shell,
0.5/0.25 details) merged into ONE obj. The shell grid treats everything
outside its bounds as solid so only room-facing surfaces are emitted.

Life-bulb rack positions are shared constants with MatchArenaRenderer:
bulbs at x = -3.0 + i*0.55 (i 0..4), y 4.75, z ±3.5.

Run from the repo root:  python3 tools/voxel/generate_arena.py
"""

import math
import os

from generate_props import (Vox, COLORS, write_palette_png, cell_hash, GRID,
                            GREEN, GREEN_LT, CHALK, WOOD_DK, WOOD, WOOD_PALE,
                            GRIME, GOLD, RUST, BLACK, RED_DK, RED_MD, RED)

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "assets", "models", "arena")

# Room bounds (world units)
RX, RZ, RH = 11, 14, 9

# ── Multi-part OBJ writer ────────────────────────────────────────────────────
# The arena ships as TWO meshes (shell + props) so each stays inside libGDX's
# 16-bit index budget. Both share arena.png.
class MeshOut:
    def __init__(self):
        self.verts, self.vcache, self.faces = [], {}, []


shell_out = MeshOut()
props_out = MeshOut()
sign_out = MeshOut()
clutter_out = MeshOut()


def emit(out, vox, cell, origin, outside_solid=False):
    verts, vcache, faces = out.verts, out.vcache, out.faces
    cells = vox.cells
    if outside_solid:
        xs = [k[0] for k in cells]
        ys = [k[1] for k in cells]
        zs = [k[2] for k in cells]
        bx0, bx1 = min(xs), max(xs)
        by0, by1 = min(ys), max(ys)
        bz0, bz1 = min(zs), max(zs)

    def filled(x, y, z):
        if (x, y, z) in cells:
            return True
        if outside_solid and not (bx0 <= x <= bx1 and by0 <= y <= by1 and bz0 <= z <= bz1):
            return True
        return False

    def vid(x, y, z):
        key = (round(origin[0] + x * cell, 4),
               round(origin[1] + y * cell, 4),
               round(origin[2] + z * cell, 4))
        if key not in vcache:
            verts.append(key)
            vcache[key] = len(verts)
        return vcache[key]

    for (x, y, z), c in sorted(cells.items()):
        vt = c + 1
        if not filled(x, y + 1, z):
            faces.append(([vid(x, y + 1, z), vid(x, y + 1, z + 1),
                           vid(x + 1, y + 1, z + 1), vid(x + 1, y + 1, z)], vt, 3))
        if not filled(x, y - 1, z):
            faces.append(([vid(x, y, z), vid(x + 1, y, z),
                           vid(x + 1, y, z + 1), vid(x, y, z + 1)], vt, 4))
        if not filled(x + 1, y, z):
            faces.append(([vid(x + 1, y, z), vid(x + 1, y + 1, z),
                           vid(x + 1, y + 1, z + 1), vid(x + 1, y, z + 1)], vt, 1))
        if not filled(x - 1, y, z):
            faces.append(([vid(x, y, z + 1), vid(x, y + 1, z + 1),
                           vid(x, y + 1, z), vid(x, y, z)], vt, 2))
        if not filled(x, y, z + 1):
            faces.append(([vid(x, y, z + 1), vid(x + 1, y, z + 1),
                           vid(x + 1, y + 1, z + 1), vid(x, y + 1, z + 1)], vt, 5))
        if not filled(x, y, z - 1):
            faces.append(([vid(x + 1, y, z), vid(x, y, z),
                           vid(x, y + 1, z), vid(x + 1, y + 1, z)], vt, 6))


# ── Room shell (1.0-unit cells) ──────────────────────────────────────────────
shell = Vox()


def floor_color(x, z):
    """x/z are integer cell coords; cell centre is x+0.5, z+0.5."""
    d_wall = min(RX - abs(x + 0.5), RZ - abs(z + 0.5))
    h = cell_hash(x, 0, z, 21)
    if d_wall <= 1:
        return BLACK                              # baked AO at the skirt
    if d_wall <= 2 and h < 0.6:
        return GRIME
    if h < 0.06:
        return RUST                               # stains
    if h < 0.12:
        return GREEN_DIM_MOSS
    return GRIME if h < 0.45 else WOOD_DK


GREEN_DIM_MOSS = GREEN                            # mossy patches


def wall_color(u, y, salt):
    h = cell_hash(u, y, salt)
    if y <= 0 or y >= RH - 1:
        return BLACK                              # AO at skirt + ceiling line
    if u % 8 == 0:
        return BLACK                              # panel grooves
    if h < 0.08 and y >= 4:
        return RUST                               # leaks from the pipe line
    if h < 0.16 and y <= 2:
        return GRIME
    return WOOD_DK if h < 0.55 else WOOD


def ceil_color(x, z):
    d_lamp = math.hypot(x + 0.5, z + 0.5)
    h = cell_hash(x, 9, z, 23)
    if d_lamp < 3.0:
        return RUST if h < 0.5 else WOOD          # warm halo baked around the lamp
    if min(RX - abs(x + 0.5), RZ - abs(z + 0.5)) <= 1:
        return BLACK
    return BLACK if h < 0.55 else GRIME


# floor (y = -1), ceiling (y = RH), walls
for x in range(-RX, RX):
    for z in range(-RZ, RZ):
        shell.set(x, -1, z, floor_color(x, z))
        shell.set(x, RH, z, ceil_color(x, z))
for y in range(0, RH):
    for z in range(-RZ, RZ):
        shell.set(-RX - 1, y, z, wall_color(z, y, 31))
        shell.set(RX, y, z, wall_color(z, y, 32))
    for x in range(-RX, RX):
        shell.set(x, y, -RZ - 1, wall_color(x, y, 33))
        shell.set(x, y, RZ, wall_color(x, y, 34))
emit(shell_out, shell, 1.0, (0.0, 0.0, 0.0), outside_solid=True)
shell_faces = len(shell_out.faces)

# ── Ceiling beams (0.5 cells) ────────────────────────────────────────────────
beams = Vox()
for bi, bz in enumerate((-18, -8, 8, 18)):        # cell z of each beam (0.5 → ~±9)
    for x in range(-21, 21):
        c = WOOD_DK if cell_hash(x, bi, bz, 41) > 0.12 else RUST
        beams.set(x, 0, bz, c)
        beams.set(x, 1, bz, c)
emit(props_out, beams, 0.5, (0.25, 7.95, 0.25))              # top at 8.95 — clear of the ceiling face

# ── Wall pipes (0.5 cells) with brackets ─────────────────────────────────────
pipes = Vox()
for z in range(-27, 27):
    c = GRIME if cell_hash(z, 0, 0, 42) > 0.2 else RUST
    pipes.set(0, 0, z, c)
    c2 = GRIME if cell_hash(z, 1, 0, 43) > 0.25 else RUST
    pipes.set(42, 0, z, c2)                       # 42*0.5 = 21 → right wall at ~+10.2
for z in (-20, -8, 8, 20):                        # brackets to the wall
    pipes.set(0, 0, z, WOOD_PALE)
    pipes.set(42, 0, z, WOOD_PALE)
emit(props_out, pipes, 0.5, (-10.7, 6.5, 0.25))              # runs along both side walls

# ── Neon sign "PONG" on the back wall (0.5 cells) ────────────────────────────
FONT = {
    "P": ["111", "101", "111", "100", "100"],
    "O": ["111", "101", "101", "101", "111"],
    "N": ["101", "111", "111", "111", "101"],
    "G": ["111", "100", "101", "101", "111"],
}
# Mounted on the LEFT wall, reading P-O-N-G into the depth (P nearest P1).
backing = Vox()
letters = Vox()
text = "PONG"
total_w = sum(len(FONT[ch][0]) for ch in text) + (len(text) - 1)
for k in range(-2, total_w + 2):                  # backing board (z columns)
    for y in range(-1, 6):
        backing.set(0, y, -k, BLACK)
col = 0
for ch in text:
    glyph = FONT[ch]
    w = len(glyph[0])
    for gy, row in enumerate(glyph):
        for gx, bit in enumerate(row):
            if bit == "1":
                c = RED_MD if cell_hash(col + gx, gy, 0, 44) > 0.15 else RED_DK
                letters.set(0, 4 - gy, -(col + gx), c)
    col += w + 1
emit(props_out, backing, 0.5, (-10.95, 5.4, 3.0), outside_solid=True)
# letters live in their own mesh so the renderer can drive them emissive
emit(sign_out, letters, 0.5, (-10.45, 5.4, 3.0))

# ── Arsenal: weapons hung on the BACK wall (faces the player) ───────────────
# '.'=empty  s=steel  c=edge  w=wood  d=dark wood  g=gun metal  r=rust
WEAPON_COLORS = {"s": WOOD_PALE, "c": CHALK, "w": WOOD, "d": WOOD_DK, "g": GRIME, "r": RUST}
WEAPONS = [
    # the shotgun — pride of place
    (("................",
      "ggggggggggggggg.",
      "wwwwgggggggggggg",
      "wwwww...g.......",
      "www............."), 5.0, -9.4),
    # machete
    (("wwssssssssss.",
      "wwsssssssssc.",
      ".....ccccc..."), 4.5, -0.2),
    # revolver
    (("gggggggg.",
      ".rgggg...",
      "..www....",
      "..ww....."), 4.9, 8.7),
    # knife
    (("wwsssss.",
      "..ssc..."), 3.5, 8.9),
    # fire axe
    ((".rrrr.",
      "rrrrw.",
      "..ww..",
      "..ww..",
      "..ww..",
      "..ww..",
      "..ww..",
      "..ww.."), 4.9, -10.6),
]
for art, top_y, x0 in WEAPONS:
    rows = len(art)
    cols = max(len(r) for r in art)
    board = Vox()
    for bx in range(-1, cols + 1):                # dark board behind each piece
        for by in range(-1, rows + 1):
            board.set(bx, by, 0, WOOD_DK if (bx + by) % 7 else BLACK)
    emit(props_out, board, 0.25, (x0 - 0.25, top_y - rows * 0.25, -13.95), outside_solid=True)
    piece = Vox()
    for gy, row in enumerate(art):
        for gx, ch in enumerate(row):
            if ch != ".":
                piece.set(gx, rows - 1 - gy, 0, WEAPON_COLORS[ch])
    piece.set(cols // 2, rows, 0, WOOD_PALE)      # hook
    emit(props_out, piece, 0.25, (x0, top_y - rows * 0.25, -13.7))

# ── Right wall: hanging chains with meat hooks (read fine edge-on) ──────────
for ci, cz in enumerate((-6.5, -4.0, -1.5, 1.0)):
    chain = Vox()
    drop = 14 + (ci * 5) % 9                      # varied lengths
    for y in range(drop):
        if y % 2 == 0:
            chain.set(0, -y, 0, GRIME)
    chain.set(0, -drop, 0, RUST)                  # the hook
    chain.set(0, -drop - 1, 0, RUST)
    chain.set(-1, -drop - 1, 0, RUST)
    emit(props_out, chain, 0.25, (10.55, 8.8, cz))

# ── Blast door behind the opponent (0.25 cells) ──────────────────────────────
door = Vox()
for x in range(0, 16):
    for y in range(0, 22):
        edge = x in (0, 15) or y in (0, 21)
        h = cell_hash(x, y, 0, 45)
        c = WOOD_DK if edge else (GRIME if h > 0.2 else RUST)
        door.set(x, y, 0, c)
for y in (4, 17):                                 # hinge plates
    door.set(0, y, 1, RUST)
    door.set(1, y, 1, RUST)
door.set(12, 10, 1, WOOD_PALE)                    # handle
door.set(12, 11, 1, WOOD_PALE)
emit(props_out, door, 0.25, (4.0, 0.0, -13.95), outside_solid=True)

# ── Hanging lamp over the table (0.25 cells) ─────────────────────────────────
lamp = Vox()
for y in range(0, 10):                            # chain (dashed)
    if y % 2 == 0:
        lamp.set(0, 14 + y, 0, GRIME)
for ly, r in ((13, 1.2), (12, 2.4), (11, 3.6)):   # cone shade
    for x in range(-4, 5):
        for z in range(-4, 5):
            d = math.hypot(x, z)
            if d <= r:
                lamp.set(x, ly, z, GRIME if d <= r - 1 else WOOD_DK)
lamp.set(0, 10, 0, GOLD)                          # the bulb
emit(props_out, lamp, 0.25, (0.0, 3.4, 0.0))                 # bulb ≈ y 6.0, shade above

# ── Life-bulb racks over each half (0.25 cells) ──────────────────────────────
# Bulb x positions (world): -3.0 + i*0.5  — mirrored in MatchArenaRenderer.
for side in (1, -1):
    rack = Vox()
    for x in range(0, 11):                        # bar from x -3.25 .. -0.5
        rack.set(x, 0, 0, GRIME if cell_hash(x, side, 0, 46) > 0.15 else RUST)
    for i in range(5):                            # hooks above each bulb (x = 1 + 2i)
        rack.set(1 + i * 2, -1, 0, GRIME)
    for ex in (0, 10):                            # chains to the ceiling
        for y in range(1, 16):
            if y % 2 == 0:
                rack.set(ex, y, 0, GRIME)
    rack_x = 0.75 if side > 0 else -3.25   # P1 right, P2 left (no overlap on screen)
    emit(props_out, rack, 0.25, (rack_x, 5.05, side * 3.5 - 0.125))

# ── Corner clutter: crates + barrel (0.25 cells) ─────────────────────────────
def crate(origin, size, salt):
    v = Vox()
    s = size
    for x in range(s):
        for y in range(s):
            for z in range(s):
                edge = sum(a in (0, s - 1) for a in (x, y, z)) >= 2
                h = cell_hash(x + salt, y, z, 47)
                v.set(x, y, z, WOOD_DK if edge else (WOOD if h > 0.25 else WOOD_PALE))
    return v


emit(props_out, crate((0, 0, 0), 7, 1), 0.25, (-10.4, 0.0, -13.2))
emit(props_out, crate((0, 0, 0), 5, 2), 0.25, (-8.9, 0.0, -12.6))
emit(props_out, crate((0, 0, 0), 5, 3), 0.25, (-10.3, 1.75, -12.9))

barrel = Vox()
for y in range(10):
    for x in range(-3, 4):
        for z in range(-3, 4):
            d = math.hypot(x, z)
            if d <= 3.2:
                band = y in (2, 7)
                h = cell_hash(x, y, z, 48)
                barrel.set(x, y, z, WOOD_PALE if band else (RUST if h < 0.5 else GRIME))
emit(props_out, barrel, 0.25, (9.6, 0.0, -12.4))

# ── Dangling wires along the ceiling (0.25 cells) ────────────────────────────
wires = Vox()
for z in range(0, 48):
    sag = int(2.5 * math.sin(math.pi * (z % 16) / 16.0) ** 2)
    if z % 2 == 0:
        wires.set(0, -sag, z, BLACK)
emit(props_out, wires, 0.25, (-6.0, 8.7, -12.0))

# ── Floor clutter along both side walls (0.25 cells, own mesh) ──────────────
def pallet():
    v = Vox()
    for x in range(8):                            # three bearers
        for z in (0, 2, 4):
            v.set(x, 0, z, WOOD_DK)
    for z in range(0, 5):                         # deck boards with gaps
        if z % 2 == 0:
            continue
        for x in range(8):
            h = cell_hash(x, 1, z, 61)
            v.set(x, 1, z, WOOD if h > 0.3 else WOOD_PALE)
    for x in range(8):                            # edge boards
        v.set(x, 1, 0, WOOD_PALE)
        v.set(x, 1, 4, WOOD_PALE)
    return v


def drum_standing():
    v = Vox()
    for y in range(9):
        for x in range(-2, 3):
            for z in range(-2, 3):
                if math.hypot(x, z) <= 2.4:
                    band = y in (2, 6)
                    h = cell_hash(x, y, z, 62)
                    v.set(x, y, z, WOOD_PALE if band else (RUST if h < 0.55 else GRIME))
    return v


def drum_tipped():
    v = Vox()
    for z in range(8):                            # axis along z, resting on the floor
        for x in range(-2, 3):
            for y in range(0, 5):
                if math.hypot(x, y - 2.2) <= 2.3:
                    band = z in (1, 6)
                    h = cell_hash(x, y, z, 63)
                    v.set(x, y, z, WOOD_PALE if band else (GRIME if h < 0.5 else RUST))
    return v


def rubble(salt):
    v = Vox()
    for i in range(70):
        h1 = cell_hash(i, 0, salt, 64)
        h2 = cell_hash(i, 1, salt, 65)
        h3 = cell_hash(i, 2, salt, 66)
        rx = int((h1 - 0.5) * 12)
        rz = int((h2 - 0.5) * 12)
        d = math.hypot(rx, rz)
        if d > 5:
            continue
        ry = 1 if (d < 2 and h3 > 0.5) else 0
        c = (GRIME, WOOD_DK, RUST, BLACK)[int(h3 * 4)]
        v.set(rx, ry, rz, c)
    return v


# left side
emit(clutter_out, pallet(), 0.25, (-10.4, 0.0, 4.6))
emit(clutter_out, pallet(), 0.25, (-10.25, 0.5, 4.75))    # stacked
emit(clutter_out, drum_tipped(), 0.25, (-10.0, 0.0, 0.4))
emit(clutter_out, rubble(3), 0.25, (-9.6, 0.0, -3.6))
emit(clutter_out, crate((0, 0, 0), 6, 9), 0.25, (-10.5, 0.0, -7.4))
# right side
emit(clutter_out, drum_standing(), 0.25, (9.8, 0.0, 2.4))
emit(clutter_out, drum_standing(), 0.25, (10.3, 0.0, 3.3))
emit(clutter_out, crate((0, 0, 0), 7, 10), 0.25, (9.7, 0.0, -2.0))
emit(clutter_out, crate((0, 0, 0), 5, 11), 0.25, (9.95, 1.75, -1.8))  # stacked
emit(clutter_out, rubble(7), 0.25, (9.6, 0.0, 5.8))
emit(clutter_out, drum_tipped(), 0.25, (9.5, 0.0, -5.6))

# ── Write arena obj ──────────────────────────────────────────────────────────
os.makedirs(ROOT, exist_ok=True)
write_palette_png(os.path.join(ROOT, "arena.png"))


def write_obj(out, name):
    with open(os.path.join(ROOT, name + ".mtl"), "w") as f:
        f.write("# generated by tools/voxel/generate_arena.py\n"
                "newmtl voxel\nKd 1.000 1.000 1.000\nmap_Kd arena.png\n")
    with open(os.path.join(ROOT, name + ".obj"), "w") as f:
        f.write("# voxel bunker arena — generated by tools/voxel/generate_arena.py\n"
                f"mtllib {name}.mtl\no {name}\n")
        for v in out.verts:
            f.write(f"v {v[0]} {v[1]} {v[2]}\n")
        for ci in range(len(COLORS)):
            gx, gy = ci % GRID, ci // GRID
            f.write(f"vt {(gx + 0.5) / GRID:.5f} {(gy + 0.5) / GRID:.5f}\n")
        for n in ("1 0 0", "-1 0 0", "0 1 0", "0 -1 0", "0 0 1", "0 0 -1"):
            f.write(f"vn {n}\n")
        f.write("usemtl voxel\n")
        for q, vt, vn in out.faces:
            f.write("f " + " ".join(f"{v}/{vt}/{vn}" for v in q) + "\n")
    print(f"{name}: verts={len(out.verts)} quads={len(out.faces)} "
          f"indices={len(out.faces) * 6}")
    assert len(out.faces) * 6 <= 32767, name + " exceeds 16-bit index budget"
    assert len(out.verts) <= 32767, name + " exceeds 16-bit vertex budget"


write_obj(shell_out, "arena")
write_obj(props_out, "props")
write_obj(sign_out, "sign")
write_obj(clutter_out, "clutter")

# ── Life bulb (separate model; tinted lit/dead by the renderer) ──────────────
bulb = Vox()
bulb.ellipsoid(2, 2, 2, 2.1, 2.1, 2.1, CHALK)     # glass
bulb.box(1, 5, 1, 3, 5, 3, GRIME)                 # cap
bulb.box(1, 4, 1, 3, 4, 3, GRIME)
bulb.write(ROOT, "bulb", 0.07)
print("wrote", os.path.abspath(ROOT))
