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
- [x] 02 · Register donor — mobile + web
- [x] 03 · Eligibility — mobile + web (eligible / deferred / not eligible)
- [x] 04 · Book a slot — mobile + web (centre → time → confirm)
- [x] 05 · My appointments — mobile + web (upcoming / past, cancel)
- [x] 06 · Move appointment — mobile + web
- [x] 07 · My certificates — mobile + web (issued / preparing / empty)
- [x] 08 · Verify a certificate — mobile + web (empty / valid / not valid / not found)

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

- 02 · Register adds a **National ID (CIN)** field that is not in the FigCraft frame. The
  contract needs it: a duplicate CIN is the 409 case (rule R5). Mock CIN `12345678` always
  returns 409, so the conflict state can be reviewed by hand.
- 03 · Eligibility can be reviewed in each outcome with `?state=eligible` and
  `?state=not-eligible` (default is the donor's real result, the deferred frame). Part 5
  replaces this query switch with the shared `MockScenario` control.
- 04 · Book and 06 · Move read the contract failures from `?state=` too
  (`slot-full`, `day-taken`, `deferred` → 409). Same Part 5 replacement.
- 04 · Book is one screen with the three steps the frame names (Centre · Time · Confirm); the
  FigCraft frame captures step 2. It opens on the day/time shown in the frame (Thu 12 · 14:00)
  and on the nearest centre, so the first view matches the design.
- 06 · Move's design frame labels 18 Nov "THU"; 18 Nov 2026 is a Wednesday, so the app prints
  "Wed 18" / "WED 18 NOV" from the real date.
- 05 · My appointments on mobile keeps both lists in the default "Upcoming" view (matching the
  frame) and the "Past" chip filters to history only. Web shows both columns at once.
- Bundle Inter Regular / Italic / SemiBold / Bold in `assets/fonts` (system fallback until then).
- The design's staff rail lists five items (Today · Slots · Visits · Deferrals · Board). There is
  no Visits list route in the screen map, so the rail ships Today · Board · Slots · Deferrals and
  a visit is opened from Today or the Board. Revisit if a Visits list screen is added.
- `MockScenario` plumbing exists but is not yet wired into every mock repository (Part 5).
- Add loading / empty / error states per screen as they land.
- Every data provider opts out of Riverpod 3's automatic retry (`noRetry` in
  `core/providers/core_providers.dart`). Riverpod 3 retries a failed provider ten times with a
  backoff, which hid the contract answers behind a restarting spinner: a 400, 404 or 409 is
  final and must be shown once. Retry and backoff for the transient 503/504 answers belong to
  the Gateway client (study §8), so revisit this when the API is wired.
- 07 · My certificates lists what `certificates.json` holds: the frame's two cards (one issued,
  one preparing). The older certificate numbers named by 05's past visits are not repeated in
  that fixture; the real service returns every certificate of the donor.
- 07 · Download PDF and Print go through the new `CertificatePdfService`. The mock answers with
  the file name the endpoint will produce (`GET /dons/{id}/certificat`, `Accept:
  application/pdf`) and writes nothing, so the screen confirms "Requested CERT-….pdf" until the
  backend is connected. A pending certificate has no file: its Download PDF is disabled and
  Print explains that the number is still being prepared.
- 08 · Verify's not-found frame prints a second copy of the field inside the answer card. The
  app puts that error on the real field (red outline + "No certificate found with this number.")
  and keeps the "Not found" badge and the explanation in the answer card, so nothing is read
  twice.
- 08 · `valide = false` is handled as a "Not valid" answer (critical badge, "cannot be counted
  for compensation"). The contract allows it, the frames do not show it.
- 08 · The answer prints ISSUING AUTHORITY from an optional `autorite` field: the contract's
  `CertificatVerification` carries `valide`, `date` and `centre` only. Either the contract owner
  adds it or the row is dropped.
- 08 · `?state=valid|invalid|not-found|unavailable` is a demo control that prefills the number
  of the frame and short-circuits the lookup; Part 5 folds it into the shared `MockScenario`.
- Replace the mock repository bindings with the Gateway client when the backend is ready.
