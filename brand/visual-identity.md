# Lumora — Visual Identity

**Product:** Lumora, a launcher for Android TV and Fire TV.
**Tagline:** *Your world on a brighter screen.*
**Personality:** warm, calm, cinematic.

Lumora borrows its look from a desert night seen through an arched window: espresso darkness, a crescent moon, amber light on the horizon. Every surface is warm-tinted; there is no pure black, no pure white and no cool blue.

## Index

| File | Contents |
| :--- | :--- |
| [color-palette.md](color-palette.md) / [color-palette.json](color-palette.json) | OKLCH, HEX and RGB tokens with their Android resource names |
| [typography.md](typography.md) | Typeface, TV type scale, vertical rhythm |
| [logo-usage.md](logo-usage.md) | Mark, wordmark, app icon, TV banner, clear space, dos and don'ts |
| [voice-and-tone.md](voice-and-tone.md) | How Lumora speaks |
| [iconography.md](iconography.md) | UI icon rules and third-party app icons |
| [components-style.md](components-style.md) | Cards, tiles, chips, focus, dialogs |
| [spacing-and-layout.md](spacing-and-layout.md) | 10-foot grid, safe areas, spacing scale |
| [photography-and-imagery.md](photography-and-imagery.md) | Wallpapers and posters |
| [assets/logo/](assets/logo/) | Master SVGs |

## Source of truth

The logo is defined once as geometry in `tools/brand/geom.py`. `tools/brand/render.py` writes the Android VectorDrawables (`ic_lumora_mark`, `ic_lumora_wordmark`), the SVGs in `assets/logo/` and a PNG preview. Edit the geometry, never the generated files.

```powershell
python tools\brand\render.py
```

Requires Python 3 with Pillow and NumPy (preview only).
