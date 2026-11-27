import 'blood_group.dart';

/// One reason of the deferral reference list (`MotifAjournement`, study §16).
///
/// The API sends the code; the label and the duration come from the reference
/// list, which is configurable.
class DeferralReason {
  const DeferralReason({
    required this.code,
    required this.label,
    required this.weeks,
    required this.durationLabel,
  });

  /// `TATOUAGE`.
  final String code;

  /// `Tattoo`.
  final String label;

  /// How long the deferral lasts, in weeks.
  final int weeks;

  /// `4 weeks`, as the reference card prints it.
  final String durationLabel;

  /// The end date this reason gives for a deferral starting on [start].
  DateTime endFrom(DateTime start) =>
      DateTime(start.year, start.month, start.day + weeks * 7);

  factory DeferralReason.fromJson(Map<String, dynamic> json) => DeferralReason(
    code: json['code'] as String,
    label: json['label'] as String,
    weeks: json['weeks'] as int,
    durationLabel: json['durationLabel'] as String,
  );
}

/// A temporary deferral (study §4 `Ajournement`).
///
/// While one is active the donor cannot book and cannot donate (R4).
class Deferral {
  const Deferral({
    required this.id,
    required this.reason,
    required this.startDate,
    required this.endDate,
    this.note,
    this.donorName,
    this.bloodGroup,
    this.lastDonation,
  });

  final String id;
  final DeferralReason reason;
  final DateTime startDate;
  final DateTime endDate;

  /// Free note, staff only (never shown to the donor).
  final String? note;
  final String? donorName;
  final BloodGroup? bloodGroup;

  /// Date of the donor's previous donation, when there is one.
  final DateTime? lastDonation;

  bool isActiveAt(DateTime moment) =>
      !moment.isBefore(startDate) && !moment.isAfter(endDate);

  /// `Tattoo · 4 weeks`.
  String get title => '${reason.label} · ${reason.durationLabel}';

  factory Deferral.fromJson(Map<String, dynamic> json, List<DeferralReason> reasons) {
    final code = json['motif'] as String;
    final reason = reasons.firstWhere(
      (item) => item.code == code,
      orElse: () => reasons.first,
    );
    return Deferral(
      id: json['id'] as String,
      reason: reason,
      startDate: DateTime.parse(json['startDate'] as String),
      endDate: DateTime.parse(json['endDate'] as String),
      note: json['note'] as String?,
      donorName: json['donorName'] as String?,
      bloodGroup: json['bloodGroup'] == null
          ? null
          : BloodGroup.fromCode(json['bloodGroup'] as String),
      lastDonation: json['lastDonation'] == null
          ? null
          : DateTime.parse(json['lastDonation'] as String),
    );
  }
}

/// The donor a deferral is recorded for, as the CIN lookup returns them.
class DonorSummary {
  const DonorSummary({
    required this.cin,
    required this.name,
    required this.bloodGroup,
    this.lastDonation,
  });

  final String cin;
  final String name;
  final BloodGroup bloodGroup;
  final DateTime? lastDonation;

  /// `Amira Ben Salah · O− · last donation 12 Aug 2026`.
  String get line => [
    name,
    bloodGroup.code,
    if (lastDonation != null) 'last donation ${_dayMonth(lastDonation!)}',
  ].join(' · ');

  static String _dayMonth(DateTime date) {
    const months = [
      'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
      'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
    ];
    return '${date.day} ${months[date.month - 1]} ${date.year}';
  }

  factory DonorSummary.fromJson(Map<String, dynamic> json) => DonorSummary(
    cin: json['cin'] as String,
    name: json['name'] as String,
    bloodGroup:
        BloodGroup.fromCode(json['bloodGroup'] as String) ?? BloodGroup.oPos,
    lastDonation: json['lastDonation'] == null
        ? null
        : DateTime.parse(json['lastDonation'] as String),
  );
}

/// What the CIN lookup answers: the donor and their active deferral, if any.
class DeferralLookup {
  const DeferralLookup({required this.donor, this.current});

  final DonorSummary donor;
  final Deferral? current;
}

/// The deferral form as it is filled in.
class DeferralDraft {
  const DeferralDraft({
    required this.cin,
    required this.reasonCode,
    required this.startDate,
    required this.endDate,
    this.note,
  });

  final String cin;
  final String reasonCode;
  final DateTime startDate;
  final DateTime endDate;
  final String? note;

  Map<String, dynamic> toJson() => {
    'motif': reasonCode,
    'dateDebut': startDate.toIso8601String().split('T').first,
    'dateFin': endDate.toIso8601String().split('T').first,
    if (note != null && note!.isNotEmpty) 'note': note,
  };
}
