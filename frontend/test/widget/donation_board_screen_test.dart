import 'package:blood_network/app/theme/app_theme.dart';
import 'package:blood_network/data/models/centre_day.dart';
import 'package:blood_network/features/centre/presentation/providers/centre_providers.dart';
import 'package:blood_network/features/centre/presentation/screens/donation_board_screen.dart';
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
    initialLocation: '/centre/board',
    routes: [
      GoRoute(
        path: '/centre/board',
        builder: (context, state) => const Scaffold(body: DonationBoardScreen()),
      ),
      GoRoute(
        path: '/centre/visits/:id',
        builder: (context, state) => Scaffold(
          body: Center(child: Text('visit ${state.pathParameters['id']}')),
        ),
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
  testWidgets('web shows the five steps with their cards', (tester) async {
    await _pump(tester);

    expect(find.text('Donation board'), findsOneWidget);
    expect(find.text('Reception'), findsOneWidget);
    expect(find.text('Interview'), findsOneWidget);
    expect(find.text('Collection'), findsOneWidget);
    expect(find.text('Rest'), findsOneWidget);
    expect(find.text('Done'), findsOneWidget);

    expect(find.text('Reception · CIN + questionnaire'), findsOneWidget);
    expect(find.text('Nurse · 450 ml + national number'), findsOneWidget);
    expect(find.text('Visit closed · certificate queued'), findsOneWidget);

    expect(find.text('D-2026-1189'), findsNWidgets(5));
    expect(find.text('cert. issued'), findsOneWidget);
    expect(find.text('12 min left'), findsOneWidget);
  });

  testWidgets('a card opens the visit', (tester) async {
    await _pump(tester);

    await tester.tap(find.text('D-2026-1189').first);
    await tester.pumpAndSettle();

    expect(find.text('visit D-2026-1189'), findsOneWidget);
  });

  testWidgets('an empty board says so in every column', (tester) async {
    final empty = sampleBoardColumns()
        .map((column) => BoardColumn(step: column.step, visits: const []))
        .toList(growable: false);

    await _pump(tester, repository: FakeVisitRepository(board: empty));

    expect(find.text('No visit'), findsNWidgets(5));
  });

  testWidgets('mobile scrolls the board horizontally', (tester) async {
    await _pump(tester, size: const Size(390, 844));

    expect(find.text('Reception'), findsOneWidget);
    expect(find.text('Every open visit by step. Steps always run in order.'), findsOneWidget);
  });
}