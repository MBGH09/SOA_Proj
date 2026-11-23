import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../app/theme/tokens/color_tokens.dart';
import '../../core/constants/app_role.dart';
import 'app_destination.dart';

/// Mobile bottom navigation — 80 dp, up to five destinations for the role.
class MobileNavBar extends StatelessWidget {
  const MobileNavBar({super.key, required this.role, required this.location});

  final AppRole role;
  final String location;

  @override
  Widget build(BuildContext context) {
    final items = destinationsFor(
      role,
    ).where((destination) => destination.inMobileBar).toList(growable: false);
    final active = activeDestinationPath(items, location);
    final index = items.indexWhere((item) => item.path == active);

    return DecoratedBox(
      decoration: const BoxDecoration(
        color: AppColors.surface,
        border: Border(top: BorderSide(color: AppColors.outlineVariant)),
      ),
      child: NavigationBar(
        height: 80,
        elevation: 0,
        backgroundColor: AppColors.surface,
        surfaceTintColor: Colors.transparent,
        indicatorColor: AppColors.primaryContainer,
        selectedIndex: index < 0 ? 0 : index,
        labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
        onDestinationSelected: (index) => context.go(items[index].path),
        destinations: [
          for (final item in items)
            NavigationDestination(
              icon: Icon(item.icon, color: AppColors.onSurfaceVariant),
              selectedIcon: Icon(
                item.icon,
                color: AppColors.onPrimaryContainer,
              ),
              label: item.label,
            ),
        ],
      ),
    );
  }
}
