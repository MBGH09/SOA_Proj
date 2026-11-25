import '../models/certificate.dart';

/// Donation certificates (Donneurs: `/dons/{id}/certificat`, `/certificats/{numero}`).
abstract interface class CertificateRepository {
  /// The donor's certificates, most recent donation first.
  ///
  /// [variant] is a demo control that selects one of the prepared outcomes
  /// (`empty`, `unavailable`) so every state can be reviewed before the backend
  /// exists. The real service answers for the donor in session and ignores it.
  Future<List<Certificate>> mine({String? variant});

  /// Verify a certificate number. Public: no session is required.
  ///
  /// Throws an [ApiError] 404 when no certificate matches the number, so the
  /// screen can show its "not found" state; 200 with `valid = false` means the
  /// number exists but cannot be counted.
  ///
  /// [variant] is a demo control (`valid`, `invalid`, `not-found`,
  /// `unavailable`) for the four states the verify screen can display.
  Future<CertificateVerification> verify(String numero, {String? variant});
}
