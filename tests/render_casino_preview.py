"""Render captured mock GPU cells; this is a simulation, not a Minecraft screenshot."""
from pathlib import Path
import sys

from PIL import Image, ImageDraw, ImageFont

source, target = map(Path, sys.argv[1:3])
lines = source.read_text(encoding="utf-8").splitlines()
width, height = map(int, lines[0].split(","))
cell_w, cell_h, margin = 12, 26, 20
canvas = Image.new("RGB", (width * cell_w + margin * 2, height * cell_h + margin * 2), "#090e15")
draw = ImageDraw.Draw(canvas)
font = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 20)
for line in lines[1:]:
    values, char = line.split("\t", 1)
    x, y, bg, fg = map(int, values.split(","))
    left, top = margin + (x - 1) * cell_w, margin + (y - 1) * cell_h
    draw.rectangle((left, top, left + cell_w - 1, top + cell_h - 1), fill=f"#{bg:06x}")
    draw.text((left, top + 2), char, font=font, fill=f"#{fg:06x}", anchor="lt")
canvas.save(target)
