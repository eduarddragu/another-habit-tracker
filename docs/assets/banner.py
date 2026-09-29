"""Regenerates the README banner (light and dark) and the icon.

Palette and type follow https://eduarddragu.dev. The banner shows the streak the way the app does;
the icon keeps the heatmap slice of the launcher icon. Run: python3 docs/assets/banner.py
"""
from pathlib import Path

OUT = Path(__file__).resolve().parent
SERIF = "'Cormorant Garamond', 'Iowan Old Style', Georgia, 'Times New Roman', serif"
SANS = "'DM Sans', -apple-system, 'Segoe UI', Helvetica, Arial, sans-serif"
MONO = "'Geist Mono', ui-monospace, SFMono-Regular, Menlo, Consolas, monospace"

THEMES = {
    "light": dict(bg="#faf8f5", border="#e2dbd2", fg="#1a1714", muted="#5a4e42", cell="#ece5db", accent="#c14f1e"),
    "dark": dict(bg="#1a1714", border="#342c26", fg="#faf8f5", muted="#b3a597", cell="#2a2420", accent="#d9632f"),
}

W, H = 1280, 360
STREAK = 42
WEEK = ["M", "T", "W", "T", "F", "S", "S"]
TODAY = 4  # Friday: the days before are done, today is still open.


def streak(t):
    """The app's streak hero: the number, its label, and this week's days (today outlined)."""
    right = W - 72
    parts = [
        f'<text x="{right}" y="232" text-anchor="end" font-family="{SANS}" font-size="160" font-weight="600" letter-spacing="-6" fill="{t["accent"]}">{STREAK}</text>',
        f'<text x="{right}" y="92" text-anchor="end" font-family="{MONO}" font-size="15" letter-spacing="4" fill="{t["muted"]}">DAYS IN A ROW</text>',
    ]
    cell, gap = 26, 10
    x0 = right - len(WEEK) * cell - (len(WEEK) - 1) * gap
    for i, day in enumerate(WEEK):
        x = x0 + i * (cell + gap)
        base = f'x="{x}" y="252" width="{cell}" height="{cell}" rx="6"'
        if i < TODAY:
            parts.append(f'<rect {base} fill="{t["accent"]}"/>')
        elif i == TODAY:
            parts.append(f'<rect x="{x + 1}" y="253" width="{cell - 2}" height="{cell - 2}" rx="5" fill="none" stroke="{t["accent"]}" stroke-width="2"/>')
        else:
            parts.append(f'<rect {base} fill="{t["cell"]}"/>')
        parts.append(f'<text x="{x + cell / 2}" y="304" text-anchor="middle" font-family="{MONO}" font-size="12" fill="{t["muted"]}">{day}</text>')
    return "\n  ".join(parts)


def banner(name, t):
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img" aria-label="(Another) Habit Tracker">
  <rect x="1" y="1" width="{W - 2}" height="{H - 2}" rx="18" fill="{t["bg"]}" stroke="{t["border"]}" stroke-width="2"/>
  <text x="72" y="92" font-family="{MONO}" font-size="15" letter-spacing="4" fill="{t["accent"]}">A HABIT TRACKER FOR ONE</text>
  <text x="68" y="150" font-family="{SERIF}" font-size="52" font-style="italic" font-weight="500" fill="{t["accent"]}">(Another)</text>
  <text x="66" y="232" font-family="{SERIF}" font-size="92" font-weight="500" letter-spacing="-2" fill="{t["fg"]}">Habit Tracker</text>
  <text x="72" y="278" font-family="{SANS}" font-size="22" fill="{t["muted"]}">It picks tonight's topic. Then it nags.</text>
  <text x="72" y="308" font-family="{SANS}" font-size="22" fill="{t["muted"]}">The streak dies at midnight.</text>
  {streak(t)}
</svg>
'''
    (OUT / f"banner-{name}.svg").write_text(svg)


def icon():
    """Same geometry as the launcher icon (res/drawable/ic_launcher_foreground.xml)."""
    t = THEMES["light"]
    levels = [[1.0, 0.5, 0.75], [0.28, 1.0, 0.5], [0.75, 0.28, None]]
    cell, gap, rx, stroke = 13.0, 3.5, 3.0, 1.8
    origin = (108 - (3 * cell + 2 * gap)) / 2
    parts = []
    for r, row in enumerate(levels):
        for c, a in enumerate(row):
            x, y = origin + c * (cell + gap), origin + r * (cell + gap)
            if a is None:
                h = stroke / 2
                parts.append(f'<rect x="{x+h}" y="{y+h}" width="{cell-stroke}" height="{cell-stroke}" rx="{rx-h}" fill="none" stroke="{t["accent"]}" stroke-width="{stroke}"/>')
            else:
                parts.append(f'<rect x="{x}" y="{y}" width="{cell}" height="{cell}" rx="{rx}" fill="{t["accent"]}" fill-opacity="{a}"/>')
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" viewBox="18 18 72 72" role="img" aria-label="(Another) Habit Tracker icon">'
           f'<rect x="18" y="18" width="72" height="72" rx="18" fill="{t["bg"]}"/>' + "".join(parts) + "</svg>\n")
    (OUT / "icon.svg").write_text(svg)


for name, t in THEMES.items():
    banner(name, t)
icon()
print("written:", ", ".join(sorted(p.name for p in OUT.glob("*.svg"))))
