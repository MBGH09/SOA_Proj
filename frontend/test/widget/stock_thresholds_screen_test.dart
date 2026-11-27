import 'package:blood_network/app/theme/app_theme.dart';
import 'package:blood_network/core/providers/core_providers.dart';
import 'package:blood_network/core/time/clock.dart';
import 'package:blood_network/features/admin/presentation/screens/stock_thresholds_screen.dart';
import 'package:blood_network/features/hospital/presentation/providers/hospital_providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import '../support/fake_stock_repository.dart';

Future<void> _pump(
  WidgetTester tester, {
  Size size = const Size(1440, 1000),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  final router = GoRouter(
    initialLocation: '/admin/thresholds',
    routes: [
      GoRoute(
        path: '/admin/thresholds',
        builder: (context, state) =>
            const Scaffold(body: StockThresholdsScreen()),
      ),
    ],
  );

  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        clockProvider.overrideWithValue(
          FrozenClock(DateTime(2026, 11, 12, 10, 24)),
        ),
        stockRepositoryProvider.overrideWithValue(
          FakeStockRepository(now: DateTime(2026, 11, 12, 10, 24)),
        ),
      ],
      child: MaterialApp.router(theme: AppTheme.light, routerConfig: router),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('web shows the editable thresholds and the audit trail', (
    tester,
  ) async {
    await _pump(tester);

    expect(find.text('Stock thresholds'), findsOneWidget);
    expect(find.text('Save changes'), findsOneWidget);
    expect(find.text('Reset'), findsOneWidget);
    expect(find.text('ALERT THRESHOLD'), findsOneWidget);
    expect(find.text('CURRENT'), findsOneWidget);
    expect(find.text('CHANGED BY'), findsOneWidget);
    expect(find.text('Amine K.'), findsWidgets);
    expect(find.text('12 Nov 2026 · 09:12'), findsWidgets);
    expect(find.text('AUDIT TRAIL · WHO CHANGED IT LAST'), findsOneWidget);
    expect(find.text('Amine K. · O negative · red cells'), findsOneWidget);
    expect(find.text('threshold 6 → 8'), findsOneWidget);
  });

  testWidgets('a threshold steps and saves with a new audit line', (
    tester,
  ) async {
    await _pump(tester);

    await tester.tap(find.byTooltip('Increase threshold').first);
    await tester.pumpAndSettle();

    final stepper = find.byKey(const ValueKey('threshold-O_NEG-CGR'));
    expect(tester.widget<Text>(stepper).data, '9');
    expect(find.text('1 unsaved change'), findsOneWidget);

    await tester.tap(find.text('Save changes'));
    await tester.pumpAndSettle();

    expect(find.text('1 threshold saved for This hospital.'), findsOneWidget);
    expect(find.text('You · O negative · red cells'), findsOneWidget);
    expect(find.text('threshold 8 → 9'), findsOneWidget);
    expect(tester.widget<Text>(stepper).data, '9');
  });

  testWidgets('Reset brings back the saved numbers', (tester) async {
    await _pump(tester);

    await tester.tap(find.byTooltip('Increase threshold').first);
    await tester.pumpAndSettle();

    final stepper = find.byKey(const ValueKey('threshold-O_NEG-CGR'));
    expect(tester.widget<Text>(stepper).data, '9');

    await tester.tap(find.text('Reset'));
    await tester.pumpAndSettle();

    expect(tester.widget<Text>(stepper).data, '8');
  });

  testWidgets('mobile keeps the controls and hints at the swipe', (
    tester,
  ) async {
    await _pump(tester, size: const Size(390, 844));

    expect(find.text('Stock thresholds'), findsOneWidget);
    expect(find.text('Save changes'), findsOneWidget);
    expect(find.text('Swipe sideways to reach every column.'), findsOneWidget);
  });
}
