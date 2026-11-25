import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;

import '../../core/errors/api_error.dart';
import '../models/donor.dart';
import '../models/eligibility.dart';
import '../repositories/donor_repository.dart';

/// In-memory Donneurs service.
///
/// CIN `12345678` always conflicts, so the 409 state can be reviewed by hand.
class MockDonorRepository implements DonorRepository {
  static const String conflictingCin = '12345678';

  Donor? _donor;

  /// The donor registered in this demo session, if any. Later screens
  /// (appointments, certificates) read the identity from here.
  Donor? get donor => _donor;

  @override
  Future<Donor> register(DonorDraft draft) async {
    await _latency();

    if (draft.cin == conflictingCin) {
      throw const ApiError(
        status: 409,
        message: 'CIN_ALREADY_EXISTS',
        fieldErrors: {'cin': 'An account already exists with this ID number.'},
      );
    }

    final donor = Donor(
      id: 'D-2026-001',
      fullName: draft.fullName,
      cin: draft.cin,
      dateOfBirth: draft.dateOfBirth,
      weightKg: draft.weightKg,
      bloodGroup: draft.bloodGroup,
      city: draft.city,
      phone: draft.phone,
    );
    _donor = donor;
    return donor;
  }

  @override
  Future<Eligibility> eligibility({String? variant}) async {
    await _latency();
    final name = variant == null ? 'eligibility' : 'eligibility_$variant';
    final raw = await rootBundle.loadString(
      'lib/data/mock/fixtures/$name.json',
    );
    return Eligibility.fromJson(jsonDecode(raw) as Map<String, dynamic>);
  }

  Future<void> _latency() async {
    await Future<void>.delayed(const Duration(milliseconds: 300));
  }
}
