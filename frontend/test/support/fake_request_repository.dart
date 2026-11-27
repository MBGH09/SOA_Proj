import 'package:blood_network/core/errors/api_error.dart';
import 'package:blood_network/data/mock/mock_request_repository.dart';
import 'package:blood_network/data/models/blood_component.dart';
import 'package:blood_network/data/models/blood_group.dart';
import 'package:blood_network/data/models/blood_request.dart';
import 'package:blood_network/data/repositories/request_repository.dart';

/// A synchronous Demandes service for widget tests: the same six records and
/// the same rules as the mock, without the bundle load or the demo latency.
class FakeRequestRepository implements RequestRepository {
  FakeRequestRepository({DateTime? now})
    : now = now ?? DateTime(2026, 11, 12, 10, 24) {
    _requests.addAll(_seed(this.now));
  }

  final DateTime now;
  final List<BloodRequest> _requests = [];
  int _sequence = 185;

  @override
  Future<BloodRequest> create(
    BloodRequestDraft draft, {
    String? variant,
  }) async {
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'REQUEST_SERVICE_UNAVAILABLE');
    }
    if (variant == 'invalid') {
      throw const ApiError(
        status: 400,
        message: 'INVALID_REQUEST',
        fieldErrors: {'units': 'A request needs at least one unit.'},
      );
    }
    MockRequestRepository.validate(draft);

    final status = switch (variant) {
      'shortage' => RequestStatus.shortage,
      _ when draft.units <= 2 => RequestStatus.satisfied,
      _ => RequestStatus.supplying,
    };
    final created = BloodRequest(
      id: 'REQ-2026-0${_sequence++}',
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
      supplySource: status == RequestStatus.satisfied
          ? SupplySource.localBank
          : status == RequestStatus.supplying
          ? SupplySource.regionalCentre
          : null,
      reservationReference: status.isOpen || status == RequestStatus.satisfied
          ? 'RES-2026-0513'
          : null,
      reservationStatus: status == RequestStatus.shortage
          ? null
          : 'Order placed',
      suppliedBy: switch (status) {
        RequestStatus.satisfied => 'Local bank · this hospital',
        RequestStatus.supplying => 'Regional centre · CRTS Sfax',
        _ => null,
      },
      deliveryStage: status == RequestStatus.shortage
          ? null
          : DeliveryStage.reserved,
    );
    _requests.insert(0, created);
    return created;
  }

  @override
  Future<List<BloodRequest>> requests({String? variant}) async {
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'REQUEST_SERVICE_UNAVAILABLE');
    }
    if (variant == 'empty') return const [];
    return List<BloodRequest>.unmodifiable(_requests);
  }

  @override
  Future<BloodRequest> byId(String id, {String? variant}) async {
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'REQUEST_SERVICE_UNAVAILABLE');
    }
    if (variant == 'not-found') {
      throw const ApiError(status: 404, message: 'REQUEST_NOT_FOUND');
    }
    for (final request in _requests) {
      if (request.id == id) return request;
    }
    throw ApiError(status: 404, message: 'REQUEST_NOT_FOUND: $id');
  }

  @override
  Future<RequestSummary> summary({String? variant}) async {
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'REQUEST_SERVICE_UNAVAILABLE');
    }
    return const RequestSummary(
      openRequests: 3,
      inDelivery: 2,
      deliveredThisWeek: 12,
      unitsNeededToday: 7,
    );
  }

  @override
  Future<BloodRequest> addCompensation(
    String requestId,
    String certificateNumber, {
    String? variant,
  }) async {
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'DONOR_SERVICE_UNAVAILABLE');
    }
    final index = _requests.indexWhere((request) => request.id == requestId);
    if (index < 0) {
      throw const ApiError(status: 404, message: 'REQUEST_NOT_FOUND');
    }
    final request = _requests[index];
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
    if (!RegExp(r'^CERT-\d{4}-\d+$').hasMatch(number)) {
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
      updatedAt: now,
    );
    _requests[index] = updated;
    return updated;
  }

  @override
  Future<BloodRequest> correctCompensation(
    String requestId,
    int received, {
    String? variant,
  }) async {
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'REQUEST_SERVICE_UNAVAILABLE');
    }
    final index = _requests.indexWhere((request) => request.id == requestId);
    if (index < 0) {
      throw const ApiError(status: 404, message: 'REQUEST_NOT_FOUND');
    }
    final request = _requests[index];
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
      updatedAt: now,
    );
    _requests[index] = updated;
    return updated;
  }

  List<BloodRequest> _seed(DateTime now) {
    DateTime at(int daysAgo, String time) {
      final parts = time.split(':');
      return DateTime(
        now.year,
        now.month,
        now.day - daysAgo,
        int.parse(parts[0]),
        int.parse(parts[1]),
      );
    }

    return [
      BloodRequest(
        id: 'REQ-2026-0184',
        hospitalId: hospitalId,
        hospitalName: hospitalName,
        patientRef: 'P20260442',
        reason: RequestReason.surgery,
        bloodGroup: BloodGroup.oNeg,
        component: BloodComponent.redCells,
        units: 3,
        urgency: RequestUrgency.critical,
        status: RequestStatus.supplying,
        createdAt: at(0, '08:40'),
        updatedAt: now.subtract(const Duration(minutes: 12)),
        note: 'Orthopaedic surgery',
        compensationRequested: 3,
        compensationReceived: 2,
        certificates: const [
          CompensationCertificate(number: 'CERT-2026-000314', valid: true),
          CompensationCertificate(number: 'CERT-2026-000298', valid: true),
        ],
        reservationReference: 'RES-2026-0512',
        reservationStatus: 'Order placed',
        suppliedBy: 'Regional centre · CRTS Sfax',
        supplySource: SupplySource.regionalCentre,
        trackingReference: 'TRK-2026-8841',
        deliveryFrom: 'CRTS Sfax',
        deliveryTo: 'This hospital · blood bank',
        estimatedMinutes: 52,
        deliveryStage: DeliveryStage.inTransit,
      ),
      BloodRequest(
        id: 'REQ-2026-0181',
        hospitalId: hospitalId,
        hospitalName: hospitalName,
        patientRef: 'P20260377',
        reason: RequestReason.surgery,
        bloodGroup: BloodGroup.aPos,
        component: BloodComponent.plasma,
        units: 2,
        urgency: RequestUrgency.high,
        status: RequestStatus.satisfied,
        createdAt: at(0, '07:20'),
        updatedAt: now.subtract(const Duration(minutes: 180)),
        reservationReference: 'RES-2026-0508',
        reservationStatus: 'Reserved',
        suppliedBy: 'Local bank · this hospital',
        supplySource: SupplySource.localBank,
        trackingReference: 'TRK-2026-8830',
        deliveryFrom: 'This hospital · blood bank',
        deliveryTo: 'Operating theatre 2',
        deliveryStage: DeliveryStage.delivered,
      ),
      BloodRequest(
        id: 'REQ-2026-0179',
        hospitalId: hospitalId,
        hospitalName: hospitalName,
        patientRef: 'P20260455',
        reason: RequestReason.emergency,
        bloodGroup: BloodGroup.bPos,
        component: BloodComponent.platelets,
        units: 1,
        urgency: RequestUrgency.critical,
        status: RequestStatus.shortage,
        createdAt: at(0, '09:02'),
        updatedAt: now.subtract(const Duration(minutes: 60)),
        note: 'Dengue case, platelets needed today',
        compensationRequested: 3,
        compensationReceived: 1,
        certificates: const [
          CompensationCertificate(number: 'CERT-2026-000271', valid: true),
        ],
      ),
      BloodRequest(
        id: 'REQ-2026-0175',
        hospitalId: hospitalId,
        hospitalName: hospitalName,
        patientRef: 'P20260310',
        reason: RequestReason.treatment,
        bloodGroup: BloodGroup.abNeg,
        component: BloodComponent.redCells,
        units: 2,
        urgency: RequestUrgency.normal,
        status: RequestStatus.satisfied,
        createdAt: at(1, '08:10'),
        updatedAt: now.subtract(const Duration(minutes: 1440)),
        reservationReference: 'RES-2026-0496',
        reservationStatus: 'Reserved',
        suppliedBy: 'Local bank · this hospital',
        supplySource: SupplySource.localBank,
        trackingReference: 'TRK-2026-8812',
        deliveryFrom: 'This hospital · blood bank',
        deliveryTo: 'Haematology ward',
        deliveryStage: DeliveryStage.delivered,
      ),
      BloodRequest(
        id: 'REQ-2026-0171',
        hospitalId: hospitalId,
        hospitalName: hospitalName,
        patientRef: 'P20260298',
        reason: RequestReason.surgery,
        bloodGroup: BloodGroup.oPos,
        component: BloodComponent.plasma,
        units: 4,
        urgency: RequestUrgency.normal,
        status: RequestStatus.waiting,
        createdAt: at(1, '08:05'),
        updatedAt: now.subtract(const Duration(minutes: 1500)),
        note: 'Scheduled for Friday',
      ),
      BloodRequest(
        id: 'REQ-2026-0166',
        hospitalId: hospitalId,
        hospitalName: hospitalName,
        patientRef: 'P20260201',
        reason: RequestReason.treatment,
        bloodGroup: BloodGroup.aNeg,
        component: BloodComponent.redCells,
        units: 1,
        urgency: RequestUrgency.high,
        status: RequestStatus.satisfied,
        createdAt: at(2, '11:30'),
        updatedAt: now.subtract(const Duration(minutes: 2880)),
        reservationReference: 'RES-2026-0471',
        reservationStatus: 'Reserved',
        suppliedBy: 'Local bank · this hospital',
        supplySource: SupplySource.localBank,
        trackingReference: 'TRK-2026-8771',
        deliveryFrom: 'This hospital · blood bank',
        deliveryTo: 'Intensive care',
        deliveryStage: DeliveryStage.delivered,
      ),
    ];
  }
}
