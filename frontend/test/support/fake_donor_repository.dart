import 'package:blood_network/core/errors/api_error.dart';
import 'package:blood_network/data/models/donor.dart';
import 'package:blood_network/data/models/eligibility.dart';
import 'package:blood_network/data/repositories/donor_repository.dart';

/// Test double for the Donneurs service: returns a prepared eligibility result
/// and either registers the donor or throws a prepared [ApiError].
class FakeDonorRepository implements DonorRepository {
  FakeDonorRepository({this.eligibilityResult, this.registerError});

  final Eligibility? eligibilityResult;
  final ApiError? registerError;

  int registerCalls = 0;

  @override
  Future<Eligibility> eligibility({String? variant}) async {
    final result = eligibilityResult;
    if (result == null) throw StateError('no eligibility result configured');
    return result;
  }

  @override
  Future<Donor> register(DonorDraft draft) async {
    registerCalls++;
    final error = registerError;
    if (error != null) throw error;
    return Donor(
      id: 'D-2026-001',
      fullName: draft.fullName,
      cin: draft.cin,
      dateOfBirth: draft.dateOfBirth,
      weightKg: draft.weightKg,
      bloodGroup: draft.bloodGroup,
      city: draft.city,
      phone: draft.phone,
    );
  }
}
