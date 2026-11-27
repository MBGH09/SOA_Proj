# Mock fixtures

Local JSON that mirrors the API contracts, loaded by the `Mock*Repository` classes through
`rootBundle`. The backend is not ready, so these are the only data source until the Gateway is
live. The folder is declared as an asset in `pubspec.yaml` (`lib/data/mock/fixtures/`).

| File | Feeds | Notes |
|---|---|---|
| `appeals.json` | 01 open appeals | at least one Critical (O−) |
| `centres.json` | booking, centre selectors | kind + city, distance, closing time |
| `eligibility.json` | 03 eligibility (default answer) | reference result |
| `eligibility_eligible.json` | 03 eligible variant | demo override |
| `eligibility_not-eligible.json` | 03 not-eligible variant | demo override |
| `slot_days.json` | 04 book a slot | free places for the next days |
| `slot_days_move.json` | 06 move appointment | alternative times |
| `appointments.json` | 05 my appointments | upcoming + past, one certificate per past donation |
| `centre_slots.json` | C5 slot management | four days, one full slot |
| `certificates.json` | 07 my certificates, 08 verify | issued, preparing, one invalid |
| `visits.json` | C1–C3 | 18 visits across every step and the board's columns |
| `centre_day.json` | C1 today | figures, agenda (open / waiting / collected / rest / absent), queue, stock |
| `deferrals.json` | C4 record a deferral | reasons, lookup records, one active deferral |
| `requests.json` | H1–H3 | six requests, summary figures, five statuses, compensation |
| `stock.json` | H4 stock dashboard, H1 local stock | 24 levels (8 groups × 3 components) for three places, figures, `updatedAt` |
| `stock_thresholds.json` | A1, H4 | 24 threshold rules per place, changed-by/at, audit trail |

Rules:

- Keep API enum values exactly as in the contracts (`EN_ATTENTE`, `APPROVISIONNEMENT_EN_COURS`,
  `EMIS`, `CGR`, `O_NEG`, `BAS`, ...). The presentation layer translates them to plain-language
  labels.
- Reference numbers stay realistic (`CERT-2026-000314`, `REQ-2026-0184`, `D-...`) so tabular
  figures and column alignment can be checked.
- `requests.json` stores `createdDaysAgo`, `createdTime` and `updatedMinutesAgo` rather than
  absolute timestamps. `MockRequestRepository` anchors them to `Clock.now()`, so H2 keeps
  reading "12 min ago" and H3 "created today at 08:40" whenever the demo runs.
- `stock_thresholds.json` carries the eight changed rows the A1 frame shows; the other sixteen
  rules exist so every group × component is editable. `ThresholdSnapshot.rulesByLastChange`
  puts the changed rows first.
- Error cases are produced by repository logic (slot full, 409 wrong step, unknown certificate,
  duplicate certificate, 503), not by extra fixture files.