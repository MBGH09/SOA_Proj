/// Source of "now", so countdowns (the 15-minute rest) and "updated X ago"
/// captions can be tested without waiting for real time.
abstract interface class Clock {
  DateTime now();
}

class SystemClock implements Clock {
  const SystemClock();

  @override
  DateTime now() => DateTime.now();
}

/// Fixed clock for tests and deterministic screenshots.
class FrozenClock implements Clock {
  FrozenClock(this._instant);

  final DateTime _instant;

  @override
  DateTime now() => _instant;
}
