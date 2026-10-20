#!/usr/bin/env python3
"""Generate optimized static assets from the editable SVG concepts (not Android screenshots)."""
from io import BytesIO
from pathlib import Path
import cairosvg
from PIL import Image, ImageDraw, ImageFont

BASE = Path(__file__).resolve().parents[1] / "docs" / "assets"


def load_svg(name, width):
    png = cairosvg.svg2png(url=str(BASE / name), output_width=width)
    return Image.open(BytesIO(png)).convert("RGBA")


def webp(img, name, quality=83):
    img.save(BASE / name, "WEBP", quality=quality, method=6)
    print(f"{name}: {(BASE / name).stat().st_size:,} bytes")


def main():
    logo = load_svg("logo-centaurix.svg", 460)
    webp(logo.resize((180, 180), Image.Resampling.LANCZOS), "logo-centaurix.webp", 84)
    logo.resize((180, 180), Image.Resampling.LANCZOS).save(BASE / "icon-180.png", optimize=True)
    logo.resize((48, 48), Image.Resampling.LANCZOS).save(BASE / "favicon.png", optimize=True)
    webp(load_svg("hero-source.svg", 710), "hero-bt200.webp", 82)
    webp(load_svg("hero-source.svg", 1280), "hero-bt200@2x.webp", 84)
    for name in ("home", "puffs", "vpc", "themes"):
        webp(load_svg(f"screen-{name}.svg", 330), f"screen-{name}.webp", 84)

    # Open Graph image includes a full-quality product mockup and readable brand text.
    og = Image.new("RGB", (1200, 630), (9, 9, 14))
    art = load_svg("hero-source.svg", 740)
    art.thumbnail((760, 640), Image.Resampling.LANCZOS)
    og.paste(art, (530, -13), art)
    overlay = Image.new("RGBA", (1200, 630))
    d = ImageDraw.Draw(overlay)
    for x in range(720):
        intensity = max(0, 1 - x / 800)
        d.rectangle((x, 0, x, 630), fill=(9, 9, 14, int(240 * intensity)))
    og = Image.alpha_composite(og.convert("RGBA"), overlay)
    d = ImageDraw.Draw(og)
    font_regular = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    font_bold = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    d.text((66, 105), "CENTAURIX", fill="#ffb664", font=ImageFont.truetype(font_bold, 31))
    d.text((66, 240), "Plus de controle.", fill="#ffffff", font=ImageFont.truetype(font_bold, 52))
    d.text((66, 317), "Plus de liberte.", fill="#ff9558", font=ImageFont.truetype(font_bold, 52))
    d.text((67, 450), "Votre BT200. Votre experience.", fill="#c4b7c5", font=ImageFont.truetype(font_regular, 22))
    webp(og.convert("RGB"), "og-preview.webp", 82)


if __name__ == "__main__":
    main()
