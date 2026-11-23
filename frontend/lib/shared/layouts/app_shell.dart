import 'package:flutter/material.dart';

import '../../app/theme/tokens/color_tokens.dart';
import '../../app/theme/tokens/spacing_tokens.dart';
import 'app_top_bar.dart';
import 'donor_rail.dart';
import 'mobile_nav_bar.dart';
import 'role_switcher.dart';

/// Shared shell: rail + top bar on web, top bar + bottom navigation on mobile.
class AppShell extends StatelessWidget {
  const AppShell({super.key, required this.location, required this.child});

  final String location;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;

    if (wide) {
      return Scaffold(
        backgroundColor: AppColors.surface,
        body: Row(
          children: [
            DonorRail(location: location),
            Expanded(
              child: Column(
                children: [
                  const AppTopBar(trailing: RoleSwitcher()),
                  Expanded(child: child),
                ],
              ),
            ),
          ],
        ),
      );
    }

    return Scaffold(
      backgroundColor: AppColors.surface,
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            const AppTopBar(trailing: RoleSwitcher(), compact: true),
            Expanded(child: child),
            MobileNavBar(location: location),
          ],
        ),
      ),
    );
  }
}