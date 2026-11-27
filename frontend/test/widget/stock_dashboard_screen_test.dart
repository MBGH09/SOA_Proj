import 'package:blood_network/app/theme/app_theme.dart';
import 'package:blood_network/core/providers/core_providers.dart';
import 'package:blood_network/core/time/clock.dart';
import 'package:blood_network/features/hospital/presentation/providers/hospital_providers.dart';
import 'package:blood_network/features/hospital/presentation/screens/stock_dashboard_screen.dart';
import 'package:blood_network/shared/widgets/app_chip.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import '../support/fake_stock_repository.dart';

Future<void> _pump(
  WidgetTester tester, {
  Size size = const Size(1440, 1000),
  String query = '',
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  final router = GoRouter(
    initialLocation: '/hospital/stock$query',
    routes: [
      GoRoute(
        path: '/hospital/stock',
        builder: (context, state) =>
            const Scaffold(body: StockDashboardScreen()),
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
  testWidgets('web shows the figures, legend, board and low list', (
    tester,
  ) async {
    await _pump(tester);

    expect(find.text('Stock'), findsOneWidget);
    expect(find.text('UNITS AVAILABLE'), findsOneWidget);
    expect(find.text('RESERVED'), findsOneWidget);
    expect(find.text('IN QUALIFICATION'), findsOneWidget);
    expect(find.text('BELOW THRESHOLD'), findsOneWidget);
    expect(find.text('214'), findsOneWidget);
    expect(find.text('38'), findsOneWidget);
    expect(find.text('22'), findsWidgets);
    expect(find.text('At or above threshold'), findsOneWidget);
    expect(find.text('Near threshold'), findsOneWidget);
    expect(find.text('O negative'), findsOneWidget);
    expect(find.text('AB positive'), findsOneWidget);
    expect(find.text('O negative · Red cells'), findsOneWidget);
    expect(
      find.text('BELOW THRESHOLD · ORDER OR ASK FOR A COLLECTION'),
      findsOneWidget,
    );
    expect(find.text('2 of 8'), findsOneWidget);
  });

  testWidgets('another place reloads the board', (tester) async {
    await _pump(tester);

    await tester.tap(find.widgetWithText(AppChip, 'CRTS Sfax'));
    await tester.pumpAndSettle();

    expect(find.text('368'), findsOneWidget);
    expect(
      find.text('Every group and component is at or above its threshold.'),
      findsOneWidget,
    );
  });

  testWidgets('an unavailable stock service shows a retry', (tester) async {
    await _pump(tester, query: '?state=unavailable');

    expect(find.text('We could not load the stock'), findsOneWidget);
    expect(find.text('Try again'), findsOneWidget);
  });

  testWidgets('mobile keeps the board and hints at the swipe', (tester) async {
    await _pump(tester, size: const Size(390, 844));

    expect(find.text('Stock'), findsOneWidget);
    expect(find.text('Swipe sideways to see every component.'), findsOneWidget);
    expect(find.text('O negative'), findsOneWidget);
  });
}
