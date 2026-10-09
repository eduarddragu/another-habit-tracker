#!/usr/bin/env python3
"""Synthesizes the notification sounds (res/raw/*.wav). Standard library only; no samples, no licensing.

A soft bell: a sine partial plus a quieter octave and a faint inharmonic partial, fast attack,
exponential decay. Run: python3 scripts/make_chimes.py
"""
import math
import struct
import wave
from pathlib import Path

RATE = 44_100
OUT = Path(__file__).resolve().parent.parent / "app/src/main/res/raw"


def bell(freq: float, start: float, length: float, gain: float, total: float, decay_rate: float = 5.5) -> list[float]:
    samples = [0.0] * int(total * RATE)
    first = int(start * RATE)
    for i in range(int(length * RATE)):
        t = i / RATE
        attack = min(1.0, t / 0.006)  # 6 ms: a soft click-free onset
        decay = math.exp(-t * decay_rate)
        tone = math.sin(2 * math.pi * freq * t) + 0.35 * math.sin(2 * math.pi * freq * 2 * t) * math.exp(-t * 9) + 0.12 * math.sin(2 * math.pi * freq * 2.76 * t) * math.exp(-t * 14)
        if first + i < len(samples):
            samples[first + i] += gain * attack * decay * tone
    return samples


def mix(*tracks: list[float]) -> list[float]:
    return [sum(values) for values in zip(*tracks)]


def write(name: str, samples: list[float], peak_db: float) -> None:
    peak = max(abs(s) for s in samples)
    scale = (10 ** (peak_db / 20)) / peak
    fade = int(0.03 * RATE)  # silence the tail cleanly
    frames = bytearray()
    for i, s in enumerate(samples):
        tail = min(1.0, (len(samples) - i) / fade)
        frames += struct.pack("<h", int(max(-1.0, min(1.0, s * scale * tail)) * 32767))
    with wave.open(str(OUT / f"{name}.wav"), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(bytes(frames))


# Reminder: two notes up a fourth (G5, C6), friendly and short.
total = 1.1
write("reminder_chime", mix(bell(783.99, 0.0, 0.9, 0.8, total), bell(1046.50, 0.14, 0.95, 1.0, total)), peak_db=-3)

# Last call: a rising C major arpeggio (C6, E6, G6) with a held top note, a bit louder.
total = 1.6
write(
    "last_call_chime",
    mix(bell(1046.50, 0.0, 0.8, 0.8, total), bell(1318.51, 0.12, 0.8, 0.85, total), bell(1567.98, 0.24, 1.3, 1.0, total)),
    peak_db=-1,
)
# Session end, softer (meditation, reading, chores): two low notes down a fourth (A5, E5), slow decay,
# quieter. The end of a calm practice, not a call to action.
total = 2.4
write("session_soft_chime", mix(bell(880.00, 0.0, 1.8, 0.7, total, decay_rate=2.6), bell(659.25, 0.45, 1.9, 0.9, total, decay_rate=2.4)), peak_db=-9)

print("written:", ", ".join(sorted(p.name for p in OUT.glob("*.wav"))))
