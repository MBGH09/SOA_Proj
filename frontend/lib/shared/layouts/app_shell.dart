import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/theme/tokens/color_tokens.dart';
import '../../app/theme/tokens/spacing_tokens.dart';
import '../../core/constants/app_role.dart';
import '../../core/session/session_providers.dart';
import '../layouts/app_top_bar.dart';
import '../layouts/role_switcher.dart';
import '../navigation/app_navigation_rail.dart';
import '../navigation/mobile_nav_bar.dart';

/// Shared shell for every role: rail + top bar on web, top bar + bottom
/// navigation on mobile. The route decides which role's destinations to show.
class AppShell extends ConsumerStatefulWidget {
  const AppShell({super.key, required this.location, required this.child});

  final String location;
  final Widget child;

  @override
  ConsumerState<AppShell> createState() => _AppShellState();
}

class _AppShellState extends ConsumerState<AppShell> {
  @override
  void initState() {
    super.initState();
    _syncRole();
  }

  @override
  void didUpdateWidget(covariant AppShell oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.location != widget.location) _syncRole();
  }

  /// Keeps the session in step with the route, so a deep link shows the right
  /// role in the AppBar switcher.
  void _syncRole() {
    final role = roleFromLocation(widget.location);
    if (ref.read(activeRoleProvider) == role) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && ref.read(activeRoleProvider) != role) {
        ref.read(activeRoleProvider.notifier).select(role);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final role = roleFromLocation(widget.location);
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;

    if (wide) {
      return Scaffold(
        backgroundColor: AppColors.surface,
        body: Row(
          children: [
            AppNavigationRail(role: role, location: widget.location),
            Expanded(
              child: Column(
                children: [
                  const AppTopBar(trailing: RoleSwitcher()),
                  Expanded(child: widget.child),
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
            Expanded(child: widget.child),
            MobileNavBar(role: role, location: widget.location),
          ],
        ),
      ),
    );
  }
}
