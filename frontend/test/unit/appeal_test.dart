import 'package:blood_network/data/models/appeal.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Appeal.metaLine', () {
    test('includes the booked count when present', () {
      final appeal = Appeal.fromJson({
        'id': 'APL-1',
        'bloodGroup': 'O−',
        'bloodGroupLabel': 'O RH NEGATIVE',
        'component': 'Red cells',
        'place': 'CRTS Sfax',
        'openedLabel': 'Opened 10 Nov',
        'donorsNeeded': 24,
        'donorsBooked': 9,
        'level': 'critical',
      });

      expect(appeal.isCritical, isTrue);
      expect(
        appeal.metaLine,
        'Opened 10 Nov · 24 donors needed · 9 booked',
      );
    });

    test('omits the booked count when absent', () {
      final appeal = Appeal.fromJson({
        'id': 'APL-2',
        'bloodGroup': 'B+',
        'bloodGroupLabel': 'B RH POSITIVE',
        'component': 'Platelets',
        'place': 'Collecte mobile, campus',
        'openedLabel': 'One-day collection · 14 Nov',
        'donorsNeeded': 8,
        'level': 'open',
      });

      expect(appeal.isCritical, isFalse);
      expect(appeal.metaLine, 'One-day collection · 14 Nov · 8 donors needed');
    });
  });
}