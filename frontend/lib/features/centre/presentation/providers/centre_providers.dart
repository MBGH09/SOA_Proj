import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/providers/core_providers.dart';
import '../../../../data/mock/mock_deferral_repository.dart';
import '../../../../data/mock/mock_visit_repository.dart';
import '../../../../data/models/centre_day.dart';
import '../../../../data/models/deferral.dart';
import '../../../../data/models/slot.dart';
import '../../../../data/models/visit.dart';
import '../../../../data/repositories/deferral_repository.dart';
import '../../../../data/repositories/visit_repository.dart';
import '../../../donor/presentation/providers/appointment_providers.dart';

/// The centre the staff session works in. A real client reads it from the
/// session; the demo is single-centre (`CRTS Sfax`), as in the frames.
const String centreId = 'C-SFAX';

final visitRepositoryProvider = Provider<VisitRepository>(
  (ref) => MockVisitRepository(),
);

final deferralRepositoryProvider = Provider<DeferralRepository>(
  (ref) => MockDeferralRepository(),
);

/// The day at a glance (C1).
///
/// The `variant` argument is a demo control driven by the `?state=` query
/// parameter (`quiet`, `unavailable`).
final centreDayProvider = FutureProvider.family<CentreDay, String?>(
  (ref, variant) => ref.watch(visitRepositoryProvider).today(variant: variant),
  retry: noRetry,
);

/// Every open visit by step (C2).
final donationBoardProvider =
    FutureProvider.family<List<BoardColumn>, String?>(
      (ref, variant) =>
          ref.watch(visitRepositoryProvider).board(variant: variant),
      retry: noRetry,
    );

/// One visit and the demo variant of its step (C3).
typedef VisitQuery = ({String id, String? variant});

final visitProvider = FutureProvider.family<Visit, VisitQuery>(
  (ref, query) =>
      ref.watch(visitRepositoryProvider).byId(query.id, variant: query.variant),
  retry: noRetry,
);

/// The reference list of deferral reasons and durations (C4).
final deferralReasonsProvider = FutureProvider<List<DeferralReason>>(
  (ref) => ref.watch(deferralRepositoryProvider).reasons(),
  retry: noRetry,
);

/// The donor behind a CIN, with any active deferral (C4).
final deferralLookupProvider = FutureProvider.family<DeferralLookup?, String>(
  (ref, cin) => ref.watch(deferralRepositoryProvider).lookup(cin),
  retry: noRetry,
);

/// The centre's own week of slots, with capacity and fill (C5).
final centreSlotDaysProvider = FutureProvider.family<List<SlotDay>, String>(
  (ref, id) =>
      ref.watch(appointmentRepositoryProvider).centreDays(centreId: id),
  retry: noRetry,
);
