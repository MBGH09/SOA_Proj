import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;

import '../../core/errors/api_error.dart';
import '../../core/time/clock.dart';
import '../models/blood_component.dart';
import '../models/blood_group.dart';
import '../models/blood_request.dart';
import '../repositories/request_repository.dart';

/// In-memory Demandes service for the hospital screens.
///
/// The design frames are a snapshot of one day, so the fixture stores each
/// request as "created N days ago at HH:MM" and "updated M minutes ago" and the
/// repository anchors those to [Clock]. The list therefore keeps reading
/// "12 min ago" and "created today at 08:40" whenever the demo runs.
class MockRequestRepository implements RequestRepository {
  MockRequestRepository(this._clock);

  final Clock _clock;

  List<BloodRequest>? _requests;
  RequestSummary? _summary;
  int _sequence = 185;

  @override
  Future<BloodRequest> create(
    BloodRequestDraft draft, {
    String? variant,
  }) async {
    await _latency();
    _guard(variant);
    validate(draft);

    final local = await _stockLevel(
      siteId: hospitalId,
      group: draft.bloodGroup,
      component: draft.component,
    );
    final regional = await _stockLevel(
      siteId: 'C-SFAX',
      group: draft.bloodGroup,
      component: draft.component,
    );
    final national = await _stockLevel(
      siteId: 'CNTS',
      group: draft.bloodGroup,
      component: draft.component,
    );

    final now = _clock.now();
    final status = switch (variant) {
      'shortage' => RequestStatus.shortage,
      'pending' => RequestStatus.waiting,
      _ when local >= draft.units => RequestStatus.satisfied,
      _ when regional >= draft.units || national >= draft.units =>
        RequestStatus.supplying,
      _ => RequestStatus.shortage,
    };

    final source = status == RequestStatus.satisfied
        ? SupplySource.localBank
        : regional >= draft.units
        ? SupplySource.regionalCentre
        : national >= draft.units
        ? SupplySource.nationalService
        : null;

    final reserved =
        status == RequestStatus.satisfied || status == RequestStatus.supplying;

    final request = BloodRequest(
      id: 'REQ-2026-0$_sequence',
      hospitalId: hospitalId,
      hospitalName: hospitalName,
      patientRef: draft.patientRef,
      reason: draft.reason,
      bloodGroup: draft.bloodGroup,
      component: draft.component,
      units: draft.units,
      urgency: draft.urgency,
      status: status,
      createdAt: now,
      updatedAt: now,
      note: draft.note,
      reservationReference: reserved ? 'RES-2026-0${_sequence + 300}' : null,
      reservationStatus: reserved ? 'Order placed' : null,
      suppliedBy: switch (source) {
        SupplySource.localBank => 'Local bank · this hospital',
        SupplySource.regionalCentre => 'Regional centre · CRTS Sfax',
        SupplySource.nationalService => 'National service · CNTS',
        null => null,
      },
      supplySource: source,
      trackingReference: reserved ? 'TRK-2026-88${_sequence + 50}' : null,
      deliveryFrom: switch (source) {
        SupplySource.localBank => 'This hospital · blood bank',
        SupplySource.regionalCentre => 'CRTS Sfax',
        SupplySource.nationalService => 'CNTS national',
        null => null,
      },
      deliveryTo: 'This hospital · blood bank',
      estimatedMinutes: reserved ? 45 : null,
      deliveryStage: reserved ? DeliveryStage.reserved : null,
    );
    _sequence++;

    final requests = await _loadRequests();
    requests.insert(0, request);

    final summary = await _loadSummary();
    _summary = RequestSummary(
      openRequests: summary.openRequests + (request.status.isOpen ? 1 : 0),
      inDelivery:
          summary.inDelivery +
          (request.status == RequestStatus.supplying ? 1 : 0),
      deliveredThisWeek:
          summary.deliveredThisWeek +
          (request.status == RequestStatus.satisfied ? 1 : 0),
      unitsNeededToday:
          summary.unitsNeededToday +
          (request.status.isOpen ? request.units : 0),
    );
    return request;
  }

  /// Contract checks for `DemandeRequest` (R11, R19, patterns).
  static void validate(BloodRequestDraft draft) {
    final fields = <String, String>{};

    final reference = draft.patientRef.trim();
    if (reference.isEmpty) {
      fields['patientRef'] = 'Enter the patient reference.';
    } else if (!RegExp(r'^[A-Za-z0-9]+$').hasMatch(reference)) {
      fields['patientRef'] =
          'Use letters and digits only — no spaces, dashes or accents.';
    } else if (reference.length > 50) {
      fields['patientRef'] = 'Keep the reference to 50 characters or fewer.';
    }

    if (draft.units < 1) {
      fields['units'] = 'A request needs at least one unit.';
    }

    if (fields.isNotEmpty) {
      throw ApiError(
        status: 400,
        message: 'INVALID_REQUEST',
        fieldErrors: fields,
      );
    }
  }

  @override
  Future<List<BloodRequest>> requests({String? variant}) async {
    await _latency();
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'REQUEST_SERVICE_UNAVAILABLE');
    }
    final list = await _loadRequests();
    if (variant == 'empty') return const [];
    return List<BloodRequest>.unmodifiable(list);
  }

  @override
  Future<BloodRequest> byId(String id, {String? variant}) async {
    await _latency();
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'REQUEST_SERVICE_UNAVAILABLE');
    }
    if (variant == 'not-found') {
      throw const ApiError(status: 404, message: 'REQUEST_NOT_FOUND');
    }
    final list = await _loadRequests();
    for (final request in list) {
      if (request.id == id) return request;
    }
    throw ApiError(status: 404, message: 'REQUEST_NOT_FOUND: $id');
  }

  @override
  Future<RequestSummary> summary({String? variant}) async {
    await _latency();
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'REQUEST_SERVICE_UNAVAILABLE');
    }
    return _summary ??= await _loadSummary();
  }

  @override
  Future<BloodRequest> addCompensation(
    String requestId,
    String certificateNumber, {
    String? variant,
  }) async {
    await _latency();
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'DONOR_SERVICE_UNAVAILABLE');
    }
    final list = await _loadRequests();
    final index = list.indexWhere((request) => request.id == requestId);
    if (index < 0) {
      throw const ApiError(status: 404, message: 'REQUEST_NOT_FOUND');
    }
    final request = list[index];
    final number = certificateNumber.trim();
    final asked = request.compensationRequested;

    if (asked == null) {
      throw const ApiError(
        status: 400,
        message: 'NO_COMPENSATION',
        fieldErrors: {
          'certificate': 'This request does not ask for family donors.',
        },
      );
    }
    if (number.isEmpty) {
      throw const ApiError(
        status: 400,
        message: 'EMPTY_CERTIFICATE',
        fieldErrors: {'certificate': 'Enter the certificate number.'},
      );
    }
    if (variant == 'invalid-certificate' ||
        !RegExp(r'^CERT-\d{4}-\d+$').hasMatch(number)) {
      throw const ApiError(
        status: 404,
        message: 'CERTIFICATE_NOT_FOUND',
        fieldErrors: {
          'certificate':
              'We could not find that certificate. Check the number.',
        },
      );
    }
    if (request.certificates.any((item) => item.number == number)) {
      throw const ApiError(
        status: 409,
        message: 'CERTIFICATE_ALREADY_USED',
        fieldErrors: {
          'certificate':
              'This certificate is already counted. A number counts once.',
        },
      );
    }
    if (request.compensationReceived >= asked) {
      throw const ApiError(
        status: 400,
        message: 'COMPENSATION_COMPLETE',
        fieldErrors: {
          'certificate': 'All the donors asked for are already counted.',
        },
      );
    }

    final updated = request.copyWith(
      compensationReceived: request.compensationReceived + 1,
      certificates: [
        ...request.certificates,
        CompensationCertificate(number: number, valid: true),
      ],
      updatedAt: _clock.now(),
    );
    list[index] = updated;
    return updated;
  }

  @override
  Future<BloodRequest> correctCompensation(
    String requestId,
    int received, {
    String? variant,
  }) async {
    await _latency();
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'REQUEST_SERVICE_UNAVAILABLE');
    }
    final list = await _loadRequests();
    final index = list.indexWhere((request) => request.id == requestId);
    if (index < 0) {
      throw const ApiError(status: 404, message: 'REQUEST_NOT_FOUND');
    }
    final request = list[index];
    final asked = request.compensationRequested ?? 0;
    if (received < 0 || received > asked) {
      throw ApiError(
        status: 400,
        message: 'INVALID_COMPENSATION',
        fieldErrors: {'received': 'Enter a number between 0 and $asked.'},
      );
    }
    final updated = request.copyWith(
      compensationReceived: received,
      updatedAt: _clock.now(),
    );
    list[index] = updated;
    return updated;
  }

  // --- helpers --------------------------------------------------------------

  /// The request list, with the fixture's relative dates anchored to now.
  Future<List<BloodRequest>> _loadRequests() async {
    if (_requests != null) return _requests!;
    final now = _clock.now();
    final raw = await rootBundle.loadString(
      'lib/data/mock/fixtures/requests.json',
    );
    final document = jsonDecode(raw) as Map<String, dynamic>;
    final items = (document['requests'] as List<dynamic>)
        .cast<Map<String, dynamic>>();
    return _requests = items
        .map((item) => _anchor(item, now))
        .toList(growable: true);
  }

  BloodRequest _anchor(Map<String, dynamic> item, DateTime now) {
    final days = item['createdDaysAgo'] as int?;
    final time = item['createdTime'] as String?;
    if (days == null || time == null) return BloodRequest.fromJson(item);

    final parts = time.split(':');
    final created = DateTime(
      now.year,
      now.month,
      now.day - days,
      int.parse(parts[0]),
      int.parse(parts[1]),
    );
    final updated = now.subtract(
      Duration(minutes: item['updatedMinutesAgo'] as int? ?? 0),
    );
    return BloodRequest.fromJson({
      ...item,
      'createdAt': created.toIso8601String(),
      'updatedAt': updated.toIso8601String(),
    });
  }

  Future<RequestSummary> _loadSummary() async {
    final raw = await rootBundle.loadString(
      'lib/data/mock/fixtures/requests.json',
    );
    final document = jsonDecode(raw) as Map<String, dynamic>;
    return RequestSummary.fromJson(document['summary'] as Map<String, dynamic>);
  }

  /// Units of one group + component at one place, from the stock fixture.
  Future<int> _stockLevel({
    required String siteId,
    required BloodGroup group,
    required BloodComponent component,
  }) async {
    final raw = await rootBundle.loadString(
      'lib/data/mock/fixtures/stock.json',
    );
    final document = jsonDecode(raw) as Map<String, dynamic>;
    final levels =
        ((document['snapshots'] as Map<String, dynamic>)[siteId]
                as Map<String, dynamic>)['levels']
            as List<dynamic>;
    for (final item in levels.cast<Map<String, dynamic>>()) {
      if (item['bloodGroup'] == group.apiValue &&
          item['component'] == component.apiValue) {
        return item['quantity'] as int;
      }
    }
    return 0;
  }

  void _guard(String? variant) {
    switch (variant) {
      case 'invalid':
        throw const ApiError(
          status: 400,
          message: 'INVALID_REQUEST',
          fieldErrors: {'units': 'A request needs at least one unit.'},
        );
      case 'unavailable':
        throw const ApiError(
          status: 503,
          message: 'REQUEST_SERVICE_UNAVAILABLE',
        );
    }
  }

  Future<void> _latency() async {
    await Future<void>.delayed(const Duration(milliseconds: 300));
  }
}

/// The hospital the demo session belongs to.
const String hospitalId = 'HOP-SFX-01';
const String hospitalName = 'CHU Habib Bourguiba Sfax';
