import 'package:blood_network/core/errors/api_error.dart';
import 'package:blood_network/data/models/certificate.dart';
import 'package:blood_network/features/donor/presentation/providers/certificate_providers.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import '../support/fake_certificate_repository.dart';

void main() {
  group('Certificate', () {
    test('parses an issued certificate from the API payload', () {
      final certificate = Certificate.fromJson(const {
        'statut': 'EMIS',
        'numeroCertificat': 'CERT-2026-000314',
        'numeroDonNational': 'NDN-2026-118842',
        'donneurNom': 'Amira Ben Salah',
        'cinMasque': '08••••67',
        'dateDon': '2026-08-12',
        'centre': 'CRTS Sfax',
        'volumeMl': 450,
        'autorite': 'CNTS',
      });

      expect(certificate.status, CertificateStatus.issued);
      expect(certificate.status.apiValue, 'EMIS');
      expect(certificate.isIssued, isTrue);
      expect(certificate.number, 'CERT-2026-000314');
      expect(certificate.nationalDonation, 'NDN-2026-118842');
      expect(certificate.maskedCin, '08••••67');
      expect(certificate.donationDate, DateTime(2026, 8, 12));
      expect(certificate.volumeMl, 450);
      expect(certificate.authority, 'CNTS');
    });

    test('a certificate under preparation has no number', () {
      final certificate = Certificate.fromJson(const {
        'statut': 'EN_PREPARATION',
        'numeroCertificat': null,
        'donneurNom': 'Amira Ben Salah',
        'cinMasque': '08••••67',
        'dateDon': '2026-11-03',
        'centre': 'CRTS Sfax',
      });

      expect(certificate.status, CertificateStatus.preparing);
      expect(certificate.isIssued, isFalse);
      expect(certificate.number, isNull);
      expect(certificate.nationalDonation, isNull);
    });

    test('the Donneurs pending value A_EMETTRE still reads as preparing', () {
      expect(
        CertificateStatus.fromApi('A_EMETTRE'),
        CertificateStatus.preparing,
      );
    });

    test('an unknown status never claims the certificate is issued', () {
      expect(
        CertificateStatus.fromApi('SOMETHING_ELSE'),
        isNot(CertificateStatus.issued),
      );
    });
  });

  group('CertificateVerification', () {
    test('parses a valid answer', () {
      final answer = CertificateVerification.fromJson(const {
        'valide': true,
        'date': '2026-08-12',
        'centre': 'CRTS Sfax',
      });

      expect(answer.valid, isTrue);
      expect(answer.date, DateTime(2026, 8, 12));
      expect(answer.centre, 'CRTS Sfax');
      // The issuer is not part of CertificatVerification in the contract.
      expect(answer.authority, 'CNTS');
    });

    test('parses a not valid answer', () {
      final answer = CertificateVerification.fromJson(const {
        'valide': false,
        'date': '2026-06-03',
        'centre': 'CHU Habib Bourguiba',
        'autorite': 'CNTS',
      });

      expect(answer.valid, isFalse);
      expect(answer.centre, 'CHU Habib Bourguiba');
    });
  });

  test('a failing certificate check is called once, not ten times', () async {
    final repository = FakeCertificateRepository(
      verifyError: const ApiError(status: 404, message: 'CERTIFICATE_NOT_FOUND'),
    );
    final container = ProviderContainer(
      overrides: [certificateRepositoryProvider.overrideWithValue(repository)],
    );
    addTearDown(container.dispose);

    await expectLater(
      container.read(
        certificateVerificationProvider((
          numero: 'CERT-2025-999999',
          variant: 'not-found',
        )).future,
      ),
      throwsA(isA<ApiError>()),
    );

    // Riverpod 3 retries a failed provider ten times with a backoff by
    // default; the screens must see the answer once.
    await Future<void>.delayed(const Duration(milliseconds: 900));
    expect(repository.verifyCalls, 1);
  });
}
