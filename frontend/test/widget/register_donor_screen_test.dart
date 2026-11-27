import 'package:blood_network/app/theme/app_theme.dart';
import 'package:blood_network/core/errors/api_error.dart';
import 'package:blood_network/features/donor/presentation/providers/donor_providers.dart';
import 'package:blood_network/features/donor/presentation/screens/register_donor_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import '../support/fake_donor_repository.dart';

Future<FakeDonorRepository> _pump(
  WidgetTester tester, {
  ApiError? registerError,
  Size size = const Size(1440, 900),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  final repository = FakeDonorRepository(registerError: registerError);
  final router = GoRouter(
    initialLocation: '/donor/register',
    routes: [
      GoRoute(
        path: '/donor/register',
        builder: (context, state) =>
            const Scaffold(body: RegisterDonorScreen()),
      ),
      GoRoute(
        path: '/donor/eligibility',
        builder: (context, state) =>
            const Scaffold(body: Center(child: Text('check eligibility'))),
      ),
    ],
  );

  await tester.pumpWidget(
    ProviderScope(
      overrides: [donorRepositoryProvider.overrideWithValue(repository)],
      child: MaterialApp.router(theme: AppTheme.light, routerConfig: router),
    ),
  );
  await tester.pumpAndSettle();
  return repository;
}

/// Fills every field with valid values (date via the picker, group via the menu).
Future<void> _fillValidForm(WidgetTester tester, {String cin = '09123456'}) async {
  final fields = find.byType(TextField);
  await tester.enterText(fields.at(0), 'Amira Ben Salah');
  await tester.enterText(fields.at(1), cin);

  await tester.tap(fields.at(2)); // date of birth opens the picker
  await tester.pumpAndSettle();
  await tester.tap(find.text('OK'));
  await tester.pumpAndSettle();

  await tester.enterText(find.byType(TextField).at(3), '62');
  await tester.enterText(find.byType(TextField).at(4), 'Sfax');
  await tester.enterText(find.byType(TextField).at(5), '92 445 210');

  await tester.tap(find.text('Select')); // blood group menu
  await tester.pumpAndSettle();
  await tester.tap(find.text('O+'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('an empty form reports every missing field', (tester) async {
    await _pump(tester);

    await tester.tap(find.text('Create account'));
    await tester.pumpAndSettle();

    expect(find.text('Enter your full name.'), findsOneWidget);
    expect(find.text('Enter your ID card number.'), findsOneWidget);
    expect(find.text('Choose your date of birth.'), findsOneWidget);
    expect(find.text('Enter your weight.'), findsOneWidget);
    expect(find.text('Choose your blood group.'), findsOneWidget);
    expect(find.text('Enter your city.'), findsOneWidget);
    expect(find.text('Enter your phone number.'), findsOneWidget);
  });

  testWidgets('a duplicate CIN is reported on the field', (tester) async {
    await _pump(
      tester,
      registerError: const ApiError(
        status: 409,
        message: 'CIN_ALREADY_EXISTS',
        fieldErrors: {'cin': 'An account already exists with this ID number.'},
      ),
    );

    await _fillValidForm(tester, cin: '12345678');
    await tester.tap(find.text('Create account'));
    await tester.pumpAndSettle();

    expect(
      find.text('An account already exists with this ID number.'),
      findsOneWidget,
    );

    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();
  });

  testWidgets('a valid form registers and moves to eligibility', (
    tester,
  ) async {
    final repository = await _pump(tester);

    await _fillValidForm(tester);
    await tester.tap(find.text('Create account'));
    await tester.pumpAndSettle();

    expect(repository.registerCalls, 1);
    expect(find.text('check eligibility'), findsOneWidget);

    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();
  });

  testWidgets('mobile pins the action under the form', (tester) async {
    await _pump(tester, size: const Size(390, 844));

    expect(find.text('Create account'), findsOneWidget);
    expect(find.text('WHAT HAPPENS NEXT'), findsNothing);
  });
}
