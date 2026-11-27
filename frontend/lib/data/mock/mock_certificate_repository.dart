import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;

import '../../core/errors/api_error.dart';
import '../models/certificate.dart';
import '../repositories/certificate_repository.dart';

/// In-memory Donneurs service for donation certificates.
///
/// The fixture holds the two cards of the design frame (one issued, one still
/// being prepared) and the two answers the public check can return. Any other
/// number answers 404, which is the "not found" state of the verify screen.
class MockCertificateRepository implements CertificateRepository {
  /// Issued by CNTS, valid, and the example printed in the design.
  static const String validNumber = 'CERT-2026-000314';

  /// No certificate of this number: the 404 example of the design.
  static const String unknownNumber = 'CERT-2025-999999';

  /// Known, but not valid — it cannot be counted for compensation.
  static const String notValidNumber = 'CERT-2026-000201';

  List<Certificate>? _mine;
  Map<String, CertificateVerification>? _verifications;

  @override
  Future<List<Certificate>> mine({String? variant}) async {
    await _latency();
    switch (variant) {
      case 'empty':
        return const [];
      case 'unavailable':
        throw const ApiError(
          status: 503,
          message: 'CERTIFICATE_SERVICE_UNAVAILABLE',
        );
    }
    return List<Certificate>.unmodifiable(
      _mine ??= await _loadMine(),
    );
  }

  @override
  Future<CertificateVerification> verify(
    String numero, {
    String? variant,
  }) async {
    await _latency();
    if (variant == 'unavailable') {
      throw const ApiError(
        status: 503,
        message: 'CERTIFICATE_SERVICE_UNAVAILABLE',
      );
    }

    final known = await _verificationsMap();
    // The demo controls short-circuit the lookup, so each state can be
    // reviewed with any number typed in the field.
    switch (variant) {
      case 'not-found':
        throw ApiError(status: 404, message: 'CERTIFICATE_NOT_FOUND: $numero');
      case 'invalid':
        return known[notValidNumber]!;
      case 'valid':
        return known[validNumber]!;
    }

    final answer = known[numero.trim().toUpperCase()];
    if (answer == null) {
      throw ApiError(status: 404, message: 'CERTIFICATE_NOT_FOUND: $numero');
    }
    return answer;
  }

  Future<List<Certificate>> _loadMine() async {
    final raw = (await _loadDocument())['mine'] as List<dynamic>;
    return raw
        .map((item) => Certificate.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  Future<Map<String, CertificateVerification>> _verificationsMap() async {
    if (_verifications != null) return _verifications!;
    final raw = (await _loadDocument())['verifications'] as List<dynamic>;
    final map = <String, CertificateVerification>{};
    for (final item in raw) {
      final json = item as Map<String, dynamic>;
      map[(json['numeroCertificat'] as String).toUpperCase()] =
          CertificateVerification.fromJson(json);
    }
    return _verifications = map;
  }

  Future<Map<String, dynamic>> _loadDocument() async {
    final raw = await rootBundle.loadString(
      'lib/data/mock/fixtures/certificates.json',
    );
    return jsonDecode(raw) as Map<String, dynamic>;
  }

  Future<void> _latency() async {
    await Future<void>.delayed(const Duration(milliseconds: 300));
  }
}
