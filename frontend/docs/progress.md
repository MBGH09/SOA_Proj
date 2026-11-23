# Build progress

Keep this list current — one line per FigCraft screen.

## Foundation

- [x] Flutter project (Android / iOS / Web), dependencies, folder structure
- [x] Colour tokens + `ColorScheme` (24 tokens, accessibility values)
- [x] Type scale (Inter S1–S10, tabular figures) + spacing / radii / breakpoint
- [x] `AppTheme` (Material 3, no elevation, hairline outlines)
- [x] App shell: top bar, role switcher, donor rail, mobile navigation bar
- [x] Routing (`go_router`) for all 28 screens + shell
- [x] Mock data layer (`AppealRepository` + JSON fixtures)
- [x] Unit test for the appeal model

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

- Bundle Inter Regular / Italic / SemiBold / Bold in `assets/fonts`.
- Add loading / empty / error states per screen as they land.
- Replace the mock repository bindings with the Gateway client when the backend is ready.