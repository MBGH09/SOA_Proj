import 'package:blood_network/app/theme/app_theme.dart';
import 'package:blood_network/core/errors/api_error.dart';
import 'package:blood_network/features/donor/presentation/providers/certificate_providers.dart';
import 'package:blood_network/features/donor/presentation/screens/my_certificates_screen.dart';
import 'package:blood_network/shared/widgets/app_card.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

import '../support/fake_certificate_repository.dart';

typedef _Harness = ({
  FakeCertificateRepository repository,
  FakeCertificatePdfService pdf,
});

Future<_Harness> _pump(
  WidgetTester tester, {
  Size size = const Size(1440, 900),
  FakeCertificateRepository? repository,
  FakeCertificatePdfService? pdf,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  final repo = repository ?? FakeCertificateRepository();
  final service = pdf ?? FakeCertificatePdfService();

  final router = GoRouter(
    initialLocation: '/donor/certificates',
    routes: [
      GoRoute(
        path: '/donor/certificates',
        builder: (context, state) => const Scaffold(
          body: MyCertificatesScreen(),
        ),
      ),
      GoRoute(
        path: '/donor/verify',
        builder: (context, state) =>
            const Scaffold(body: Center(child: Text('verify a certificate'))),
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
      overrides: [
        certificateRepositoryProvider.overrideWithValue(repo),
        certificatePdfServiceProvider.overrideWithValue(service),
      ],
      child: MaterialApp.router(theme: AppTheme.light, routerConfig: router),
    ),
  );
  await tester.pumpAndSettle();

  return (repository: repo, pdf: service);
}

Finder _downloadIn(Finder card) => find.descendant(
  of: card,
  matching: find.widgetWithText(FilledButton, 'Download PDF'),
);

void main() {
  testWidgets('web lists an issued and a pending certificate', (tester) async {
    await _pump(tester);

    expect(find.text('My certificates'), findsOneWidget);
    expect(find.text('Donation certificate'), findsNWidgets(2));
    expect(find.text('Issued'), findsOneWidget);
    expect(find.text('Preparing'), findsOneWidget);
    expect(find.text('CERT-2026-000314'), findsOneWidget);
    expect(find.text('Number pending'), findsOneWidget);
    expect(find.text('NATIONAL DONATION N°'), findsOneWidget);
    expect(find.text('NDN-2026-118842'), findsOneWidget);
    expect(find.text('DONOR'), findsOneWidget);
    expect(find.text('Amira Ben Salah'), findsOneWidget);
    expect(find.text('CIN'), findsOneWidget);
    expect(find.text('08••••67'), findsOneWidget);
    expect(find.text('12 August 2026'), findsOneWidget);
    expect(find.text('CENTRE'), findsOneWidget);
    expect(find.text('VOLUME'), findsOneWidget);
    expect(find.text('450 ml'), findsOneWidget);
    expect(
      find.text(
        'Contains no health data: no blood pressure, haemoglobin or '
        'interview decision.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('the pending card explains itself and cannot be downloaded', (
    tester,
  ) async {
    await _pump(tester);

    final pendingCard = find.ancestor(
      of: find.text('Number pending'),
      matching: find.byType(AppCard),
    );

    expect(
      find.descendant(
        of: pendingCard,
        matching: find.text(
          'The retry job issues pending certificates automatically. A refused '
          'visit gets none.',
        ),
      ),
      findsOneWidget,
    );
    expect(
      tester.widget<FilledButton>(_downloadIn(pendingCard)).onPressed,
      isNull,
    );
  });

  testWidgets('Download PDF on an issued certificate asks for the file', (
    tester,
  ) async {
    final harness = await _pump(tester);

    final issuedCard = find.ancestor(
      of: find.text('CERT-2026-000314'),
      matching: find.byType(AppCard),
    );
    await tester.ensureVisible(_downloadIn(issuedCard));
    await tester.tap(_downloadIn(issuedCard));
    await tester.pumpAndSettle();

    expect(harness.pdf.downloadCalls, 1);
    expect(harness.pdf.printCalls, 0);
    expect(
      find.text('Requested CERT-2026-000314.pdf.'),
      findsOneWidget,
    );
  });

  testWidgets('Print on a pending certificate says there is no file yet', (
    tester,
  ) async {
    final harness = await _pump(tester);

    final pendingCard = find.ancestor(
      of: find.text('Number pending'),
      matching: find.byType(AppCard),
    );
    final print = find.descendant(
      of: pendingCard,
      matching: find.widgetWithText(OutlinedButton, 'Print'),
    );
    await tester.tap(print);
    await tester.pumpAndSettle();

    expect(harness.pdf.printCalls, 0);
    expect(
      find.textContaining('There is no file yet'),
      findsOneWidget,
    );
  });

  testWidgets('Verify a certificate opens the public check', (tester) async {
    await _pump(tester);

    await tester.tap(find.text('Verify a certificate'));
    await tester.pumpAndSettle();

    expect(find.text('verify a certificate'), findsOneWidget);
  });

  testWidgets('no certificate invites the next donation', (tester) async {
    await _pump(
      tester,
      repository: FakeCertificateRepository(mine: const []),
    );

    expect(find.text('No certificate yet'), findsOneWidget);

    await tester.tap(find.text('Book a slot'));
    await tester.pumpAndSettle();

    expect(find.text('book a slot'), findsOneWidget);
  });

  testWidgets('a service that is down is reported in plain language', (
    tester,
  ) async {
    await _pump(
      tester,
      repository: FakeCertificateRepository(
        mineError: const ApiError(status: 503, message: 'UNAVAILABLE'),
      ),
    );

    expect(find.text('We could not load your certificates'), findsOneWidget);
    expect(
      find.text('The service is busy. Please try again in a moment.'),
      findsOneWidget,
    );
  });

  testWidgets('mobile stacks the cards and shortens the pending note', (
    tester,
  ) async {
    await _pump(tester, size: const Size(390, 844));

    expect(find.text('Donation certificate'), findsNWidgets(2));
    expect(
      find.text(
        'A refused visit gets no certificate. The retry job issues pending '
        'certificates automatically.',
      ),
      findsOneWidget,
    );
  });
}
