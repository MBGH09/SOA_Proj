import '../models/blood_request.dart';
import '../models/stock_level.dart';

/// The hospital's blood requests (Demandes: `/demandes`,
/// `/demandes/{id}`, `/demandes/{id}/compensations`).
abstract interface class RequestRepository {
  /// Create a request (`POST /demandes`). The status depends on where the
  /// pouches can come from (study: 201 local stock, 202 ordered, 202 shortage).
  ///
  /// [variant] is a demo control for the contract outcomes
  /// (`shortage` → `EN_PENURIE`, `pending` → `EN_ATTENTE`, `invalid` → 400,
  /// `unavailable` → 503); the real service decides from the stock.
  Future<BloodRequest> create(BloodRequestDraft draft, {String? variant});

  /// Every request of the hospital, newest first (`GET /demandes`).
  Future<List<BloodRequest>> requests({String? variant});

  /// One request with its reservation, delivery and compensation.
  Future<BloodRequest> byId(String id, {String? variant});

  /// The four figures above the list.
  Future<RequestSummary> summary({String? variant});

  /// Add a family donor's certificate (`POST /demandes/{id}/compensations`).
  ///
  /// [variant] forces `duplicate` → 409 (R26), `invalid-certificate` → 404,
  /// `unavailable` → 503.
  Future<BloodRequest> addCompensation(
    String requestId,
    String certificateNumber, {
    String? variant,
  });

  /// Correct the counter by hand (`PATCH /demandes/{id}`,
  /// `compensationRecue`), the fallback when the national service is slow.
  Future<BloodRequest> correctCompensation(
    String requestId,
    int received, {
    String? variant,
  });
}

/// Stock levels and alert thresholds (Demandes: `/stock/niveaux`).
///
/// The dashboard (H4) and the thresholds screen (A1) share it, one per place.
abstract interface class StockRepository {
  /// The places the stock board can show.
  Future<List<StockSite>> sites();

  /// Every level of one place.
  Future<StockSnapshot> snapshot(String siteId, {String? variant});

  /// The alert thresholds of one place, with the current levels joined in.
  Future<ThresholdSnapshot> thresholds(String siteId, {String? variant});

  /// Replace the thresholds of one place and append the audit lines.
  ///
  /// The contract has no endpoint for this yet; the mock applies the change in
  /// memory so the screen can be reviewed.
  Future<ThresholdSnapshot> saveThresholds(
    String siteId,
    List<ThresholdRule> rules, {
    String? variant,
  });
}
