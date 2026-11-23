import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

/// Date, time and quantity formatting shared by every screen.
///
/// References and quantities are always rendered with tabular figures so
/// columns line up (tokens.md §2).
abstract final class AppFormatters {
  static final DateFormat _dayMonth = DateFormat('d MMM');
  static final DateFormat _fullDate = DateFormat('d MMM y');
  static final DateFormat _time = DateFormat('HH:mm');
  static final DateFormat _dayMonthTime = DateFormat('d MMM · HH:mm');

  /// `14 Nov`
  static String dayMonth(DateTime date) => _dayMonth.format(date);

  /// `14 Nov 2026`
  static String fullDate(DateTime date) => _fullDate.format(date);

  /// `09:30`
  static String time(DateTime instant) => _time.format(instant);

  /// `14 Nov · 09:30`
  static String dayMonthTime(DateTime instant) => _dayMonthTime.format(instant);

  /// `2 min ago`, `3 h ago`, `2 d ago`, `just now`.
  static String ageFrom(DateTime then, DateTime now) {
    final seconds = now.difference(then).inSeconds;
    if (seconds < 45) return 'just now';
    final minutes = (seconds / 60).round();
    if (minutes < 60) return '$minutes min ago';
    final hours = (minutes / 60).round();
    if (hours < 24) return '$hours h ago';
    final days = (hours / 24).round();
    return '$days d ago';
  }

  /// `1 donor` / `4 donors` — never "1 donors".
  static String count(int value, String singular, [String? plural]) {
    final word = value == 1 ? singular : (plural ?? '${singular}s');
    return '$value $word';
  }

  /// Zero-padded counter, e.g. `6 / 6`.
  static String ratio(int current, int total) => '$current / $total';

  /// Text style helper: any [base] style with tabular figures enabled.
  static TextStyle tabular(TextStyle? base) =>
      (base ?? const TextStyle()).copyWith(
        fontFeatures: const [FontFeature.tabularFigures()],
      );
}
