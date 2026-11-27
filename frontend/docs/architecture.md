# Architecture — Flutter frontend

## 1. Constraints

- **Frontend only.** No backend, gateway or database exists yet. Every screen must run on
  local mocks from day one.
- **Design is frozen.** The 28 FigCraft screen frames are the visual contract; `docs/tokens.md`
  is the token contract. Do not invent styles.
- **Two form factors.** Web 1440 × 900 and mobile 390 × 844, same widgets.
- **Four roles, no sign-in.** Donor / Centre / Hospital / Admin, switched from the AppBar.
  Real authentication comes later; the switcher is the demo control.

## 2. Layers

```
presentation (screens, widgets, providers)
        │  depends on ↓
domain (entities, enums, use-cases — pure Dart)
        │  depends on ↓
data (repository implementations, DTOs, mocks)
        │  depends on ↓
core (network, errors, constants, validation, utils)
```

Rule: **UI never imports `dio` and never reads JSON.** A screen asks a provider, the
provider calls a repository interface, the repository is backed by either `MockApi` or the
Gateway client. Swapping the backend touches `data/` only.

| Folder | Owns | Must not |
|---|---|---|
| `features/<area>/presentation` | screens, widgets, Riverpod providers | call `dio`, hold business rules |
| `features/<area>/domain` | entities, enums, use-cases | import Flutter widgets |
| `features/<area>/data` | repository impls, DTO mapping | import screens |
| `data/repositories` | abstract contracts shared across features | know about widgets |
| `data/mock` | fake backend + fixtures | be imported by production code paths directly |
| `core/network` | dio setup, interceptors, result type | know feature models |
| `shared/widgets` | design-system widgets | contain feature logic |
| `shared/layouts` | shell, AppBar, RoleSwitcher, rails | fetch data |
| `app/theme` | token → `ThemeData` | be bypassed by hard-coded values |

## 3. Routing

`go_router` with four top-level branches behind one shell (`AppShell`). The shell renders
the web rail (≥ 900 dp) or the mobile `NavigationBar` (< 900 dp) and the AppBar.

| Branch | Prefix | Screens |
|---|---|---|
| Donor | `/donor` | appeals, register, eligibility, book, appointments, move, certificates, verify |
| Centre | `/centre` | today, board, visit, deferral, slots |
| Hospital | `/hospital` | new request, requests, request detail, stock |
| Admin | `/admin` | stock thresholds, stock (shared) |

- `/` redirects to `/donor/appeals`.
- The route's first segment decides which role's shell is drawn, so a deep link always
  renders the right rail. `AppShell` mirrors that role into the session; the RoleSwitcher
  writes the session and navigates to that branch's root.
- Deep links mirror the FigCraft screen numbers (`/donor/appeals`, `/centre/board`, …).
- Back behaviour: mobile pushes; web uses the same router with URL sync.

## 4. State

- `flutter_riverpod` 3.x (`Notifier` / `AsyncNotifier`).
- One provider file per feature (`donor_providers.dart`, …); repository providers are
  overridden at app start with either the mock or the API implementation.
- Screen state is local (`StatefulWidget`/`ConsumerState`) unless shared; no global
  mutable singletons.

## 5. Networking, errors, mocks

`core/errors/ApiError` mirrors the contract's standard JSON:

```json
{ "timestamp": "...", "status": 409, "error": "CONFLICT", "message": "...", "path": "..." }
```

`ApiError` exposes `status`, `message`, `fieldErrors`. One `messageFor(error)` helper maps
400 / 401 / 404 / 409 / 503 and the 202 "accepted, pending" cases to UI copy; every screen
uses it (this is the design's "plain-language" rule in code).

**Mock-first plan**

- `MockApi` keeps an in-memory store seeded from `data/mock/fixtures/*.json`.
- Fixtures mirror the study's seed: 5 places (CNTS + 4 CRTS/hospital sites), ~20 donors
  (including a 48 kg donor and an active deferral), free slots for 7 days, one finished
  donation with an issued certificate, hospital requests in the five statuses, 24 stock
  gauges.
- Artificial latency (200–600 ms) so loading states are real.
- Switch: `--dart-define=USE_MOCKS=true|false` (default `true`), plus
  `--dart-define=API_BASE_URL=http://localhost:8080`.
- Resources to mock, matching the contracts: `donneurs`, `centres`, `creneaux`,
  `rendez-vous`, `dons` (+ `entretien`, `prelevement`, `repos`, `certificat`),
  `certificats/{numero}`, `demandes` (+ `compensations`), `appels`, `stock/niveaux`
  (+ `stock/niveaux/flux` later via SSE).

## 6. Theming

`app/theme/tokens/` holds the values from `docs/tokens.md` as Dart constants; `app_theme.dart`
maps them to Material 3:

| Token group | Mapping |
|---|---|
| 24 `color.*` | `ColorScheme` (+ `ThemeExtension` for `success` / `warning` families) |
| TextTheme S1–S10 | `TextTheme` (`displaySmall` … `labelMedium`, plus `mono/Table figures`) |
| Spacing (4 / 8 dp scale) | `AppSpacing` constants, not a `ThemeData` field |
| Radii (8 / 10 / 12 / pill) | `AppRadius` constants used by shared widgets |
| Red | `color.error` only: critical / low / urgent |

Also set: `useMaterial3: true`, Inter as `fontFamily`, `fontFeatures: [tnum]` for references,
1 px `outline` borders, **no elevation** (surface tone only).

Accessibility is already baked into the tokens (`onSurfaceVariant` `#655F58`, `warning`
`#8F5C0E`, every pairing ≥ 4.5:1). Touch targets ≥ 48 dp on mobile.

## 7. Responsive rules

| Range | Shell | Content |
|---|---|---|
| < 900 dp | AppBar + `NavigationBar` (80), body padding 16, gap 12, CTA/chips 48 | single column |
| ≥ 900 dp | `NavigationRail` (76) + AppBar + content padding 28/56/32/56, gap 16, CTA 44 | 1–2 columns per design |

Breakpoint lives in one place (`AppBreakpoints.wide` in `app/theme/tokens/spacing_tokens.dart`).
Never branch on device type; branch on width.

## 8. Naming

- Files `snake_case.dart`; classes `PascalCase`; providers `<name>Provider`.
- Screens: `<screen>_screen.dart` (e.g. `donation_board_screen.dart`).
- Shared widgets keep the design names: `AppBar`, `RoleSwitcher`, `NavigationRail`,
  `NavigationBar`, `FilledButton`, `OutlinedButton`, `TextField`, `Card`, `ListTile`,
  `Chip`, `Badge`, `DataTable`, `Divider`, `StatCard`.
- No abbreviations except the domain's own (`CRTS`, `CNTS`, `CGR`).

## 9. Testing (planned)

- `test/unit` — validators, enum→label mapping, mock API rules (slot full, deferral, wrong step).
- `test/widget` — registration validation, booking 409, board step order, certificate states.
- `test/fixtures` — shared JSON.
- Target: `flutter test` green before any integration work.

## 10. Open decisions

- Auth: no sign-in screen now; when Student 2 exposes the token endpoint, add an in-memory
  token + 401 redirect and hide staff data from donors.
- Live stock: polling first (`GET /stock/niveaux` every 5 s), then SSE when the gateway is ready.
- PDF certificates: `printing`/`pdf` package later; not part of the visual scope.