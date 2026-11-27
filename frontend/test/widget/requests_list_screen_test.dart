import 'package:blood_network/app/theme/app_theme.dart';
import 'package:blood_network/core/providers/core_providers.dart';
import 'package:blood_network/core/time/clock.dart';
import 'package:blood_network/features/hospital/presentation/providers/hospital_providers.dart';
import 'package:blood_network/features/hospital/presentation/screens/requests_list_screen.dart';
import 'package:blood_network/shared/widgets/app_chip.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import '../support/fake_request_repository.dart';

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
    initialLocation: '/hospital/requests$query',
    routes: [
      GoRoute(
        path: '/hospital/requests',
        builder: (context, state) => const Scaffold(body: RequestsListScreen()),
      ),
      GoRoute(
        path: '/hospital/requests/new',
        builder: (context, state) =>
            const Scaffold(body: Center(child: Text('new request'))),
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
      ],
      child: MaterialApp.router(theme: AppTheme.light, routerConfig: router),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('web lists the requests with the four figures', (tester) async {
    await _pump(tester);

    expect(find.text('Requests'), findsOneWidget);
    expect(find.text('OPEN REQUESTS'), findsOneWidget);
    expect(find.text('IN DELIVERY'), findsOneWidget);
    expect(find.text('DELIVERED THIS WEEK'), findsOneWidget);
    expect(find.text('UNITS NEEDED TODAY'), findsOneWidget);
    expect(find.text('3'), findsWidgets);
    expect(find.text('12'), findsOneWidget);
    expect(find.text('7'), findsOneWidget);

    expect(find.text('REFERENCE'), findsOneWidget);
    expect(find.text('REQ-2026-0184'), findsOneWidget);
    expect(find.text('Red cells · O negative'), findsOneWidget);
    expect(find.text('In delivery'), findsWidgets);
    expect(find.text('12 min ago'), findsOneWidget);
    expect(find.text('Yesterday'), findsNWidgets(2));
  });

  testWidgets('Critical keeps only critical requests', (tester) async {
    await _pump(tester);

    await tester.tap(find.widgetWithText(AppChip, 'Critical'));
    await tester.pumpAndSettle();

    expect(find.text('REQ-2026-0184'), findsOneWidget);
    expect(find.text('REQ-2026-0179'), findsOneWidget);
    expect(find.text('REQ-2026-0181'), findsNothing);
    expect(find.text('REQ-2026-0166'), findsNothing);
  });

  testWidgets('search finds a reference', (tester) async {
    await _pump(tester);

    await tester.enterText(find.byType(TextField), '0171');
    await tester.pumpAndSettle();

    expect(find.text('REQ-2026-0171'), findsOneWidget);
    expect(find.text('REQ-2026-0184'), findsNothing);
  });

  testWidgets('an empty result offers to clear the filters', (tester) async {
    await _pump(tester);

    await tester.enterText(find.byType(TextField), 'nothing-matches');
    await tester.pumpAndSettle();

    expect(find.text('No request matches this filter'), findsOneWidget);
    expect(find.text('Clear filters'), findsOneWidget);
  });

  testWidgets('a row opens the request detail', (tester) async {
    await _pump(tester);

    await tester.tap(find.text('REQ-2026-0184'));
    await tester.pumpAndSettle();

    expect(find.text('detail REQ-2026-0184'), findsOneWidget);
  });

  testWidgets('mobile lists the requests as cards', (tester) async {
    await _pump(tester, size: const Size(390, 844));

    expect(find.text('Requests'), findsOneWidget);
    expect(find.text('REQ-2026-0184'), findsOneWidget);
    expect(find.text('Red cells · O negative'), findsOneWidget);
    expect(find.text('REFERENCE'), findsNothing);
  });
}
