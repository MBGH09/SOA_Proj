# Design tokens — National Blood Network

Source of truth: FigCraft file `out/project.figcraft`, page **Design System**.
All tokens live in one variable collection, **`Design Tokens`** (mode `Light`).
Frame reference in this document is a 390-wide phone and a 1440-wide web canvas;
1 design unit = 1 logical pixel.

Font: **Inter** (Regular, Italic, SemiBold, Bold). Numeric/reference data uses
tabular figures (`tnum`).

---

## 1. ColorScheme

Map these to Material 3 `ColorScheme` fields directly (light scheme).

| Token | Hex | Material 3 role | Used for |
|---|---|---|---|
| `color.surface` | `#FAF8F5` | `surface` | page / screen background (warm off-white) |
| `color.surfaceContainerLowest` | `#FFFFFF` | `surfaceContainerLowest` | cards, result panels, table bodies |
| `color.surfaceContainerLow` | `#F2EFEA` | `surfaceContainerLow` | inset panels, side cards, empty states |
| `color.surfaceContainer` | `#EBE7E1` | `surfaceContainer` | muted blocks |
| `color.surfaceContainerHighest` | `#E2DDD5` | `surfaceContainerHighest` | progress / capacity tracks |
| `color.onSurface` | `#1B1815` | `onSurface` | primary ink (near-black) |
| `color.onSurfaceVariant` | `#655F58` | `onSurfaceVariant` | secondary text, captions, helper |
| `color.outline` | `#D9D3CB` | `outline` | hairline borders (cards, fields, buttons) |
| `color.outlineVariant` | `#E9E4DE` | `outlineVariant` | dividers, row rules |
| `color.primary` | `#3B3430` | `primary` | primary buttons, completed step dots |
| `color.onPrimary` | `#FFFFFF` | `onPrimary` | text/icon on primary |
| `color.primaryContainer` | `#E6E1DB` | `primaryContainer` | active nav indicator, fill bars |
| `color.onPrimaryContainer` | `#2A2420` | `onPrimaryContainer` | text/icon on the nav indicator |
| `color.secondary` | `#4A4643` | `secondary` | supporting ink |
| `color.error` | `#C0262D` | `error` | critical only: refuse, absent, invalid |
| `color.onError` | `#FFFFFF` | `onError` | text on error |
| `color.errorContainer` | `#F7E5E5` | `errorContainer` | refuse / invalid surfaces |
| `color.onErrorContainer` | `#8E2026` | `onErrorContainer` | text on error surface |
| `color.success` | `#2E6A45` | — (custom) | valid, issued, collected |
| `color.successContainer` | `#E3EEE7` | — (custom) | success badge surface |
| `color.onSuccessContainer` | `#1F4730` | — (custom) | text on success badge |
| `color.warning` | `#8F5C0E` | — (custom) | attention, in-progress |
| `color.warningContainer` | `#F6EEDC` | — (custom) | warning badge / alert surface |
| `color.onWarningContainer` | `#6B4410` | — (custom) | text on warning surface |

Notes
- There is exactly **one** red (`color.error` `#C0262D`) and it is reserved for critical
  states: unfit/refused visits, "Absent", invalid certificate, over-capacity.
- No gradients, no shadows — depth comes from surface tone + 1 px hairline outlines
  (`outline` for containers, `outlineVariant` for dividers).
- `success`/`warning` are registered as custom scheme extensions; in Flutter keep them
  in a `ThemeExtension` alongside the standard `ColorScheme`.
- Accessibility (final pass): `onSurfaceVariant` was darkened `#6F6962 → #655F58` and
  `warning` `#A96E12 → #8F5C0E` so every text/background pairing clears **4.5:1**.
  These changes live in the tokens, so every screen inherits them.

---

## 2. TextTheme (Inter)

| Style (Figma) | Size | Weight | Line height | Letter spacing | Flutter `TextTheme` |
|---|---|---|---|---|---|
| `displaySmall` | 36 | SemiBold | 44 | −0.5 px | `displaySmall` |
| `headlineSmall` | 24 | SemiBold | 32 | −0.25 px | `headlineSmall` |
| `titleLarge` | 20 | SemiBold | 28 | −0.15 px | `titleLarge` |
| `titleMedium` | 16 | SemiBold | 24 | −0.1 px | `titleMedium` |
| `bodyLarge` | 16 | Regular | 24 | 0 | `bodyLarge` |
| `bodyMedium` | 14 | Regular | 20 | 0 | `bodyMedium` |
| `bodySmall` | 12 | Regular | 16 | 0 | `bodySmall` |
| `labelLarge` | 14 | SemiBold | 20 | +0.1 px | `labelLarge` |
| `labelMedium` | 12 | SemiBold | 16 | +0.2 px | `labelMedium` |
| `mono/Table figures` | 14 | SemiBold | 20 | 0 | custom, `fontFeatures: [FontFeature.tabularFigures()]` |

Conventions
- Screen titles: 30 / SemiBold / 38 / −0.4 px (web) and 24 / SemiBold / 30 (mobile).
- All-caps micro-labels ("DONATION DATE", "REASONS · REFERENCE"): 11 / SemiBold / 15 /
  +0.5 px, `onSurfaceVariant`.
- References and counters (RDV-, D-, CERT-, NDN-, times, quantities, "6 / 6") always use
  tabular figures so columns stay aligned.

---

## 3. Spacing

Base unit **4 px**, working scale **8 dp**.

| Step | Value | Typical use |
|---|---|---|
| 1 | 4 | label → value inside a field |
| 2 | 8 | chip gaps, tight stacks |
| 3 | 12 | element gaps inside cards, table columns |
| 4 | 16 | screen padding (mobile), card padding |
| 5 | 18–20 | card padding (web), section gaps |
| 6 | 24 | column gap between content columns |
| 7 | 28–32 | content top padding |
| 8 | 56 | content left/right padding (web) |

Screen shells
- Mobile 390 × 844: `AppBar` (64) + body padding 16, gap 10, optional `NavigationBar` (80).
- Web 1440 × 900: `NavigationRail` (76) + column of `AppBar` (64) + content
  (padding 28/56/32/56, gap 16).

---

## 4. Shape, stroke, elevation

| Element | Radius | Stroke |
|---|---|---|
| Cards, panels, tables | 12 | 1 inside `outline` |
| Small visit cards, list rows | 10 | 1 inside `outline` |
| Buttons, text fields | 10–12 | 1 inside `outline` |
| Chips, badges | full pill | 1 inside `outline` |
| Progress / capacity track | 4 | none (`surfaceContainerHighest`) |

No drop shadows anywhere. Elevation is communicated only by surface tone.

---

## 5. Component sizes (as built)

| Component | Size |
|---|---|
| `AppBar · Mobile 392` / `AppBar · Web 1336` | h 64; role switcher hit area 130 × 48 |
| `NavigationBar · Mobile 392` | h 80 |
| `NavigationRail · Web 76` (donor) | w 76, h 900 (stretch) |
| `NavigationRail · Staff · Web 76` | w 76, h 605 (Today · Slots · Visits · Deferrals · Board) |
| `NavigationRail · Hospital · Web 76` | w 76, h 389 (New request · Requests · Stock) |
| `Icon / Sun` `Icon / CalendarGrid` `Icon / ClipboardHeart` `Icon / Kanban` `Icon / FilePlus` | 24 × 24, stroke 1.6 |
| `RoleSwitcher · Menu` | w 264; header + 4 options, each h 48 |
| `CTA / FilledButton`, `Critical`, `Disabled` | h 40–44 (web) / **48 (mobile)** |
| `CTA / OutlinedButton` | h 40–44 (web) / 48 (mobile) |
| `Field / TextField` (+ `Focused`, `Error`) | w 360 default (overridden 173–764), h 62 |
| `Chip / FilterChip Selected` / `Default` | h 36 (web) / **48 (mobile)** |
| `Badge / Neutral` `Success` `Warning` `Critical` | h 28 (non-interactive) |
| `ListTile` | w 358 (mobile) / 760 (web), h 72 |
| `Divider` | h 1, `outlineVariant` |

State colours for badges: Neutral → `surfaceContainer` / `onSurfaceVariant`,
Success → `successContainer` / `onSuccessContainer`,
Warning → `warningContainer` / `onWarningContainer`,
Critical → `errorContainer` / `onErrorContainer`.

Stock-board cells reuse the same idea without a pill: `In stock` →
`surfaceContainerLowest` / `onSurface`, `Near` → `warningContainer` /
`onWarningContainer`, `Low` → `errorContainer` / `onErrorContainer`.

---

## 6. Layer-name conventions

Components are named with `/` and variants are separate components:
`CTA / FilledButton`, `Field / TextField Error`, `Chip / FilterChip Selected`, `Badge / Success`.

Structural layers use Flutter widget names: `AppBar`, `NavigationBar`, `NavigationRail`,
`FilledButton`, `OutlinedButton`, `TextField`, `Card`, `ListTile`, `Chip`, `DataTable`,
`Divider`, `body`, `header`, `content`, `footer`, `row · <key>`, `StatCard · <label>`,
`RoleSwitcher`, `RoleSwitcher · Menu`.

---

## 6b. Consistency pass (final)

Every page was normalised to a single shell contract. The following were reconciled
across all 32 frames:

- **AppBar** — one variant per platform (`Mobile 392` / `Web 1336`), h 64, `spaceBetween`,
  gap 16, padding 0/16. Left = `Icon / Drop` + "National Blood Network"; right = the role
  switcher. Role text width fixed at 62; switcher is a 48 dp hit area on every screen.
- **Page titles** — web 30 / SemiBold / 38 / −0.4; mobile 24 / SemiBold / 30. One H1 per
  screen, followed by a single `onSurfaceVariant` subtitle at 14 / 20.
- **Paddings** — web `content` is `28 / 56 / 32 / 56` with gap 16; mobile `body` is
  `16 / 16 / 16 / 16` with gap 12. Applied uniformly.
- **Rails** — donor, staff and hospital rails all w 76, right hairline `outlineVariant`,
  destination frames 73 wide; identical indicator/label/icon treatment.
- **Cards** — 12 radius, 1 inside `outline`, `surfaceContainerLowest`; inset panels
  `surfaceContainerLow`. No shadows anywhere.
- **Badges** — single pill family (h 28), tint mapping exactly as in §5.
- **Buttons** — `FilledButton` for the primary action, `OutlinedButton` for the secondary;
  40–44 tall on web, 48 on mobile; the two are never mixed as two primaries.
- **Jargon** — technical codes (HTTP status, gRPC, rule ids, `PLANIFIE`, `EN_LIGNE`, …)
  are replaced with plain-language UI copy throughout.


---

## 7. Roles and navigation

The `AppBar` carries the role switcher on every screen. The label is set per screen and its
text width is fixed at **62** (fits "Donor", "Centre", "Hospital", "Admin" without wrapping).
The switcher frame is a **48 dp** hit area (height 48, contents centred) in both the mobile
and web `AppBar` components, so the target is uniform everywhere. The open menu is the
`RoleSwitcher · Menu` component (Design System page): one header row + four options, the
active role filled `primaryContainer` / `onPrimaryContainer` with a `Check` icon.

| Role | Rail | Destinations (icons) |
|---|---|---|
| Donor | `NavigationRail · Web 76` | Appeals · Book · Appointments · Certificates · Stock |
| Centre staff | `NavigationRail · Staff · Web 76` | Today · Slots · Visits · Deferrals · Board (Sun, CalendarGrid, ClipboardHeart, Clock, Kanban) |
| Hospital staff | `NavigationRail · Hospital · Web 76` | New request · Requests · Stock (FilePlus, List, Stock) |
| Admin | `NavigationRail · Hospital · Web 76` | same as hospital; the stock dashboard is shared with admins (AppBar role "Admin") |

Active destination = `primaryContainer` indicator pill + SemiBold label +
`onPrimaryContainer` icon stroke. Inactive = no fill, Regular label, `onSurfaceVariant`.
Rail labels: 9.5 / 12 line height, width 68–74, centred.

---

## 8. Screen inventory

| Screen | Page | Size | Contents |
|---|---|---|---|
| 01 · Open appeals | Donor | 390 × 844 + 1440 × 900 | appeal list, eligibility hints, critical state |
| 02 · Register donor | Donor | 390 × 844 + 1440 × 900 | identity, contact, blood group, consent |
| 03 · Eligibility | Donor | 390 × 844 + 1440 × 900 | questionnaire, pass/deferred result |
| 04 · Book a slot | Donor | 390 × 844 + 1440 × 900 | place, date, slot ListTiles |
| 05 · My appointments | Donor | 390 × 844 + 1440 × 900 | upcoming / past, badge states |
| 06 · Move appointment | Donor | 390 × 844 + 1440 × 900 | reschedule flow |
| 07 · My certificates | Donor | 390 × 844 + 1440 × 900 | issued certificates, tabular references |
| 08 · Verify a certificate | Donor | 390 × 844 + 1440 × 900 | 3 states: found / not found / invalid |
| C1 · Today at the centre | Centre Staff | 1440 × 900 | staff rail, agenda, capacity |
| C2 · Donation board | Centre Staff | 1440 × 900 | kanban of visits |
| C3 · Donation visit | Centre Staff | 1440 × 900 + 390 × 844 | collection, qualification, outcome |
| C4 · Record a deferral | Centre Staff | 1440 × 900 | deferral reasons with references |
| C5 · Slot management | Centre Staff | 1440 × 900 | capacity per slot |
| H1 · New request | Hospital | 1440 × 900 | reason, blood group, component, quantity, urgency (+ critical note), anonymous patient reference, note, compensation hint |
| H2 · Requests list | Hospital | 1440 × 900 | filter chips, 4 summary tiles, table with status + urgency badges, ETA and tabular references |
| H3 · Request detail | Hospital | 1440 × 900 | reservation source, order, delivery + ETA, compensation progress with certificate entry |
| H4 · Stock dashboard | Hospital | 1440 × 900 | live levels (place × blood group × component) vs alert thresholds; place selector, legend, below-threshold alerts |
| A1 · Stock thresholds | Admin | 1440 × 900 | per place × blood group × component alert thresholds with steppers, Save changes / Reset, and an audit trail of who changed it last |
| Cover · Screen map by role | Cover | 1440 × 900 | Donor / Centre / Hospital / Admin columns listing every screen, plus the navigation-flow strip |

Web grid: one screen per row, x = 96, row pitch 1300 (900 frame + 400 gap).
Screens per page: Donor (16 frames), Centre Staff (C1–C5), Hospital (H1–H4),
Admin (A1), Cover (1).

---

## 9. Accessibility

Contrast (WCAG 2.1, computed from the final tokens — all text pairings ≥ **4.5:1**):

| Pairing | Ratio |
|---|---|
| `onSurface` on `surface` | 16.7 |
| `onSurface` on `surfaceContainer` | 14.4 |
| `onSurfaceVariant` on `surface` | 5.95 |
| `onSurfaceVariant` on `surfaceContainer` | 5.12 |
| `onSurfaceVariant` on `surfaceContainerHighest` | 4.66 |
| `onPrimary` on `primary` | 12.2 |
| `onPrimaryContainer` on `primaryContainer` | 11.8 |
| `onErrorContainer` on `errorContainer` | 7.25 |
| `onWarningContainer` on `warningContainer` | 7.39 |
| `onSuccessContainer` on `successContainer` | 8.82 |
| `error` on `surface` | 5.57 |
| `warning` on `surface` | 5.35 |
| `success` on `surface` | 6.07 |

Touch targets (mobile): every interactive control is **≥ 48 dp** — `FilledButton` /
`OutlinedButton` and `Chip` are 48 tall on mobile, `ListTile` and `NavigationBar`
destinations are 72, `Field / TextField` is 62, and the AppBar role switcher is 48.
Badges (h 28) and inline icons are non-interactive and inherit the surrounding target.


