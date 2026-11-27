import 'package:blood_network/app/theme/app_theme.dart';
import 'package:blood_network/core/errors/api_error.dart';
import 'package:blood_network/features/donor/presentation/providers/certificate_providers.dart';
import 'package:blood_network/features/donor/presentation/screens/verify_certificate_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import '../support/fake_certificate_repository.dart';

Future<FakeCertificateRepository> _pump(
  WidgetTester tester, {
  Size size = const Size(1440, 900),
  FakeCertificateRepository? repository,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  final repo = repository ?? FakeCertificateRepository();

  final router = GoRouter(
    initialLocation: '/donor/verify',
    routes: [
      GoRoute(
        path: '/donor/verify',
        builder: (context, state) =>
            const Scaffold(body: VerifyCertificateScreen()),
      ),
    ],
  );

  await tester.pumpWidget(
    ProviderScope(
      overrides: [certificateRepositoryProvider.overrideWithValue(repo)],
      child: MaterialApp.router(theme: AppTheme.light, routerConfig: router),
    ),
  );
  await tester.pumpAndSettle();
  return repo;
}

Finder get _verifyButton =>
    find.widgetWithText(FilledButton, 'Verify certificate');

Future<void> _check(WidgetTester tester, String numero) async {
  await tester.enterText(find.byType(TextField), numero);
  await tester.pump();
  await tester.tap(_verifyButton);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('an empty field keeps the verify button disabled', (
    tester,
  ) async {
    await _pump(tester);

    expect(find.text('No input'), findsOneWidget);
    expect(find.text('Waiting for a number'), findsOneWidget);
    expect(
      find.text(
        'No number entered. The verify button stays disabled until the field '
        'has a value.',
      ),
      findsOneWidget,
    );
    expect(tester.widget<FilledButton>(_verifyButton).onPressed, isNull);
  });

  testWidgets('a valid number answers with the date and the centre', (
    tester,
  ) async {
    final repo = await _pump(tester);

    await _check(tester, 'CERT-2026-000314');

    expect(repo.verifyCalls, 1);
    expect(repo.lastNumero, 'CERT-2026-000314');
    expect(find.text('Valid'), findsOneWidget);
    expect(find.text('DONATION DATE'), findsOneWidget);
    expect(find.text('12 August 2026'), findsOneWidget);
    expect(find.text('CRTS Sfax'), findsOneWidget);
    expect(find.text('ISSUING AUTHORITY'), findsOneWidget);
    expect(find.text('CNTS'), findsOneWidget);
    expect(
      find.text('No personal or health data is returned.'),
      findsOneWidget,
    );
  });

  testWidgets('an unknown number is reported on the field and in the answer', (
    tester,
  ) async {
    await _pump(
      tester,
      repository: FakeCertificateRepository(
        verifyError: const ApiError(
          status: 404,
          message: 'CERTIFICATE_NOT_FOUND',
        ),
      ),
    );

    await _check(tester, 'CERT-2025-999999');

    expect(find.text('Not found'), findsOneWidget);
    expect(
      find.text('No certificate found with this number.'),
      findsOneWidget,
    );
    expect(
      find.textContaining('No certificate matches this number.'),
      findsOneWidget,
    );
  });

  testWidgets('a number that cannot be counted is answered as not valid', (
    tester,
  ) async {
    await _pump(
      tester,
      repository: FakeCertificateRepository(
        verification: sampleVerification(
          valid: false,
          date: DateTime(2026, 6, 3),
          centre: 'CHU Habib Bourguiba',
        ),
      ),
    );

    await _check(tester, 'CERT-2026-000201');

    expect(find.text('Not valid'), findsOneWidget);
    expect(find.text('3 June 2026'), findsOneWidget);
    expect(
      find.textContaining('cannot be counted for compensation'),
      findsOneWidget,
    );
  });

  testWidgets('a failing service offers a retry instead of a wrong answer', (
    tester,
  ) async {
    await _pump(
      tester,
      repository: FakeCertificateRepository(
        verifyError: const ApiError(status: 503, message: 'UNAVAILABLE'),
      ),
    );

    await _check(tester, 'CERT-2026-000314');

    expect(find.text('We could not check this certificate'), findsOneWidget);
    expect(
      find.text('The service is busy. Please try again in a moment.'),
      findsOneWidget,
    );
  });

  testWidgets('Clear empties the field and the answer', (tester) async {
    await _pump(tester);

    await _check(tester, 'CERT-2026-000314');
    expect(find.text('Valid'), findsOneWidget);

    await tester.tap(find.text('Clear'));
    await tester.pumpAndSettle();

    expect(find.text('Valid'), findsNothing);
    expect(find.text('No input'), findsOneWidget);
  });

  testWidgets('mobile keeps the answer to date and centre', (tester) async {
    await _pump(tester, size: const Size(390, 844));

    await _check(tester, 'CERT-2026-000314');

    expect(find.text('Valid'), findsOneWidget);
    expect(find.text('12 August 2026'), findsOneWidget);
    expect(find.text('CRTS Sfax'), findsOneWidget);
    expect(find.text('ISSUING AUTHORITY'), findsNothing);
    expect(find.text('Clear'), findsNothing);
  });
}
