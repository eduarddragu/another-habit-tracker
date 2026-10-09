"""Regenerates the README banner and badges (light and dark) and the icon.

Palette and type follow https://eduarddragu.dev. The banner shows the streak the way the app does;
the icon keeps the heatmap slice of the launcher icon. GitHub can't load web fonts, so each SVG
carries the fonts it uses, cut to plain ASCII and fetched from Google Fonts while it's written.
Run: python3 docs/assets/banner.py
"""
import base64
import re
import string
import urllib.parse
import urllib.request
from pathlib import Path

OUT = Path(__file__).resolve().parent
# Each embedded face has its own family name, so a weight or a style never falls back onto another.
FACES = {
    "serif": ("Cormorant Garamond", "ital,wght@0,500"),
    "serifItalic": ("Cormorant Garamond", "ital,wght@1,500"),
    "sans": ("DM Sans", "wght@400"),
    "sansBold": ("DM Sans", "wght@600"),
    "mono": ("Geist Mono", "wght@500"),
}
SERIF = "'e-serif', 'Cormorant Garamond', Georgia, serif"
SERIF_ITALIC = "'e-serifItalic', 'Cormorant Garamond', Georgia, serif"
SANS = "'e-sans', 'DM Sans', -apple-system, 'Segoe UI', Helvetica, Arial, sans-serif"
SANS_BOLD = "'e-sansBold', 'DM Sans', -apple-system, 'Segoe UI', Helvetica, Arial, sans-serif"
MONO = "'e-mono', 'Geist Mono', ui-monospace, SFMono-Regular, Menlo, Consolas, monospace"

_faces = {}


def font_style(*keys):
    """A <style> with the given faces as data URLs (woff2, ASCII only)."""
    rules = []
    for key in keys:
        if key not in _faces:
            family, axes = FACES[key]
            text = urllib.parse.quote("".join(ch for ch in string.printable if ch.isprintable()))
            url = f"https://fonts.googleapis.com/css2?family={family.replace(' ', '+')}:{axes}&text={text}"
            # A modern User-Agent, or Google serves TTF instead of woff2.
            ua = {"User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"}
            css = urllib.request.urlopen(urllib.request.Request(url, headers=ua), timeout=30).read().decode()
            src = re.search(r"url\((https:[^)]+)\)", css).group(1)
            data = base64.b64encode(urllib.request.urlopen(urllib.request.Request(src, headers=ua), timeout=30).read()).decode()
            _faces[key] = f"@font-face{{font-family:'e-{key}';src:url(data:font/woff2;base64,{data}) format('woff2');}}"
        rules.append(_faces[key])
    return "<style>" + "".join(rules) + "</style>"


def badges(name, t, items, file):
    """A row of badges in the app's labels: the key on the accent, the value on the card colour."""
    # Small enough for the whole row, "by" included, to fit the README column on one line.
    h, pad, char, gap, size = 22, 7, 7.0, 6, 10
    x, parts = 0, []
    for key, value in items:
        kw = len(key) * char + 2 * pad
        vw = len(value) * char + 2 * pad if value else 0
        w = kw + vw
        parts.append(f'<rect x="{x + 0.5}" y="0.5" width="{w - 1}" height="{h - 1}" rx="6" fill="{t["cell"]}" stroke="{t["border"]}"/>')
        parts.append(f'<path d="M{x + 6},0.5 H{x + kw} V{h - 0.5} H{x + 6} A5.5,5.5 0 0 1 {x + 0.5},{h - 6} V6 A5.5,5.5 0 0 1 {x + 6},0.5 Z" fill="{t["accent"]}"/>' if vw else f'<rect x="{x + 0.5}" y="0.5" width="{w - 1}" height="{h - 1}" rx="6" fill="{t["accent"]}"/>')
        parts.append(f'<text x="{x + pad}" y="15" font-family="{MONO}" font-size="{size}" letter-spacing="1" fill="{t["bg"]}">{key}</text>')
        if vw:
            parts.append(f'<text x="{x + kw + pad}" y="15" font-family="{MONO}" font-size="{size}" letter-spacing="1" fill="{t["fg"]}">{value}</text>')
        x += w + gap
    width = x - gap
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="{width:.0f}" height="{h}" viewBox="0 0 {width:.0f} {h}" role="img" aria-label="{", ".join(" ".join(filter(None, i)) for i in items)}">{font_style("mono")}{"".join(parts)}</svg>\n'
    (OUT / f"{file}-{name}.svg").write_text(svg)

THEMES = {
    "light": dict(bg="#faf8f5", border="#e2dbd2", fg="#1a1714", muted="#5a4e42", cell="#ece5db", accent="#b04619"),
    "dark": dict(bg="#1a1714", border="#342c26", fg="#faf8f5", muted="#b3a597", cell="#2a2420", accent="#e8743f"),
}

W, H = 1280, 360
STREAK = 42
WEEK = ["M", "T", "W", "T", "F", "S", "S"]
TODAY = 4  # Friday: the days before are done, today is still open.


def streak(t):
    """The app's streak hero: the number, its label, and this week's days (today outlined)."""
    right = W - 72
    parts = [
        f'<text x="{right}" y="232" text-anchor="end" font-family="{SANS_BOLD}" font-size="160" font-weight="600" letter-spacing="-6" fill="{t["accent"]}">{STREAK}</text>',
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
  {font_style("serif", "serifItalic", "sans", "sansBold", "mono")}
  <rect x="1" y="1" width="{W - 2}" height="{H - 2}" rx="18" fill="{t["bg"]}" stroke="{t["border"]}" stroke-width="2"/>
  <text x="72" y="92" font-family="{MONO}" font-size="15" letter-spacing="4" fill="{t["accent"]}">A HABIT TRACKER FOR ONE</text>
  <text x="68" y="150" font-family="{SERIF_ITALIC}" font-size="52" font-style="italic" font-weight="500" fill="{t["accent"]}">(Another)</text>
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


BADGES = [("KOTLIN", "2.4"), ("UI", "JETPACK COMPOSE"), ("ANDROID", "14+"), ("INTERNET", "NOT EVEN ASKED"), ("LICENSE", "GPL V3")]

for name, t in THEMES.items():
    banner(name, t)
    badges(name, t, BADGES, "badges")
    badges(name, t, [("BY", "EDUARDDRAGU.DEV")], "badge-by")
print("written:", ", ".join(sorted(p.name for p in OUT.glob("*.svg"))))
