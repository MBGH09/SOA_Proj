import 'package:blood_network/app/theme/app_theme.dart';
import 'package:blood_network/features/donor/presentation/providers/appointment_providers.dart';
import 'package:blood_network/features/donor/presentation/screens/my_appointments_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import '../support/fake_appointment_repository.dart';

Future<FakeAppointmentRepository> _pump(
  WidgetTester tester, {
  Size size = const Size(1440, 900),
  FakeAppointmentRepository? repository,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  final repo = repository ?? FakeAppointmentRepository();
  final router = GoRouter(
    initialLocation: '/donor/appointments',
    routes: [
      GoRoute(
        path: '/donor/appointments',
        builder: (context, state) =>
            const Scaffold(body: MyAppointmentsScreen()),
      ),
      GoRoute(
        path: '/donor/appointments/move',
        builder: (context, state) =>
            const Scaffold(body: Center(child: Text('move appointment'))),
      ),
      GoRoute(
        path: '/donor/certificates',
        builder: (context, state) =>
            const Scaffold(body: Center(child: Text('certificates'))),
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
      overrides: [appointmentRepositoryProvider.overrideWithValue(repo)],
      child: MaterialApp.router(theme: AppTheme.light, routerConfig: router),
    ),
  );
  await tester.pumpAndSettle();
  return repo;
}

void main() {
  testWidgets('web lists the appointment, summary and rules', (tester) async {
    await _pump(tester);

    expect(find.text('My appointments'), findsOneWidget);
    expect(find.text('CRTS Sfax'), findsNWidgets(2));
    expect(find.text('Thursday 12 November · 10:30'), findsOneWidget);
    expect(find.text('Planned'), findsOneWidget);
    expect(
      find.text(
        'Whole blood · 450 ml · reference RDV-2026-0442 · booked online',
      ),
      findsOneWidget,
    );
    expect(find.text('Move'), findsOneWidget);
    expect(find.text('Cancel'), findsOneWidget);
    expect(find.text('PAST VISITS'), findsOneWidget);
    expect(find.text('SUMMARY'), findsOneWidget);
    expect(find.text('Total donations'), findsOneWidget);
    expect(find.text('7'), findsOneWidget);
    expect(find.text('12 Aug 2026'), findsOneWidget);
    expect(find.text('Thu 12 Nov · 10:30'), findsOneWidget);
    expect(find.text('None'), findsOneWidget);
    expect(find.text('View certificate'), findsOneWidget);
    expect(find.text('RULES'), findsOneWidget);
  });

  testWidgets('cancelling asks first, then removes the appointment', (
    tester,
  ) async {
    final repo = await _pump(tester);

    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();

    expect(find.text('Cancel this appointment?'), findsOneWidget);
    expect(repo.cancelCalls, 0);

    await tester.tap(find.text('Cancel appointment'));
    await tester.pumpAndSettle();

    expect(repo.cancelCalls, 1);
    expect(repo.cancelledId, 'RDV-2026-0442');
  });

  testWidgets('Move opens the move screen with the appointment id', (
    tester,
  ) async {
    await _pump(tester);

    await tester.tap(find.text('Move'));
    await tester.pumpAndSettle();

    expect(find.text('move appointment'), findsOneWidget);
  });

  testWidgets('View certificate opens the certificates screen', (
    tester,
  ) async {
    await _pump(tester);

    await tester.tap(find.text('View certificate'));
    await tester.pumpAndSettle();

    expect(find.text('certificates'), findsOneWidget);
  });

  testWidgets('mobile filters between upcoming and past', (tester) async {
    await _pump(tester, size: const Size(390, 844));

    expect(find.text('Upcoming'), findsOneWidget);
    expect(find.text('Past'), findsOneWidget);
    expect(find.text('CRTS Sfax'), findsOneWidget);

    await tester.tap(find.text('Past'));
    await tester.pumpAndSettle();

    expect(find.text('CRTS Sfax'), findsNothing);
    expect(find.text('CRTS Sfax — 12 August 2026'), findsOneWidget);
  });

  testWidgets('an empty list invites booking', (tester) async {
    await _pump(
      tester,
      repository: FakeAppointmentRepository(upcoming: const [], past: const []),
    );

    expect(find.text('No appointment booked'), findsOneWidget);
    expect(find.text('Book a slot'), findsOneWidget);

    await tester.tap(find.text('Book a slot'));
    await tester.pumpAndSettle();

    expect(find.text('book a slot'), findsOneWidget);
  });
}
