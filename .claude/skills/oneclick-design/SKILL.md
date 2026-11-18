---
name: oneclick-design
description: Use when building or reviewing One Click UI — Compose theme (Color.kt, Type.kt, Dimens.kt), screens, components, status chips, map pins, animations, accessibility, or the Next.js admin dashboard look.
---

# One Click design tokens

Detail (sketches, wireframes, flows): `docs/DESIGN.md`. Grep the section you need.

## Colors (`ui/theme/Color.kt`)
| Token | Hex | Use |
|---|---|---|
| Navy | #1A1A2E | Provider-mode header, splash, `completed` |
| DarkBlue | #0F3D6B | Customer-mode header, buttons, VERIFIED, `en_route` |
| BrightBlue | #2196F3 | CTAs, links, ON JOB, `accepted` |
| HudDark | #16213E | Active-job HUD |
| Green | #4CAF50 | ONLINE, success, `in_progress` |
| Red | #F44336 | SOS, error, `disputed` |
| Orange | #FF9800 | warning, low wallet, `pending` |
| Gray | #9E9E9E | OFFLINE, disabled, `cancelled` |
| LtGray | #F5F5F5 | app bg · White #FFFFFF cards |

Pins: Plumber #1565C0 · Electrician #F9A825 · Welder #BF360C · Mechanic #37474F · Carpenter #4E342E · Painter #6A1B9A · Other #0F3D6B. Category colors come from API (`categories.pin_color`) — this table = seed data.

## Type (`Type.kt`) — Inter (Latin), Noto Nastaliq Urdu, JetBrains Mono (IDs)
displayLarge 32 Bold · headlineLarge 24 Bold · headlineMedium 20 SemiBold · titleLarge 18 SemiBold · titleMedium 16 Medium · bodyLarge 15 · bodyMedium 13 · labelLarge 12 Medium · labelSmall 10.

## Shape/space (`Dimens.kt`)
Radius 4 chip · 8 input · 12 button · 16 card/sheet · 24 modal · 32 toggle pill · circle avatar/SOS/pin.
Space 2 · 4 · 8 · 12 · 16 · 24 card pad · 32 screen margin · 48 hero.

## Fixed behaviors
SOS: 64dp red circle, long-press, top z-order, always on job screens. Job popup 30s timer bar, green→orange→red at 10s. Pin move 300ms ease-in-out; sheet 400ms spring; toggle 200ms. Contrast ≥4.5:1; touch ≥48dp; icon+label on bottom nav; dark+light variants; Material 3 `ModalBottomSheet`.
