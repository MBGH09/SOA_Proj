import 'package:blood_network/app/theme/app_theme.dart';
import 'package:blood_network/core/errors/api_error.dart';
import 'package:blood_network/core/providers/core_providers.dart';
import 'package:blood_network/core/time/clock.dart';
import 'package:blood_network/features/centre/presentation/providers/centre_providers.dart';
import 'package:blood_network/features/centre/presentation/screens/record_deferral_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import '../support/fake_deferral_repository.dart';

Future<FakeDeferralRepository> _pump(
  WidgetTester tester, {
  Size size = const Size(1440, 900),
  FakeDeferralRepository? repository,
  String location = '/centre/deferrals/new',
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  final repo = repository ?? FakeDeferralRepository();

  final router = GoRouter(
    initialLocation: location,
    routes: [
      GoRoute(
        path: '/centre/deferrals/new',
        builder: (context, state) =>
            const Scaffold(body: RecordDeferralScreen()),
      ),
      GoRoute(
        path: '/centre/today',
        builder: (context, state) =>
            const Scaffold(body: Center(child: Text('today'))),
      ),
    ],
  );

  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        deferralRepositoryProvider.overrideWithValue(repo),
        clockProvider.overrideWithValue(FrozenClock(DateTime(2026, 11, 12, 10))),
      ],
      child: MaterialApp.router(theme: AppTheme.light, routerConfig: router),
    ),
  );
  await tester.pumpAndSettle();
  return repo;
}

Future<void> _lookUp(WidgetTester tester, String cin) async {
  await tester.enterText(find.byType(TextField).first, cin);
  await tester.testTextInput.receiveAction(TextInputAction.done);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('web shows the form, the reference list and the empty side', (
    tester,
  ) async {
    await _pump(tester);

    expect(find.text('Record a deferral'), findsOneWidget);
    expect(find.text('DONOR'), findsOneWidget);
    expect(find.text('REASON'), findsOneWidget);
    expect(find.text('TATOUAGE'), findsOneWidget);
    expect(find.text('PIERCING'), findsOneWidget);
    expect(find.text('CHIRURGIE'), findsOneWidget);
    expect(find.text('ANTIBIOTIQUES'), findsOneWidget);
    expect(find.text('INFECTION'), findsOneWidget);
    expect(find.text('1 month'), findsOneWidget);
    expect(find.text('3 months'), findsWidgets);
    expect(find.text('START DATE'), findsOneWidget);
    expect(find.text('END DATE'), findsOneWidget);
    expect(find.text('12 November 2026'), findsOneWidget);
    expect(find.text('12 December 2026'), findsOneWidget);
    expect(find.text('REASONS · REFERENCE'), findsOneWidget);
    expect(find.text('Surgery'), findsOneWidget);
    expect(find.text('Look a donor up to see their deferral.'), findsOneWidget);
  });

  testWidgets('the CIN lookup brings the donor and their deferral', (
    tester,
  ) async {
    await _pump(tester);
    await _lookUp(tester, '08123456');

    expect(
      find.text('Amira Ben Salah · O− · last donation 12 Aug 2026'),
      findsOneWidget,
    );
    expect(find.text('Tattoo · 4 weeks'), findsOneWidget);
    expect(
      find.textContaining('Active until 12 December 2026'),
      findsOneWidget,
    );
  });

  testWidgets('Record is disabled until a donor is found, then sends the draft', (
    tester,
  ) async {
    final repo = await _pump(tester);

    final button = find.widgetWithText(FilledButton, 'Record deferral');
    expect(tester.widget<FilledButton>(button).onPressed, isNull);

    await _lookUp(tester, '08123456');
    expect(tester.widget<FilledButton>(button).onPressed, isNotNull);

    await tester.tap(button);
    await tester.pumpAndSettle();

    expect(repo.lastDraft?.cin, '08123456');
    expect(repo.lastDraft?.reasonCode, 'TATOUAGE');
    expect(repo.lastDraft?.startDate, DateTime(2026, 11, 12));
    expect(repo.lastDraft?.endDate, DateTime(2026, 12, 12));
    expect(
      find.text('Deferral recorded. It takes effect immediately.'),
      findsOneWidget,
    );
  });

  testWidgets('an unknown CIN is reported on the donor line', (tester) async {
    await _pump(tester, repository: FakeDeferralRepository(found: false));
    await _lookUp(tester, '00000000');

    expect(find.text('No donor found with this CIN.'), findsOneWidget);
  });

  testWidgets('a service that is down is reported in plain language', (
    tester,
  ) async {
    final repo = await _pump(
      tester,
      location: '/centre/deferrals/new?state=unavailable',
      repository: FakeDeferralRepository(
        recordError: const ApiError(status: 503, message: 'UNAVAILABLE'),
      ),
    );
    await _lookUp(tester, '08123456');
    await tester.tap(find.widgetWithText(FilledButton, 'Record deferral'));
    await tester.pumpAndSettle();

    expect(repo.lastDraft, isNotNull);
    expect(
      find.text('The service is busy. Please try again in a moment.'),
      findsOneWidget,
    );
  });

  testWidgets('mobile stacks the form above the reference cards', (tester) async {
    await _pump(tester, size: const Size(390, 844));

    expect(find.text('Record a deferral'), findsOneWidget);
    expect(find.text('REASONS · REFERENCE'), findsOneWidget);
  });
}