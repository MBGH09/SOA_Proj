import 'package:blood_network/app/theme/app_theme.dart';
import 'package:blood_network/core/errors/api_error.dart';
import 'package:blood_network/features/centre/presentation/providers/centre_providers.dart';
import 'package:blood_network/features/centre/presentation/screens/today_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import '../support/fake_visit_repository.dart';

Future<FakeVisitRepository> _pump(
  WidgetTester tester, {
  Size size = const Size(1440, 900),
  FakeVisitRepository? repository,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  final repo = repository ?? FakeVisitRepository();

  final router = GoRouter(
    initialLocation: '/centre/today',
    routes: [
      GoRoute(
        path: '/centre/today',
        builder: (context, state) =>
            const Scaffold(body: TodayScreen()),
      ),
      GoRoute(
        path: '/centre/visits/:id',
        builder: (context, state) =>
            Scaffold(body: Center(child: Text('visit ${state.pathParameters['id']}'))),
      ),
    ],
  );

  await tester.pumpWidget(
    ProviderScope(
      overrides: [visitRepositoryProvider.overrideWithValue(repo)],
      child: MaterialApp.router(theme: AppTheme.light, routerConfig: router),
    ),
  );
  await tester.pumpAndSettle();
  return repo;
}

void main() {
  testWidgets('web shows the day figures, agenda, queue and alert', (tester) async {
    await _pump(tester);

    expect(find.text('Today at the centre'), findsOneWidget);
    expect(
      find.textContaining('CRTS Sfax · Thursday 12 November 2026'),
      findsOneWidget,
    );
    expect(find.text('ARRIVALS EXPECTED'), findsOneWidget);
    expect(find.text('12'), findsOneWidget);
    expect(find.text('10 online · 2 walk-in'), findsOneWidget);
    expect(find.text('3 150 ml · 450 ml each'), findsOneWidget);

    expect(find.text('08:30'), findsOneWidget);
    expect(find.text('D-2026-1189 · walk-in'), findsOneWidget);
    expect(find.text('RDV-2026-0442 · online'), findsOneWidget);
    expect(find.text('Interview'), findsOneWidget);
    expect(find.text('Waiting'), findsOneWidget);
    expect(find.text('Absent'), findsOneWidget);
    expect(find.text('Check in'), findsOneWidget);
    expect(find.text('Close'), findsOneWidget);

    expect(find.text('D-2026-1191  ·  O−'), findsOneWidget);
    expect(find.text('6 min'), findsOneWidget);
    expect(
      find.text(
        'O− is below its alert threshold at CRTS Sfax. Consider adding a '
        'mobile collection.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('Open walks to the visit', (tester) async {
    await _pump(tester);

    await tester.tap(find.text('Open').first);
    await tester.pumpAndSettle();

    expect(find.text('visit D-2026-1189'), findsOneWidget);
  });

  testWidgets('Check in starts the visit of the waiting donor', (tester) async {
    final repo = await _pump(tester);

    await tester.tap(find.text('Check in'));
    await tester.pumpAndSettle();

    expect(repo.startedCins, contains('08123457'));
    expect(find.textContaining('visit D-2026-1189'), findsOneWidget);
  });

  testWidgets('Close marks the absent appointment after a confirmation', (
    tester,
  ) async {
    final repo = await _pump(tester);

    await tester.tap(find.text('Close'));
    await tester.pumpAndSettle();
    expect(find.text('Close this appointment?'), findsOneWidget);

    await tester.tap(find.text('Mark absent'));
    await tester.pumpAndSettle();

    expect(repo.absentReferences, ['RDV-2026-0446 · online']);
    expect(find.text('RDV-2026-0446 · online'), findsNothing);
  });

  testWidgets('a service that is down is reported in plain language', (
    tester,
  ) async {
    await _pump(
      tester,
      repository: FakeVisitRepository(
        dayError: const ApiError(status: 503, message: 'UNAVAILABLE'),
      ),
    );

    expect(find.text('We could not load the day'), findsOneWidget);
    expect(
      find.text('The service is busy. Please try again in a moment.'),
      findsOneWidget,
    );
  });

  testWidgets('mobile stacks the figures and keeps the queue', (tester) async {
    await _pump(tester, size: const Size(390, 844));

    expect(find.text('ARRIVALS EXPECTED'), findsOneWidget);
    expect(find.text('COLLECTED TODAY'), findsOneWidget);
    expect(find.text('D-2026-1192  ·  A+'), findsOneWidget);
  });
}