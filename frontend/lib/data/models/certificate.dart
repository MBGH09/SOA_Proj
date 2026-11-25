/// Donation certificate (study §6.1 `Certificat`).
///
/// A certificate is issued by CNTS after a **completed** visit and is the
/// donor's proof of donation; the same number is used by a hospital for family
/// compensation (R24–R27).
enum CertificateStatus {
  issued('EMIS', 'Issued'),
  preparing('EN_PREPARATION', 'Preparing');

  const CertificateStatus(this.apiValue, this.label);

  final String apiValue;
  final String label;

  /// Donneurs keeps the pending state as `A_EMETTRE` while the CNTS call is
  /// retried (study §4); the certificate endpoint answers `EN_PREPARATION`.
  static CertificateStatus fromApi(String value) =>
      value == 'EMIS' ? CertificateStatus.issued : CertificateStatus.preparing;
}

/// One donation certificate (`GET /api/v1/dons/{id}/certificat`).
///
/// Carries no health data — no blood pressure, haemoglobin or interview
/// decision ever reaches this payload (R27).
class Certificate {
  const Certificate({
    required this.status,
    required this.donorName,
    required this.maskedCin,
    required this.donationDate,
    required this.centre,
    this.number,
    this.nationalDonation,
    this.volumeMl,
    this.authority = 'CNTS',
  });

  final CertificateStatus status;

  /// `CERT-2026-000314` — absent while [CertificateStatus.preparing].
  final String? number;

  /// National donation number assigned by CNTS.
  final String? nationalDonation;
  final String donorName;

  /// Masked National ID, e.g. `08••••67`.
  final String maskedCin;
  final DateTime donationDate;
  final String centre;
  final int? volumeMl;

  /// Issuing authority; CNTS issues every certificate.
  final String authority;

  bool get isIssued => status == CertificateStatus.issued;

  factory Certificate.fromJson(Map<String, dynamic> json) => Certificate(
    status: CertificateStatus.fromApi(json['statut'] as String),
    number: json['numeroCertificat'] as String?,
    nationalDonation: json['numeroDonNational'] as String?,
    donorName: json['donneurNom'] as String,
    maskedCin: json['cinMasque'] as String,
    donationDate: DateTime.parse(json['dateDon'] as String),
    centre: json['centre'] as String,
    volumeMl: json['volumeMl'] as int?,
    authority: json['autorite'] as String? ?? 'CNTS',
  );
}

/// The public answer of `GET /api/v1/certificats/{numero}`
/// (`CertificatVerification`).
///
/// Only validity, date and centre are returned: no donor name, no personal or
/// health data, so anyone — a hospital desk in particular — can check a
/// certificate before counting it for compensation.
class CertificateVerification {
  const CertificateVerification({
    required this.valid,
    required this.date,
    required this.centre,
    this.authority = 'CNTS',
  });

  final bool valid;
  final DateTime date;
  final String centre;

  /// The frames print an issuing authority; the contract lists validity, date
  /// and centre only, so this stays optional with CNTS as the default.
  final String authority;

  factory CertificateVerification.fromJson(Map<String, dynamic> json) =>
      CertificateVerification(
        valid: json['valide'] as bool,
        date: DateTime.parse(json['date'] as String),
        centre: json['centre'] as String,
        authority: json['autorite'] as String? ?? 'CNTS',
      );
}
