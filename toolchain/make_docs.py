#!/usr/bin/env python3
"""
Builds the artwork used by the README from the same numbers the app uses:

  docs/preview.gif  – a short loop of the keyboard typing "limbo"
  docs/themes.svg   – the preset palette, read straight from theme/Theme.kt

    python3 toolchain/make_docs.py
"""
from __future__ import annotations

import math
import os
import re
from typing import Dict, List, Tuple

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(ROOT, "docs")
THEME_KT = os.path.join(ROOT, "app", "src", "main", "java", "app", "limbo", "keyboard", "theme", "Theme.kt")

SEED_RE = re.compile(
    r'Seed\(\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)"'
)

WALL_TOP = (0x0E, 0x16, 0x30)
WALL_BOTTOM = (0x29, 0x40, 0x6B)
ACCENT = (0x8F, 0xB3, 0xFF)
KEY_TOP = (0x33, 0x43, 0x5E)
KEY_BOTTOM = (0x1B, 0x25, 0x39)
TEXT = (0xF2, 0xF5, 0xFF)
MUTED = (0x9F, 0xAE, 0xC8)


def hexc(value: str) -> Tuple[int, int, int]:
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))  # type: ignore[return-value]


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def read_seeds() -> List[Dict[str, str]]:
    with open(THEME_KT, encoding="utf-8") as handle:
        source = handle.read()
    return [
        {"id": m[0], "ru": m[1], "en": m[2], "top": m[3], "bottom": m[4], "accent": m[5]}
        for m in SEED_RE.findall(source)
    ]


# ---------------------------------------------------------------------------- preview


class Board:
    """Key rectangles for the demo, laid out like the real keyboard."""

    def __init__(self, width: int, top: int, key_height: int = 44, gap: int = 6, pad: int = 7):
        self.width = width
        self.top = top
        self.key_height = key_height
        self.gap = gap
        self.pad = pad
        self.rows: List[List[Tuple[str, float, Tuple[int, int, int, int]]]] = []
        self.lookup: Dict[str, Tuple[int, int, int, int]] = {}
        self._layout()

    def _row(self, entries, y, inset=0.0):
        total_weight = sum(w for _, w in entries)
        available = self.width - self.pad * 2 - inset * 2 - self.gap * (len(entries) - 1)
        units = available / total_weight
        result = []
        x = self.pad + inset
        for label, weight in entries:
            w = units * weight
            rect = (int(x), int(y), int(x + w), int(y + self.key_height))
            result.append((label, weight, rect))
            if label not in self.lookup:
                self.lookup[label] = rect
            x += w + self.gap
        self.rows.append(result)

    def _layout(self):
        unit = (self.width - self.pad * 2 - self.gap * 9) / 10
        y = self.top
        self._row([(c, 1) for c in "qwertyuiop"], y)
        y += self.key_height + self.gap
        self._row([(c, 1) for c in "asdfghjkl"], y, inset=unit * 0.5)
        y += self.key_height + self.gap
        self._row([("SHIFT", 1.4)] + [(c, 1) for c in "zxcvbnm"] + [("BACK", 1.4)], y)
        y += self.key_height + self.gap
        self._row([("123", 1.5), ("GLOBE", 1.1), ("CLIP", 1.1), ("EMOJI", 1.1), (",", 0.9), ("SPACE", 4.4), (".", 0.9), ("ENTER", 1.9)], y)
        self.bottom = y + self.key_height


def draw_wallpaper(draw: ImageDraw.ImageDraw, width: int, height: int):
    for y in range(height):
        t = y / max(1, height - 1)
        draw.line([(0, y), (width, y)], fill=mix(WALL_TOP, WALL_BOTTOM, t ** 0.85))


def rounded(draw, rect, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(rect, radius=radius, fill=fill, outline=outline, width=width)


def draw_key(draw: ImageDraw.ImageDraw, rect, pressed=False, accent=False, radius=9):
    x0, y0, x1, y1 = rect
    if accent:
        top = ACCENT
        bottom = mix(ACCENT, (0, 0, 0), 0.25)
    else:
        top = mix(KEY_TOP, (255, 255, 255), 0.12)
        bottom = KEY_BOTTOM
    if pressed:
        top = mix(top, (0, 0, 0), 0.18)
        bottom = mix(bottom, (0, 0, 0), 0.2)
        y0 += 2
        y1 += 2
    layers = 10
    for i in range(layers):
        t = i / (layers - 1)
        draw.rounded_rectangle(
            [x0, y0 + (y1 - y0) * t - 0.5, x1, y1],
            radius=radius,
            fill=mix(top, bottom, t),
        )
    draw.rounded_rectangle(rect, radius=radius, outline=mix(top, (255, 255, 255), 0.25), width=1)


def text_center(draw, xy, text, font, fill):
    bbox = draw.textbbox((0, 0), text, font=font)
    draw.text((xy[0] - (bbox[2] - bbox[0]) / 2 - bbox[0], xy[1] - (bbox[3] - bbox[1]) / 2 - bbox[1]), text, font=font, fill=fill)


def label_for(key: str) -> str:
    return {
        "SHIFT": "\u21e7", "BACK": "\u232b", "GLOBE": "\u25cf\u25cf", "CLIP": "\u25a4",
        "EMOJI": "\u25d5", "SPACE": "", "ENTER": "\u21b5",
    }.get(key, key.upper() if len(key) == 1 else key)


def render_frame(typed: str, pressed: str | None, press_progress: float, word_index: int) -> Image.Image:
    width = 640
    board_top = 128
    board = Board(width, board_top + 10)
    height = board.bottom + 18
    image = Image.new("RGB", (width, height), WALL_TOP)
    draw = ImageDraw.Draw(image)
    draw_wallpaper(draw, width, height)

    try:
        from PIL import ImageFont
        font_key = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 17)
        font_small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 13)
        font_field = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 21)
        font_chip = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 14)
    except OSError:
        font_key = font_small = font_field = font_chip = None

    # message card
    rounded(draw, [22, 20, width - 22, 74], 20, (21, 30, 50), (60, 78, 112), 1)
    draw.text((40, 38), typed or " ", font=font_field, fill=TEXT)
    caret_x = 40 + (draw.textlength(typed, font=font_field) if font_field else 0) + 2
    if press_progress >= 0:
        draw.rectangle([caret_x, 36, caret_x + 2, 60], fill=ACCENT)

    # suggestion strip
    chips = ["limbo", "limbo\u00a0keyboard", "limbus"] if typed else ["Limbo \u2014 type freely"]
    x = 18
    for chip in chips:
        w = (draw.textlength(chip, font=font_chip) if font_chip else len(chip) * 8) + 34
        rounded(draw, [x, board_top - 46, x + w, board_top - 12], 15, (26, 37, 60))
        text_center(draw, (x + w / 2, board_top - 29), chip, font_chip, MUTED)
        x += w + 8

    # panel
    rounded(draw, [0, board_top - 2, width, height], 26, (17, 24, 40))
    for row in board.rows:
        for label, _, rect in row:
            pressed_here = pressed == label
            progress = press_progress if pressed_here else 0.0
            draw_key(draw, rect, pressed=progress > 0.35, accent=label == "ENTER")
            if label == "SPACE":
                continue
            color = (16, 22, 36) if label == "ENTER" else (TEXT if len(label) == 1 else MUTED)
            size = font_key if len(label) == 1 else font_small
            text_center(draw, ((rect[0] + rect[2]) / 2, (rect[1] + rect[3]) / 2), label_for(label), size, color)

    # magnified preview above the pressed key
    if pressed and pressed in board.lookup and len(pressed) == 1:
        rect = board.lookup[pressed]
        cx = (rect[0] + rect[2]) / 2
        bw, bh = 52, 56
        bx0 = max(8, min(width - bw - 8, cx - bw / 2))
        by0 = max(board_top - 74, rect[1] - bh - 12)
        rounded(draw, [bx0, by0 + 4, bx0 + bw, by0 + bh + 4], 16, (0, 0, 0))
        rounded(draw, [bx0, by0, bx0 + bw, by0 + bh], 16, mix(KEY_TOP, (255, 255, 255), 0.22), (120, 140, 180), 1)
        text_center(draw, (bx0 + bw / 2, by0 + bh / 2), pressed.upper(), font_field, TEXT)

    return image


def build_gif():
    sequence = "limbo"
    frames: List[Image.Image] = []
    delays: List[int] = []

    frames.append(render_frame("", None, 0, 0))
    delays.append(700)

    typed = ""
    for index, char in enumerate(sequence):
        frames.append(render_frame(typed, char, 1.0, index))
        delays.append(90)
        frames.append(render_frame(typed, char, 0.4, index))
        delays.append(70)
        typed += char
        frames.append(render_frame(typed, None, 0, index))
        delays.append(110)

    for _ in range(2):
        frames.append(render_frame(typed, None, 0, len(sequence)))
        delays.append(1400)

    converted = [frame.convert("P", palette=Image.ADAPTIVE, colors=128) for frame in frames]
    converted[0].save(
        os.path.join(DOCS, "preview.gif"),
        save_all=True,
        append_images=converted[1:],
        duration=delays,
        loop=0,
        optimize=True,
        disposal=2,
    )


# ---------------------------------------------------------------------------- presets


def build_theme_svg(seeds: List[Dict[str, str]]):
    columns = 5
    cell_w, cell_h = 228, 120
    rows = math.ceil(len(seeds) / columns)
    width = columns * cell_w + 24
    height = rows * cell_h + 24
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}" role="img" aria-label="Limbo presets">',
        "<defs>",
    ]
    for index, seed in enumerate(seeds):
        parts.append(
            f'<linearGradient id="g{index}" x1="0" y1="0" x2="1" y2="1">'
            f'<stop offset="0" stop-color="{seed["top"]}"/>'
            f'<stop offset="1" stop-color="{seed["bottom"]}"/></linearGradient>'
        )
    parts.append("</defs>")
    parts.append(f'<rect width="{width}" height="{height}" fill="#0b1020" rx="20"/>')

    for index, seed in enumerate(seeds):
        col, row = index % columns, index // columns
        x = 12 + col * cell_w
        y = 12 + row * cell_h
        w, h = cell_w - 12, cell_h - 12
        accent = seed["accent"]
        parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="18" fill="url(#g{index})"/>')
        # mini keyboard
        panel_x, panel_y = x + 14, y + 26
        parts.append(
            f'<rect x="{panel_x}" y="{panel_y}" width="{w - 28}" height="{h - 62}" rx="12" '
            f'fill="#ffffff" fill-opacity="0.12" stroke="#ffffff" stroke-opacity="0.18"/>'
        )
        for r in range(3):
            count = 8 - r
            key_w = (w - 40) / 8.6
            for k in range(count):
                kx = panel_x + 8 + k * (key_w + 3) + (r * key_w * 0.25)
                ky = panel_y + 8 + r * 11
                fill = accent if (r == 1 and k == 2) else "#ffffff"
                opacity = "0.85" if (r == 1 and k == 2) else "0.55"
                parts.append(
                    f'<rect x="{kx:.1f}" y="{ky:.1f}" width="{key_w:.1f}" height="8" rx="3" '
                    f'fill="{fill}" fill-opacity="{opacity}"/>'
                )
        parts.append(
            f'<text x="{x + 14}" y="{y + h - 12}" font-family="-apple-system,Segoe UI,Roboto,sans-serif" '
            f'font-size="13" font-weight="600" fill="#ffffff" fill-opacity="0.92">{seed["en"]}</text>'
        )
        parts.append(
            f'<circle cx="{x + w - 20}" cy="{y + h - 16}" r="5" fill="{accent}"/>'
        )
    parts.append("</svg>")
    with open(os.path.join(DOCS, "themes.svg"), "w", encoding="utf-8") as handle:
        handle.write("\n".join(parts))


def build_theme_cards(seeds: List[Dict[str, str]]) -> None:
    """One small card per preset, so the README gallery shows real colours."""
    from PIL import ImageFont

    out = os.path.join(DOCS, "themes")
    os.makedirs(out, exist_ok=True)
    try:
        font_name = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 15)
        font_hex = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", 11)
    except OSError:
        font_name = font_hex = None

    w, h = 240, 156
    for seed in seeds:
        top, bottom, accent = hexc(seed["top"]), hexc(seed["bottom"]), hexc(seed["accent"])
        image = Image.new("RGB", (w, h), top)
        draw = ImageDraw.Draw(image)
        for y in range(h):
            draw.line([(0, y), (w, y)], fill=mix(top, bottom, (y / (h - 1)) ** 0.9))
        # glow, layered from large and faint to small and warm
        glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        gd = ImageDraw.Draw(glow)
        for i in range(36, 0, -1):
            radius = 150 * i / 36
            alpha = int(40 * (1 - i / 36) ** 1.6)
            gd.ellipse([34 - radius, 10 - radius, 34 + radius, 10 + radius],
                       fill=accent + (alpha,))
        image = Image.alpha_composite(image.convert("RGBA"), glow).convert("RGB")
        draw = ImageDraw.Draw(image)
        # panel
        px0, py0, px1, py1 = 16, 34, w - 16, h - 52
        card = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        cd = ImageDraw.Draw(card)
        cd.rounded_rectangle([px0, py0, px1, py1], radius=18, fill=(14, 20, 34, 120), outline=(255, 255, 255, 26))
        image = Image.alpha_composite(image.convert("RGBA"), card)
        draw = ImageDraw.Draw(image)
        # keys
        for row in range(3):
            count = 8 - row
            key_w = (px1 - px0 - 22) / 8.4
            for k in range(count):
                kx = px0 + 11 + k * (key_w + 3) + row * key_w * 0.28
                ky = py0 + 9 + row * 12
                fill = accent if (row == 1 and k == 2) else mix(top, (255, 255, 255), 0.82)
                draw.rounded_rectangle([kx, ky, kx + key_w, ky + 8], radius=3, fill=fill)
        if font_name:
            draw.text((px0 + 4, py1 + 6), seed["en"], font=font_name, fill=(255, 255, 255))
            draw.text((px0 + 4, py1 + 24), seed["accent"].upper(), font=font_hex, fill=mix((255, 255, 255), top, 0.35))
        draw.ellipse([px1 - 12, py1 + 12, px1, py1 + 24], fill=accent)
        mask = Image.new("L", (w, h), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, w, h], radius=20, fill=255)
        image.putalpha(mask)
        image.save(os.path.join(out, f"{seed['id']}.png"))


if __name__ == "__main__":
    os.makedirs(DOCS, exist_ok=True)
    seeds = read_seeds()
    build_theme_svg(seeds)
    build_theme_cards(seeds)
    build_gif()
    print(f"Wrote docs/preview.gif and docs/themes.svg ({len(seeds)} presets)")
