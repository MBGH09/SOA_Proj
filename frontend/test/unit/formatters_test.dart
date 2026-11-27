import 'package:blood_network/core/format/formatters.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final instant = DateTime(2026, 11, 14, 9, 30);

  test('formats dates and times', () {
    expect(AppFormatters.dayMonth(instant), '14 Nov');
    expect(AppFormatters.fullDate(instant), '14 Nov 2026');
    expect(AppFormatters.time(instant), '09:30');
    expect(AppFormatters.dayMonthTime(instant), '14 Nov · 09:30');
  });

  test('counts never read "1 donors"', () {
    expect(AppFormatters.count(1, 'donor'), '1 donor');
    expect(AppFormatters.count(4, 'donor'), '4 donors');
    expect(AppFormatters.ratio(6, 6), '6 / 6');
  });

  test('ageFrom keeps to a short caption', () {
    final now = DateTime(2026, 11, 14, 10);
    expect(
      AppFormatters.ageFrom(now.subtract(const Duration(seconds: 20)), now),
      'just now',
    );
    expect(
      AppFormatters.ageFrom(now.subtract(const Duration(minutes: 2)), now),
      '2 min ago',
    );
    expect(
      AppFormatters.ageFrom(now.subtract(const Duration(hours: 3)), now),
      '3 h ago',
    );
    expect(
      AppFormatters.ageFrom(now.subtract(const Duration(days: 2)), now),
      '2 days ago',
    );
    expect(
      AppFormatters.ageFrom(now.subtract(const Duration(days: 1)), now),
      'Yesterday',
    );
  });
}
