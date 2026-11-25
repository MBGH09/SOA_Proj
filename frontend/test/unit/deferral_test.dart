import 'package:blood_network/data/models/blood_group.dart';
import 'package:blood_network/data/models/deferral.dart';
import 'package:flutter_test/flutter_test.dart';

DeferralReason reason({int weeks = 4}) => DeferralReason(
  code: 'TATOUAGE',
  label: 'Tattoo',
  weeks: weeks,
  durationLabel: weeks == 4 ? '4 weeks' : '3 months',
);

void main() {
  group('DeferralReason', () {
    test('its end date follows the reference duration', () {
      expect(
        reason(weeks: 4).endFrom(DateTime(2026, 11, 12)),
        DateTime(2026, 12, 10),
      );
      expect(
        reason(weeks: 13).endFrom(DateTime(2026, 11, 12)),
        DateTime(2027, 2, 11),
      );
    });
  });

  group('Deferral', () {
    Deferral deferral({DateTime? start, DateTime? end}) => Deferral(
      id: 'AJ-2026-0031',
      reason: reason(),
      startDate: start ?? DateTime(2026, 11, 12),
      endDate: end ?? DateTime(2026, 12, 12),
    );

    test('reads as reason · duration', () {
      expect(deferral().title, 'Tattoo · 4 weeks');
    });

    test('is active inside its window only', () {
      final active = deferral();
      expect(active.isActiveAt(DateTime(2026, 11, 12)), isTrue);
      expect(active.isActiveAt(DateTime(2026, 12, 12)), isTrue);
      expect(active.isActiveAt(DateTime(2026, 12, 13)), isFalse);
      expect(active.isActiveAt(DateTime(2026, 11, 11)), isFalse);
    });
  });

  group('DonorSummary', () {
    test('prints donor · group · last donation', () {
      final donor = DonorSummary(
        cin: '08123456',
        name: 'Amira Ben Salah',
        bloodGroup: BloodGroup.oNeg,
        lastDonation: DateTime(2026, 8, 12),
      );
      expect(donor.line, 'Amira Ben Salah · O− · last donation 12 Aug 2026');
    });

    test('omits the last donation when there is none', () {
      final donor = DonorSummary(
        cin: '08123456',
        name: 'Sami Kacem',
        bloodGroup: BloodGroup.oPos,
      );
      expect(donor.line, 'Sami Kacem · O+');
    });
  });

  group('DeferralDraft', () {
    test('serialises to the contract field names', () {
      final draft = DeferralDraft(
        cin: '08123456',
        reasonCode: 'TATOUAGE',
        startDate: DateTime(2026, 11, 12),
        endDate: DateTime(2026, 12, 12),
        note: 'Four weeks ago',
      );
      expect(draft.toJson(), {
        'motif': 'TATOUAGE',
        'dateDebut': '2026-11-12',
        'dateFin': '2026-12-12',
        'note': 'Four weeks ago',
      });
    });
  });
}
