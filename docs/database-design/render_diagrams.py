#!/usr/bin/env python3
"""Renderizador determinista de los diagramas de Gestera.

Los JSON de cada carpeta son la fuente editable. Este script genera SVG y PNG
sin depender de Mermaid, Graphviz, PlantUML ni servicios externos.
"""

from __future__ import annotations

import html
import json
import math
from collections import defaultdict
from pathlib import Path
from typing import Any

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parent
SOURCES = (
    ROOT / "01-logical" / "diseño_logico.json",
    ROOT / "02-physical" / "diseño_fisico.json",
    ROOT / "03-uml" / "diagrama_clases.json",
)

COLORS = {
    "background": "#FFFFFF",
    "ink": "#36344A",
    "muted": "#706D83",
    "line": "#8B7CF6",
    "line_dark": "#6E5DDA",
    "header": "#EFEDFF",
    "header_strong": "#E2DEFF",
    "row_alt": "#FBFAFF",
    "group": "#FAF9FF",
    "note": "#F5F2FF",
    "white": "#FFFFFF",
}


def find_font(bold: bool = False, mono: bool = False) -> str:
    windows = Path("C:/Windows/Fonts")
    candidates = (
        ["consolab.ttf", "DejaVuSansMono-Bold.ttf"] if mono and bold else
        ["consola.ttf", "DejaVuSansMono.ttf"] if mono else
        ["segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf"] if bold else
        ["segoeui.ttf", "arial.ttf", "DejaVuSans.ttf"]
    )
    for candidate in candidates:
        path = windows / candidate
        if path.exists():
            return str(path)
    return candidates[-1]


FONT_REGULAR = find_font()
FONT_BOLD = find_font(bold=True)
FONT_MONO = find_font(mono=True)
FONT_MONO_BOLD = find_font(bold=True, mono=True)


def font(size: int, bold: bool = False, mono: bool = False) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(
        FONT_MONO_BOLD if mono and bold else
        FONT_MONO if mono else
        FONT_BOLD if bold else FONT_REGULAR,
        size,
    )


def node_height(spec: dict[str, Any], diagram: dict[str, Any]) -> int:
    if diagram["kind"] == "table":
        return 60 + len(spec.get("rows", [])) * int(diagram.get("row_height", 44))
    line_height = 32
    attributes = spec.get("attributes", [])
    methods = spec.get("methods", [])
    height = 78 + len(attributes) * line_height + 12
    if methods:
        height += 12 + len(methods) * line_height
    return height + 14


def boxes(diagram: dict[str, Any]) -> dict[str, tuple[float, float, float, float]]:
    return {
        n["id"]: (n["x"], n["y"], n["w"], node_height(n, diagram))
        for n in diagram["nodes"]
    }


def anchor_slots(diagram: dict[str, Any]) -> dict[tuple[int, str, str], int]:
    grouped: dict[tuple[str, str], list[int]] = defaultdict(list)
    for index, edge in enumerate(diagram.get("edges", [])):
        grouped[(edge["from"], edge.get("from_side", "right"))].append(index)
        grouped[(edge["to"], edge.get("to_side", "left"))].append(index)
    slots: dict[tuple[int, str, str], int] = {}
    for (node_id, side), indexes in grouped.items():
        for position, index in enumerate(indexes):
            slots[(index, node_id, side)] = position
            slots[(index, node_id, side, "count")] = len(indexes)  # type: ignore[index]
    return slots


def anchor(
    box: tuple[float, float, float, float],
    side: str,
    position: int,
    count: int,
) -> tuple[float, float]:
    x, y, w, h = box
    fraction = (position + 1) / (count + 1)
    if side == "left":
        return x, y + h * (0.18 + 0.64 * fraction)
    if side == "right":
        return x + w, y + h * (0.18 + 0.64 * fraction)
    if side == "top":
        return x + w * (0.12 + 0.76 * fraction), y
    return x + w * (0.12 + 0.76 * fraction), y + h


def route(start: tuple[float, float], end: tuple[float, float], side_a: str, side_b: str) -> list[tuple[float, float]]:
    sx, sy = start
    ex, ey = end
    horizontal_a = side_a in {"left", "right"}
    horizontal_b = side_b in {"left", "right"}
    if horizontal_a and horizontal_b:
        middle = (sx + ex) / 2
        return [start, (middle, sy), (middle, ey), end]
    if not horizontal_a and not horizontal_b:
        middle = (sy + ey) / 2
        return [start, (sx, middle), (ex, middle), end]
    if horizontal_a:
        return [start, (ex, sy), end]
    return [start, (sx, ey), end]


def longest_midpoint(points: list[tuple[float, float]]) -> tuple[float, float]:
    segments = list(zip(points, points[1:]))
    a, b = max(segments, key=lambda pair: math.dist(pair[0], pair[1]))
    return (a[0] + b[0]) / 2, (a[1] + b[1]) / 2


def svg_text(
    x: float,
    y: float,
    value: str,
    size: int,
    *,
    weight: int = 400,
    anchor_value: str = "start",
    family: str = "'Segoe UI', Arial, sans-serif",
    fill: str | None = None,
) -> str:
    return (
        f'<text x="{x:.1f}" y="{y:.1f}" font-family="{family}" '
        f'font-size="{size}" font-weight="{weight}" text-anchor="{anchor_value}" '
        f'fill="{fill or COLORS["ink"]}">{html.escape(value)}</text>'
    )


def draw_centered(draw: ImageDraw.ImageDraw, xy: tuple[float, float], value: str,
                  chosen_font: ImageFont.FreeTypeFont, fill: str) -> None:
    draw.text(xy, value, font=chosen_font, fill=fill, anchor="mm")


def edge_geometry(diagram: dict[str, Any]) -> list[tuple[dict[str, Any], list[tuple[float, float]]]]:
    all_boxes = boxes(diagram)
    slots = anchor_slots(diagram)
    output = []
    for index, edge in enumerate(diagram.get("edges", [])):
        side_a = edge.get("from_side", "right")
        side_b = edge.get("to_side", "left")
        pos_a = slots[(index, edge["from"], side_a)]
        count_a = slots[(index, edge["from"], side_a, "count")]  # type: ignore[index]
        pos_b = slots[(index, edge["to"], side_b)]
        count_b = slots[(index, edge["to"], side_b, "count")]  # type: ignore[index]
        start = anchor(all_boxes[edge["from"]], side_a, pos_a, count_a)
        end = anchor(all_boxes[edge["to"]], side_b, pos_b, count_b)
        output.append((edge, route(start, end, side_a, side_b)))
    return output


def diamond_points(start: tuple[float, float], next_point: tuple[float, float], size: float = 15) -> list[tuple[float, float]]:
    dx, dy = next_point[0] - start[0], next_point[1] - start[1]
    length = max(math.hypot(dx, dy), 1)
    ux, uy = dx / length, dy / length
    px, py = -uy, ux
    return [
        start,
        (start[0] + ux * size + px * size * 0.65, start[1] + uy * size + py * size * 0.65),
        (start[0] + ux * size * 2, start[1] + uy * size * 2),
        (start[0] + ux * size - px * size * 0.65, start[1] + uy * size - py * size * 0.65),
    ]


def arrow_points(end: tuple[float, float], previous: tuple[float, float], size: float = 14) -> list[tuple[float, float]]:
    dx, dy = end[0] - previous[0], end[1] - previous[1]
    length = max(math.hypot(dx, dy), 1)
    ux, uy = dx / length, dy / length
    px, py = -uy, ux
    return [
        end,
        (end[0] - ux * size + px * size * 0.55, end[1] - uy * size + py * size * 0.55),
        (end[0] - ux * size - px * size * 0.55, end[1] - uy * size - py * size * 0.55),
    ]


def render_svg(diagram: dict[str, Any], destination: Path) -> None:
    width, height = diagram["width"], diagram["height"]
    out = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        f'<rect width="100%" height="100%" fill="{COLORS["background"]}"/>',
        svg_text(width / 2, 52, diagram["title"], 38, weight=700, anchor_value="middle", fill=COLORS["line_dark"]),
        svg_text(width / 2, 87, diagram["subtitle"], 21, anchor_value="middle", fill=COLORS["muted"]),
    ]

    for group in diagram.get("groups", []):
        out.append(
            f'<rect x="{group["x"]}" y="{group["y"]}" width="{group["w"]}" height="{group["h"]}" '
            f'rx="18" fill="{COLORS["group"]}" stroke="{COLORS["header_strong"]}" stroke-width="3"/>'
        )
        out.append(svg_text(group["x"] + 25, group["y"] + 38, group["title"], 22, weight=700, fill=COLORS["line_dark"]))

    for edge, points in edge_geometry(diagram):
        dash = ' stroke-dasharray="13 9"' if edge.get("dashed") else ""
        path = " ".join(f"{x:.1f},{y:.1f}" for x, y in points)
        out.append(f'<polyline points="{path}" fill="none" stroke="{COLORS["line"]}" stroke-width="4" stroke-linejoin="round"{dash}/>' )
        if edge.get("type") == "composition":
            diamond = " ".join(f"{x:.1f},{y:.1f}" for x, y in diamond_points(points[0], points[1]))
            out.append(f'<polygon points="{diamond}" fill="{COLORS["line_dark"]}"/>')
        if edge.get("type") == "dependency" or edge.get("dashed"):
            triangle = " ".join(f"{x:.1f},{y:.1f}" for x, y in arrow_points(points[-1], points[-2]))
            out.append(f'<polygon points="{triangle}" fill="{COLORS["line_dark"]}"/>')
        lx, ly = longest_midpoint(points)
        label = edge.get("label", "")
        if label:
            label_width = max(90, len(label) * 12 + 24)
            out.append(f'<rect x="{lx-label_width/2:.1f}" y="{ly-20:.1f}" width="{label_width}" height="31" rx="10" fill="white" stroke="{COLORS["header_strong"]}"/>')
            out.append(svg_text(lx, ly + 2, label, 17, weight=600, anchor_value="middle", fill=COLORS["muted"]))
        if edge.get("from_card"):
            out.append(svg_text(points[0][0] + 10, points[0][1] - 10, edge["from_card"], 18, weight=700, fill=COLORS["line_dark"]))
        if edge.get("to_card"):
            out.append(svg_text(points[-1][0] - 10, points[-1][1] - 10, edge["to_card"], 18, weight=700, anchor_value="end", fill=COLORS["line_dark"]))

    for spec in diagram["nodes"]:
        if diagram["kind"] == "table":
            render_table_svg(out, spec, diagram)
        else:
            render_class_svg(out, spec, diagram)

    for note in diagram.get("notes", []):
        out.append(f'<rect x="{note["x"]}" y="{note["y"]}" width="{note["w"]}" height="85" rx="12" fill="{COLORS["note"]}" stroke="{COLORS["line"]}" stroke-width="2"/>')
        out.append(svg_text(note["x"] + 22, note["y"] + 52, note["text"], 21, weight=600, fill=COLORS["ink"]))

    legend = diagram.get("legend", [])
    if legend:
        legend_x, legend_y = 100, height - 70
        out.append(svg_text(legend_x, legend_y, "   ·   ".join(legend), 20, weight=600, fill=COLORS["muted"]))

    out.append("</svg>")
    destination.write_text("\n".join(out), encoding="utf-8")


def render_table_svg(out: list[str], spec: dict[str, Any], diagram: dict[str, Any]) -> None:
    x, y, w = spec["x"], spec["y"], spec["w"]
    row_height = int(diagram.get("row_height", 44))
    height = node_height(spec, diagram)
    header_height = 60
    marker_width = 105
    name_width = min(330, int(w * 0.38))
    out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{height}" fill="white" stroke="{COLORS["line"]}" stroke-width="3"/>')
    out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{header_height}" fill="{COLORS["header"]}" stroke="{COLORS["line"]}" stroke-width="3"/>')
    out.append(svg_text(x + w / 2, y + 40, spec["title"], 25, weight=700, anchor_value="middle", fill=COLORS["line_dark"]))
    for index, row in enumerate(spec.get("rows", [])):
        ry = y + header_height + index * row_height
        if index % 2:
            out.append(f'<rect x="{x+1}" y="{ry}" width="{w-2}" height="{row_height}" fill="{COLORS["row_alt"]}"/>')
        out.append(f'<line x1="{x}" y1="{ry}" x2="{x+w}" y2="{ry}" stroke="{COLORS["header_strong"]}" stroke-width="1.5"/>')
        out.append(svg_text(x + 12, ry + row_height * 0.7, row[0], 19, weight=700, family="Consolas, monospace", fill=COLORS["line_dark"]))
        out.append(svg_text(x + marker_width + 12, ry + row_height * 0.7, row[1], 20, weight=600, family="Consolas, monospace"))
        out.append(svg_text(x + marker_width + name_width + 12, ry + row_height * 0.7, row[2], 18, family="Consolas, monospace", fill=COLORS["muted"]))
    out.append(f'<line x1="{x+marker_width}" y1="{y+header_height}" x2="{x+marker_width}" y2="{y+height}" stroke="{COLORS["header_strong"]}" stroke-width="1.5"/>')
    out.append(f'<line x1="{x+marker_width+name_width}" y1="{y+header_height}" x2="{x+marker_width+name_width}" y2="{y+height}" stroke="{COLORS["header_strong"]}" stroke-width="1.5"/>')


def render_class_svg(out: list[str], spec: dict[str, Any], diagram: dict[str, Any]) -> None:
    x, y, w = spec["x"], spec["y"], spec["w"]
    height = node_height(spec, diagram)
    header_height = 78
    out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{height}" rx="5" fill="white" stroke="{COLORS["line"]}" stroke-width="3"/>')
    out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{header_height}" rx="5" fill="{COLORS["header"]}" stroke="{COLORS["line"]}" stroke-width="3"/>')
    stereotype = spec.get("stereotype", "")
    if stereotype:
        out.append(svg_text(x + w / 2, y + 24, stereotype, 17, anchor_value="middle", fill=COLORS["muted"]))
        title_y = y + 56
    else:
        title_y = y + 49
    out.append(svg_text(x + w / 2, title_y, spec["title"], 25, weight=700, anchor_value="middle", fill=COLORS["line_dark"]))
    current_y = y + header_height + 30
    for value in spec.get("attributes", []):
        out.append(svg_text(x + 18, current_y, value, 19, family="Consolas, monospace"))
        current_y += 32
    methods = spec.get("methods", [])
    if methods:
        current_y += 2
        out.append(f'<line x1="{x}" y1="{current_y}" x2="{x+w}" y2="{current_y}" stroke="{COLORS["header_strong"]}" stroke-width="2"/>')
        current_y += 31
        for value in methods:
            out.append(svg_text(x + 18, current_y, value, 18, family="Consolas, monospace", fill=COLORS["ink"]))
            current_y += 32


def render_png(diagram: dict[str, Any], destination: Path) -> None:
    width, height = diagram["width"], diagram["height"]
    image = Image.new("RGB", (width, height), COLORS["background"])
    draw = ImageDraw.Draw(image)
    draw_centered(draw, (width / 2, 40), diagram["title"], font(38, bold=True), COLORS["line_dark"])
    draw_centered(draw, (width / 2, 77), diagram["subtitle"], font(21), COLORS["muted"])

    for group in diagram.get("groups", []):
        draw.rounded_rectangle(
            (group["x"], group["y"], group["x"] + group["w"], group["y"] + group["h"]),
            radius=18, fill=COLORS["group"], outline=COLORS["header_strong"], width=3,
        )
        draw.text((group["x"] + 25, group["y"] + 17), group["title"], font=font(22, bold=True), fill=COLORS["line_dark"])

    for edge, points in edge_geometry(diagram):
        if edge.get("dashed"):
            draw_dashed(draw, points, COLORS["line"], 4)
        else:
            draw.line(points, fill=COLORS["line"], width=4, joint="curve")
        if edge.get("type") == "composition":
            draw.polygon(diamond_points(points[0], points[1]), fill=COLORS["line_dark"])
        if edge.get("type") == "dependency" or edge.get("dashed"):
            draw.polygon(arrow_points(points[-1], points[-2]), fill=COLORS["line_dark"])
        lx, ly = longest_midpoint(points)
        label = edge.get("label", "")
        if label:
            label_font = font(17, bold=True)
            bounds = draw.textbbox((0, 0), label, font=label_font)
            label_width = max(90, bounds[2] - bounds[0] + 24)
            draw.rounded_rectangle((lx - label_width / 2, ly - 21, lx + label_width / 2, ly + 11), radius=10, fill=COLORS["white"], outline=COLORS["header_strong"], width=1)
            draw_centered(draw, (lx, ly - 4), label, label_font, COLORS["muted"])
        card_font = font(18, bold=True)
        if edge.get("from_card"):
            draw.text((points[0][0] + 10, points[0][1] - 30), edge["from_card"], font=card_font, fill=COLORS["line_dark"])
        if edge.get("to_card"):
            draw.text((points[-1][0] - 10, points[-1][1] - 30), edge["to_card"], font=card_font, fill=COLORS["line_dark"], anchor="ra")

    for spec in diagram["nodes"]:
        if diagram["kind"] == "table":
            render_table_png(draw, spec, diagram)
        else:
            render_class_png(draw, spec, diagram)

    for note in diagram.get("notes", []):
        draw.rounded_rectangle((note["x"], note["y"], note["x"] + note["w"], note["y"] + 85), radius=12, fill=COLORS["note"], outline=COLORS["line"], width=2)
        draw.text((note["x"] + 22, note["y"] + 28), note["text"], font=font(21, bold=True), fill=COLORS["ink"])

    legend = diagram.get("legend", [])
    if legend:
        draw.text((100, height - 52), "   ·   ".join(legend), font=font(20, bold=True), fill=COLORS["muted"])
    image.save(destination, format="PNG", optimize=True, dpi=(300, 300))


def draw_dashed(draw: ImageDraw.ImageDraw, points: list[tuple[float, float]], color: str, width: int) -> None:
    dash, gap = 15.0, 10.0
    for start, end in zip(points, points[1:]):
        dx, dy = end[0] - start[0], end[1] - start[1]
        length = math.hypot(dx, dy)
        if not length:
            continue
        ux, uy = dx / length, dy / length
        cursor = 0.0
        while cursor < length:
            finish = min(cursor + dash, length)
            draw.line((start[0] + ux * cursor, start[1] + uy * cursor,
                       start[0] + ux * finish, start[1] + uy * finish), fill=color, width=width)
            cursor += dash + gap


def render_table_png(draw: ImageDraw.ImageDraw, spec: dict[str, Any], diagram: dict[str, Any]) -> None:
    x, y, w = spec["x"], spec["y"], spec["w"]
    row_height = int(diagram.get("row_height", 44))
    height = node_height(spec, diagram)
    header_height = 60
    marker_width = 105
    name_width = min(330, int(w * 0.38))
    draw.rectangle((x, y, x + w, y + height), fill=COLORS["white"], outline=COLORS["line"], width=3)
    draw.rectangle((x, y, x + w, y + header_height), fill=COLORS["header"], outline=COLORS["line"], width=3)
    draw_centered(draw, (x + w / 2, y + header_height / 2), spec["title"], font(25, bold=True), COLORS["line_dark"])
    for index, row in enumerate(spec.get("rows", [])):
        ry = y + header_height + index * row_height
        if index % 2:
            draw.rectangle((x + 2, ry, x + w - 2, ry + row_height), fill=COLORS["row_alt"])
        draw.line((x, ry, x + w, ry), fill=COLORS["header_strong"], width=2)
        draw.text((x + 12, ry + 8), row[0], font=font(19, bold=True, mono=True), fill=COLORS["line_dark"])
        draw.text((x + marker_width + 12, ry + 7), row[1], font=font(20, bold=True, mono=True), fill=COLORS["ink"])
        draw.text((x + marker_width + name_width + 12, ry + 9), row[2], font=font(18, mono=True), fill=COLORS["muted"])
    draw.line((x + marker_width, y + header_height, x + marker_width, y + height), fill=COLORS["header_strong"], width=2)
    draw.line((x + marker_width + name_width, y + header_height, x + marker_width + name_width, y + height), fill=COLORS["header_strong"], width=2)


def render_class_png(draw: ImageDraw.ImageDraw, spec: dict[str, Any], diagram: dict[str, Any]) -> None:
    x, y, w = spec["x"], spec["y"], spec["w"]
    height = node_height(spec, diagram)
    header_height = 78
    draw.rounded_rectangle((x, y, x + w, y + height), radius=5, fill=COLORS["white"], outline=COLORS["line"], width=3)
    draw.rounded_rectangle((x, y, x + w, y + header_height), radius=5, fill=COLORS["header"], outline=COLORS["line"], width=3)
    stereotype = spec.get("stereotype", "")
    if stereotype:
        draw_centered(draw, (x + w / 2, y + 18), stereotype, font(17), COLORS["muted"])
        title_y = y + 51
    else:
        title_y = y + 40
    draw_centered(draw, (x + w / 2, title_y), spec["title"], font(25, bold=True), COLORS["line_dark"])
    current_y = y + header_height + 12
    for value in spec.get("attributes", []):
        draw.text((x + 18, current_y), value, font=font(19, mono=True), fill=COLORS["ink"])
        current_y += 32
    methods = spec.get("methods", [])
    if methods:
        current_y += 6
        draw.line((x, current_y, x + w, current_y), fill=COLORS["header_strong"], width=2)
        current_y += 15
        for value in methods:
            draw.text((x + 18, current_y), value, font=font(18, mono=True), fill=COLORS["ink"])
            current_y += 32


def main() -> None:
    for source in SOURCES:
        diagram = json.loads(source.read_text(encoding="utf-8"))
        destination = source.with_suffix("")
        render_svg(diagram, destination.with_suffix(".svg"))
        render_png(diagram, destination.with_suffix(".png"))
        print(f"OK {source.relative_to(ROOT)} -> {diagram['width']}x{diagram['height']}")


if __name__ == "__main__":
    main()
