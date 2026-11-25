import 'package:blood_network/app/theme/app_theme.dart';
import 'package:blood_network/core/errors/api_error.dart';
import 'package:blood_network/data/models/slot.dart';
import 'package:blood_network/features/centre/presentation/screens/slot_management_screen.dart';
import 'package:blood_network/features/donor/presentation/providers/appointment_providers.dart';
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
    initialLocation: '/centre/slots',
    routes: [
      GoRoute(
        path: '/centre/slots',
        builder: (context, state) =>
            const Scaffold(body: SlotManagementScreen()),
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
  testWidgets('web shows the day, the slots and the day summary', (tester) async {
    await _pump(tester);

    expect(find.text('Slot management'), findsOneWidget);
    expect(find.text('Thu 12'), findsOneWidget);
    expect(find.text('Fri 13'), findsOneWidget);

    expect(find.text('09:00 – 10:00'), findsOneWidget);
    expect(find.text('6 / 6'), findsOneWidget);
    expect(find.text('Full'), findsOneWidget);
    expect(find.text('Open'), findsNWidgets(4));

    expect(find.text('CREATE SLOTS'), findsOneWidget);
    expect(find.text('Thursday 12 November 2026'), findsOneWidget);
    expect(find.text('Single'), findsOneWidget);
    expect(find.text('Daily'), findsOneWidget);
    expect(find.text('Weekly'), findsOneWidget);
    expect(find.text('Create slots'), findsOneWidget);

    expect(find.text('Places'), findsOneWidget);
    expect(find.text('30'), findsOneWidget);
    expect(find.text('Booked'), findsOneWidget);
    expect(find.text('15'), findsOneWidget);
    expect(find.text('50 %'), findsOneWidget);
  });

  testWidgets('another day changes the table and the summary', (tester) async {
    await _pump(tester);

    await tester.tap(find.text('Fri 13'));
    await tester.pumpAndSettle();

    expect(find.text('5 / 6'), findsOneWidget);
    expect(find.text('3 / 6'), findsOneWidget);
    expect(find.text('67 %'), findsOneWidget);
  });

  testWidgets('Create slots sends the form values', (tester) async {
    final repo = await _pump(tester);

    await tester.tap(find.text('Create slots'));
    await tester.pumpAndSettle();

    expect(repo.createCalls, 1);
    expect(repo.createdCapacity, 6);
    expect(repo.createdRepeat, SlotRepeat.single);
    expect(repo.createdFrom, '09:00');
    expect(repo.createdTo, '12:00');
    expect(find.text('Slots created for 09:00 – 12:00.'), findsOneWidget);
  });

  testWidgets('a slot that overlaps is reported in plain language', (tester) async {
    await _pump(
      tester,
      repository: FakeAppointmentRepository(
        createError: const ApiError(status: 409, message: 'SLOT_OVERLAP'),
      ),
    );

    await tester.tap(find.text('Create slots'));
    await tester.pumpAndSettle();

    expect(
      find.text('This is not possible right now. Refresh the page and try again.'),
      findsOneWidget,
    );
  });

  testWidgets('mobile stacks the table above the create form', (tester) async {
    await _pump(tester, size: const Size(390, 844));

    expect(find.text('Slot management'), findsOneWidget);
    expect(find.text('CREATE SLOTS'), findsOneWidget);
  });
}