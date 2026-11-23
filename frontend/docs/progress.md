# Build progress

Keep this list current — one line per FigCraft screen.

## Foundation

- [x] Flutter project (Android / iOS / Web), dependencies, folder structure
- [x] Colour tokens + `ColorScheme` (24 tokens, accessibility values)
- [x] Type scale (Inter S1–S10, tabular figures) + spacing / radii / breakpoint
- [x] `AppTheme` (Material 3, no elevation, hairline outlines)
- [x] `color.primary` lightened `#26201D → #3B3430` (12.2:1 on white)
- [x] Role-aware shell: `AppNavigationRail` (all roles) + `MobileNavBar`, route decides the role
- [x] `RoleSwitcher` backed by a `SessionRepository` instead of a bare role provider
- [x] Routing (`go_router`) for all 28 screens + shell
- [x] Core helpers: `Clock`, `AppFormatters`, `ApiError` + `apiMessageFor`, `MockScenario`
- [x] Shared widgets: page header, text field, chip, list tile, data table, divider,
      stat card, stock cell, step indicator, empty / error / loading state,
      confirm dialog, snack bar
- [x] Mock data layer (`AppealRepository` + JSON fixtures)
- [x] Tests: appeal model, navigation, formatters, API errors, shell

## Screens

### Donor

- [x] 01 · Open blood appeals — mobile + web
- [ ] 02 · Register donor
- [ ] 03 · Eligibility
- [ ] 04 · Book a slot
- [ ] 05 · My appointments
- [ ] 06 · Move appointment
- [ ] 07 · My certificates
- [ ] 08 · Verify a certificate

### Centre staff

- [ ] C1 · Today at the centre
- [ ] C2 · Donation board
- [ ] C3 · Donation visit
- [ ] C4 · Record a deferral
- [ ] C5 · Slot management

### Hospital

- [ ] H1 · New request
- [ ] H2 · Requests list
- [ ] H3 · Request detail
- [ ] H4 · Stock dashboard

### Admin

- [ ] A1 · Stock thresholds

## Known follow-ups

- Bundle Inter Regular / Italic / SemiBold / Bold in `assets/fonts` (system fallback until then).
- The design's staff rail lists five items (Today · Slots · Visits · Deferrals · Board). There is
  no Visits list route in the screen map, so the rail ships Today · Board · Slots · Deferrals and
  a visit is opened from Today or the Board. Revisit if a Visits list screen is added.
- `MockScenario` plumbing exists but is not yet wired into every mock repository (Part 5).
- Add loading / empty / error states per screen as they land.
- Replace the mock repository bindings with the Gateway client when the backend is ready.
