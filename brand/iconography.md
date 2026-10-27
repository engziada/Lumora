# Iconography

## Lumora UI icons

- 24dp grid, 2dp round-joined strokes, no fills except small accents.
- Default colour `nova_text_dim`; selected or focused icons take the active accent through tinting.
- Metaphors follow the identity where possible: the Sleep action uses a crescent, the Shows tab a screen.

## Third-party app icons

Apps are shown on a rounded espresso plate (`bg_icon_plate`) so mismatched artwork lines up.

When **HD icons** are on, Lumora fetches a matching logo from the open-source [dashboard-icons](https://github.com/homarr-labs/dashboard-icons) collection (jsDelivr CDN) and caches it on the device. If no match exists, the app's installed icon is used.

Posters come from TVmaze and keep their original artwork; never tint or crop them beyond the 2:3 frame.
