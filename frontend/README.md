# National Blood Network — Flutter frontend

**Status: foundation + first screen implemented.**
The Flutter app, design tokens, shell and mock data layer are in place.
**01 · Open blood appeals** (Donor, mobile + web) is built end-to-end; the remaining
screens are routed to a placeholder while they are implemented one by one.

Design source of truth: `../out/project.figcraft` (exported PNGs in `../out/`).
Design tokens: [`docs/tokens.md`](docs/tokens.md).

---

## Stack

| Concern | Choice | Version |
|---|---|---|
| SDK | Flutter stable / Dart | 3.44.4 / 3.12.2 |
| UI | Material 3 | — |
| State | `flutter_riverpod` | 3.5.0 |
| Routing | `go_router` | 18.1.4 |
| HTTP | `dio` | 5.11.1 |
| Formatting | `intl` | 0.20.2 |

The backend is not ready, so the app is **mock-first**: repositories are interfaces and a
`MockApi` serves the demo seed from local JSON. Switching to the Gateway later is one flag
(`--dart-define=USE_MOCKS=false`) plus one repository binding — no screen changes.

## Run

```powershell
cd D:\Study\p\frontend
flutter pub get
flutter run -d chrome --dart-define=USE_MOCKS=true
```

Android: `flutter run -d <device-id>`. The 1440-wide web design is the primary demo target;
the 390-wide mobile design is the same screens through a breakpoint.

## Layout

```
lib/
  main.dart                    # bootstrap (template for now)
  app/
    app.dart                   # root MaterialApp.router
    router/                    # go_router config, role branches, redirects
    theme/
      app_theme.dart           # ThemeData built from tokens
      tokens/                  # colors, typography, spacing, shape (from tokens.md)
  core/
    constants/                 # app constants, API paths
    errors/                    # ApiError, Failure, message mapping
    network/                   # dio client, interceptors, result type
    utils/                     # date/number/reference formatters (tnum)
    validation/                # form validators (CIN, phone, weight, ...)
  data/
    models/                    # DTOs / domain entities (API enums kept intact)
    repositories/              # abstract repository contracts
    mock/
      mock_api.dart            # in-memory fake backend
      fixtures/                # seed JSON (5 places, donors, slots, requests, stock)
  features/
    donor/                     # domain · data · presentation/{screens,widgets}
    centre/                    # staff area (C1–C5)
    hospital/                  # H1–H4
    admin/                     # A1
  shared/
    layouts/                   # AppShell, RoleSwitcher, rails, NavigationBar
    widgets/                   # design-system widgets (Badge, Chip, Field, DataTable, ...)
  l10n/                        # copy / localization
assets/
  fonts/                       # drop Inter .ttf files here (see assets/fonts/README.md)
  images/  icons/
test/
  unit/  widget/  fixtures/
integration_test/
docs/
  architecture.md              # layers, rules, mock plan, theming
  screen-map.md                # every FigCraft frame → route + file
  tokens.md                    # exported design tokens
```

## Conventions

- **Feature-first.** A screen owns its widgets; cross-feature UI lives in `lib/shared`.
- **One screen widget per FigCraft frame.** Mobile and web are one responsive widget with a
  single breakpoint (900 dp), not two files. The FigCraft frames are the two layouts.
- **Layer names mirror Flutter widgets** (`AppBar`, `NavigationRail`, `FilledButton`,
  `ListTile`, `Chip`, `DataTable`, …) so the design file and the code read the same.
- **Tokens only.** No hard-coded colours or sizes in feature code; everything resolves
  through `app/theme`. Red (`color.error`) is reserved for critical states.
- **Plain-language copy.** API enums (`EN_ATTENTE`, `APPROVISIONNEMENT_EN_COURS`, …) stay in
  the models; the presentation layer maps them to human labels. No technical codes on screen.
- **Tabular figures** for references, times and quantities.
- **No direct `dio` calls in widgets.** Screen → provider → repository → API.

## Next steps (when coding starts)

1. `app/theme` — map the 24 colour tokens, TextTheme S1–S10, spacing 8 dp and radii.
2. `shared/layouts` — AppShell + AppBar + RoleSwitcher, donor/staff/hospital rails, NavigationBar.
3. `data` — models + fixtures + mock API for the seven resources.
4. Donor screens (01–08), then Centre (C1–C5), Hospital (H1–H4), Admin (A1).
5. Loading / empty / error states per screen (400, 404, 409, 202, 503).

See [`docs/architecture.md`](docs/architecture.md) and [`docs/screen-map.md`](docs/screen-map.md).