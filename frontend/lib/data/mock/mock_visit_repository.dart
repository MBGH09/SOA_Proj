import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;

import '../../core/errors/api_error.dart';
import '../models/blood_group.dart';
import '../models/centre_day.dart';
import '../models/certificate.dart';
import '../models/visit.dart';
import '../repositories/visit_repository.dart';

/// In-memory Donneurs service for the four-step donation visit.
///
/// The mock session is frozen on 12 November 2026, the day of the design
/// frames, and every action updates the in-memory visit, so a visit really
/// walks from reception to the certificate while the demo runs.
class MockVisitRepository implements VisitRepository {
  static const int minimumRestMinutes = 15;
  static const int volumePerDonationMl = 450;

  /// `?state=` of `byId`: the visit as if it stood on another step.
  static const Map<String, VisitStep> _stepVariants = {
    'reception': VisitStep.reception,
    'interview': VisitStep.interview,
    'collection': VisitStep.collection,
    'rest': VisitStep.rest,
    'done': VisitStep.done,
    'refused': VisitStep.refused,
  };

  Map<String, Visit>? _visits;
  List<Map<String, dynamic>>? _board;
  CentreDay? _day;
  int _sequence = 1195;

  @override
  Future<CentreDay> today({String? variant}) async {
    await _latency();
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'VISIT_SERVICE_UNAVAILABLE');
    }
    final day = _day ??= await _loadDay();
    if (variant == 'quiet') {
      return CentreDay(
        centreName: day.centreName,
        date: day.date,
        stats: day.stats,
        agenda: const [],
        queue: const [],
        stock: day.stock,
        lowStockGroups: day.lowStockGroups,
      );
    }
    return day;
  }

  @override
  Future<List<BoardColumn>> board({String? variant}) async {
    await _latency();
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'VISIT_SERVICE_UNAVAILABLE');
    }
    final visits = await _loadVisits();
    final board = _board ??= await _loadBoard();
    if (variant == 'empty') {
      return board
          .map(
            (column) => BoardColumn(
              step: VisitStep.fromApi(column['step'] as String),
              visits: const [],
            ),
          )
          .toList(growable: false);
    }
    return board
        .map((column) => BoardColumn.fromJson(column, visits))
        .toList(growable: false);
  }

  @override
  Future<Visit> byId(String id, {String? variant}) async {
    await _latency();
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'VISIT_SERVICE_UNAVAILABLE');
    }
    final visit = (await _loadVisits())[id];
    if (visit == null) {
      throw ApiError(status: 404, message: 'VISIT_NOT_FOUND: $id');
    }
    final step = _stepVariants[variant];
    return step == null ? visit : _atStep(visit, step);
  }

  @override
  Future<Visit> start({
    required String cin,
    String? rendezVousId,
    String? variant,
  }) async {
    await _latency();
    if (variant == 'active-visit') {
      throw const ApiError(
        status: 409,
        message: 'VISIT_ALREADY_OPEN',
        fieldErrors: {'cin': 'This donor already has a visit open today.'},
      );
    }
    if (variant == 'wrong-cin') {
      throw const ApiError(
        status: 400,
        message: 'CIN_MISMATCH',
        fieldErrors: {'cin': 'This CIN does not match the appointment.'},
      );
    }

    final visits = await _loadVisits();
    final visit = Visit(
      id: 'D-2026-0${_sequence++}',
      donorName: 'Amira Ben Salah',
      bloodGroup: _groupOf(cin),
      centreName: 'CRTS Sfax',
      channel: rendezVousId == null ? VisitChannel.walkIn : VisitChannel.online,
      step: VisitStep.reception,
      startedAt: DateTime(2026, 11, 12, 9, 47),
      rendezVousReference: rendezVousId,
      elapsedMinutes: 0,
      questionnaire: const [
        VisitQuestion(label: 'Feels well today, no fever in the last 7 days', answer: true),
        VisitQuestion(label: 'Weighs at least 50 kg', answer: true),
        VisitQuestion(label: 'No dental extraction in the last 7 days', answer: true),
        VisitQuestion(label: 'No tattoo or piercing in the last 4 months', answer: true),
      ],
    );
    visits[visit.id] = visit;
    return visit;
  }

  @override
  Future<Visit> recordReception(
    String visitId, {
    required String cin,
    required List<VisitQuestion> questionnaire,
    String? variant,
  }) async {
    await _latency();
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'VISIT_SERVICE_UNAVAILABLE');
    }
    final current = await _requireVisit(visitId);
    _guardStep(current, VisitStep.reception, variant);
    final received = Visit(
      id: current.id,
      donorName: current.donorName,
      bloodGroup: current.bloodGroup,
      centreName: current.centreName,
      channel: current.channel,
      step: VisitStep.interview,
      startedAt: current.startedAt,
      questionnaire: questionnaire,
      rendezVousReference: current.rendezVousReference,
      elapsedMinutes: current.elapsedMinutes ?? 0,
    );
    return (await _loadVisits())[visitId] = received;
  }

  @override
  Future<Visit> recordInterview(
    String visitId,
    VisitInterview interview, {
    String? variant,
  }) async {
    await _latency();
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'VISIT_SERVICE_UNAVAILABLE');
    }
    final current = await _requireVisit(visitId);
    _guardStep(current, VisitStep.interview, variant);

    final decided = current.copyWith(
      systolic: interview.systolic,
      diastolic: interview.diastolic,
      haemoglobin: interview.haemoglobin,
      decision: interview.decision,
      step: interview.decision == VisitDecision.fit
          ? VisitStep.collection
          : VisitStep.refused,
    );
    return (await _loadVisits())[visitId] = decided;
  }

  @override
  Future<Visit> recordCollection(
    String visitId,
    VisitCollection collection, {
    String? variant,
  }) async {
    await _latency();
    if (collection.volumeMl != volumePerDonationMl) {
      throw const ApiError(
        status: 400,
        message: 'INVALID_VOLUME',
        fieldErrors: {'volume': 'The volume must be exactly 450 ml.'},
      );
    }
    final current = await _requireVisit(visitId);
    _guardStep(current, VisitStep.collection, variant);

    final collectedAt = DateTime(2026, 11, 12, 9, 41);
    final collected = current.copyWith(
      step: VisitStep.rest,
      collectedAt: collectedAt,
      durationMinutes: collection.durationMinutes,
      volumeMl: collection.volumeMl,
      nationalDonationNumber: 'NDN-2026-1188${current.id.substring(current.id.length - 2)}',
      restStartedAt: collectedAt.add(Duration(minutes: 10)),
      restEndsAt: collectedAt.add(
        Duration(minutes: 10 + minimumRestMinutes),
      ),
      restMinutesLeft: minimumRestMinutes,
    );
    return (await _loadVisits())[visitId] = collected;
  }

  @override
  Future<Visit> finishRest(
    String visitId, {
    required DateTime restStartedAt,
    required DateTime restEndsAt,
    String? variant,
  }) async {
    await _latency();
    if (variant == 'rest-not-finished') {
      throw const ApiError(
        status: 409,
        message: 'REST_NOT_FINISHED',
        fieldErrors: {
          'rest': 'The visit can only end after 15 minutes of rest.',
        },
      );
    }
    final current = await _requireVisit(visitId);
    _guardStep(current, VisitStep.rest, variant);

    final finished = current.copyWith(
      step: VisitStep.done,
      restStartedAt: restStartedAt,
      restEndsAt: restEndsAt,
      restMinutesLeft: 0,
      certificateStatus: CertificateStatus.preparing,
    );
    return (await _loadVisits())[visitId] = finished;
  }

  @override
  Future<Visit> refuse(String visitId, {String? variant}) async {
    await _latency();
    final current = await _requireVisit(visitId);
    _guardStep(current, VisitStep.interview, variant);
    final refused = current.copyWith(
      step: VisitStep.refused,
      decision: VisitDecision.unfit,
    );
    return (await _loadVisits())[visitId] = refused;
  }

  @override
  Future<CentreDay> markAbsent(String reference, {String? variant}) async {
    await _latency();
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'VISIT_SERVICE_UNAVAILABLE');
    }
    final day = await today();
    AgendaRow? row;
    for (final item in day.agenda) {
      if (item.reference == reference) {
        row = item;
        break;
      }
    }
    if (row == null) {
      throw const ApiError(status: 404, message: 'APPOINTMENT_NOT_FOUND');
    }
    final online = row.channel == VisitChannel.online;
    return _day = CentreDay(
      centreName: day.centreName,
      date: day.date,
      stats: CentreDayStats(
        booked: day.stats.booked - 1,
        online: online ? day.stats.online - 1 : day.stats.online,
        walkIn: online ? day.stats.walkIn : day.stats.walkIn - 1,
        receptionDone: day.stats.receptionDone,
        inInterview: day.stats.inInterview,
        unfit: day.stats.unfit,
        collectedUnits: day.stats.collectedUnits,
        collectedMl: day.stats.collectedMl,
        mlPerDonation: day.stats.mlPerDonation,
      ),
      agenda: day.agenda
          .where((item) => item.reference != reference)
          .toList(growable: false),
      queue: day.queue,
      stock: day.stock,
      lowStockGroups: day.lowStockGroups,
    );
  }

  // --- helpers --------------------------------------------------------------

  /// A 409 when the visit has already moved past [expected] (R6).
  void _guardStep(Visit visit, VisitStep expected, String? variant) {
    if (variant == 'wrong-step' || visit.step != expected) {
      throw ApiError(
        status: 409,
        message: 'WRONG_STEP',
        fieldErrors: {
          'step': 'This step is not the current one. Reload to see the visit '
              'as it stands.',
        },
      );
    }
  }

  /// A blood group that stays stable for the demo: a walked-in donor gets one
  /// of the four common groups, chosen from the CIN they typed.
  BloodGroup _groupOf(String cin) {
    const groups = [
      BloodGroup.oPos,
      BloodGroup.aPos,
      BloodGroup.bPos,
      BloodGroup.oNeg,
    ];
    final last = cin.isEmpty ? 0 : int.tryParse(cin[cin.length - 1]) ?? 0;
    return groups[last % groups.length];
  }

  Future<Visit> _requireVisit(String id) async {    final visit = (await _loadVisits())[id];
    if (visit == null) {
      throw ApiError(status: 404, message: 'VISIT_NOT_FOUND: $id');
    }
    return visit;
  }

  /// The same visit, as it stood on [step], for the demo controls.
  Visit _atStep(Visit visit, VisitStep step) => visit.copyWith(
    step: step,
    decision: switch (step) {
      VisitStep.reception => null,
      VisitStep.refused => VisitDecision.unfit,
      _ => visit.decision ?? VisitDecision.fit,
    },
    collectedAt: step.hasReached(VisitStep.rest) ? visit.collectedAt : null,
    volumeMl: step.hasReached(VisitStep.rest) ? visit.volumeMl : null,
    nationalDonationNumber: step.hasReached(VisitStep.rest)
        ? visit.nationalDonationNumber
        : null,
    restMinutesLeft: step == VisitStep.rest ? visit.restMinutesLeft : null,
    certificateStatus: step == VisitStep.done
        ? CertificateStatus.issued
        : visit.certificateStatus,
  );

  Future<Map<String, Visit>> _loadVisits() async {
    if (_visits != null) return _visits!;
    final raw = (await _loadDocument())['visits'] as Map<String, dynamic>;
    return _visits = {
      for (final entry in raw.entries)
        entry.key: Visit.fromJson(entry.value as Map<String, dynamic>),
    };
  }

  Future<List<Map<String, dynamic>>> _loadBoard() async {
    final raw = (await _loadDocument())['board'] as List<dynamic>;
    return raw.cast<Map<String, dynamic>>();
  }

  Future<CentreDay> _loadDay() async {
    final raw = await rootBundle.loadString(
      'lib/data/mock/fixtures/centre_day.json',
    );
    return CentreDay.fromJson(jsonDecode(raw) as Map<String, dynamic>);
  }

  Future<Map<String, dynamic>> _loadDocument() async {
    final raw = await rootBundle.loadString(
      'lib/data/mock/fixtures/visits.json',
    );
    return jsonDecode(raw) as Map<String, dynamic>;
  }

  Future<void> _latency() async {
    await Future<void>.delayed(const Duration(milliseconds: 300));
  }
}
