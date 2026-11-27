import '../models/deferral.dart';

/// Temporary deferrals (Donneurs `/donneurs/{id}/ajournements`).
///
/// A deferral takes effect immediately: the donor cannot book and cannot
/// donate until its end date (R4).
abstract interface class DeferralRepository {
  /// The reference list of reasons and their durations (study §16).
  Future<List<DeferralReason>> reasons();

  /// Look a donor up by their CIN, with the deferral they may already have.
  ///
  /// Answers `null` when no donor carries that CIN.
  Future<DeferralLookup?> lookup(String cin, {String? variant});

  /// Record a deferral for the donor.
  ///
  /// Throws an [ApiError] 400 when the end date is before the start date.
  Future<Deferral> record(DeferralDraft draft, {String? variant});
}
