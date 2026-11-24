# Mock fixtures

Local JSON that mirrors the API contracts, loaded by `../mock_api.dart`. The backend is not
ready, so these are the only data source until the Gateway is live.

Populate one file per resource (names match `docs/architecture.md` §5):

| File | Feeds | Notes |
|---|---|---|
| `places.json` | centre/place selector, stock | CNTS + 4 places |
| `donors.json` | donor register, eligibility | ~20 donors, incl. a 48 kg donor and one with an active deferral |
| `centres.json` | booking, slots | type + city filters |
| `slots.json` | book / move appointment | free places for the next 7 days, capacity 4 |
| `appointments.json` | my appointments, centre today | mixed statuses |
| `donations.json` | donation visit + board | one finished donation with an issued certificate |
| `certificates.json` | my certificates, verify | issued + preparing + one invalid number |
| `appeals.json` | open appeals | at least one Critical (O−) |
| `requests.json` | hospital list / detail | all five statuses: waiting, satisfied, supplying, shortage, cancelled |
| `stock.json` | stock dashboard / thresholds | 24 gauges (8 groups × 3 components) per place |

Rules:

- Keep API enum values exactly as in the contracts (`EN_ATTENTE`, `APPROVISIONNEMENT_EN_COURS`,
  `EMIS`, `CGR`, …). The presentation layer translates them to plain-language labels.
- Reference numbers stay realistic (`CERT-2026-000314`, `RDV-…`, `D-…`) so tabular figures
  and column alignment can be checked.
- Error cases are produced by `MockApi` logic (slot full, 409 wrong step, unknown certificate),
  not by extra fixture files.