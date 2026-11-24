import 'package:blood_network/app/theme/app_theme.dart';
import 'package:blood_network/shared/layouts/app_shell.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

Future<void> _pumpShell(
  WidgetTester tester,
  String location,
  Size size,
) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  await tester.pumpWidget(
    ProviderScope(
      child: MaterialApp(
        theme: AppTheme.light,
        home: AppShell(location: location, child: const SizedBox.expand()),
      ),
    ),
  );
  await tester.pump();
}

void main() {
  testWidgets('web shell draws the rail of the route role', (tester) async {
    await _pumpShell(tester, '/centre/board', const Size(1440, 900));

    expect(find.text('Today'), findsOneWidget);
    expect(find.text('Board'), findsOneWidget);
    expect(find.text('Deferrals'), findsOneWidget);
    expect(find.text('Appeals'), findsNothing);
    expect(find.byType(NavigationBar), findsNothing);
  });

  testWidgets('mobile shell draws the bottom bar of the route role', (
    tester,
  ) async {
    await _pumpShell(tester, '/hospital/requests', const Size(390, 844));

    expect(find.byType(NavigationBar), findsOneWidget);
    expect(find.text('New request'), findsOneWidget);
    expect(find.text('Requests'), findsOneWidget);
    expect(find.text('Stock'), findsOneWidget);
  });

  testWidgets('switching the route changes the rail', (tester) async {
    await _pumpShell(tester, '/donor/appeals', const Size(1440, 900));
    expect(find.text('Appeals'), findsOneWidget);

    await _pumpShell(tester, '/admin/thresholds', const Size(1440, 900));
    expect(find.text('Thresholds'), findsOneWidget);
    expect(find.text('Appeals'), findsNothing);
  });
}
