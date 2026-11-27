import 'blood_group.dart';

/// The registration form as the donor filled it, before the backend assigns an id.
class DonorDraft {
  const DonorDraft({
    required this.fullName,
    required this.cin,
    required this.dateOfBirth,
    required this.weightKg,
    required this.bloodGroup,
    required this.city,
    required this.phone,
  });

  final String fullName;

  /// National ID card number. Unique — the contract returns 409 on a duplicate.
  final String cin;
  final DateTime dateOfBirth;
  final int weightKg;
  final BloodGroup bloodGroup;
  final String city;
  final String phone;

  Map<String, dynamic> toJson() => {
    'nomComplet': fullName,
    'cin': cin,
    'dateNaissance': dateOfBirth.toIso8601String().split('T').first,
    'poidsKg': weightKg,
    'groupeSanguin': bloodGroup.code,
    'ville': city,
    'telephone': phone,
  };
}

/// A registered donor, as the API returns it.
class Donor {
  const Donor({
    required this.id,
    required this.fullName,
    required this.cin,
    required this.dateOfBirth,
    required this.weightKg,
    required this.bloodGroup,
    required this.city,
    required this.phone,
  });

  final String id;
  final String fullName;
  final String cin;
  final DateTime dateOfBirth;
  final int weightKg;
  final BloodGroup bloodGroup;
  final String city;
  final String phone;

  /// Whole years, as eligibility rules are expressed in years.
  int ageAt(DateTime now) {
    var age = now.year - dateOfBirth.year;
    if (now.month < dateOfBirth.month ||
        (now.month == dateOfBirth.month && now.day < dateOfBirth.day)) {
      age--;
    }
    return age;
  }

  factory Donor.fromJson(Map<String, dynamic> json) => Donor(
    id: json['id'] as String,
    fullName: json['nomComplet'] as String,
    cin: json['cin'] as String,
    dateOfBirth: DateTime.parse(json['dateNaissance'] as String),
    weightKg: json['poidsKg'] as int,
    bloodGroup:
        BloodGroup.fromCode(json['groupeSanguin'] as String) ?? BloodGroup.oPos,
    city: json['ville'] as String,
    phone: json['telephone'] as String,
  );
}
