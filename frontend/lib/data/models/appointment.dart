import 'centre.dart';

/// Appointment lifecycle (study §8, `RendezVous.statut`).
enum AppointmentStatus {
  planned('PLANIFIE', 'Planned'),
  cancelled('ANNULE', 'Cancelled'),
  missed('ABSENT', 'Missed'),
  completed('TERMINE', 'Completed');

  const AppointmentStatus(this.apiValue, this.label);

  final String apiValue;
  final String label;

  static AppointmentStatus fromApi(String value) {
    for (final status in values) {
      if (status.apiValue == value) return status;
    }
    return AppointmentStatus.planned;
  }
}

/// How the appointment was created (study §7.1, `canal`).
enum BookingChannel {
  online('EN_LIGNE', 'booked online'),
  onSite('SUR_PLACE', 'booked at the centre');

  const BookingChannel(this.apiValue, this.label);

  final String apiValue;
  final String label;

  static BookingChannel fromApi(String value) {
    for (final channel in values) {
      if (channel.apiValue == value) return channel;
    }
    return BookingChannel.online;
  }
}

/// One appointment or past visit (`RendezVous`).
class Appointment {
  const Appointment({
    required this.id,
    required this.centreName,
    required this.centreKind,
    required this.startsAt,
    required this.channel,
    required this.component,
    required this.status,
    this.volumeMl,
    this.collectedAt,
    this.certificateNumber,
  });

  /// Reference shown to the donor, e.g. `RDV-2026-0442`.
  final String id;
  final String centreName;
  final CentreKind centreKind;
  final DateTime startsAt;
  final BookingChannel channel;

  /// `Whole blood`, `Plasma`, `Platelets`.
  final String component;

  /// Target volume, when the centre states one.
  final int? volumeMl;
  final AppointmentStatus status;

  /// Day the donation was collected (past visits only).
  final DateTime? collectedAt;

  /// Certificate number, once it has been issued.
  final String? certificateNumber;

  bool get isUpcoming => status == AppointmentStatus.planned;

  /// `Whole blood · 450 ml` (web) or `Whole blood` (mobile, no volume).
  String componentLine({bool withVolume = true}) =>
      withVolume && volumeMl != null ? '$component · $volumeMl ml' : component;

  /// Trailing descriptor: a mobile collection names the kind, otherwise the
  /// channel (`booked online`, `booked at the centre`).
  String get sourceLabel => centreKind == CentreKind.mobileCollection
      ? centreKind.label.toLowerCase()
      : channel.label;

  /// `Whole blood · 450 ml · reference RDV-2026-0442 · booked online` (web).
  String get metaLine =>
      '${componentLine()} · reference $id · $sourceLabel';

  /// `Whole blood · 450 ml · RDV-2026-0442` (mobile).
  String get metaLineMobile => '${componentLine()} · $id';

  factory Appointment.fromJson(Map<String, dynamic> json) => Appointment(
    id: json['id'] as String,
    centreName: json['centreName'] as String,
    centreKind: CentreKind.fromApi(json['centreKind'] as String),
    startsAt: DateTime.parse(json['startsAt'] as String),
    channel: BookingChannel.fromApi(json['channel'] as String),
    component: json['component'] as String,
    status: AppointmentStatus.fromApi(json['status'] as String),
    volumeMl: json['volumeMl'] as int?,
    collectedAt: json['collectedAt'] == null
        ? null
        : DateTime.parse(json['collectedAt'] as String),
    certificateNumber: json['certificateNumber'] as String?,
  );
}
