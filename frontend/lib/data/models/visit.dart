import 'blood_group.dart';
import 'certificate.dart';

/// The four steps of a donation visit and its two terminal states
/// (`Don.etape`, study §7.2). Steps always run in order and cannot be skipped
/// or repeated, so a wrong order is a 409 (R6).
enum VisitStep {
  reception('ACCUEIL', 'Reception'),
  interview('ENTRETIEN_MEDICAL', 'Medical interview'),
  collection('PRELEVEMENT', 'Collection'),
  rest('REPOS', 'Rest'),
  done('TERMINE', 'Done'),
  refused('REFUSE', 'Refused');

  const VisitStep(this.apiValue, this.label);

  final String apiValue;
  final String label;

  static VisitStep fromApi(String value) {
    for (final step in values) {
      if (step.apiValue == value) return step;
    }
    return VisitStep.reception;
  }

  /// The four working steps, in order; the terminal states are not on the board.
  static const List<VisitStep> ordered = [
    VisitStep.reception,
    VisitStep.interview,
    VisitStep.collection,
    VisitStep.rest,
  ];

  /// 1 to 4, or null for [done] and [refused].
  int? get position => switch (this) {
    VisitStep.reception => 1,
    VisitStep.interview => 2,
    VisitStep.collection => 3,
    VisitStep.rest => 4,
    _ => null,
  };

  bool get isTerminal => this == VisitStep.done || this == VisitStep.refused;

  /// True once the visit has reached [other] or a later step.
  bool hasReached(VisitStep other) {
    final mine = position;
    final theirs = other.position;
    if (mine == null || theirs == null) return this == other;
    return mine >= theirs;
  }

  /// The step the header names: `STEP 2 · MEDICAL INTERVIEW`.
  String get boardLabel => 'STEP $position · ${label.toUpperCase()}';

  /// Short title of a board column (C2): `Reception`, `Interview`, ...
  String get boardTitle => switch (this) {
    VisitStep.reception => 'Reception',
    VisitStep.interview => 'Interview',
    VisitStep.collection => 'Collection',
    VisitStep.rest => 'Rest',
    VisitStep.done => 'Done',
    VisitStep.refused => 'Refused',
  };
}

/// The physician's decision at step 2 (`Don.decision`), R7.
enum VisitDecision {
  fit('APTE', 'Fit for collection', 'Fit'),
  unfit('INAPTE', 'Unfit — REFUSE', 'Unfit');

  const VisitDecision(this.apiValue, this.label, this.shortLabel);

  final String apiValue;
  final String label;

  /// The shorter label of the 390 layout.
  final String shortLabel;

  static VisitDecision? fromApi(String? value) {
    for (final decision in values) {
      if (decision.apiValue == value) return decision;
    }
    return null;
  }
}

/// How the visit was created (the appointment's `canal`, or a walk-in).
enum VisitChannel {
  online('EN_LIGNE', 'Online', 'online'),
  walkIn('SUR_PLACE', 'Walk-in', 'walk-in');

  const VisitChannel(this.apiValue, this.label, this.descriptor);

  final String apiValue;
  final String label;

  /// `D-2026-1189 · walk-in`.
  final String descriptor;

  static VisitChannel fromApi(String value) =>
      value == 'SUR_PLACE' ? VisitChannel.walkIn : VisitChannel.online;
}

/// One question of the reception questionnaire, as it was answered.
class VisitQuestion {
  const VisitQuestion({required this.label, required this.answer});

  final String label;
  final bool answer;

  factory VisitQuestion.fromJson(Map<String, dynamic> json) => VisitQuestion(
    label: json['label'] as String,
    answer: json['answer'] as bool,
  );
}

/// A donation visit (`Don`, study §6.1 and §7.2).
class Visit {
  const Visit({
    required this.id,
    required this.donorName,
    required this.bloodGroup,
    required this.centreName,
    required this.channel,
    required this.step,
    required this.startedAt,
    required this.questionnaire,
    this.rendezVousReference,
    this.decision,
    this.systolic,
    this.diastolic,
    this.haemoglobin,
    this.collectedAt,
    this.durationMinutes,
    this.restStartedAt,
    this.restEndsAt,
    this.volumeMl,
    this.nationalDonationNumber,
    this.certificateStatus = CertificateStatus.preparing,
    this.elapsedMinutes,
    this.restMinutesLeft,
  });

  /// `D-2026-1189`.
  final String id;
  final String donorName;
  final BloodGroup bloodGroup;
  final String centreName;
  final VisitChannel channel;
  final VisitStep step;

  /// Reception time, when step 1 was recorded.
  final DateTime startedAt;

  /// Answers of the health questionnaire recorded at reception.
  final List<VisitQuestion> questionnaire;

  /// `RDV-2026-0442` when the visit is linked to an appointment.
  final String? rendezVousReference;
  final VisitDecision? decision;

  /// Step 2 measurements.
  final int? systolic;
  final int? diastolic;
  final double? haemoglobin;

  /// Step 3 collection.
  final DateTime? collectedAt;
  final int? durationMinutes;
  final int? volumeMl;

  /// National donation number returned by the CNTS system after collection.
  final String? nationalDonationNumber;

  /// Step 4 rest window; the visit ends only after the minimum rest (R9).
  final DateTime? restStartedAt;
  final DateTime? restEndsAt;

  /// Certificate state of a finished visit (`certificatStatut`).
  final CertificateStatus certificateStatus;

  /// Minutes since reception. The mock sends it ready-made for its frozen
  /// session; the real client derives it from [startedAt] and the device clock.
  final int? elapsedMinutes;

  /// Minutes of rest still to run, same convention as [elapsedMinutes].
  final int? restMinutesLeft;

  bool get isOpen => !step.isTerminal;
  bool get isRefused => step == VisitStep.refused;

  /// `AB+ · 09:22` — the second line of a board card.
  String get groupAndStart =>
      '${bloodGroup.code} · ${_hhmm(startedAt)}';

  /// The third line of a board card: time spent, rest left, or the certificate.
  String? get trailLabel {
    if (step.isTerminal) {
      return certificateStatus == CertificateStatus.issued
          ? 'cert. issued'
          : 'cert. pending';
    }
    if (step == VisitStep.rest) {
      final left = restMinutesLeft;
      return left == null ? null : '$left min left';
    }
    final elapsed = elapsedMinutes;
    return elapsed == null ? null : '$elapsed min';
  }

  static String _hhmm(DateTime instant) =>
      '${instant.hour.toString().padLeft(2, '0')}:'
      '${instant.minute.toString().padLeft(2, '0')}';

  Visit copyWith({
    VisitStep? step,
    VisitDecision? decision,
    int? systolic,
    int? diastolic,
    double? haemoglobin,
    DateTime? collectedAt,
    int? durationMinutes,
    int? volumeMl,
    String? nationalDonationNumber,
    DateTime? restStartedAt,
    DateTime? restEndsAt,
    int? restMinutesLeft,
    CertificateStatus? certificateStatus,
    int? elapsedMinutes,
  }) => Visit(
    id: id,
    donorName: donorName,
    bloodGroup: bloodGroup,
    centreName: centreName,
    channel: channel,
    step: step ?? this.step,
    startedAt: startedAt,
    questionnaire: questionnaire,
    rendezVousReference: rendezVousReference,
    decision: decision ?? this.decision,
    systolic: systolic ?? this.systolic,
    diastolic: diastolic ?? this.diastolic,
    haemoglobin: haemoglobin ?? this.haemoglobin,
    collectedAt: collectedAt ?? this.collectedAt,
    durationMinutes: durationMinutes ?? this.durationMinutes,
    restStartedAt: restStartedAt ?? this.restStartedAt,
    restEndsAt: restEndsAt ?? this.restEndsAt,
    volumeMl: volumeMl ?? this.volumeMl,
    nationalDonationNumber:
        nationalDonationNumber ?? this.nationalDonationNumber,
    certificateStatus: certificateStatus ?? this.certificateStatus,
    elapsedMinutes: elapsedMinutes ?? this.elapsedMinutes,
    restMinutesLeft: restMinutesLeft ?? this.restMinutesLeft,
  );

  factory Visit.fromJson(Map<String, dynamic> json) => Visit(
    id: json['id'] as String,
    donorName: json['donorName'] as String,
    bloodGroup:
        BloodGroup.fromCode(json['bloodGroup'] as String) ?? BloodGroup.oPos,
    centreName: json['centreName'] as String,
    channel: VisitChannel.fromApi(json['channel'] as String),
    step: VisitStep.fromApi(json['step'] as String),
    startedAt: DateTime.parse(json['startedAt'] as String),
    questionnaire: (json['questionnaire'] as List<dynamic>? ?? const [])
        .map((item) => VisitQuestion.fromJson(item as Map<String, dynamic>))
        .toList(growable: false),
    rendezVousReference: json['rendezVousReference'] as String?,
    decision: VisitDecision.fromApi(json['decision'] as String?),
    systolic: json['systolic'] as int?,
    diastolic: json['diastolic'] as int?,
    haemoglobin: (json['haemoglobin'] as num?)?.toDouble(),
    collectedAt: json['collectedAt'] == null
        ? null
        : DateTime.parse(json['collectedAt'] as String),
    durationMinutes: json['durationMinutes'] as int?,
    restStartedAt: json['restStartedAt'] == null
        ? null
        : DateTime.parse(json['restStartedAt'] as String),
    restEndsAt: json['restEndsAt'] == null
        ? null
        : DateTime.parse(json['restEndsAt'] as String),
    volumeMl: json['volumeMl'] as int?,
    nationalDonationNumber: json['nationalDonationNumber'] as String?,
    certificateStatus: json['certificateStatus'] == null
        ? CertificateStatus.preparing
        : CertificateStatus.fromApi(json['certificateStatus'] as String),
    elapsedMinutes: json['elapsedMinutes'] as int?,
    restMinutesLeft: json['restMinutesLeft'] as int?,
  );
}

/// The medical interview as the doctor records it (step 2).
class VisitInterview {
  const VisitInterview({
    required this.systolic,
    required this.diastolic,
    required this.haemoglobin,
    required this.decision,
  });

  final int systolic;
  final int diastolic;
  final double haemoglobin;
  final VisitDecision decision;

  Map<String, dynamic> toJson() => {
    'tensionSystolique': systolic,
    'tensionDiastolique': diastolic,
    'hemoglobineGdl': haemoglobin,
    'decision': decision.apiValue,
  };
}

/// The collection as the nurse records it (step 3).
class VisitCollection {
  const VisitCollection({required this.volumeMl, required this.durationMinutes});

  final int volumeMl;
  final int durationMinutes;

  Map<String, dynamic> toJson() => {
    'volumeMl': volumeMl,
    'dureeMinutes': durationMinutes,
  };
}
