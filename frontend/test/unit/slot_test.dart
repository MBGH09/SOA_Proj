import 'package:blood_network/data/models/slot.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('SlotTime', () {
    test('a full slot says so and never books below zero', () {
      const full = SlotTime(
        start: '09:00',
        end: '10:00',
        capacity: 6,
        placesLeft: 0,
      );
      expect(full.isFull, isTrue);
      expect(full.booked, 6);
      expect(full.label, '09:00 · Full');
      expect(full.range, '09:00 – 10:00');
      expect(full.fill, 1);
    });

    test('an open slot counts its free places', () {
      const open = SlotTime(
        start: '10:00',
        end: '11:00',
        capacity: 6,
        placesLeft: 2,
      );
      expect(open.isFull, isFalse);
      expect(open.booked, 4);
      expect(open.label, '10:00 · 2 left');
      expect(open.fill, closeTo(0.667, 0.01));
    });
  });

  group('SlotDay', () {
    final day = SlotDay(
      date: DateTime(2026, 11, 12),
      times: const [
        SlotTime(start: '09:00', capacity: 6, placesLeft: 0),
        SlotTime(start: '10:00', capacity: 6, placesLeft: 2),
        SlotTime(start: '11:00', capacity: 6, placesLeft: 4),
        SlotTime(start: '14:00', capacity: 6, placesLeft: 6),
        SlotTime(start: '15:00', capacity: 6, placesLeft: 3),
      ],
    );

    test('adds up places, bookings and fill', () {
      expect(day.places, 30);
      expect(day.booked, 15);
      expect(day.placesLeft, 15);
      expect(day.fill, 0.5);
      expect(day.hasFreePlace, isTrue);
    });

    test('a day with no free place cannot be booked', () {
      final full = SlotDay(
        date: DateTime(2026, 11, 12),
        times: const [
          SlotTime(start: '09:00', capacity: 4, placesLeft: 0),
        ],
      );
      expect(full.hasFreePlace, isFalse);
    });
  });

  group('SlotRepeat', () {
    test('knows how far apart two repetitions are', () {
      expect(SlotRepeat.single.stepInDays, 0);
      expect(SlotRepeat.daily.stepInDays, 1);
      expect(SlotRepeat.weekly.stepInDays, 7);
    });
  });
}