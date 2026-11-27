import 'blood_component.dart';
import 'blood_group.dart';

/// Why the hospital asks for blood (`MotifDemande`).
enum RequestReason {
  surgery('CHIRURGIE', 'Surgery'),
  emergency('URGENCE', 'Emergency'),
  treatment('TRAITEMENT', 'Treatment');

  const RequestReason(this.apiValue, this.label);

  final String apiValue;
  final String label;

  static RequestReason fromApi(String? value) {
    for (final reason in values) {
      if (reason.apiValue == value) return reason;
    }
    return RequestReason.surgery;
  }
}

/// How fast the pouches are needed (`Urgence`).
enum RequestUrgency {
  normal('NORMALE', 'Normal'),
  high('HAUTE', 'High'),
  critical('CRITIQUE', 'Critical');

  const RequestUrgency(this.apiValue, this.label);

  final String apiValue;
  final String label;

  bool get isCritical => this == RequestUrgency.critical;

  /// The line under the urgency chips when Critical is chosen (H1).
  static const String criticalNote =
      'Critical · this request is treated as an emergency and gets a shorter '
      'delivery time.';

  static RequestUrgency fromApi(String? value) {
    for (final urgency in values) {
      if (urgency.apiValue == value) return urgency;
    }
    return RequestUrgency.normal;
  }
}

/// Lifecycle of a request (`StatutDemande`, study R16).
enum RequestStatus {
  waiting('EN_ATTENTE', 'Waiting'),
  satisfied('SATISFAITE', 'Delivered'),
  supplying('APPROVISIONNEMENT_EN_COURS', 'In delivery'),
  shortage('EN_PENURIE', 'Shortage'),
  cancelled('ANNULEE', 'Cancelled');

  const RequestStatus(this.apiValue, this.label);

  final String apiValue;
  final String label;

  /// Still needs pouches: waiting, on the way, or nowhere to be found yet.
  bool get isOpen =>
      this == RequestStatus.waiting ||
      this == RequestStatus.supplying ||
      this == RequestStatus.shortage;

  static RequestStatus fromApi(String? value) {
    for (final status in values) {
      if (status.apiValue == value) return status;
    }
    return RequestStatus.waiting;
  }
}

/// Where the pouches came from: the hospital's own bank, the regional CRTS, or
/// the national service (R12–R14).
enum SupplySource {
  localBank('Local bank', 'the local bank'),
  regionalCentre('Regional centre', 'the regional CRTS'),
  nationalService('National service', 'the national service');

  const SupplySource(this.label, this.sentence);

  final String label;

  /// `the regional CRTS` — used in a sentence.
  final String sentence;

  static SupplySource? fromApi(String? value) {
    for (final source in values) {
      if (source.name == value) return source;
    }
    return null;
  }
}

/// How far the delivery is (H3's three chips).
enum DeliveryStage {
  reserved('Reserved'),
  inTransit('In transit'),
  delivered('Delivered');

  const DeliveryStage(this.label);

  final String label;

  /// True once the delivery has reached [other] or gone past it.
  bool hasReached(DeliveryStage other) => index >= other.index;

  static DeliveryStage? fromApi(String? value) {
    for (final stage in values) {
      if (stage.name == value) return stage;
    }
    return null;
  }
}

/// A family donor counted towards a request (R17, R26), by certificate number.
class CompensationCertificate {
  const CompensationCertificate({
    required this.number,
    required this.valid,
    this.donorName,
  });

  final String number;
  final bool valid;

  /// The donor behind the certificate, when the service knows it.
  final String? donorName;

  factory CompensationCertificate.fromJson(Map<String, dynamic> json) =>
      CompensationCertificate(
        number: json['number'] as String,
        valid: json['valid'] as bool? ?? true,
        donorName: json['donorName'] as String?,
      );
}

/// One blood request, as the hospital and the admin see it (`Demande`).
class BloodRequest {
  const BloodRequest({
    required this.id,
    required this.hospitalId,
    required this.hospitalName,
    required this.patientRef,
    required this.reason,
    required this.bloodGroup,
    required this.component,
    required this.units,
    required this.urgency,
    required this.status,
    required this.createdAt,
    required this.updatedAt,
    this.note,
    this.compensationRequested,
    this.compensationReceived = 0,
    this.certificates = const [],
    this.reservationReference,
    this.reservationStatus,
    this.suppliedBy,
    this.supplySource,
    this.trackingReference,
    this.deliveryFrom,
    this.deliveryTo,
    this.estimatedMinutes,
    this.deliveryStage,
  });

  /// `REQ-2026-0184`.
  final String id;
  final String hospitalId;
  final String hospitalName;

  /// Anonymous reference of the patient (R19), e.g. `P20260442`.
  final String patientRef;
  final RequestReason reason;
  final BloodGroup bloodGroup;
  final BloodComponent component;
  final int units;
  final RequestUrgency urgency;
  final RequestStatus status;
  final DateTime createdAt;

  /// Last change of the request, shown as `12 min ago`.
  final DateTime updatedAt;

  /// The free-text note the hospital typed (H1 `NOTE`).
  final String? note;

  /// Family donors asked for (R17), between 2 and 3, or null.
  final int? compensationRequested;

  /// Valid certificates recorded so far.
  final int compensationReceived;
  final List<CompensationCertificate> certificates;

  // --- supply and delivery (H3) ---------------------------------------------
  final String? reservationReference;
  final String? reservationStatus;

  /// `Regional centre · CRTS Sfax`.
  final String? suppliedBy;
  final SupplySource? supplySource;
  final String? trackingReference;
  final String? deliveryFrom;
  final String? deliveryTo;

  /// Estimated minutes until arrival.
  final int? estimatedMinutes;
  final DeliveryStage? deliveryStage;

  /// `Red cells · O negative` — the table's NEEDED column.
  String get neededLine => '${component.label} · ${bloodGroup.plainName}';

  /// `3 units` / `1 unit`.
  String get unitsLine => units == 1 ? '1 unit' : '$units units';

  /// The line under the title (H3):
  /// `Red cells · O negative · 3 units · Orthopaedic surgery · created today at 08:40.`
  String summaryLine(DateTime now) =>
      '$neededLine · $unitsLine · $reasonLine · ${createdLine(now)}.';

  /// The reason printed in the order: the note when the hospital wrote one,
  /// otherwise the contract's motif.
  String get reasonLine => note == null || note!.isEmpty ? reason.label : note!;

  /// `created today at 08:40`, `created yesterday at 14:02`, `created 21 Oct`.
  String createdLine(DateTime now) {
    final day = DateTime(createdAt.year, createdAt.month, createdAt.day);
    final today = DateTime(now.year, now.month, now.day);
    final difference = today.difference(day).inDays;
    final time =
        '${createdAt.hour.toString().padLeft(2, '0')}:'
        '${createdAt.minute.toString().padLeft(2, '0')}';
    return switch (difference) {
      0 => 'created today at $time',
      1 => 'created yesterday at $time',
      _ => 'created on ${createdAt.day} ${_month(createdAt.month)}',
    };
  }

  static const List<String> _months = [
    'Jan',
    'Feb',
    'Mar',
    'Apr',
    'May',
    'Jun',
    'Jul',
    'Aug',
    'Sep',
    'Oct',
    'Nov',
    'Dec',
  ];

  static String _month(int month) => _months[month - 1];

  /// `2 of 3` — the compensation progress.
  String get compensationProgress =>
      '$compensationReceived of ${compensationRequested ?? 0}';

  /// 0 to 1, for the progress bar.
  double get compensationRatio {
    final asked = compensationRequested;
    if (asked == null || asked <= 0) return 0;
    return (compensationReceived / asked).clamp(0, 1);
  }

  bool get compensationComplete {
    final asked = compensationRequested;
    return asked != null && compensationReceived >= asked;
  }

  /// `52 min` for the ETA column, `—` when there is no estimate.
  String get etaLabel =>
      estimatedMinutes == null ? '—' : '$estimatedMinutes min';

  BloodRequest copyWith({
    RequestStatus? status,
    int? compensationReceived,
    List<CompensationCertificate>? certificates,
    DateTime? updatedAt,
    String? note,
  }) => BloodRequest(
    id: id,
    hospitalId: hospitalId,
    hospitalName: hospitalName,
    patientRef: patientRef,
    reason: reason,
    bloodGroup: bloodGroup,
    component: component,
    units: units,
    urgency: urgency,
    status: status ?? this.status,
    createdAt: createdAt,
    updatedAt: updatedAt ?? this.updatedAt,
    note: note ?? this.note,
    compensationRequested: compensationRequested,
    compensationReceived: compensationReceived ?? this.compensationReceived,
    certificates: certificates ?? this.certificates,
    reservationReference: reservationReference,
    reservationStatus: reservationStatus,
    suppliedBy: suppliedBy,
    supplySource: supplySource,
    trackingReference: trackingReference,
    deliveryFrom: deliveryFrom,
    deliveryTo: deliveryTo,
    estimatedMinutes: estimatedMinutes,
    deliveryStage: deliveryStage,
  );

  factory BloodRequest.fromJson(Map<String, dynamic> json) => BloodRequest(
    id: json['id'] as String,
    hospitalId: json['hospitalId'] as String,
    hospitalName: json['hospitalName'] as String,
    patientRef: json['patientRef'] as String,
    reason: RequestReason.fromApi(json['reason'] as String?),
    bloodGroup:
        BloodGroup.fromApi(json['bloodGroup'] as String) ?? BloodGroup.oNeg,
    component: BloodComponent.fromApi(json['component'] as String),
    units: json['units'] as int,
    urgency: RequestUrgency.fromApi(json['urgency'] as String?),
    status: RequestStatus.fromApi(json['status'] as String?),
    createdAt: DateTime.parse(json['createdAt'] as String),
    updatedAt: DateTime.parse(json['updatedAt'] as String),
    note: json['note'] as String?,
    compensationRequested: json['compensationRequested'] as int?,
    compensationReceived: json['compensationReceived'] as int? ?? 0,
    certificates: (json['certificates'] as List<dynamic>? ?? const [])
        .map(
          (item) =>
              CompensationCertificate.fromJson(item as Map<String, dynamic>),
        )
        .toList(growable: false),
    reservationReference: json['reservationReference'] as String?,
    reservationStatus: json['reservationStatus'] as String?,
    suppliedBy: json['suppliedBy'] as String?,
    supplySource: SupplySource.fromApi(json['supplySource'] as String?),
    trackingReference: json['trackingReference'] as String?,
    deliveryFrom: json['deliveryFrom'] as String?,
    deliveryTo: json['deliveryTo'] as String?,
    estimatedMinutes: json['estimatedMinutes'] as int?,
    deliveryStage: DeliveryStage.fromApi(json['deliveryStage'] as String?),
  );
}

/// The four figures above the request table (H2).
///
/// The service counts them over the week; the table shows the latest requests,
/// so an explicit summary is not the same as summing the visible rows.
class RequestSummary {
  const RequestSummary({
    required this.openRequests,
    required this.inDelivery,
    required this.deliveredThisWeek,
    required this.unitsNeededToday,
  });

  final int openRequests;
  final int inDelivery;
  final int deliveredThisWeek;
  final int unitsNeededToday;

  factory RequestSummary.fromJson(Map<String, dynamic> json) => RequestSummary(
    openRequests: json['openRequests'] as int,
    inDelivery: json['inDelivery'] as int,
    deliveredThisWeek: json['deliveredThisWeek'] as int,
    unitsNeededToday: json['unitsNeededToday'] as int,
  );
}

/// What H1 sends to `POST /demandes`.
class BloodRequestDraft {
  const BloodRequestDraft({
    required this.patientRef,
    required this.reason,
    required this.bloodGroup,
    required this.component,
    required this.units,
    required this.urgency,
    this.note,
  });

  final String patientRef;
  final RequestReason reason;
  final BloodGroup bloodGroup;
  final BloodComponent component;
  final int units;
  final RequestUrgency urgency;
  final String? note;

  /// The body of the contract call. The hospital identity comes from the
  /// session, not from the form.
  Map<String, dynamic> toJson({
    required String hospitalId,
    required String hospitalName,
  }) => {
    'hopitalId': hospitalId,
    'hopitalNom': hospitalName,
    'patientRef': patientRef,
    'motif': reason.apiValue,
    'groupeSanguin': bloodGroup.apiValue,
    'composant': component.apiValue,
    'quantitePoches': units,
    'urgence': urgency.apiValue,
    if (note != null && note!.isNotEmpty) 'note': note,
  };
}
