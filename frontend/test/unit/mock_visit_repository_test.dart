import 'package:blood_network/core/errors/api_error.dart';
import 'package:blood_network/data/mock/mock_visit_repository.dart';
import 'package:blood_network/data/models/visit.dart';
import 'package:flutter_test/flutter_test.dart';

/// The mock repository enforces the visit rules the real service returns as
/// 409/400 answers (R6 order, R7 unfit, R8 volume, R9 rest). These tests pin
/// those answers so a demo cannot drift away from the contract.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late MockVisitRepository visits;

  setUp(() => visits = MockVisitRepository());

  Future<ApiError> failure(Future<void> Function() action) async {
    try {
      await action();
    } on ApiError catch (error) {
      return error;
    }
    fail('the call should have failed');
  }

  test('a step out of order is a 409 WRONG_STEP (R6)', () async {
    // D-2026-1186 already reached the collection step.
    final error = await failure(
      () => visits.recordInterview(
        'D-2026-1186',
        const VisitInterview(
          systolic: 120,
          diastolic: 80,
          haemoglobin: 14,
          decision: VisitDecision.fit,
        ),
      ),
    );

    expect(error.status, 409);
    expect(error.message, 'WRONG_STEP');
    expect(error.fieldErrors, contains('step'));
  });

  test('a collection volume other than 450 ml is a 400 (R8)', () async {
    final error = await failure(
      () => visits.recordCollection(
        'D-2026-1186',
        const VisitCollection(volumeMl: 400, durationMinutes: 10),
      ),
    );

    expect(error.status, 400);
    expect(error.message, 'INVALID_VOLUME');
    expect(error.fieldErrors, contains('volume'));
  });

  test('ending the visit before the rest is over is a 409 (R9)', () async {
    final error = await failure(
      () => visits.finishRest(
        'D-2026-1184',
        restStartedAt: DateTime(2026, 11, 12, 9, 51),
        restEndsAt: DateTime(2026, 11, 12, 10, 6),
        variant: 'rest-not-finished',
      ),
    );

    expect(error.status, 409);
    expect(error.message, 'REST_NOT_FINISHED');
    expect(error.fieldErrors, contains('rest'));
  });

  test('reception, interview, collection and rest walk one visit', () async {
    final started = await visits.start(cin: '08123457');
    expect(started.step, VisitStep.reception);
    expect(started.channel, VisitChannel.walkIn);

    final received = await visits.recordReception(
      started.id,
      cin: '08123457',
      questionnaire: const [
        VisitQuestion(label: 'Feels well today', answer: true),
      ],
    );
    expect(received.step, VisitStep.interview);
    expect(received.questionnaire.single.label, 'Feels well today');

    final decided = await visits.recordInterview(
      started.id,
      const VisitInterview(
        systolic: 120,
        diastolic: 80,
        haemoglobin: 14.2,
        decision: VisitDecision.fit,
      ),
    );
    expect(decided.step, VisitStep.collection);
    expect(decided.decision, VisitDecision.fit);

    final collected = await visits.recordCollection(
      started.id,
      const VisitCollection(volumeMl: 450, durationMinutes: 10),
    );
    expect(collected.step, VisitStep.rest);
    expect(collected.volumeMl, 450);
    expect(collected.nationalDonationNumber, isNotNull);
    expect(
      collected.restEndsAt!.difference(collected.restStartedAt!).inMinutes,
      MockVisitRepository.minimumRestMinutes,
    );

    final finished = await visits.finishRest(
      started.id,
      restStartedAt: collected.restStartedAt!,
      restEndsAt: collected.restEndsAt!,
    );
    expect(finished.step, VisitStep.done);
    expect(finished.isOpen, isFalse);

    // The visit is stored, so a reload sees the same terminal state.
    final reloaded = await visits.byId(started.id);
    expect(reloaded.step, VisitStep.done);
  });

  test('an unfit decision ends the visit as refused (R7)', () async {
    // D-2026-1189 stands at the medical interview.
    final refused = await visits.recordInterview(
      'D-2026-1189',
      const VisitInterview(
        systolic: 120,
        diastolic: 80,
        haemoglobin: 9.4,
        decision: VisitDecision.unfit,
      ),
    );

    expect(refused.step, VisitStep.refused);
    expect(refused.decision, VisitDecision.unfit);

    // A refused visit never reaches collection (R7).
    final error = await failure(
      () => visits.recordCollection(
        'D-2026-1189',
        const VisitCollection(volumeMl: 450, durationMinutes: 10),
      ),
    );
    expect(error.status, 409);
    expect(error.message, 'WRONG_STEP');
  });

  test('closing an appointment removes it from the day (C1)', () async {
    final before = await visits.today();
    expect(
      before.agenda.any((row) => row.reference == 'RDV-2026-0446 · online'),
      isTrue,
    );

    final after = await visits.markAbsent('RDV-2026-0446 · online');

    expect(
      after.agenda.any((row) => row.reference == 'RDV-2026-0446 · online'),
      isFalse,
    );
    expect(after.stats.booked, before.stats.booked - 1);
    expect(after.stats.online, before.stats.online - 1);
  });

  test('an unknown appointment reference is a 404', () async {
    final error = await failure(() => visits.markAbsent('RDV-2026-9999'));

    expect(error.status, 404);
    expect(error.message, 'APPOINTMENT_NOT_FOUND');
  });
}