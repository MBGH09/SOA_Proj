import 'package:blood_network/app/theme/app_theme.dart';
import 'package:blood_network/core/providers/core_providers.dart';
import 'package:blood_network/core/time/clock.dart';
import 'package:blood_network/features/hospital/presentation/providers/hospital_providers.dart';
import 'package:blood_network/features/hospital/presentation/screens/request_detail_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import '../support/fake_request_repository.dart';

Future<void> _pump(
  WidgetTester tester, {
  Size size = const Size(1440, 1000),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  final router = GoRouter(
    initialLocation: '/hospital/requests/REQ-2026-0184',
    routes: [
      GoRoute(
        path: '/hospital/requests/:id',
        builder: (context, state) => Scaffold(
          body: RequestDetailScreen(id: state.pathParameters['id'] ?? ''),
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
      ],
      child: MaterialApp.router(theme: AppTheme.light, routerConfig: router),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('web shows the reservation, order, delivery and compensation', (
    tester,
  ) async {
    await _pump(tester);

    expect(find.text('Request REQ-2026-0184'), findsOneWidget);
    expect(
      find.text(
        'Red cells · O negative · 3 units · Orthopaedic surgery · '
        'created today at 08:40.',
      ),
      findsOneWidget,
    );
    expect(find.text('RESERVATION'), findsOneWidget);
    expect(find.text('Regional centre · CRTS Sfax'), findsOneWidget);
    expect(find.text('RES-2026-0512'), findsOneWidget);
    expect(find.text('Order placed'), findsOneWidget);

    expect(find.text('ORDER'), findsOneWidget);
    expect(find.text('Compensation'), findsNothing);
    expect(find.text('Patient reference'), findsOneWidget);
    expect(find.text('P20260442 (anonymous)'), findsOneWidget);

    expect(find.text('DELIVERY'), findsOneWidget);
    expect(find.text('52 minutes'), findsOneWidget);
    expect(find.text('TRK-2026-8841'), findsOneWidget);
    expect(find.text('Reserved'), findsNWidgets(2));
    expect(find.text('In transit'), findsOneWidget);
    expect(find.text('Delivered'), findsWidgets);

    expect(find.text('COMPENSATION'), findsOneWidget);
    expect(find.text('2 of 3'), findsOneWidget);
    expect(find.text('CERT-2026-000314'), findsOneWidget);
    expect(find.text('Valid'), findsNWidgets(2));
  });

  testWidgets('a certificate is refused twice (R26)', (tester) async {
    await _pump(tester);

    await tester.enterText(find.byType(TextField).first, 'CERT-2026-000314');
    await tester.tap(find.text('Add certificate'));
    await tester.pumpAndSettle();

    expect(
      find.text('This certificate is already counted. A number counts once.'),
      findsOneWidget,
    );
    expect(find.text('2 of 3'), findsOneWidget);
  });

  testWidgets('a new certificate completes the compensation', (tester) async {
    await _pump(tester);

    await tester.enterText(find.byType(TextField).first, 'CERT-2026-000999');
    await tester.tap(find.text('Add certificate'));
    await tester.pumpAndSettle();

    expect(find.text('3 of 3'), findsOneWidget);
    expect(find.text('CERT-2026-000999'), findsOneWidget);
    expect(find.text('Valid'), findsNWidgets(3));
  });

  testWidgets('the counter can be corrected by hand', (tester) async {
    await _pump(tester);

    await tester.tap(find.text('Correct the number by hand'));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField).last, '1');
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    expect(find.text('1 of 3'), findsOneWidget);
  });

  testWidgets('mobile stacks the sections at 390', (tester) async {
    await _pump(tester, size: const Size(390, 844));

    expect(find.text('Request REQ-2026-0184'), findsOneWidget);
    expect(find.text('RESERVATION'), findsOneWidget);
    expect(find.text('ORDER'), findsOneWidget);
    expect(find.text('COMPENSATION'), findsOneWidget);
  });
}
