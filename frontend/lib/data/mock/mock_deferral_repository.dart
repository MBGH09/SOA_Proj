import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;

import '../../core/errors/api_error.dart';
import '../models/deferral.dart';
import '../repositories/deferral_repository.dart';

/// In-memory Donneurs service for temporary deferrals.
///
/// The donor list and the reason reference come from `deferrals.json`; a
/// recorded deferral is kept in memory so the "current deferral" card of the
/// C4 screen really appears after the form is submitted.
class MockDeferralRepository implements DeferralRepository {
  List<DeferralReason>? _reasons;
  final Map<String, Deferral> _recorded = {};
  int _sequence = 32;

  @override
  Future<List<DeferralReason>> reasons() async {
    await _latency();
    return List<DeferralReason>.unmodifiable(
      _reasons ??= await _loadReasons(),
    );
  }

  @override
  Future<DeferralLookup?> lookup(String cin, {String? variant}) async {
    await _latency();
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'DEFERRAL_SERVICE_UNAVAILABLE');
    }
    if (variant == 'unknown') return null;

    final donor = await _findDonor(cin);
    if (donor == null) return null;

    final recorded = _recorded[cin];
    final current =
        recorded ?? await _currentDeferral(donor, await _loadReasons());
    return DeferralLookup(donor: donor, current: current);
  }

  @override
  Future<Deferral> record(DeferralDraft draft, {String? variant}) async {
    await _latency();
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'DEFERRAL_SERVICE_UNAVAILABLE');
    }
    if (draft.endDate.isBefore(draft.startDate)) {
      throw const ApiError(
        status: 400,
        message: 'INVALID_RANGE',
        fieldErrors: {
          'endDate': 'The end date cannot be before the start date.',
        },
      );
    }

    final reasons = await _loadReasons();
    final reason = reasons.firstWhere(
      (item) => item.code == draft.reasonCode,
      orElse: () => reasons.first,
    );
    final donor = await _findDonor(draft.cin);
    final deferral = Deferral(
      id: 'AJ-2026-00${_sequence++}',
      reason: reason,
      startDate: draft.startDate,
      endDate: draft.endDate,
      note: draft.note,
      donorName: donor?.name,
      bloodGroup: donor?.bloodGroup,
      lastDonation: donor?.lastDonation,
    );
    _recorded[draft.cin] = deferral;
    return deferral;
  }

  // --- helpers --------------------------------------------------------------

  Future<Deferral?> _currentDeferral(
    DonorSummary donor,
    List<DeferralReason> reasons,
  ) async {
    final raw = (await _loadDocument())['donors'] as List<dynamic>;
    for (final item in raw.cast<Map<String, dynamic>>()) {
      if (item['cin'] != donor.cin) continue;
      final current = item['currentDeferral'];
      if (current == null) return null;
      final deferral = Deferral.fromJson(
        current as Map<String, dynamic>,
        reasons,
      );
      return Deferral(
        id: deferral.id,
        reason: deferral.reason,
        startDate: deferral.startDate,
        endDate: deferral.endDate,
        note: deferral.note,
        donorName: donor.name,
        bloodGroup: donor.bloodGroup,
        lastDonation: donor.lastDonation,
      );
    }
    return null;
  }

  Future<DonorSummary?> _findDonor(String cin) async {
    final raw = (await _loadDocument())['donors'] as List<dynamic>;
    for (final item in raw.cast<Map<String, dynamic>>()) {
      if (item['cin'] == cin) return DonorSummary.fromJson(item);
    }
    return null;
  }

  Future<List<DeferralReason>> _loadReasons() async {
    final raw = (await _loadDocument())['reasons'] as List<dynamic>;
    return raw
        .map((item) => DeferralReason.fromJson(item as Map<String, dynamic>))
        .toList(growable: false);
  }

  Future<Map<String, dynamic>> _loadDocument() async {
    final raw = await rootBundle.loadString(
      'lib/data/mock/fixtures/deferrals.json',
    );
    return jsonDecode(raw) as Map<String, dynamic>;
  }

  Future<void> _latency() async {
    await Future<void>.delayed(const Duration(milliseconds: 300));
  }
}
