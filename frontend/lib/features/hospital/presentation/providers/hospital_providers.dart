import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/providers/core_providers.dart';
import '../../../../data/mock/mock_request_repository.dart';
import '../../../../data/mock/mock_stock_repository.dart';
import '../../../../data/models/blood_request.dart';
import '../../../../data/models/stock_level.dart';
import '../../../../data/repositories/request_repository.dart';

/// The Demo Demandes service. The clock seeds the fixture's relative dates, so
/// "12 min ago" and "created today at 08:40" stay true whenever the demo runs.
final requestRepositoryProvider = Provider<RequestRepository>(
  (ref) => MockRequestRepository(ref.watch(clockProvider)),
);

/// Stock levels and thresholds, shared by H4 and the admin screens.
final stockRepositoryProvider = Provider<StockRepository>(
  (ref) => MockStockRepository(ref.watch(clockProvider)),
);

/// Every request of the hospital (H2). `?state=empty|unavailable` drives the
/// demo outcomes.
final bloodRequestsProvider =
    FutureProvider.family<List<BloodRequest>, String?>(
      (ref, variant) =>
          ref.watch(requestRepositoryProvider).requests(variant: variant),
      retry: noRetry,
    );

/// The four figures above the list (H2).
final requestSummaryProvider = FutureProvider.family<RequestSummary, String?>(
  (ref, variant) =>
      ref.watch(requestRepositoryProvider).summary(variant: variant),
  retry: noRetry,
);

/// One request with its order, delivery and compensation (H3).
typedef RequestQuery = ({String id, String? variant});

final bloodRequestProvider = FutureProvider.family<BloodRequest, RequestQuery>(
  (ref, query) => ref
      .watch(requestRepositoryProvider)
      .byId(query.id, variant: query.variant),
  retry: noRetry,
);

/// The places the stock board can show.
final stockSitesProvider = FutureProvider<List<StockSite>>(
  (ref) => ref.watch(stockRepositoryProvider).sites(),
  retry: noRetry,
);

typedef StockQuery = ({String siteId, String? variant});

/// Every level of one place (H4).
final stockSnapshotProvider = FutureProvider.family<StockSnapshot, StockQuery>(
  (ref, query) => ref
      .watch(stockRepositoryProvider)
      .snapshot(query.siteId, variant: query.variant),
  retry: noRetry,
);

typedef ThresholdQuery = ({String siteId, String? variant});

/// The alert thresholds of one place (A1).
final thresholdsProvider =
    FutureProvider.family<ThresholdSnapshot, ThresholdQuery>(
      (ref, query) => ref
          .watch(stockRepositoryProvider)
          .thresholds(query.siteId, variant: query.variant),
      retry: noRetry,
    );
