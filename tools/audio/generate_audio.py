#!/usr/bin/env python3
"""Generates all synthesized audio for the game (pure stdlib, deterministic).

Writes:
  assets/Sounds/Effects/ui_click.wav    — dry short click (buttons, READY)
  assets/Sounds/Effects/ui_hover.wav    — tiny soft tick (hover rise)
  assets/Sounds/Effects/item_use.wav    — thump + metallic shimmer
  assets/Sounds/Effects/life_lost.wav   — bulb pop + dark descending tone
  assets/Sounds/Effects/fly_buzz.wav    — seamless 1.6 s fly-wings loop
  assets/Sounds/Music/ambient_bunker.wav — seamless 64 s ambient loop
                                           (low detuned drones, slow swells,
                                           sparse dissonant pings w/ echo,
                                           distant metal groans)

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


# ── Music: 64 s seamless ambient loop (22.05 kHz mono) ──────────────────────
MR = 22050
LOOP = 64.0


def q(f):
    """Quantize a frequency to an integer cycle count over the loop."""
    return round(f * LOOP) / LOOP


def music():
    n = int(LOOP * MR)
    out = [0.0] * n

    # 1. detuned low drone + dark pad partials, all loop-exact, slow LFOs
    voices = (
        (q(55.00), 0.15, q(1 / 16.0), 0.0),       # A1
        (q(55.45), 0.13, q(1 / 16.0), math.pi),   # beating partner
        (q(82.41), 0.065, q(3 / 64.0), 1.3),      # E2
        (q(130.81), 0.05, q(5 / 64.0), 3.9),      # C3 (minor colour)
        (q(61.74), 0.035, q(2 / 64.0), 5.1),      # D#2 — uneasy tritone vs A
    )
    for f, amp, lfo, ph in voices:
        for i in range(n):
            t = i / MR
            env = 0.8 + 0.2 * math.sin(TWO_PI * lfo * t + ph)
            out[i] += amp * env * math.sin(TWO_PI * f * t)

    # 2. brown-noise rumble bed (circular crossfade for the seam)
    bed = [0.0] * n
    x = 0.0
    for i in range(n):
        x = (x + 0.02 * (rng.random() * 2 - 1)) * 0.998
        bed[i] = x
    fade = int(0.75 * MR)
    for i in range(fade):
        a = i / fade
        bed[i] = bed[i] * a + bed[n - fade + i] * (1 - a)
    for i in range(n):
        out[i] += 2.2 * bed[i]                   # bed is tiny; scale up

    # 3. sparse dissonant pings with echo taps
    pings = ((6.5, 932.33, 0.075), (18.4, 622.25, 0.06), (31.0, 739.99, 0.07),
             (44.2, 466.16, 0.055), (55.5, 587.33, 0.065))
    for t0, f, amp in pings:
        for tap, gain in ((0.0, 1.0), (0.311, 0.5), (0.622, 0.27), (0.933, 0.14)):
            start = int((t0 + tap) * MR)
            length = int(2.4 * MR)
            for j in range(length):
                if start + j >= n:
                    break
                t = j / MR
                env = min(1.0, t / 0.01) * math.exp(-t / 0.9)
                s = math.sin(TWO_PI * f * t) + 0.6 * math.sin(TWO_PI * f * 1.003 * t)
                out[start + j] += amp * gain * env * s * 0.5

    # 4. distant metal groans (slow downward gliss, vibrato, hann swell)
    for t0, fa, fb in ((24.0, 146.83, 103.83), (50.0, 130.81, 92.50)):
        length = int(5.0 * MR)
        start = int(t0 * MR)
        phase = 0.0
        for j in range(length):
            if start + j >= n:
                break
            t = j / MR
            p = j / length
            f = fa + (fb - fa) * p
            vib = 1.0 + 0.004 * math.sin(TWO_PI * 4.5 * t)
            phase += TWO_PI * f * vib / MR
            env = 0.5 * (1 - math.cos(TWO_PI * p))          # hann
            out[start + j] += 0.06 * env * (math.sin(phase) + 0.4 * math.sin(phase * 2.01))

    # gentle soft-clip + headroom
    return [math.tanh(s * 1.15) * 0.85 for s in out]


if __name__ == "__main__":
    fx = os.path.join(ROOT, "Effects")
    write_wav(os.path.join(fx, "ui_click.wav"), ui_click(), SR)
    write_wav(os.path.join(fx, "ui_hover.wav"), ui_hover(), SR)
    write_wav(os.path.join(fx, "item_use.wav"), item_use(), SR)
    write_wav(os.path.join(fx, "life_lost.wav"), life_lost(), SR)
    write_wav(os.path.join(fx, "fly_buzz.wav"), fly_buzz(), SR)
    write_wav(os.path.join(ROOT, "Music", "ambient_bunker.wav"), music(), MR)
    print("done:", os.path.abspath(ROOT))
