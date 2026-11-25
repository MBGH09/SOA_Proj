import '../models/certificate.dart';
import '../services/certificate_pdf_service.dart';

/// Demo stand-in for the PDF form of the certificate endpoint.
///
/// No file is written: it answers with the name the backend will produce, so
/// both buttons can be reviewed end to end. Only an issued certificate has a
/// file — a pending one has no number yet.
class MockCertificatePdfService implements CertificatePdfService {
  @override
  Future<String> download(Certificate certificate) async =>
      _fileNameOf(certificate);

  @override
  Future<String> print(Certificate certificate) async =>
      _fileNameOf(certificate);

  String _fileNameOf(Certificate certificate) {
    final number = certificate.number;
    if (number == null) {
      throw StateError('a certificate under preparation has no file yet');
    }
    return '$number.pdf';
  }
}
