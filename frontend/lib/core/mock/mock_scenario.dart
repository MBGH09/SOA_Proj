import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../errors/api_error.dart';

/// Simulated backend outcomes, so every screen can be reviewed in each state
/// before the real services exist.
enum MockScenario {
  ok(status: 200),
  badRequest(status: 400),
  notFound(status: 404),
  conflict(status: 409),
  accepted(status: 202),
  serviceUnavailable(status: 503);

  const MockScenario({required this.status});

  final int status;

  bool get isOk => this == MockScenario.ok;

  /// The error this scenario produces, or `null` when it is a success path.
  ApiError? get asError => switch (this) {
    MockScenario.ok || MockScenario.accepted => null,
    _ => ApiError(status: status, message: _technicalMessage),
  };

  String get _technicalMessage => switch (this) {
    MockScenario.badRequest => 'BAD_REQUEST',
    MockScenario.notFound => 'NOT_FOUND',
    MockScenario.conflict => 'CONFLICT',
    MockScenario.serviceUnavailable => 'SERVICE_UNAVAILABLE',
    _ => 'OK',
  };
}

/// Throws [ApiError] when the scenario is a failure, so a mock repository can
/// reproduce a backend state with one line.
void ensureScenarioOk(MockScenario scenario) {
  final error = scenario.asError;
  if (error != null) throw error;
}

/// The scenario the demo is currently showing.
class MockScenarioController extends Notifier<MockScenario> {
  @override
  MockScenario build() => MockScenario.ok;

  void set(MockScenario scenario) => state = scenario;

  void reset() => state = MockScenario.ok;
}

final mockScenarioProvider =
    NotifierProvider<MockScenarioController, MockScenario>(
      MockScenarioController.new,
    );
