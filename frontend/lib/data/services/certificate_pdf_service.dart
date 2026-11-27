import '../models/certificate.dart';

/// Produces the printable certificate file.
///
/// The backend returns it from `GET /api/v1/dons/{id}/certificat` with
/// `Accept: application/pdf` — the same operation the JSON view uses (study
/// §6.1). Keeping it behind its own interface means the screens never care how
/// the file is produced, and the Gateway client can replace the mock without
/// touching the UI.
abstract interface class CertificatePdfService {
  /// Save the certificate as a PDF file. Returns the file name.
  Future<String> download(Certificate certificate);

  /// Open the print dialog for the certificate. Returns the file name.
  Future<String> print(Certificate certificate);
}
