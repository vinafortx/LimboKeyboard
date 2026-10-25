#!/usr/bin/env python3
"""
Builds the artwork used by the README from the same numbers the app uses.

The palette derivation below mirrors ThemeFactory.build() in Theme.kt, including the neutral
frosted-glass rules, so the pictures on the project page cannot drift away from the product.

  docs/preview.gif      – a short loop of the keyboard typing "limbo"
  docs/themes/*.png     – one card per preset
  docs/themes.svg       – the whole palette in one vector sheet

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

Color = Tuple[int, int, int]
WHITE: Color = (255, 255, 255)
BLACK: Color = (0, 0, 0)


# ---------------------------------------------------------------------------- colour maths


def hexc(value: str) -> Color:
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))  # type: ignore[return-value]


def mix(a: Color, b: Color, t: float) -> Color:
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))  # type: ignore[return-value]


def alpha(c: Color, a: float) -> Tuple[int, int, int, int]:
    return (c[0], c[1], c[2], max(0, min(255, int(a * 255))))


class Look:
    """Everything Glass.kt needs to paint one preset."""

    def __init__(self, seed: Dict[str, object]):
        self.dark: bool = bool(seed["dark"])
        self.wall_top = hexc(str(seed["top"]))
        self.wall_bottom = hexc(str(seed["bottom"]))
        self.accent = hexc(str(seed["accent"]))
        self.frost = 0.58 if self.dark else 0.66

        average = mix(self.wall_top, self.wall_bottom, 0.35)
        if self.dark:
            base = mix(average, BLACK, 0.74)
            panel_top = mix(base, WHITE, 0.05)
            panel_bottom = mix(base, WHITE, 0.03)
            self.key_top = mix(base, WHITE, 0.125)
            self.key_bottom = mix(base, WHITE, 0.105)
            self.text = WHITE
            self.text_secondary = mix(WHITE, base, 0.45)
        else:
            base = mix(average, WHITE, 0.55)
            panel_top = mix(base, BLACK, 0.055)
            panel_bottom = mix(base, BLACK, 0.075)
            self.key_top = mix(base, WHITE, 0.75)
            self.key_bottom = mix(base, WHITE, 0.66)
            self.text = mix(base, BLACK, 0.84)
            self.text_secondary = mix(base, BLACK, 0.48)

        veil = 0.010 + 0.030 * self.frost if self.dark else 0.026 + 0.060 * self.frost
        self.panel_top = mix(panel_top, WHITE, veil)
        self.panel_bottom = mix(panel_bottom, WHITE, veil)
        self.border_amount = 0.055 if self.dark else 0.05
        self.edge_amount = 0.12 if self.dark else 0.34
        self.edge_solid = mix(self.panel_top, WHITE, self.edge_amount) if self.dark else mix(self.panel_top, BLACK, 0.08)
        self.accent_text = BLACK if (0.2126 * self.accent[0] + 0.7152 * self.accent[1] + 0.0722 * self.accent[2]) > 150 else WHITE


# ---------------------------------------------------------------------------- preset parsing

SEED_START = re.compile(r"Seed\(\s*")
QUOTED = re.compile(r'"([^"]*)"')


def read_seeds() -> List[Dict[str, object]]:
    with open(THEME_KT, encoding="utf-8") as handle:
        source = handle.read()
    body = source[source.index("private val seeds = listOf("):]
    seeds: List[Dict[str, object]] = []
    for chunk in body.split("Seed(")[1:]:
        block = chunk.split("),")[0]
        strings = QUOTED.findall(block)
        if len(strings) < 6:
            continue
        seeds.append(
            {
                "id": strings[0],
                "ru": strings[1],
                "en": strings[2],
                "top": strings[3],
                "bottom": strings[4],
                "accent": strings[5],
                "dark": "dark = false" not in block,
                "style": (re.search(r"style = KeyStyle\.(\w+)", block) or [None, "GLASS"])[1],
            }
        )
    return seeds


# ---------------------------------------------------------------------------- preview board


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
        self._row(
            [("123", 1.5), ("GLOBE", 1.1), ("CLIP", 1.1), ("EMOJI", 1.1), (",", 0.9), ("SPACE", 4.4), (".", 0.9), ("ENTER", 1.9)],
            y,
        )
        self.bottom = y + self.key_height


def draw_wallpaper(image: Image.Image, look: Look) -> Image.Image:
    """Vertical wall gradient plus one soft bloom, composited honestly."""
    width, height = image.size
    draw = ImageDraw.Draw(image)
    for y in range(height):
        t = (y / max(1, height - 1)) ** 0.85
        draw.line([(0, y), (width, y)], fill=mix(look.wall_top, look.wall_bottom, t))

    bloom = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    bd = ImageDraw.Draw(bloom)
    strength = 0.12 if look.dark else 0.10
    for i in range(48, 0, -1):
        radius = width * 1.05 * i / 48
        a = int(255 * strength * (1 - i / 48) ** 1.8)
        bd.ellipse(
            [width * 0.2 - radius, height * 0.1 - radius, width * 0.2 + radius, height * 0.1 + radius],
            fill=(look.accent[0], look.accent[1], look.accent[2], a),
        )
    return Image.alpha_composite(image, bloom)


def vertical_gradient(image: Image.Image, rect, top: Color, bottom: Color) -> None:
    x0, y0, x1, y1 = (int(v) for v in rect)
    if y1 <= y0:
        return
    draw = ImageDraw.Draw(image)
    for y in range(y0, y1):
        t = (y - y0) / max(1, (y1 - y0 - 1))
        draw.line([(x0, y), (x1, y)], fill=mix(top, bottom, t))


def rounded_vertical_gradient(image: Image.Image, rect, radius: int, top: Color, bottom: Color) -> None:
    layer = Image.new("RGBA", image.size, (0, 0, 0, 0))
    vertical_gradient(layer, rect, top, bottom)
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([int(v) for v in rect], radius=radius, fill=255)
    layer.putalpha(mask)
    image.paste(Image.alpha_composite(image, layer), (0, 0))


def on(base: Color, overlay: Color, amount: float) -> Color:
    """Opaque equivalent of painting `overlay` at `amount` alpha on top of `base`."""
    return mix(base, overlay, amount)


def draw_key(image: Image.Image, rect, look: Look, pressed=False, accent=False, radius=9) -> None:
    x0, y0, x1, y1 = rect
    if pressed:
        y0 += 1
        y1 += 1
    draw = ImageDraw.Draw(image)
    if accent:
        fill = mix(look.accent, BLACK, 0.07) if pressed else look.accent
        draw.rounded_rectangle([x0, y0, x1, y1], radius=radius, fill=fill)
        draw.rounded_rectangle(
            [x0 + radius * 0.55, y0 + 1, x1 - radius * 0.55, y0 + 2],
            radius=1,
            fill=on(fill, WHITE, 0.16),
        )
        return

    if pressed:
        top = on(look.key_top, WHITE if look.dark else BLACK, 0.135 if look.dark else 0.055)
        bottom = on(look.key_bottom, WHITE if look.dark else BLACK, 0.10 if look.dark else 0.075)
    else:
        top, bottom = look.key_top, look.key_bottom
    rounded_vertical_gradient(image, (x0, y0, x1, y1), radius, top, bottom)
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle([x0 + radius * 0.55, y0 + 1, x1 - radius * 0.55, y0 + 2], radius=1, fill=on(top, WHITE, look.edge_amount))
    draw.rounded_rectangle([x0, y0, x1, y1], radius=radius, outline=on(top, WHITE if look.dark else BLACK, look.border_amount), width=1)


def text_center(draw, xy, text, font, fill):
    bbox = draw.textbbox((0, 0), text, font=font)
    draw.text(
        (xy[0] - (bbox[2] - bbox[0]) / 2 - bbox[0], xy[1] - (bbox[3] - bbox[1]) / 2 - bbox[1]),
        text,
        font=font,
        fill=fill,
    )


def label_for(key: str) -> str:
    return {
        "SHIFT": "\u21e7", "BACK": "\u232b", "GLOBE": "\u25cf\u25cf", "CLIP": "\u25a4",
        "EMOJI": "\u25d5", "SPACE": "", "ENTER": "\u21b5",
    }.get(key, key.upper() if len(key) == 1 else key)


def render_frame(look: Look, typed: str, pressed: str | None, press_progress: float) -> Image.Image:
    width = 640
    board_top = 128
    board = Board(width, board_top + 10)
    height = board.bottom + 18
    image = Image.new("RGBA", (width, height), look.wall_top + (255,))
    image = draw_wallpaper(image, look)
    draw = ImageDraw.Draw(image)

    from PIL import ImageFont
    font_path = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    try:
        font_key = ImageFont.truetype(font_path, 17)
        font_small = ImageFont.truetype(font_path, 13)
        font_field = ImageFont.truetype(font_path, 21)
        font_chip = ImageFont.truetype(font_path, 14)
    except OSError:
        font_key = font_small = font_field = font_chip = None

    # message card
    draw.rounded_rectangle([22, 20, width - 22, 74], radius=20, fill=look.panel_top,
                          outline=on(look.panel_top, WHITE if look.dark else BLACK, look.border_amount), width=1)
    draw.text((40, 38), typed or " ", font=font_field, fill=look.text)
    caret_x = 40 + (draw.textlength(typed, font=font_field) if font_field else 0) + 2
    if press_progress >= 0:
        draw.rectangle([caret_x, 36, caret_x + 2, 60], fill=look.accent)

    # suggestion strip
    chips = ["limbo", "limbo\u00a0keyboard", "limbus"] if typed else ["Limbo \u2014 type freely"]
    x = 18
    for chip in chips:
        w = (draw.textlength(chip, font=font_chip) if font_chip else len(chip) * 8) + 34
        draw.rounded_rectangle([x, board_top - 46, x + w, board_top - 12], radius=15,
                               fill=on(look.panel_top, look.text, 0.05))
        text_center(draw, (x + w / 2, board_top - 29), chip, font_chip, look.text_secondary)
        x += w + 8

    # the sheet
    rounded_vertical_gradient(image, (0, board_top - 2, width, height), 26, look.panel_top, look.panel_bottom)
    draw = ImageDraw.Draw(image)
    draw.line([(24, board_top - 1), (width - 24, board_top - 1)], fill=look.edge_solid)

    for row in board.rows:
        for label, _, rect in row:
            pressed_here = pressed == label
            progress = press_progress if pressed_here else 0.0
            draw_key(image, rect, look, pressed=progress > 0.35, accent=label == "ENTER")
            if label == "SPACE":
                continue
            draw = ImageDraw.Draw(image)
            color = look.accent_text if label == "ENTER" else (look.text if len(label) == 1 else look.text_secondary)
            size = font_key if len(label) == 1 else font_small
            text_center(draw, ((rect[0] + rect[2]) / 2, (rect[1] + rect[3]) / 2), label_for(label), size, color)

    # magnified preview above the pressed key
    if pressed and pressed in board.lookup and len(pressed) == 1:
        draw = ImageDraw.Draw(image)
        rect = board.lookup[pressed]
        cx = (rect[0] + rect[2]) / 2
        bw, bh = 52, 56
        bx0 = max(8, min(width - bw - 8, cx - bw / 2))
        by0 = max(board_top - 74, rect[1] - bh - 12)
        shadow = Image.new("RGBA", image.size, (0, 0, 0, 0))
        sd = ImageDraw.Draw(shadow)
        for step in range(6, 0, -1):
            sd.rounded_rectangle(
                [bx0 - step * 0.6, by0 + 3 + step * 0.7, bx0 + bw + step * 0.6, by0 + bh + 3 + step * 0.7],
                radius=16 + step,
                fill=(0, 0, 0, 18),
            )
        image = Image.alpha_composite(image, shadow)
        rounded_vertical_gradient(image, (bx0, by0, bx0 + bw, by0 + bh), 16, on(look.key_top, WHITE, 0.05), on(look.key_bottom, WHITE, 0.05))
        draw = ImageDraw.Draw(image)
        draw.rounded_rectangle([bx0, by0, bx0 + bw, by0 + bh], radius=16,
                               outline=on(look.key_top, WHITE if look.dark else BLACK, look.border_amount), width=1)
        text_center(draw, (bx0 + bw / 2, by0 + bh / 2), pressed.upper(), font_field, look.text)

    return image.convert("RGB")


def build_gif(seeds: List[Dict[str, object]]):
    seed = next(s for s in seeds if s["id"] == "frost")
    look = Look(seed)
    sequence = "limbo"
    frames: List[Image.Image] = []
    delays: List[int] = []

    frames.append(render_frame(look, "", None, 0))
    delays.append(700)

    typed = ""
    for char in sequence:
        frames.append(render_frame(look, typed, char, 1.0))
        delays.append(90)
        frames.append(render_frame(look, typed, char, 0.4))
        delays.append(70)
        typed += char
        frames.append(render_frame(look, typed, None, 0))
        delays.append(110)

    for _ in range(2):
        frames.append(render_frame(look, typed, None, 0))
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


# ---------------------------------------------------------------------------- preset cards


def build_theme_cards(seeds: List[Dict[str, object]]) -> None:
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
        look = Look(seed)
        image = Image.new("RGBA", (w, h), look.wall_top + (255,))
        image = draw_wallpaper(image, look)
        draw = ImageDraw.Draw(image)

        # the sheet
        px0, py0, px1, py1 = 16, 34, w - 16, h - 52
        draw.rounded_rectangle([px0, py0, px1, py1], radius=16, fill=look.panel_top)
        draw.line([(px0 + 12, py0 + 1), (px1 - 12, py0 + 1)], fill=look.edge_solid)

        # keys
        key_h = 9
        for row in range(3):
            count = 8 - row
            key_w = (px1 - px0 - 22) / 8.4
            for k in range(count):
                kx = px0 + 11 + k * (key_w + 3) + row * key_w * 0.28
                ky = py0 + 10 + row * (key_h + 3)
                rect = (int(kx), int(ky), int(kx + key_w), int(ky + key_h))
                draw_key(image, rect, look, accent=(row == 1 and k == 2), radius=3)
                draw = ImageDraw.Draw(image)

        if font_name:
            draw.text((px0 + 2, py1 + 6), str(seed["en"]), font=font_name, fill=look.text)
            draw.text((px0 + 2, py1 + 24), str(seed["accent"]).upper(), font=font_hex, fill=look.text_secondary)
        draw.ellipse([px1 - 12, py1 + 12, px1, py1 + 24], fill=look.accent)

        mask = Image.new("L", (w, h), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, w, h], radius=20, fill=255)
        image.putalpha(mask)
        image.save(os.path.join(out, f"{seed['id']}.png"))


# ---------------------------------------------------------------------------- vector sheet


def build_theme_svg(seeds: List[Dict[str, object]]):
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
        look = Look(seed)
        col, row = index % columns, index // columns
        x = 12 + col * cell_w
        y = 12 + row * cell_h
        w, h = cell_w - 12, cell_h - 12
        parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="18" fill="url(#g{index})"/>')
        panel_x, panel_y = x + 14, y + 26
        parts.append(
            f'<rect x="{panel_x}" y="{panel_y}" width="{w - 28}" height="{h - 62}" rx="12" '
            f'fill="{"#%02X%02X%02X" % look.panel_top}" fill-opacity="0.94" '
            f'stroke="{"#%02X%02X%02X" % look.edge_solid}" stroke-opacity="0.5"/>'
        )
        for r in range(3):
            count = 8 - r
            key_w = (w - 40) / 8.6
            for k in range(count):
                kx = panel_x + 8 + k * (key_w + 3) + (r * key_w * 0.25)
                ky = panel_y + 8 + r * 11
                is_accent = r == 1 and k == 2
                fill = "#%02X%02X%02X" % (look.accent if is_accent else look.key_top)
                parts.append(
                    f'<rect x="{kx:.1f}" y="{ky:.1f}" width="{key_w:.1f}" height="8" rx="3" '
                    f'fill="{fill}" fill-opacity="{0.95 if is_accent else 0.9}"/>'
                )
        parts.append(
            f'<text x="{x + 14}" y="{y + h - 12}" font-family="-apple-system,Segoe UI,Roboto,sans-serif" '
            f'font-size="13" font-weight="600" fill="#ffffff" fill-opacity="0.92">{seed["en"]}</text>'
        )
        parts.append(f'<circle cx="{x + w - 20}" cy="{y + h - 16}" r="5" fill="{seed["accent"]}"/>')
    parts.append("</svg>")
    with open(os.path.join(DOCS, "themes.svg"), "w", encoding="utf-8") as handle:
        handle.write("\n".join(parts))


if __name__ == "__main__":
    os.makedirs(DOCS, exist_ok=True)
    seeds = read_seeds()
    build_theme_svg(seeds)
    build_theme_cards(seeds)
    build_gif(seeds)
    print(f"Wrote docs/preview.gif, docs/themes.svg and {len(seeds)} preset cards")
