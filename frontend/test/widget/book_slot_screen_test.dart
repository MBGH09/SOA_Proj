import 'package:blood_network/app/theme/app_theme.dart';
import 'package:blood_network/features/donor/presentation/providers/appointment_providers.dart';
import 'package:blood_network/features/donor/presentation/screens/book_slot_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import '../support/fake_appointment_repository.dart';

Future<FakeAppointmentRepository> _pump(
  WidgetTester tester, {
  Size size = const Size(1440, 900),
  String location = '/donor/book',
  FakeAppointmentRepository? repository,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  final repo = repository ?? FakeAppointmentRepository();
  final router = GoRouter(
    initialLocation: location,
    routes: [
      GoRoute(
        path: '/donor/book',
        builder: (context, state) => const Scaffold(body: BookSlotScreen()),
      ),
      GoRoute(
        path: '/donor/appointments',
        builder: (context, state) =>
            const Scaffold(body: Center(child: Text('my appointments'))),
      ),
    ],
  );

  await tester.pumpWidget(
    ProviderScope(
      overrides: [appointmentRepositoryProvider.overrideWithValue(repo)],
      child: MaterialApp.router(theme: AppTheme.light, routerConfig: router),
    ),
  );
  await tester.pumpAndSettle();
  return repo;
}

void main() {
  testWidgets('web shows the centre, day and time steps from the design', (
    tester,
  ) async {
    await _pump(tester);

    expect(find.text('Book a slot'), findsOneWidget);
    expect(find.text('1 · Centre'), findsOneWidget);
    expect(find.text('2 · Time'), findsOneWidget);
    expect(find.text('3 · Confirm'), findsOneWidget);
    expect(find.text('CENTRE'), findsOneWidget);
    expect(
      find.text('CRTS Sfax — 9 Avenue Habib Bourguiba'),
      findsOneWidget,
    );
    expect(find.text('Open today until 15:00 · distance 0.8 km'), findsOneWidget);
    expect(find.text('DAY'), findsOneWidget);
    expect(find.text('Thu 12'), findsOneWidget);
    expect(find.text('TIME'), findsOneWidget);
    expect(find.text('14:00 · 5 left'), findsOneWidget);
    expect(find.text('Change centre'), findsOneWidget);
    expect(find.text('Confirm booking'), findsOneWidget);
  });

  testWidgets('picking another day shows that day\'s slots', (tester) async {
    await _pump(tester);

    // Thursday has no 10:30 slot; Wednesday does.
    expect(find.text('10:30 · Full'), findsNothing);

    await tester.tap(find.text('Wed 11'));
    await tester.pumpAndSettle();

    expect(find.text('10:30 · Full'), findsOneWidget);
  });

  testWidgets('Change centre returns to the centre step', (tester) async {
    await _pump(tester);

    await tester.tap(find.text('Change centre'));
    await tester.pumpAndSettle();

    expect(find.text('CRTS Sfax'), findsOneWidget);
    expect(find.text('Continue'), findsOneWidget);
    expect(find.text('Confirm booking'), findsNothing);
  });

  testWidgets('confirm shows a summary then books the slot', (tester) async {
    final repo = await _pump(tester);

    await tester.tap(find.text('Confirm booking'));
    await tester.pumpAndSettle();

    expect(find.text('CHECK YOUR BOOKING'), findsOneWidget);
    expect(find.text('Thursday 12 November'), findsOneWidget);
    expect(find.text('14:00'), findsOneWidget);

    await tester.tap(find.text('Book this slot'));
    await tester.pumpAndSettle();

    expect(repo.bookCalls, 1);
    expect(repo.bookedAt, DateTime(2026, 11, 12, 14, 0));
    expect(find.text('my appointments'), findsOneWidget);
  });

  testWidgets('the demo state is forwarded to the service', (tester) async {
    final repo = await _pump(tester, location: '/donor/book?state=slot-full');

    await tester.tap(find.text('Confirm booking'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Book this slot'));
    await tester.pumpAndSettle();

    expect(repo.bookVariant, 'slot-full');
  });

  testWidgets('mobile uses the short labels and drops the footnote', (
    tester,
  ) async {
    await _pump(tester, size: const Size(390, 844));

    expect(find.text('Confirm'), findsOneWidget);
    expect(
      find.text(
        'Eligibility is re-checked at the centre. Bring your National ID card '
        '(CIN) and donation card.',
      ),
      findsNothing,
    );
    expect(find.text('Change centre'), findsOneWidget);
  });
}
