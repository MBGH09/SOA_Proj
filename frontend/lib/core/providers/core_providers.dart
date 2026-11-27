import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../time/clock.dart';

/// Shared infrastructure. Repositories and screens read the clock from here so
/// a test can override it with a [FrozenClock].
final clockProvider = Provider<Clock>((ref) => const SystemClock());

/// The retry policy of every data provider.
///
/// Riverpod 3 retries a failed provider ten times with a backoff by default.
/// A contract answer is final — a 400, 404 or 409 will not change on its own —
/// so it is shown once instead of being hidden behind a spinner that keeps
/// restarting. Retry and backoff for the transient 503/504 answers belong to
/// the Gateway client (study §8, resilience table), not to the screens.
Duration? noRetry(int retryCount, Object error) => null;
