#!/usr/bin/env python3
"""Generates all synthesized audio for the game (pure stdlib, deterministic).

Writes:
  assets/Sounds/Effects/ui_click.wav    — dry short click (buttons, READY)
  assets/Sounds/Effects/ui_hover.wav    — tiny soft tick (hover rise)
  assets/Sounds/Effects/item_use.wav    — thump + metallic shimmer
  assets/Sounds/Effects/life_lost.wav   — bulb pop + dark descending tone
  assets/Sounds/Effects/fly_buzz.wav    — seamless 1.6 s fly-wings loop
  assets/Sounds/Music/bunker_bar.wav    — seamless 64 s jukebox loop
                                           (lazy swung 12-bar blues: honky-tonk
                                           piano, walking upright bass, brush
                                           hits, vinyl crackle, faint room bed)

Loop seams: every periodic component uses an integer number of cycles over
the loop length; noise beds get a circular crossfade.

Run from the repo root:  python3 tools/audio/generate_audio.py
"""

import array
import math
import os
import random
import wave

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "assets", "Sounds")
TWO_PI = 2.0 * math.pi
rng = random.Random(20260610)


def write_wav(path, samples, rate):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    pcm = array.array("h", (max(-32767, min(32767, int(s * 32767))) for s in samples))
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(pcm.tobytes())
    print(f"{os.path.relpath(path, ROOT):40s} {len(samples) / rate:6.2f}s")


# ── SFX (44.1 kHz) ───────────────────────────────────────────────────────────
SR = 44100


def ui_click():
    n = int(0.06 * SR)
    out = [0.0] * n
    phase = 0.0
    for i in range(n):
        t = i / SR
        f = 900.0 - 6000.0 * t          # quick downward blip
        phase += TWO_PI * max(f, 320.0) / SR
        out[i] = 0.7 * math.sin(phase) * math.exp(-t / 0.012)
        if t < 0.004:                    # transient
            out[i] += 0.25 * (rng.random() * 2 - 1) * (1 - t / 0.004)
    return out


def ui_hover():
    n = int(0.045 * SR)
    out = [0.0] * n
    for i in range(n):
        t = i / SR
        out[i] = 0.22 * math.sin(TWO_PI * 1400 * t) * math.exp(-t / 0.008)
    return out


def item_use():
    n = int(0.4 * SR)
    out = [0.0] * n
    partials = ((2080.0, 0.10, 0.05), (3150.0, 0.07, 0.035), (4730.0, 0.05, 0.025))
    phase = 0.0
    for i in range(n):
        t = i / SR
        f = 160.0 * math.exp(-t * 9.0) + 88.0   # thump glide
        phase += TWO_PI * f / SR
        s = 0.8 * math.sin(phase) * math.exp(-t / 0.07)
        for pf, pa, ptau in partials:           # metal shimmer
            s += pa * math.sin(TWO_PI * pf * t) * math.exp(-t / ptau)
        if t < 0.003:
            s += 0.3 * (rng.random() * 2 - 1)
        out[i] = s
    return out


def life_lost():
    n = int(0.8 * SR)
    out = [0.0] * n
    phase = 0.0
    for i in range(n):
        t = i / SR
        s = 0.0
        if t < 0.012:                            # the pop
            s += 0.65 * (rng.random() * 2 - 1) * (1 - t / 0.012)
        f = 85.0 + 335.0 * math.exp(-t * 11.0)   # falling dark tone
        phase += TWO_PI * f / SR
        s += 0.5 * math.sin(phase) * math.exp(-t / 0.20)
        s += 0.25 * math.sin(TWO_PI * 58 * t) * math.exp(-t / 0.12)  # sub thud
        out[i] = s
    return out


def fly_buzz():
    dur = 1.6
    n = int(dur * SR)
    f0 = 190.0                                   # 304 cycles over 1.6 s — seamless
    out = [0.0] * n
    phase = 0.0
    for i in range(n):
        t = i / SR
        wob = 1.0 + 0.012 * math.sin(TWO_PI * 3.125 * t)   # 5 cycles
        phase += TWO_PI * f0 * wob / SR
        s = 0.0
        for h in range(1, 9):                    # rough saw-ish spectrum
            s += math.sin(phase * h) / (h ** 1.15)
        am = 1.0 + 0.35 * math.sin(TWO_PI * 25.0 * t) + 0.2 * math.sin(TWO_PI * 7.5 * t)
        out[i] = 0.16 * s * am
    return out


# ── Music: 64 s seamless jukebox loop (22.05 kHz mono) ──────────────────────
# 90 BPM, 4/4 → 96 beats = 24 bars = two choruses of a 12-bar blues in F.
# Note tails wrap around modulo the loop length, so the seam stays clean.
MR = 22050
LOOP = 64.0
BEAT = LOOP / 96.0                               # 90 BPM


def midi_hz(m):
    return 440.0 * 2 ** ((m - 69) / 12.0)


def add_piano(out, n, beat, midi, dur_beats, amp):
    """Honky-tonk piano: pair of slightly detuned 'strings' per note."""
    f = midi_hz(midi)
    start = int(beat * BEAT * MR)
    dur = dur_beats * BEAT
    tau = 0.16 + 0.20 * dur
    det = TWO_PI * f * 1.0038
    w = TWO_PI * f
    for j in range(int((dur + 0.8) * MR)):
        t = j / MR
        env = min(1.0, t / 0.005) * math.exp(-t / tau)
        if t > dur:
            env *= math.exp(-(t - dur) / 0.06)
        s = 0.0
        for h, ha in ((1, 1.0), (2, 0.45), (3, 0.22), (4, 0.10)):
            s += ha * (math.sin(w * h * t) + math.sin(det * h * t))
        out[(start + j) % n] += amp * env * s * 0.5


def add_bass(out, n, beat, midi, amp=0.17):
    """Upright-bass pluck, one beat long."""
    f = midi_hz(midi)
    start = int(beat * BEAT * MR)
    w = TWO_PI * f
    for j in range(int(0.92 * BEAT * MR)):
        t = j / MR
        env = min(1.0, t / 0.006) * math.exp(-t / 0.38)
        s = math.sin(w * t) + 0.35 * math.sin(2 * w * t) + 0.08 * math.sin(3 * w * t)
        out[(start + j) % n] += amp * env * s


def add_brush(out, n, beat, amp=0.030):
    """Soft brushed-snare tick (high-passed noise burst)."""
    start = int(beat * BEAT * MR)
    prev = 0.0
    for j in range(int(0.09 * MR)):
        t = j / MR
        white = rng.random() * 2 - 1
        out[(start + j) % n] += amp * (white - prev) * math.exp(-t / 0.028)
        prev = white


# F blues melody material (F Ab Bb B C Eb F). Times in beats, swing baked in
# (offbeats land on the +0.67 triplet spot). One 48-beat chorus.
MELODY = (
    (0.00, 72, 0.6), (0.67, 70, 0.3), (1.00, 68, 0.6), (2.00, 65, 1.8),
    (5.67, 65, 0.3), (6.00, 68, 0.6), (6.67, 70, 1.2),
    (8.00, 72, 0.9), (9.00, 71, 0.4), (9.67, 70, 0.3), (10.00, 68, 1.6),
    (13.00, 65, 2.0),
    (16.00, 70, 0.6), (16.67, 72, 0.3), (17.00, 75, 1.4), (18.67, 72, 1.2),
    (20.00, 70, 0.6), (20.67, 68, 0.3), (21.00, 70, 0.6), (22.00, 68, 1.4),
    (24.00, 65, 1.6), (25.67, 68, 0.3), (26.00, 70, 0.6), (26.67, 72, 1.6),
    (29.00, 72, 0.9), (30.00, 70, 1.4),
    (32.00, 72, 0.6), (33.00, 75, 1.2), (34.00, 72, 0.9),
    (36.00, 70, 0.6), (36.67, 71, 0.3), (37.00, 72, 0.6), (38.00, 70, 1.4),
    (40.00, 65, 0.9), (41.67, 68, 0.3), (42.00, 65, 1.8),
    (44.00, 72, 0.4), (44.67, 70, 0.4), (45.33, 68, 0.4), (46.00, 65, 1.6),
)

# 12-bar form: bass roots and piano shell voicings (root/3rd/b7).
FORM = ("F", "F", "F", "F", "Bb", "Bb", "F", "F", "C", "Bb", "F", "C")
ROOTS = {"F": 41, "Bb": 46, "C": 48}
SHELLS = {"F": (53, 57, 63), "Bb": (56, 58, 62), "C": (52, 58, 60)}


def music():
    n = int(LOOP * MR)
    out = [0.0] * n

    # 1. rhythm section: walking bass + piano comping on 2 & 4 + brushes
    for bar in range(24):
        chord = FORM[bar % 12]
        nxt = FORM[(bar + 1) % 12]
        root = ROOTS[chord]
        b0 = bar * 4.0
        walk = [root, root + 4, root + 7]
        if nxt == chord:
            walk.append(root + 9)                # stay: land on the 6th
        else:                                    # chromatic approach note
            walk.append(ROOTS[nxt] + (1 if bar % 2 else -1))
        for k, m in enumerate(walk):
            add_bass(out, n, b0 + k, m)
        for hit in (1.0, 3.0):                   # backbeat comp, short & soft
            for m in SHELLS[chord]:
                add_piano(out, n, b0 + hit, m, 0.45, 0.050)
            add_brush(out, n, b0 + hit)

    # 2. melody: full chorus, then a sparser softer variation
    for t, m, d in MELODY:
        add_piano(out, n, t, m, d, 0.105)
    for i, (t, m, d) in enumerate(MELODY):
        if i % 3 != 2:
            add_piano(out, n, t + 48.0, m, d, 0.085)

    # 3. faint room-noise bed (kept from the bunker mix, way down in level).
    # Generate fade extra samples and blend the overhang into the head so the
    # value at sample 0 continues seamlessly from sample n-1.
    fade = int(0.75 * MR)
    bed = [0.0] * (n + fade)
    x = 0.0
    for i in range(n + fade):
        x = (x + 0.02 * (rng.random() * 2 - 1)) * 0.998
        bed[i] = x
    for i in range(fade):
        a = i / fade
        bed[i] = bed[i] * a + bed[n + i] * (1 - a)
    for i in range(n):
        out[i] += 0.55 * bed[i]

    # 4. vinyl crackle: sparse tiny pops
    for _ in range(140):
        start = rng.randrange(n)
        gain = 0.05 + 0.07 * rng.random()
        for j in range(rng.randrange(2, 6)):
            out[(start + j) % n] += gain * (rng.random() * 2 - 1)

    # gentle soft-clip + headroom
    return [math.tanh(s * 1.15) * 0.85 for s in out]


if __name__ == "__main__":
    fx = os.path.join(ROOT, "Effects")
    write_wav(os.path.join(fx, "ui_click.wav"), ui_click(), SR)
    write_wav(os.path.join(fx, "ui_hover.wav"), ui_hover(), SR)
    write_wav(os.path.join(fx, "item_use.wav"), item_use(), SR)
    write_wav(os.path.join(fx, "life_lost.wav"), life_lost(), SR)
    write_wav(os.path.join(fx, "fly_buzz.wav"), fly_buzz(), SR)
    write_wav(os.path.join(ROOT, "Music", "bunker_bar.wav"), music(), MR)
    print("done:", os.path.abspath(ROOT))
