import 'package:blood_network/app/theme/app_theme.dart';
import 'package:blood_network/data/models/visit.dart';
import 'package:blood_network/features/centre/presentation/providers/centre_providers.dart';
import 'package:blood_network/features/centre/presentation/screens/donation_visit_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import '../support/fake_visit_repository.dart';

Future<FakeVisitRepository> _pump(
  WidgetTester tester, {
  Visit? visit,
  Size size = const Size(1440, 900),
  String location = '/centre/visits/D-2026-1189',
  FakeVisitRepository? repository,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  final repo = repository ?? FakeVisitRepository(visit: visit);

  final router = GoRouter(
    initialLocation: location,
    routes: [
      GoRoute(
        path: '/centre/visits/:id',
        builder: (context, state) => const Scaffold(body: DonationVisitScreen()),
      ),
      GoRoute(
        path: '/centre/board',
        builder: (context, state) =>
            const Scaffold(body: Center(child: Text('board'))),
      ),
      GoRoute(
        path: '/centre/deferrals/new',
        builder: (context, state) =>
            const Scaffold(body: Center(child: Text('deferral'))),
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
  testWidgets('web shows the steps and the interview panel', (tester) async {
    await _pump(tester);

    expect(find.text('Donation visit · D-2026-1189'), findsOneWidget);
    expect(find.textContaining('Amira Ben Salah · O− · reception 09:12'), findsOneWidget);
    expect(find.text('Reception'), findsOneWidget);
    expect(find.text('Medical interview'), findsOneWidget);
    expect(find.text('STEP 2 · MEDICAL INTERVIEW'), findsOneWidget);
    expect(find.text('BLOOD PRESSURE (SYSTOLIC)'), findsOneWidget);
    expect(find.text('(DIASTOLIC)'), findsOneWidget);
    expect(find.text('HAEMOGLOBIN (G/DL)'), findsOneWidget);
    expect(find.text('Fit for collection'), findsOneWidget);
    expect(find.text('Unfit — REFUSE'), findsOneWidget);
    expect(find.text('Confirm fit'), findsOneWidget);
    expect(find.text('Record deferral'), findsOneWidget);
    expect(find.text('Refuse visit'), findsOneWidget);
    expect(find.text('A step cannot be skipped or repeated.'), findsOneWidget);
  });

  testWidgets('Confirm fit records APTE and moves to collection', (tester) async {
    final repo = await _pump(tester);

    await tester.tap(find.widgetWithText(FilledButton, 'Confirm fit'));
    await tester.pumpAndSettle();

    expect(repo.lastInterview?.decision, VisitDecision.fit);
    expect(repo.lastInterview?.systolic, 128);
    expect(repo.lastInterview?.haemoglobin, 13.4);
    expect(find.text('Confirm collection'), findsOneWidget);
  });

  testWidgets('Refuse visit asks first, then records INAPTE', (tester) async {
    final repo = await _pump(tester);

    await tester.tap(find.widgetWithText(OutlinedButton, 'Refuse visit'));
    await tester.pumpAndSettle();
    expect(find.text('Refuse this visit?'), findsOneWidget);

    await tester.tap(
      find.descendant(
        of: find.byType(AlertDialog),
        matching: find.text('Refuse visit'),
      ),
    );
    await tester.pumpAndSettle();

    expect(repo.lastInterview?.decision, VisitDecision.unfit);
  });

  testWidgets('a wrong step is reported once, in plain language', (tester) async {
    await _pump(tester, location: '/centre/visits/D-2026-1189?state=wrong-step');

    await tester.tap(find.widgetWithText(FilledButton, 'Confirm fit'));
    await tester.pumpAndSettle();

    expect(
      find.text('This is not possible right now. Refresh the page and try again.'),
      findsOneWidget,
    );
  });

  testWidgets('collection is fixed at 450 ml and asks for the duration', (
    tester,
  ) async {
    final repo = await _pump(
      tester,
      visit: sampleVisit(step: VisitStep.collection, durationMinutes: 10),
    );

    expect(find.text('STEP 3 · COLLECTION'), findsOneWidget);
    expect(find.text('450'), findsOneWidget);

    await tester.tap(find.widgetWithText(FilledButton, 'Confirm collection'));
    await tester.pumpAndSettle();

    expect(repo.lastCollection?.volumeMl, 450);
    expect(repo.lastCollection?.durationMinutes, 10);
  });

  testWidgets('End the visit stays disabled until the rest is done', (
    tester,
  ) async {
    await _pump(
      tester,
      visit: sampleVisit(step: VisitStep.rest, restMinutesLeft: 12),
    );

    final button = find.widgetWithText(FilledButton, 'End the visit');
    expect(tester.widget<FilledButton>(button).onPressed, isNull);
    expect(find.textContaining('12 min left'), findsOneWidget);
  });

  testWidgets('End the visit records the rest once it is over', (tester) async {
    final repo = await _pump(
      tester,
      visit: sampleVisit(
        step: VisitStep.rest,
        restMinutesLeft: 0,
        collectedAt: DateTime(2026, 11, 12, 9, 41),
        restStartedAt: DateTime(2026, 11, 12, 9, 51),
        restEndsAt: DateTime(2026, 11, 12, 10, 6),
      ),
    );

    await tester.tap(find.widgetWithText(FilledButton, 'End the visit'));
    await tester.pumpAndSettle();

    expect(repo.lastRestEndsAt, DateTime(2026, 11, 12, 10, 6));
  });

  testWidgets('reception records the CIN and the questionnaire', (tester) async {
    final repo = await _pump(
      tester,
      visit: sampleVisit(step: VisitStep.reception),
    );

    expect(find.text('STEP 1 · RECEPTION'), findsOneWidget);
    await tester.enterText(find.byType(TextField).first, '08123456');
    await tester.tap(find.widgetWithText(FilledButton, 'Confirm reception'));
    await tester.pumpAndSettle();

    expect(repo.lastReception?.cin, '08123456');
    expect(find.text('STEP 2 · MEDICAL INTERVIEW'), findsOneWidget);
  });

  testWidgets('mobile shows the four step chips', (tester) async {
    await _pump(tester, size: const Size(390, 844));

    expect(find.text('1 Reception'), findsOneWidget);
    expect(find.text('2 Interview'), findsOneWidget);
    expect(find.text('3 Collection'), findsOneWidget);
    expect(find.text('4 Rest'), findsOneWidget);
    expect(find.text('STEP 2 · MEDICAL INTERVIEW'), findsOneWidget);
  });
}