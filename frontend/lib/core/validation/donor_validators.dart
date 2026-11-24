import '../../data/models/blood_group.dart';

/// Form rules for donor registration.
///
/// Wording is plain language: no rule ids, no HTTP codes (study §8).
abstract final class DonorValidators {
  static final RegExp _name = RegExp(r"^[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ' \-]{2,59}$");
  static final RegExp _cin = RegExp(r'^\d{8}$');
  static final RegExp _phone = RegExp(r'^(\+216)?\d{8}$');

  static String? fullName(String? value) {
    final name = value?.trim() ?? '';
    if (name.isEmpty) return 'Enter your full name.';
    if (!_name.hasMatch(name)) {
      return 'Use letters only, as written on your ID card.';
    }
    return null;
  }

  static String? cin(String? value) {
    final raw = (value ?? '').replaceAll(RegExp(r'\s'), '');
    if (raw.isEmpty) return 'Enter your ID card number.';
    if (!_cin.hasMatch(raw)) return 'The ID number is 8 digits.';
    return null;
  }

  /// Age must be between 18 and 65 (rules R1, R2).
  static String? dateOfBirth(DateTime? value, {DateTime? now}) {
    if (value == null) return 'Choose your date of birth.';
    final today = now ?? DateTime.now();
    if (value.isAfter(today)) return 'The date cannot be in the future.';
    final age = _ageAt(value, today);
    if (age < 18) return 'You must be at least 18 to donate.';
    if (age > 65) return 'Donations are accepted up to 65 years old.';
    return null;
  }

  /// Minimum 50 kg (rule R3).
  static String? weight(String? value) {
    final raw = (value ?? '').trim();
    if (raw.isEmpty) return 'Enter your weight.';
    final kg = int.tryParse(raw);
    if (kg == null) return 'Enter your weight in whole kilograms.';
    if (kg < 50) return 'Donors must weigh at least 50 kg.';
    if (kg > 250) return 'Check the weight you entered.';
    return null;
  }

  static String? bloodGroup(BloodGroup? value) =>
      value == null ? 'Choose your blood group.' : null;

  static String? city(String? value) {
    final city = value?.trim() ?? '';
    if (city.isEmpty) return 'Enter your city.';
    if (city.length < 2) return 'Enter your city.';
    return null;
  }

  /// Tunisian mobile or landline number, with or without the +216 prefix.
  static String? phone(String? value) {
    final raw = (value ?? '').replaceAll(RegExp(r'[\s\-]'), '');
    if (raw.isEmpty) return 'Enter your phone number.';
    if (!_phone.hasMatch(raw)) return 'Enter 8 digits, for example 92 445 210.';
    return null;
  }

  static int _ageAt(DateTime birth, DateTime now) {
    var age = now.year - birth.year;
    if (now.month < birth.month ||
        (now.month == birth.month && now.day < birth.day)) {
      age--;
    }
    return age;
  }
}
