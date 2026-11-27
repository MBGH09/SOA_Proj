import 'package:blood_network/core/errors/api_error.dart';
import 'package:blood_network/core/time/clock.dart';
import 'package:blood_network/data/mock/mock_request_repository.dart';
import 'package:blood_network/data/models/blood_component.dart';
import 'package:blood_network/data/models/blood_group.dart';
import 'package:blood_network/data/models/blood_request.dart';
import 'package:flutter_test/flutter_test.dart';

/// Contract and rule checks for a blood request (R11, R19, the request status
/// table in `demandes.yaml`).
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final now = DateTime(2026, 11, 12, 10, 24);
  late MockRequestRepository requests;

  setUp(() => requests = MockRequestRepository(FrozenClock(now)));

  BloodRequestDraft draft({
    String patientRef = 'P20260442',
    int units = 3,
    BloodGroup group = BloodGroup.oNeg,
    BloodComponent component = BloodComponent.redCells,
  }) => BloodRequestDraft(
    patientRef: patientRef,
    reason: RequestReason.surgery,
    bloodGroup: group,
    component: component,
    units: units,
    urgency: RequestUrgency.high,
    note: 'Orthopaedic surgery',
  );

  Future<ApiError> failure(Future<void> Function() action) async {
    try {
      await action();
    } on ApiError catch (error) {
      return error;
    }
    fail('the call should have failed');
  }

  test('the blood group round-trips the contract value', () {
    expect(BloodGroup.oNeg.apiValue, 'O_NEG');
    expect(BloodGroup.fromApi('AB_POS'), BloodGroup.abPos);
    expect(BloodGroup.fromApi('unknown'), isNull);
    expect(BloodGroup.oNeg.plainName, 'O negative');
  });

  test('a request maps its contract fields and summary line', () async {
    final request = await requests.byId('REQ-2026-0184');

    expect(request.bloodGroup, BloodGroup.oNeg);
    expect(request.component, BloodComponent.redCells);
    expect(request.units, 3);
    expect(request.status, RequestStatus.supplying);
    expect(request.urgency, RequestUrgency.critical);
    expect(request.supplySource, SupplySource.regionalCentre);
    expect(request.compensationProgress, '2 of 3');
    expect(request.compensationComplete, isFalse);
    expect(request.etaLabel, '52 min');
    expect(
      request.summaryLine(now),
      'Red cells · O negative · 3 units · Orthopaedic surgery · '
      'created today at 08:40.',
    );
  });

  test('a reference with a dash is a 400, before any call', () {
    expect(
      () => MockRequestRepository.validate(draft(patientRef: 'P-2026-0442')),
      throwsA(
        isA<ApiError>()
            .having((error) => error.status, 'status', 400)
            .having(
              (error) => error.fieldErrors,
              'fieldErrors',
              contains('patientRef'),
            ),
      ),
    );
  });

  test('zero units is a 400 (R11)', () {
    expect(
      () => MockRequestRepository.validate(draft(units: 0)),
      throwsA(
        isA<ApiError>()
            .having((error) => error.status, 'status', 400)
            .having(
              (error) => error.fieldErrors,
              'fieldErrors',
              contains('units'),
            ),
      ),
    );
  });

  test('a request the local bank can cover is satisfied (R13)', () async {
    final created = await requests.create(
      draft(units: 1, group: BloodGroup.oPos),
    );

    expect(created.status, RequestStatus.satisfied);
    expect(created.supplySource, SupplySource.localBank);
    expect(created.reservationReference, isNotNull);
  });

  test(
    'a request the local bank cannot cover goes to the regional CRTS (R14)',
    () async {
      final created = await requests.create(
        draft(units: 3, group: BloodGroup.oNeg),
      );

      expect(created.status, RequestStatus.supplying);
      expect(created.supplySource, SupplySource.regionalCentre);
    },
  );

  test(
    'nobody can supply: shortage, and the request stays open (R16)',
    () async {
      final created = await requests.create(
        draft(units: 99, group: BloodGroup.abNeg),
      );

      expect(created.status, RequestStatus.shortage);
      expect(created.status.isOpen, isTrue);
      expect(created.reservationReference, isNull);
    },
  );

  test('a certificate counts once (R26)', () async {
    final error = await failure(
      () => requests.addCompensation('REQ-2026-0184', 'CERT-2026-000314'),
    );

    expect(error.status, 409);
    expect(error.message, 'CERTIFICATE_ALREADY_USED');
    expect(error.fieldErrors, contains('certificate'));
  });

  test('a new certificate increases the counter', () async {
    final updated = await requests.addCompensation(
      'REQ-2026-0184',
      'CERT-2026-000999',
    );

    expect(updated.compensationReceived, 3);
    expect(updated.compensationComplete, isTrue);
    expect(
      updated.certificates.map((item) => item.number),
      contains('CERT-2026-000999'),
    );
  });

  test('an unknown certificate is a 404', () async {
    final error = await failure(
      () => requests.addCompensation('REQ-2026-0184', 'NOPE-1'),
    );

    expect(error.status, 404);
    expect(error.fieldErrors, contains('certificate'));
  });

  test('the counter can be corrected by hand, within what was asked', () async {
    final updated = await requests.correctCompensation('REQ-2026-0184', 1);
    expect(updated.compensationReceived, 1);

    final error = await failure(
      () => requests.correctCompensation('REQ-2026-0184', 5),
    );
    expect(error.status, 400);
    expect(error.fieldErrors, contains('received'));
  });

  test(
    'a request that does not ask for donors refuses a certificate',
    () async {
      final error = await failure(
        () => requests.addCompensation('REQ-2026-0181', 'CERT-2026-000777'),
      );

      expect(error.status, 400);
      expect(error.message, 'NO_COMPENSATION');
    },
  );
}
