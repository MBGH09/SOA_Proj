import 'package:blood_network/app/theme/app_theme.dart';
import 'package:blood_network/core/providers/core_providers.dart';
import 'package:blood_network/core/time/clock.dart';
import 'package:blood_network/features/hospital/presentation/providers/hospital_providers.dart';
import 'package:blood_network/features/hospital/presentation/screens/new_request_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import '../support/fake_request_repository.dart';
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
    initialLocation: '/hospital/requests/new',
    routes: [
      GoRoute(
        path: '/hospital/requests/new',
        builder: (context, state) => const Scaffold(body: NewRequestScreen()),
      ),
      GoRoute(
        path: '/hospital/requests/:id',
        builder: (context, state) => Scaffold(
          body: Center(child: Text('detail ${state.pathParameters['id']}')),
        ),
      ),
    ],
  );

  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        clockProvider.overrideWithValue(
          FrozenClock(DateTime(2026, 11, 12, 10, 24)),
        ),
        requestRepositoryProvider.overrideWithValue(
          FakeRequestRepository(now: DateTime(2026, 11, 12, 10, 24)),
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
  testWidgets('web shows the form and the local stock side card', (
    tester,
  ) async {
    await _pump(tester);

    expect(find.text('New request'), findsOneWidget);
    expect(find.text('PATIENT REFERENCE'), findsOneWidget);
    expect(find.text('P20260442'), findsOneWidget);
    expect(
      find.text('An anonymous reference only — no patient name is stored.'),
      findsOneWidget,
    );
    expect(find.text('LOCAL STOCK · O NEGATIVE'), findsOneWidget);
    expect(find.text('2 available'), findsOneWidget);
    expect(
      find.text(
        'This request needs 3 units, so it will be supplied by the regional '
        'CRTS.',
      ),
      findsOneWidget,
    );
    expect(
      find.text(
        'The request is saved first, then stock is checked and reserved in '
        'one operation, so two hospitals can never take the same pouches.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('Critical explains the shorter delivery time', (tester) async {
    await _pump(tester);

    await tester.tap(find.text('Critical'));
    await tester.pumpAndSettle();

    expect(
      find.text(
        'Critical · this request is treated as an emergency and gets a '
        'shorter delivery time.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('Clear empties the form', (tester) async {
    await _pump(tester);

    await tester.tap(find.text('Clear'));
    await tester.pumpAndSettle();

    expect(find.text('P20260442'), findsNothing);
    final units = tester.widget<TextField>(find.byType(TextField).at(1));
    expect(units.controller?.text, '1');
    expect(find.text('LOCAL STOCK · O NEGATIVE'), findsOneWidget);
  });

  testWidgets('a dashed reference and zero units are refused before the call', (
    tester,
  ) async {
    await _pump(tester);

    await tester.enterText(find.byType(TextField).at(0), 'P-2026-0442');
    await tester.enterText(find.byType(TextField).at(1), '0');
    await tester.tap(find.text('Send request'));
    await tester.pumpAndSettle();

    expect(
      find.text('Use letters and digits only — no spaces, dashes or accents.'),
      findsOneWidget,
    );
    expect(find.text('A request needs at least one unit.'), findsOneWidget);
  });

  testWidgets('sending opens the new request', (tester) async {
    await _pump(tester);

    await tester.tap(find.text('Send request'));
    await tester.pumpAndSettle();

    expect(find.text('detail REQ-2026-0185'), findsOneWidget);
  });

  testWidgets('mobile keeps the form readable at 390', (tester) async {
    await _pump(tester, size: const Size(390, 844));

    expect(find.text('New request'), findsOneWidget);
    expect(find.text('PATIENT REFERENCE'), findsOneWidget);
    expect(find.text('Send request'), findsOneWidget);
    expect(find.text('LOCAL STOCK · O NEGATIVE'), findsOneWidget);
  });
}
