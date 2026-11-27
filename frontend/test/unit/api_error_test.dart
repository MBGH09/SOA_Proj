import 'package:blood_network/core/errors/api_error.dart';
import 'package:blood_network/core/mock/mock_scenario.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('maps contract statuses to plain-language copy', () {
    expect(
      apiMessageFor(const ApiError(status: 409, message: 'CONFLICT')),
      contains('not possible'),
    );
    expect(
      apiMessageFor(const ApiError(status: 404, message: 'NOT_FOUND')),
      contains('could not find'),
    );
    expect(
      apiMessageFor(const ApiError(status: 503, message: 'SERVICE_UNAVAILABLE')),
      contains('busy'),
    );
  });

  group('MockScenario', () {
    test('ok and accepted produce no error', () {
      expect(MockScenario.ok.asError, isNull);
      expect(MockScenario.accepted.asError, isNull);
      expect(MockScenario.accepted.status, 202);
    });

    test('failure scenarios carry their status', () {
      expect(MockScenario.badRequest.asError?.status, 400);
      expect(MockScenario.notFound.asError?.status, 404);
      expect(MockScenario.conflict.asError?.status, 409);
      expect(MockScenario.serviceUnavailable.asError?.status, 503);
    });

    test('ensureScenarioOk throws only on a failure', () {
      expect(() => ensureScenarioOk(MockScenario.ok), returnsNormally);
      expect(
        () => ensureScenarioOk(MockScenario.conflict),
        throwsA(isA<ApiError>()),
      );
    });
  });
}
