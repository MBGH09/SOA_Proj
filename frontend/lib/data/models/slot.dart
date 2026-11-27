/// How often the slots created from the C5 form repeat.
enum SlotRepeat {
  single('Single'),
  daily('Daily'),
  weekly('Weekly');

  const SlotRepeat(this.label);

  final String label;

  /// Days between two repetitions.
  int get stepInDays => switch (this) {
    SlotRepeat.single => 0,
    SlotRepeat.daily => 1,
    SlotRepeat.weekly => 7,
  };
}

/// One bookable time inside a day (`Creneau`, study §3).
class SlotTime {
  const SlotTime({
    required this.start,
    required this.placesLeft,
    this.end,
    this.capacity = 4,
  });

  /// Start time, `HH:mm`.
  final String start;

  /// End time, `HH:mm`; the centre's own table shows the whole range.
  final String? end;

  /// Free places left; rule R22 says it can never go below zero.
  final int placesLeft;

  /// Places the slot was created with (`capacite`, default 4 per study §16).
  final int capacity;

  bool get isFull => placesLeft <= 0;

  /// Places already taken, never below zero.
  int get booked {
    final taken = capacity - placesLeft;
    return taken < 0 ? 0 : taken;
  }

  /// Share of the slot that is taken, 0 to 1.
  double get fill =>
      capacity <= 0 ? 0 : (booked / capacity).clamp(0, 1).toDouble();

  /// `09:00 · 2 left` or `10:30 · Full`.
  String get label => isFull ? '$start · Full' : '$start · $placesLeft left';

  /// `09:00 – 10:00`, or just the start when the slot has no end.
  String get range => end == null ? start : '$start – $end';

  factory SlotTime.fromJson(Map<String, dynamic> json) => SlotTime(
    start: json['start'] as String,
    placesLeft: json['placesLeft'] as int,
    end: json['end'] as String?,
    capacity: json['capacity'] as int? ?? 4,
  );
}

/// One day of the coming week with every slot of that day.
class SlotDay {
  const SlotDay({required this.date, required this.times});

  final DateTime date;
  final List<SlotTime> times;

  /// A day with no free place cannot be booked or moved to.
  bool get hasFreePlace => times.any((time) => !time.isFull);

  /// Places, booked and free, of the whole day (C5 summary).
  int get places => times.fold(0, (sum, time) => sum + time.capacity);
  int get booked => times.fold(0, (sum, time) => sum + time.booked);
  int get placesLeft => times.fold(0, (sum, time) => sum + time.placesLeft);

  /// Share of the day that is taken, 0 to 1.
  double get fill => places == 0 ? 0 : (booked / places).clamp(0, 1).toDouble();

  factory SlotDay.fromJson(Map<String, dynamic> json) => SlotDay(
    date: DateTime.parse(json['date'] as String),
    times: (json['times'] as List<dynamic>)
        .map((item) => SlotTime.fromJson(item as Map<String, dynamic>))
        .toList(growable: false),
  );
}
