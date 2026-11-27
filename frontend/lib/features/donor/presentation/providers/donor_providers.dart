import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/providers/core_providers.dart';
import '../../../../data/mock/mock_donor_repository.dart';
import '../../../../data/models/eligibility.dart';
import '../../../../data/repositories/donor_repository.dart';

final donorRepositoryProvider = Provider<DonorRepository>(
  (ref) => MockDonorRepository(),
);

/// Eligibility of the donor in session.
///
/// The `variant` argument is a demo control driven by the `?state=` query
/// parameter, so each outcome can be reviewed at `/donor/eligibility?state=...`.
final eligibilityProvider = FutureProvider.family<Eligibility, String?>(
  (ref, variant) =>
      ref.watch(donorRepositoryProvider).eligibility(variant: variant),
  retry: noRetry,
);
