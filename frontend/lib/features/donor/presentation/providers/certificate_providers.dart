import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/providers/core_providers.dart';
import '../../../../data/mock/mock_certificate_pdf_service.dart';
import '../../../../data/mock/mock_certificate_repository.dart';
import '../../../../data/models/certificate.dart';
import '../../../../data/repositories/certificate_repository.dart';
import '../../../../data/services/certificate_pdf_service.dart';

final certificateRepositoryProvider = Provider<CertificateRepository>(
  (ref) => MockCertificateRepository(),
);

/// The PDF form of a certificate; the backend serves it from the same
/// operation as the JSON one (`Accept: application/pdf`).
final certificatePdfServiceProvider = Provider<CertificatePdfService>(
  (ref) => MockCertificatePdfService(),
);

/// The donor's certificates.
///
/// The `variant` argument is a demo control driven by the `?state=` query
/// parameter (`empty`, `unavailable`), so each state can be reviewed at
/// `/donor/certificates?state=...`.
final myCertificatesProvider = FutureProvider.family<List<Certificate>, String?>(
  (ref, variant) =>
      ref.watch(certificateRepositoryProvider).mine(variant: variant),
  retry: noRetry,
);

/// One submitted check: the number in the field, plus the demo variant.
typedef CertificateCheck = ({String numero, String? variant});

/// The public answer for a certificate number.
final certificateVerificationProvider =
    FutureProvider.family<CertificateVerification, CertificateCheck>(
      (ref, check) => ref
          .watch(certificateRepositoryProvider)
          .verify(check.numero, variant: check.variant),
      retry: noRetry,
    );
