import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/providers/core_providers.dart';
import '../../../../data/models/appeal.dart';
import '../../../../data/mock/mock_appeal_repository.dart';
import '../../../../data/repositories/appeal_repository.dart';

final appealRepositoryProvider = Provider<AppealRepository>(
  (ref) => const MockAppealRepository(),
);

final openAppealsProvider = FutureProvider<List<Appeal>>(
  (ref) => ref.watch(appealRepositoryProvider).fetchOpenAppeals(),
  retry: noRetry,
);