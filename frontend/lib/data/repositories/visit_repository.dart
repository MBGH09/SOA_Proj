import '../models/centre_day.dart';
import '../models/visit.dart';

/// The donation visit: reception, medical interview, collection and rest
/// (Donneurs `/dons`, study §7.2). Steps run in order, so a wrong order is a
/// 409 (R6).
abstract interface class VisitRepository {
  /// The day at a glance for the centre in session (C1).
  ///
  /// [variant] is a demo control (`quiet`, `unavailable`) so each state can be
  /// reviewed before the backend exists.
  Future<CentreDay> today({String? variant});

  /// Every open visit grouped by step, in board order (C2).
  Future<List<BoardColumn>> board({String? variant});

  /// One visit (`GET /api/v1/dons/{id}`).
  Future<Visit> byId(String id, {String? variant});

  /// Reception: record the CIN and the questionnaire, with or without an
  /// appointment (step 1, `canal = SUR_PLACE` for a walk-in).
  Future<Visit> start({
    required String cin,
    String? rendezVousId,
    String? variant,
  });

  /// Complete the reception step of a visit already sitting at `ACCUEIL`: its
  /// CIN and questionnaire are recorded and it moves on to the medical
  /// interview (step 1 → 2, R6).
  ///
  /// The contract records reception when the visit is created (`POST /dons`),
  /// which is what [start] does; this completes a walk-in the board already
  /// shows at `ACCUEIL`.
  Future<Visit> recordReception(
    String visitId, {
    required String cin,
    required List<VisitQuestion> questionnaire,
    String? variant,
  });

  /// The medical interview (step 2, R7).
  Future<Visit> recordInterview(
    String visitId,
    VisitInterview interview, {
    String? variant,
  });

  /// The collection (step 3): exactly 450 ml, and the national donation
  /// number comes back with it (R8).
  Future<Visit> recordCollection(
    String visitId,
    VisitCollection collection, {
    String? variant,
  });

  /// Rest (step 4): the visit ends only after the minimum rest (R9).
  Future<Visit> finishRest(
    String visitId, {
    required DateTime restStartedAt,
    required DateTime restEndsAt,
    String? variant,
  });

  /// An unfit decision ends the visit as `REFUSE`: no collection, no
  /// certificate (R7, R24).
  Future<Visit> refuse(String visitId, {String? variant});

  /// Mark an appointment the donor did not turn up for as `ABSENT`, which
  /// drops it from the day's agenda (C1).
  Future<CentreDay> markAbsent(String reference, {String? variant});
}
