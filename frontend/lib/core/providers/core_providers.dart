import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../time/clock.dart';

/// Shared infrastructure. Repositories and screens read the clock from here so
/// a test can override it with a [FrozenClock].
final clockProvider = Provider<Clock>((ref) => const SystemClock());
