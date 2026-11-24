import 'package:blood_network/app/theme/app_theme.dart';
import 'package:blood_network/features/donor/presentation/providers/appointment_providers.dart';
import 'package:blood_network/features/donor/presentation/screens/move_appointment_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import '../support/fake_appointment_repository.dart';

Future<FakeAppointmentRepository> _pump(
  WidgetTester tester, {
  Size size = const Size(1440, 900),
  String location = '/donor/appointments/move?id=RDV-2026-0442',
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
        path: '/donor/appointments/move',
        builder: (context, state) =>
            const Scaffold(body: MoveAppointmentScreen()),
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
  testWidgets('web shows the current booking, rules and slots', (tester) async {
    await _pump(tester);

    expect(find.text('Move your appointment'), findsOneWidget);
    expect(find.text('CURRENTLY BOOKED'), findsOneWidget);
    expect(find.text('Thursday 12 November · 10:30'), findsOneWidget);
    expect(
      find.text('Whole blood · 450 ml · reference RDV-2026-0442'),
      findsOneWidget,
    );
    expect(find.text('BEFORE YOU CONFIRM'), findsOneWidget);
    expect(find.text('1 · Day'), findsOneWidget);
    expect(find.text('DAY'), findsOneWidget);
    expect(find.text('Thu 12'), findsOneWidget);
    expect(find.text('TIME · THU 12 NOV'), findsOneWidget);
    expect(find.text('Keep current'), findsOneWidget);
    expect(find.text('Move here'), findsOneWidget);
    expect(
      find.text(
        'The move is applied immediately. The old slot is released for other '
        'donors.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('Move here moves the appointment to the chosen slot', (
    tester,
  ) async {
    final repo = await _pump(tester);

    await tester.tap(find.text('Move here'));
    await tester.pumpAndSettle();

    expect(repo.moveCalls, 1);
    expect(repo.movedAt, DateTime(2026, 11, 12, 9, 0));
    expect(find.text('my appointments'), findsOneWidget);
  });

  testWidgets('Keep current returns without moving', (tester) async {
    final repo = await _pump(tester);

    await tester.tap(find.text('Keep current'));
    await tester.pumpAndSettle();

    expect(repo.moveCalls, 0);
    expect(find.text('my appointments'), findsOneWidget);
  });

  testWidgets('mobile shows the compact card and the rule footnote', (
    tester,
  ) async {
    await _pump(tester, size: const Size(390, 844));

    expect(find.text('BEFORE YOU CONFIRM'), findsNothing);
    expect(find.text('Thu 12 Nov · 10:30'), findsOneWidget);
    expect(find.text('TIME'), findsOneWidget);
    expect(
      find.text(
        'One active appointment per day. Moving an appointment keeps your '
        'place in the appeal queue.',
      ),
      findsOneWidget,
    );
    expect(find.text('Move here'), findsOneWidget);
  });
}
