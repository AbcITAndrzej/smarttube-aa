"""Rasterize the WaveAA logo.svg mark at the existing 320 px launcher size."""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT_MUSIC = ROOT / "smarttubetv/src/stmobile/res/mipmap-nodpi/app_icon.png"
OUT_VIDEO = ROOT / "smarttubetv/src/carvideo/res/mipmap-nodpi/app_icon.png"

# logo.svg is 128 px. The current nodpi launcher icon is 320 px.
SCALE = 320 / 128
SUPERSAMPLE = 4


def cubic(p0, c1, c2, p1, steps=24):
    points = []
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        x = u**3 * p0[0] + 3 * u**2 * t * c1[0] + 3 * u * t**2 * c2[0] + t**3 * p1[0]
        y = u**3 * p0[1] + 3 * u**2 * t * c1[1] + 3 * u * t**2 * c2[1] + t**3 * p1[1]
        points.append((x, y))
    return points


def wave_points():
    # Path from assets/img/logo.svg, parsed in the 128 px viewBox.
    spans = [
        ((14, 76), (28, 76), (28, 48), (42, 48)),
        ((42, 48), (56, 48), (56, 76), (70, 76)),
        ((70, 76), (84, 76), (84, 48), (98, 48)),
        ((98, 48), (112, 48), (112, 76), (114, 76)),
    ]
    points = []
    for span in spans:
        chunk = cubic(*span, steps=80)
        points.extend(chunk if not points else chunk[1:])
    return points


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def render(bar_color):
    size = 128 * SUPERSAMPLE
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    s = SUPERSAMPLE
    radius = 34 * s
    draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=(8, 22, 37, 255))

    left = (94, 231, 255)
    right = (139, 92, 246)
    stroke = 12 * s
    radius = stroke / 2
    raw = wave_points()
    for x, y in raw:
        px, py = x * s, y * s
        color = mix(left, right, x / 128) + (255,)
        draw.ellipse((px - radius, py - radius, px + radius, py + radius), fill=color)

    bar_y = 97 * s
    bar_w = 8 * s
    draw.line((36 * s, bar_y, 92 * s, bar_y), fill=bar_color, width=bar_w)
    cap = bar_w / 2
    for x in (36 * s, 92 * s):
        draw.ellipse((x - cap, bar_y - cap, x + cap, bar_y + cap), fill=bar_color)

    return image.resize((320, 320), Image.Resampling.LANCZOS)


def main():
    white = (255, 255, 255, 230)
    red = (255, 45, 45, 255)
    music = render(white)
    video = render(red)
    OUT_MUSIC.parent.mkdir(parents=True, exist_ok=True)
    OUT_VIDEO.parent.mkdir(parents=True, exist_ok=True)
    music.save(OUT_MUSIC, "PNG")
    video.save(OUT_VIDEO, "PNG")
    preview = Path(r"D:\APK\_SMARTUBE_\Logi\waveaa-icons")
    preview.mkdir(parents=True, exist_ok=True)
    music.save(preview / "waveaa.png")
    video.save(preview / "waveaa-video.png")
    print(OUT_MUSIC)
    print(OUT_VIDEO)


if __name__ == "__main__":
    main()
