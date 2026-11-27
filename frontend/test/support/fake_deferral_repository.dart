import 'package:blood_network/core/errors/api_error.dart';
import 'package:blood_network/data/models/blood_group.dart';
import 'package:blood_network/data/models/deferral.dart';
import 'package:blood_network/data/repositories/deferral_repository.dart';

/// The reason reference list of the FigCraft C4 frame.
List<DeferralReason> sampleReasons() => const [
  DeferralReason(
    code: 'TATOUAGE',
    label: 'Tattoo',
    weeks: 4,
    durationLabel: '4 weeks',
  ),
  DeferralReason(
    code: 'PIERCING',
    label: 'Piercing',
    weeks: 4,
    durationLabel: '4 weeks',
  ),
  DeferralReason(
    code: 'CHIRURGIE',
    label: 'Surgery',
    weeks: 13,
    durationLabel: '3 months',
  ),
  DeferralReason(
    code: 'ANTIBIOTIQUES',
    label: 'Antibiotics',
    weeks: 2,
    durationLabel: '2 weeks',
  ),
  DeferralReason(
    code: 'INFECTION',
    label: 'Infection',
    weeks: 2,
    durationLabel: '2 weeks',
  ),
];

DonorSummary sampleDonorSummary() => DonorSummary(
  cin: '08123456',
  name: 'Amira Ben Salah',
  bloodGroup: BloodGroup.oNeg,
  lastDonation: DateTime(2026, 8, 12),
);

Deferral sampleDeferral() => Deferral(
  id: 'AJ-2026-0031',
  reason: sampleReasons().first,
  startDate: DateTime(2026, 11, 12),
  endDate: DateTime(2026, 12, 12),
  donorName: 'Amira Ben Salah',
  bloodGroup: BloodGroup.oNeg,
  lastDonation: DateTime(2026, 8, 12),
);

/// Test double for the deferral service.
class FakeDeferralRepository implements DeferralRepository {
  FakeDeferralRepository({
    List<DeferralReason>? reasons,
    DeferralLookup? lookup,
    this.found = true,
    this.lookupError,
    this.recordError,
  }) : _reasons = reasons ?? sampleReasons(),
       _lookup =
           lookup ??
           DeferralLookup(donor: sampleDonorSummary(), current: sampleDeferral());

  final List<DeferralReason> _reasons;
  final DeferralLookup? _lookup;

  /// False reproduces a CIN no donor carries.
  final bool found;
  final ApiError? lookupError;
  final ApiError? recordError;

  int lookupCalls = 0;
  DeferralDraft? lastDraft;

  @override
  Future<List<DeferralReason>> reasons() async => List.of(_reasons);

  @override
  Future<DeferralLookup?> lookup(String cin, {String? variant}) async {
    lookupCalls++;
    final error = lookupError;
    if (error != null) throw error;
    return found ? _lookup : null;
  }

  @override
  Future<Deferral> record(DeferralDraft draft, {String? variant}) async {
    lastDraft = draft;
    final error = recordError;
    if (error != null) throw error;
    return Deferral(
      id: 'AJ-2026-0099',
      reason: _reasons.firstWhere(
        (reason) => reason.code == draft.reasonCode,
        orElse: () => _reasons.first,
      ),
      startDate: draft.startDate,
      endDate: draft.endDate,
      note: draft.note,
      donorName: 'Amira Ben Salah',
      bloodGroup: BloodGroup.oNeg,
    );
  }
}
