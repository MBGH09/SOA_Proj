import 'package:blood_network/data/models/blood_group.dart';
import 'package:blood_network/data/models/certificate.dart';
import 'package:blood_network/data/models/visit.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('VisitStep', () {
    test('maps every API value of the contract', () {
      expect(VisitStep.fromApi('ACCUEIL'), VisitStep.reception);
      expect(VisitStep.fromApi('ENTRETIEN_MEDICAL'), VisitStep.interview);
      expect(VisitStep.fromApi('PRELEVEMENT'), VisitStep.collection);
      expect(VisitStep.fromApi('REPOS'), VisitStep.rest);
      expect(VisitStep.fromApi('TERMINE'), VisitStep.done);
      expect(VisitStep.fromApi('REFUSE'), VisitStep.refused);
    });

    test('runs in order and cannot be skipped', () {
      expect(VisitStep.ordered, [
        VisitStep.reception,
        VisitStep.interview,
        VisitStep.collection,
        VisitStep.rest,
      ]);
      expect(VisitStep.reception.hasReached(VisitStep.collection), isFalse);
      expect(VisitStep.collection.hasReached(VisitStep.reception), isTrue);
      expect(VisitStep.collection.hasReached(VisitStep.collection), isTrue);
      expect(VisitStep.rest.hasReached(VisitStep.done), isFalse);
    });

    test('positions and labels the step', () {
      expect(VisitStep.reception.position, 1);
      expect(VisitStep.rest.position, 4);
      expect(VisitStep.done.position, isNull);
      expect(VisitStep.interview.boardLabel, 'STEP 2 · MEDICAL INTERVIEW');
      expect(VisitStep.interview.boardTitle, 'Interview');
      expect(VisitStep.done.boardTitle, 'Done');
    });
  });

  group('Visit', () {
    Visit visit({
      VisitStep step = VisitStep.interview,
      int? restMinutesLeft,
      int? elapsedMinutes = 26,
      CertificateStatus certificate = CertificateStatus.preparing,
    }) => Visit(
      id: 'D-2026-1189',
      donorName: 'Amira Ben Salah',
      bloodGroup: BloodGroup.oNeg,
      centreName: 'CRTS Sfax',
      channel: VisitChannel.walkIn,
      step: step,
      startedAt: DateTime(2026, 11, 12, 9, 12),
      questionnaire: const [],
      restMinutesLeft: restMinutesLeft,
      elapsedMinutes: elapsedMinutes,
      certificateStatus: certificate,
    );

    test('prints the board card lines', () {
      final open = visit();
      expect(open.groupAndStart, 'O− · 09:12');
      expect(open.trailLabel, '26 min');
      expect(open.isOpen, isTrue);
    });

    test('a visit on rest shows the minutes left', () {
      final resting = visit(step: VisitStep.rest, restMinutesLeft: 12);
      expect(resting.trailLabel, '12 min left');
    });

    test('a finished visit shows its certificate state', () {
      expect(
        visit(step: VisitStep.done, certificate: CertificateStatus.issued)
            .trailLabel,
        'cert. issued',
      );
      expect(
        visit(step: VisitStep.done).trailLabel,
        'cert. pending',
      );
    });
  });

  group('VisitInterview', () {
    test('serialises to the contract field names', () {
      const interview = VisitInterview(
        systolic: 128,
        diastolic: 82,
        haemoglobin: 13.4,
        decision: VisitDecision.fit,
      );
      expect(interview.toJson(), {
        'tensionSystolique': 128,
        'tensionDiastolique': 82,
        'hemoglobineGdl': 13.4,
        'decision': 'APTE',
      });
    });
  });

  group('VisitCollection', () {
    test('serialises an exactly-450 ml pouch (R8)', () {
      const collection = VisitCollection(volumeMl: 450, durationMinutes: 10);
      expect(collection.toJson(), {'volumeMl': 450, 'dureeMinutes': 10});
    });
  });
}
