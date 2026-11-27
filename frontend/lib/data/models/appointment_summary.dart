/// The figures in the "Summary" card of 05 · My appointments.
class AppointmentSummary {
  const AppointmentSummary({
    required this.totalDonations,
    required this.lastDonation,
    this.nextAt,
    this.activeDeferral,
    this.certificateReady = false,
  });

  final int totalDonations;
  final DateTime lastDonation;

  /// When the donor's next appointment starts, if there is one.
  final DateTime? nextAt;

  /// Plain-language end date of an active deferral, or null for `None`.
  final String? activeDeferral;

  /// Whether the last donation already has an issued certificate.
  final bool certificateReady;

  factory AppointmentSummary.fromJson(Map<String, dynamic> json) =>
      AppointmentSummary(
        totalDonations: json['totalDonations'] as int,
        lastDonation: DateTime.parse(json['lastDonation'] as String),
        nextAt: json['nextAt'] == null
            ? null
            : DateTime.parse(json['nextAt'] as String),
        activeDeferral: json['activeDeferral'] as String?,
        certificateReady: json['certificateReady'] as bool? ?? false,
      );
}
