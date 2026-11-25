import 'package:blood_network/app/theme/app_theme.dart';
import 'package:blood_network/data/models/eligibility.dart';
import 'package:blood_network/features/donor/presentation/providers/donor_providers.dart';
import 'package:blood_network/features/donor/presentation/screens/eligibility_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import '../support/fake_donor_repository.dart';

Eligibility _sample(EligibilityDecision decision) => Eligibility(
  decision: decision,
  headline: switch (decision) {
    EligibilityDecision.eligible => 'You can donate today',
    EligibilityDecision.deferred => 'Take a break for now',
    EligibilityDecision.notEligible => 'You cannot donate for now',
  },
  summary: 'Summary line.',
  eligibleFrom: decision == EligibilityDecision.deferred
      ? DateTime(2026, 11, 22)
      : null,
  checks: const [
    EligibilityCheck(
      label: 'Weight at least 50 kg',
      detail: 'You recorded 62 kg · satisfied',
      satisfied: true,
    ),
  ],
);

Future<void> _pump(
  WidgetTester tester,
  EligibilityDecision decision, {
  Size size = const Size(1440, 900),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  final router = GoRouter(
    initialLocation: '/donor/eligibility',
    routes: [
      GoRoute(
        path: '/donor/eligibility',
        builder: (context, state) =>
            const Scaffold(body: EligibilityScreen()),
      ),
      GoRoute(
        path: '/donor/book',
        builder: (context, state) =>
            const Scaffold(body: Center(child: Text('book a slot'))),
      ),
    ],
  );

  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        donorRepositoryProvider.overrideWithValue(
          FakeDonorRepository(eligibilityResult: _sample(decision)),
        ),
      ],
      child: MaterialApp.router(theme: AppTheme.light, routerConfig: router),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('deferred shows the amber badge and the delayed action', (
    tester,
  ) async {
    await _pump(tester, EligibilityDecision.deferred);

    expect(find.text('Take a break for now'), findsOneWidget);
    expect(find.text('Deferred'), findsOneWidget);
    expect(
      find.text('See available slots after 22 Nov'),
      findsOneWidget,
    );
  });

  testWidgets('eligible shows the success badge and the plain action', (
    tester,
  ) async {
    await _pump(tester, EligibilityDecision.eligible);

    expect(find.text('You can donate today'), findsOneWidget);
    expect(find.text('Eligible'), findsOneWidget);
    expect(find.text('See available slots'), findsOneWidget);
  });

  testWidgets('not eligible hides the booking action', (tester) async {
    await _pump(tester, EligibilityDecision.notEligible);

    expect(find.text('You cannot donate for now'), findsOneWidget);
    expect(find.text('Not eligible'), findsOneWidget);
    expect(find.text('See available slots'), findsNothing);
  });

  testWidgets('the action moves the donor to booking', (tester) async {
    await _pump(tester, EligibilityDecision.eligible);

    await tester.tap(find.text('See available slots'));
    await tester.pumpAndSettle();

    expect(find.text('book a slot'), findsOneWidget);
  });

  testWidgets('mobile uses the shorter action label', (tester) async {
    await _pump(
      tester,
      EligibilityDecision.deferred,
      size: const Size(390, 844),
    );

    expect(find.text('See slots after 22 Nov'), findsOneWidget);
  });
}
