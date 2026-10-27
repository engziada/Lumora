# Color palette

Warm espresso surfaces, cream text and an amber accent. Values are authored in OKLCH; HEX and RGB are the sRGB equivalents used by Android.

## Surfaces and text

| Token | Android resource | OKLCH | HEX | RGB |
| :--- | :--- | :--- | :--- | :--- |
| `espresso-950` | `nova_bg` | `oklch(15.8% 0.020 45.6)` | `#140A06` | `rgb(20 10 6)` |
| `espresso-900` | `nova_surface` | `oklch(19.4% 0.025 45.6)` | `#1E110B` | `rgb(30 17 11)` |
| `espresso-850` | `nova_surface_2` | `oklch(21.9% 0.030 47.3)` | `#26160E` | `rgb(38 22 14)` |
| `espresso-800` | `nova_card` | `oklch(24.4% 0.035 48.3)` | `#2E1B11` | `rgb(46 27 17)` |
| `espresso-700` | `nova_card_hi` | `oklch(28.0% 0.043 47.0)` | `#3A2216` | `rgb(58 34 22)` |
| `espresso-600` | `nova_stroke` | `oklch(33.1% 0.049 50.2)` | `#4A2E1E` | `rgb(74 46 30)` |
| `cream-100` | `nova_text` | `oklch(93.1% 0.026 73.2)` | `#F3E6D6` | `rgb(243 230 214)` |
| `sand-300` | `nova_text_dim` | `oklch(77.9% 0.041 68.4)` | `#C9B39C` | `rgb(201 179 156)` |
| `taupe-500` | `nova_text_muted` | `oklch(65.6% 0.040 63.1)` | `#A38C78` | `rgb(163 140 120)` |

## Accents and states

| Token | Android resource | OKLCH | HEX | RGB |
| :--- | :--- | :--- | :--- | :--- |
| `amber-400` (default accent) | `accent_amber`, `nova_accent` | `oklch(75.9% 0.118 66.4)` | `#E3A15A` | `rgb(227 161 90)` |
| `moon-200` | `accent_moonlight` | `oklch(88.1% 0.075 79.9)` | `#F2D3A0` | `rgb(242 211 160)` |
| `copper-500` | `accent_copper` | `oklch(66.3% 0.127 50.7)` | `#D07A45` | `rgb(208 122 69)` |
| `rose-400` | `accent_rose` | `oklch(71.2% 0.099 29.1)` | `#D98A7E` | `rgb(217 138 126)` |
| `bronze-600` | `accent_bronze` | `oklch(51.5% 0.085 56.7)` | `#8C5A34` | `rgb(140 90 52)` |
| `error-400` | `state_error` | `oklch(68.4% 0.146 31.4)` | `#E5735F` | `rgb(229 115 95)` |
| `success-400` | `state_success` | `oklch(77.7% 0.091 136.2)` | `#9CC48A` | `rgb(156 196 138)` |

The user can switch the accent between Amber, Copper, Rose and Moonlight. The accent drives focus rings, the selected tab, chips and the favourites row.

## Contrast (WCAG 2.x)

| Text | on `nova_bg` | on `nova_card` |
| :--- | :--- | :--- |
| `nova_text` | 15.9:1 | 13.3:1 |
| `nova_text_dim` | 9.7:1 | 8.1:1 |
| `nova_text_muted` | 6.1:1 | 5.1:1 |
| `accent_amber` | 8.8:1 | 7.4:1 |

Text placed on an amber fill uses `nova_text_on_accent` (`#1E110B`).

## Light surfaces (print, documents)

On paper or cream backgrounds use the light mark variant and `#2E170C` for the wordmark, with `#B8743F` for the star in the O.
