# Screen map — FigCraft frame → Flutter route + file

**28 screen frames** on 5 pages (`Donor` 16, `Centre Staff` 6, `Hospital` 4, `Admin` 1,
`Cover` 1). The Cover frame is a design-document overview, not an app screen.

Every screen is **one responsive widget**. The two frame widths (390 mobile / 1440 web) are
two layouts of the same file through the 900 dp breakpoint. Do not create `_mobile.dart`
and `_web.dart` copies.

## Donor — `features/donor/presentation/screens/`

| # | Screen | Layouts | Route | File | Key states |
|---|---|---|---|---|---|
| 01 | Open appeals | 390 + 1440 | `/donor/appeals` | `open_appeals_screen.dart` | list, loading, empty, error; Critical badge |
| 02 | Register donor | 390 + 1440 | `/donor/register` | `register_donor_screen.dart` | 400 field errors, 409 CIN exists, submitting |
| 03 | Eligibility | 390 + 1440 | `/donor/eligibility` | `eligibility_screen.dart` | eligible / deferred (with end date) / not eligible |
| 04 | Book a slot | 390 + 1440 | `/donor/book` | `book_slot_screen.dart` | free slots, 409 slot full, deferral, already booked |
| 05 | My appointments | 390 + 1440 | `/donor/appointments` | `my_appointments_screen.dart` | upcoming / past, cancel |
| 06 | Move appointment | 390 + 1440 | `/donor/appointments/move` | `move_appointment_screen.dart` | new slot, 409 |
| 07 | My certificates | 390 + 1440 | `/donor/certificates` | `my_certificates_screen.dart` | issued / preparing, empty, 503, refresh |
| 08 | Verify a certificate | 390 + 1440 | `/donor/verify` | `verify_certificate_screen.dart` | empty input / valid / not valid / not found (3 frames + contract) |

## Centre staff — `features/centre/presentation/screens/`

| # | Screen | Layouts | Route | File | Key states |
|---|---|---|---|---|---|
| C1 | Today at the centre | 1440 + 390 | `/centre/today` | `today_screen.dart` | figures, agenda (open / check in / close), queue, stock, low-stock alert |
| C2 | Donation board | 1440 + 390 | `/centre/board` | `donation_board_screen.dart` | 5 columns (Reception → Done), empty board, 503 |
| C3 | Donation visit | 1440 + 390 | `/centre/visits/:id` | `donation_visit_screen.dart` | reception → interview → collection → rest; 409 wrong step, 400 volume, 409 rest |
| C4 | Record a deferral | 1440 + 390 | `/centre/deferrals/new` | `record_deferral_screen.dart` | CIN lookup, reason, duration, start/end date, invalid range, current deferral |
| C5 | Slot management | 1440 + 390 | `/centre/slots` | `slot_management_screen.dart` | day table + summary, create slots (single/daily/weekly), overlap 409 |

## Hospital — `features/hospital/presentation/screens/`

| # | Screen | Layouts | Route | File | Key states |
|---|---|---|---|---|---|
| H1 | New request | 1440 | `/hospital/requests/new` | `new_request_screen.dart` | motif/group/component/quantity/urgency, anonymous ref |
| H2 | Requests list | 1440 | `/hospital/requests` | `requests_list_screen.dart` | 5 statuses, urgency badges, filters |
| H3 | Request detail | 1440 | `/hospital/requests/:id` | `request_detail_screen.dart` | delivery ETA, compensation progress + cert entry |
| H4 | Stock dashboard | 1440 | `/hospital/stock` | `stock_dashboard_screen.dart` | live levels vs thresholds, legend |

## Admin — `features/admin/presentation/screens/`

| # | Screen | Layouts | Route | File | Key states |
|---|---|---|---|---|---|
| A1 | Stock thresholds | 1440 | `/admin/thresholds` | `stock_thresholds_screen.dart` | per group × component steppers, save/reset, audit trail |
| — | Stock dashboard (shared) | 1440 | `/admin/stock` | reuse `hospital/.../stock_dashboard_screen.dart` | same widget, Admin role label |

## Shell and shared widgets

| Design layer | Flutter file (`lib/shared/`) |
|---|---|
| `AppBar · Mobile 392` / `AppBar · Web 1336` | `layouts/app_bar.dart` |
| `RoleSwitcher` + `RoleSwitcher · Menu` | `layouts/role_switcher.dart` |
| `NavigationRail · Web 76` (all roles) | `navigation/app_navigation_rail.dart` |
| rail + bar destinations per role | `navigation/app_destination.dart` |
| `NavigationBar · Mobile 392` | `navigation/mobile_nav_bar.dart` |
| `AppShell` (rail/nav + AppBar + body) | `layouts/app_shell.dart` |
| `CTA / FilledButton`, `OutlinedButton` | `widgets/app_buttons.dart` |
| `Field / TextField` (+ Focused, Error) | `widgets/app_text_field.dart` |
| `Chip / FilterChip` | `widgets/app_chip.dart` |
| step chips (`1 · Centre  2 · Time`) | `widgets/app_flow_steps.dart` |
| circular icon tile (centre pin, past visit) | `widgets/app_icon_avatar.dart` |
| closed-list field (blood group, place) | `widgets/app_select_field.dart` |
| `Badge / Neutral · Success · Warning · Critical` | `widgets/app_badge.dart` |
| label/value grid (certificate fields, verification answer) | `widgets/app_detail_grid.dart` |
| `ListTile` | `widgets/app_list_tile.dart` |
| `DataTable` | `widgets/app_data_table.dart` |
| `StatCard` | `widgets/app_stat_card.dart` |
| stock board cell | `widgets/stock_cell.dart` |
| step dots | `widgets/app_step_indicator.dart` |
| `Divider` | `widgets/app_divider.dart` |
| page title block | `widgets/app_page_header.dart` |
| empty / error / loading | `widgets/app_empty_state.dart`, `app_error_state.dart`, `app_loading_state.dart` |
| confirm dialog / snack bar | `widgets/app_confirm_dialog.dart`, `widgets/app_snack_bar.dart` |

## Rules captured from the design

- Red only for critical: refused/absent visit, invalid certificate, low stock, over-capacity.
- Selected chips (`FilterChip` Selected, step chips, day/time slots) are solid `primary` with
  `onPrimary` text; a full slot is faded and not tappable.
- Beige panels (Rules, Currently booked, Before you confirm) are `surfaceContainerLow`; a badge
  inside one reads lighter (`surfaceContainerLowest`).
- Status → badge mapping: Open → Neutral, Success → green, Near/Attention → amber,
  Critical/Low → red.
- References and quantities use tabular figures (`tnum`).
- No technical codes in UI copy — map enums to labels in presentation.
- No sign-in screen; the AppBar role switcher is the role control for the demo.