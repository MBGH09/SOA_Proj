import 'package:blood_network/core/errors/api_error.dart';
import 'package:blood_network/data/models/certificate.dart';
import 'package:blood_network/data/repositories/certificate_repository.dart';
import 'package:blood_network/data/services/certificate_pdf_service.dart';

/// The issued certificate of the FigCraft frames.
Certificate sampleIssuedCertificate({
  String number = 'CERT-2026-000314',
  String centre = 'CRTS Sfax',
  DateTime? donationDate,
}) => Certificate(
  status: CertificateStatus.issued,
  number: number,
  nationalDonation: 'NDN-2026-118842',
  donorName: 'Amira Ben Salah',
  maskedCin: '08••••67',
  donationDate: donationDate ?? DateTime(2026, 8, 12),
  centre: centre,
  volumeMl: 450,
);

/// A donation the CNTS system has not answered for yet: no number.
Certificate samplePendingCertificate() => Certificate(
  status: CertificateStatus.preparing,
  donorName: 'Amira Ben Salah',
  maskedCin: '08••••67',
  donationDate: DateTime(2026, 11, 3),
  centre: 'CRTS Sfax',
  volumeMl: 450,
);

CertificateVerification sampleVerification({
  bool valid = true,
  DateTime? date,
  String centre = 'CRTS Sfax',
}) => CertificateVerification(
  valid: valid,
  date: date ?? DateTime(2026, 8, 12),
  centre: centre,
);

/// Test double for the certificate service.
class FakeCertificateRepository implements CertificateRepository {
  FakeCertificateRepository({
    List<Certificate>? mine,
    CertificateVerification? verification,
    this.mineError,
    this.verifyError,
  }) : _mine = mine ?? [sampleIssuedCertificate(), samplePendingCertificate()],
       _verification = verification ?? sampleVerification();

  final List<Certificate> _mine;
  final CertificateVerification _verification;
  final ApiError? mineError;
  final ApiError? verifyError;

  int verifyCalls = 0;
  String? lastNumero;
  String? lastVariant;

  /// The demo variant the screen passed on, if any.
  String? get lastMineVariant => _lastMineVariant;
  String? _lastMineVariant;

  @override
  Future<List<Certificate>> mine({String? variant}) async {
    _lastMineVariant = variant;
    final error = mineError;
    if (error != null) throw error;
    return List.of(_mine);
  }

  @override
  Future<CertificateVerification> verify(
    String numero, {
    String? variant,
  }) async {
    verifyCalls++;
    lastNumero = numero;
    lastVariant = variant;
    final error = verifyError;
    if (error != null) throw error;
    return _verification;
  }
}

/// Records what the two certificate buttons ask the service for.
class FakeCertificatePdfService implements CertificatePdfService {
  int downloadCalls = 0;
  int printCalls = 0;

  @override
  Future<String> download(Certificate certificate) async {
    downloadCalls++;
    return '${certificate.number}.pdf';
  }

  @override
  Future<String> print(Certificate certificate) async {
    printCalls++;
    return '${certificate.number}.pdf';
  }
}
