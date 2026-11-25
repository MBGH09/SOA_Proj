import 'package:blood_network/data/models/blood_group.dart';
import 'package:blood_network/data/models/centre_day.dart';
import 'package:blood_network/data/models/visit.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('CentreDayStats', () {
    const stats = CentreDayStats(
      booked: 12,
      online: 10,
      walkIn: 2,
      receptionDone: 11,
      inInterview: 3,
      unfit: 1,
      collectedUnits: 7,
      collectedMl: 3150,
    );

    test('splits arrivals by channel', () {
      expect(stats.bookedDetail, '10 online · 2 walk-in');
    });

    test('groups the collected volume in thousands', () {
      expect(stats.collectedDetail, '3 150 ml · 450 ml each');
    });
  });

  group('CentreDay', () {
    CentreDay day({List<BloodGroup> low = const []}) => CentreDay(
      centreName: 'CRTS Sfax',
      date: DateTime(2026, 11, 12),
      stats: const CentreDayStats(
        booked: 12,
        online: 10,
        walkIn: 2,
        receptionDone: 11,
        inInterview: 3,
        unfit: 1,
        collectedUnits: 7,
        collectedMl: 3150,
      ),
      agenda: const [],
      queue: const [],
      stock: const [],
      lowStockGroups: low,
    );

    test('names the groups below their threshold', () {
      expect(
        day(low: const [BloodGroup.oNeg]).lowStockMessage,
        'O− is below its alert threshold at CRTS Sfax. Consider adding a '
        'mobile collection.',
      );
      expect(day().lowStockMessage, isNull);
    });
  });

  group('AgendaRow', () {
    test('keeps the appointment channel and action', () {
      const row = AgendaRow(
        time: '09:00',
        reference: 'RDV-2026-0442 · online',
        channel: VisitChannel.online,
        status: AgendaStatus.waiting,
      );
      expect(row.status.label, 'Waiting');
      expect(row.status.action, 'Check in');
      expect(row.channel.label, 'Online');
    });

    test('maps the absent row to its close action', () {
      expect(AgendaStatus.absent.action, 'Close');
      expect(AgendaStatus.interview.action, 'Open');
    });
  });

  group('BoardColumn', () {
    test('names who works each step', () {
      final reception = BoardColumn(
        step: VisitStep.reception,
        visits: const [],
      );
      expect(reception.count, 0);
      expect(reception.caption, 'Reception · CIN + questionnaire');
      expect(
        BoardColumn(step: VisitStep.done, visits: const []).caption,
        'Visit closed · certificate queued',
      );
    });
  });
}