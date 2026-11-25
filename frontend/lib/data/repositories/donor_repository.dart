import '../models/donor.dart';
import '../models/eligibility.dart';

/// Donor records and their eligibility (Donneurs service).
abstract interface class DonorRepository {
  /// Register a donor. Throws an [ApiError] 400 (invalid field) or
  /// 409 (this CIN already exists).
  Future<Donor> register(DonorDraft draft);

  /// Eligibility of the donor in session.
  ///
  /// [variant] is a demo control: it selects one of the prepared outcomes
  /// (`eligible`, `not-eligible`) so every state can be reviewed before the
  /// backend exists. The real service always answers for the donor in session
  /// and ignores it.
  Future<Eligibility> eligibility({String? variant});
}
