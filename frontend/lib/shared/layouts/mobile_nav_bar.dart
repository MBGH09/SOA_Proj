import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../app/theme/tokens/color_tokens.dart';

class _NavItem {
  const _NavItem(this.label, this.path, this.icon);

  final String label;
  final String path;
  final IconData icon;
}

const _items = [
  _NavItem('Appeals', '/donor/appeals', Icons.notifications_none_rounded),
  _NavItem('Book', '/donor/book', Icons.calendar_today_outlined),
  _NavItem(
    'Appointments',
    '/donor/appointments',
    Icons.format_list_bulleted_rounded,
  ),
  _NavItem('Certificates', '/donor/certificates', Icons.description_outlined),
];

/// Mobile bottom navigation — 80 dp, four donor destinations.
class MobileNavBar extends StatelessWidget {
  const MobileNavBar({super.key, required this.location});

  final String location;

  int get _index {
    final index = _items.indexWhere(
      (item) => location == item.path || location.startsWith('${item.path}/'),
    );
    return index < 0 ? 0 : index;
  }

  @override
  Widget build(BuildContext context) {
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
        selectedIndex: _index,
        labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
        onDestinationSelected: (index) => context.go(_items[index].path),
        destinations: [
          for (final item in _items)
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