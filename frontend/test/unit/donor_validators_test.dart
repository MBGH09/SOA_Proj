import 'package:blood_network/core/validation/donor_validators.dart';
import 'package:blood_network/data/models/blood_group.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('fullName', () {
    test('rejects empty and non-letters', () {
      expect(DonorValidators.fullName(''), isNotNull);
      expect(DonorValidators.fullName('   '), isNotNull);
      expect(DonorValidators.fullName('A1'), isNotNull);
    });

    test('accepts a full name with accents', () {
      expect(DonorValidators.fullName('Amira Ben Salah'), isNull);
      expect(DonorValidators.fullName('Amélie Zaïdi'), isNull);
    });
  });

  group('cin', () {
    test('requires exactly 8 digits', () {
      expect(DonorValidators.cin('09123456'), isNull);
      expect(DonorValidators.cin('0912345'), isNotNull);
      expect(DonorValidators.cin('0912345a'), isNotNull);
      expect(DonorValidators.cin(''), isNotNull);
    });
  });

  group('dateOfBirth', () {
    final now = DateTime(2026, 11, 14);

    test('requires a date', () {
      expect(DonorValidators.dateOfBirth(null, now: now), isNotNull);
    });

    test('accepts ages 18 to 65', () {
      expect(
        DonorValidators.dateOfBirth(DateTime(2008, 11, 14), now: now),
        isNull,
      );
      expect(
        DonorValidators.dateOfBirth(DateTime(1961, 11, 14), now: now),
        isNull,
      );
    });

    test('rejects 17 and 66', () {
      expect(
        DonorValidators.dateOfBirth(DateTime(2008, 11, 15), now: now),
        isNotNull,
      );
      expect(
        DonorValidators.dateOfBirth(DateTime(1960, 11, 14), now: now),
        isNotNull,
      );
    });
  });

  group('weight', () {
    test('minimum is 50 kg', () {
      expect(DonorValidators.weight('62'), isNull);
      expect(DonorValidators.weight('50'), isNull);
      expect(DonorValidators.weight('49'), isNotNull);
      expect(DonorValidators.weight('heavy'), isNotNull);
    });
  });

  group('phone', () {
    test('accepts Tunisian numbers with or without the prefix', () {
      expect(DonorValidators.phone('92 445 210'), isNull);
      expect(DonorValidators.phone('+216 92 445 210'), isNull);
      expect(DonorValidators.phone('9244521'), isNotNull);
    });
  });

  test('blood group and city are required', () {
    expect(DonorValidators.bloodGroup(null), isNotNull);
    expect(DonorValidators.bloodGroup(BloodGroup.oPos), isNull);
    expect(DonorValidators.city(''), isNotNull);
    expect(DonorValidators.city('Sfax'), isNull);
  });

  test('blood group codes round-trip', () {
    expect(BloodGroup.fromCode('AB−'), BloodGroup.abNeg);
    expect(BloodGroup.fromCode('O+'), BloodGroup.oPos);
    expect(BloodGroup.fromCode('XY'), isNull);
  });
}
