/// One bookable time inside a day (`Creneau`, study §3).
class SlotTime {
  const SlotTime({required this.start, required this.placesLeft});

  /// Start time, `HH:mm`.
  final String start;

  /// Free places left; rule R22 says it can never go below zero.
  final int placesLeft;

  bool get isFull => placesLeft <= 0;

  /// `09:00 · 2 left` or `10:30 · Full`.
  String get label => isFull ? '$start · Full' : '$start · $placesLeft left';

  factory SlotTime.fromJson(Map<String, dynamic> json) => SlotTime(
    start: json['start'] as String,
    placesLeft: json['placesLeft'] as int,
  );
}

/// One day of the coming week with every slot of that day.
class SlotDay {
  const SlotDay({required this.date, required this.times});

  final DateTime date;
  final List<SlotTime> times;

  /// A day with no free place cannot be booked or moved to.
  bool get hasFreePlace => times.any((time) => !time.isFull);

  factory SlotDay.fromJson(Map<String, dynamic> json) => SlotDay(
    date: DateTime.parse(json['date'] as String),
    times: (json['times'] as List<dynamic>)
        .map((item) => SlotTime.fromJson(item as Map<String, dynamic>))
        .toList(growable: false),
  );
}
