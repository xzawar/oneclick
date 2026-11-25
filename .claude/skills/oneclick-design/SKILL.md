---
name: oneclick-design
description: Use when building or reviewing One Click UI — Compose theme and components (android/app/src/main/java/pk/oneclick/app/Theme.kt), screens, copy, icons, motion, or the admin dashboard look.
---

# One Click design system (calm-fintech structure, One Click brand)

Source of truth: `Theme.kt` (`OC` colour roles, `T` type, `Gap` spacing, `Radius`, `Motion`, components).
Never pick a hex in a screen — pick a role from `OC`.

## Rules
1. **One bright thing per screen**: gold `OC.gold #E8B84A` fills only the primary button; text on it is deep emerald `OC.deep #0B3D2E`.
2. **Forest theme** (emerald surface, gold numbers) only for brand moments: balance card, provider status, splash, success.
3. **Colour is semantic**: content (ink/text/muted/link) · interactive · background (white / neutral tint `OC.card`) · sentiments
   negative `#CB272F/#FBEAEA`, positive `#054D28/#E2F6D5`, warning `#4A3B1C/#FFF7D7`, proposition `#054D4D/#E0F7F7`.
4. **Type**: Inter Regular 400 for reading, SemiBold 600 for anything to be found. Screen title 30/34.5 SB. Amounts `T.number` 40/44 SB.
   Archivo Black ALL CAPS (`T.displaySmall/displayMedium`) once per hero moment (onboarding, success) — never in lists.
5. **Shape**: interactive = pill (buttons, chips, avatars); containers 16dp; big containers 24–32dp. Flat — no shadows except floating layers.
6. **Buttons**: full-width 48dp pills (`PillButton`): Primary (gold) · Secondary (gold tint) · SecondaryNeutral · Tertiary (underlined text) ·
   PrimaryNegative · SecondaryNegative (quiet red, e.g. "Cancel booking"). Loading = label skeleton, never a size jump.
7. **Sheets** float: inset 8dp, 32dp corners, handle 8dp above (`OverlaySheet`). Confirmations use sheets; safe action is primary.
8. **Icons**: Material Outlined 24dp, single colour; filled circle icons only for status. In lists, icons sit in 48dp neutral avatars.
9. **Spacing by relationship** (`Gap`, scales with font size): text 8 · text→component 16 · content→button 24 · sections 32 · gutter 16.
10. **One question per screen** for flows (booking, sign-in): app bar back · progress · close, question as title, sticky primary.
11. **Copy**: second person, sentence case, title ≤ 7 words, button ≤ 3 words, hedged times ("Should arrive in about 3 min"),
    always show the numbers (price, our fee, you pay / you get). No exclamation marks.
12. **Motion**: standard `(.25,0,.5,1)`, enter `(.3,0,.1,1)` 450ms, exit `(.9,0,.7,1)` 300ms; overshoot only for money moments.
    Home items that are optional (setup checklist, tips) are dismissible.

## Fixed behaviours
SOS: long-press red circle on Safety. Offer/request timers as 4dp progress lines. Map pins: gold "you", emerald provider.
Touch ≥ 48dp, contentDescription on icon-only controls, tab bar shows icon + label.
