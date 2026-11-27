import 'package:flutter/material.dart';

import '../../core/constants/app_role.dart';

/// One destination in the web rail and the mobile navigation bar.
class AppDestination {
  const AppDestination({
    required this.label,
    required this.railLabel,
    required this.path,
    required this.icon,
    this.inMobileBar = true,
  });

  /// Full label, shown in the mobile bar.
  final String label;

  /// Short label for the 76 dp web rail.
  final String railLabel;

  final String path;
  final IconData icon;

  /// Donor "Stock" lives in the rail but not in the four-item mobile bar.
  final bool inMobileBar;
}

/// The destinations of a role's shell, in rail order.
List<AppDestination> destinationsFor(AppRole role) => switch (role) {
  AppRole.donor => const [
    AppDestination(
      label: 'Appeals',
      railLabel: 'Appeals',
      path: '/donor/appeals',
      icon: Icons.notifications_none_rounded,
    ),
    AppDestination(
      label: 'Book',
      railLabel: 'Book',
      path: '/donor/book',
      icon: Icons.calendar_today_outlined,
    ),
    AppDestination(
      label: 'Appointments',
      railLabel: 'Appointments',
      path: '/donor/appointments',
      icon: Icons.format_list_bulleted_rounded,
    ),
    AppDestination(
      label: 'Certificates',
      railLabel: 'Certificates',
      path: '/donor/certificates',
      icon: Icons.description_outlined,
    ),
    AppDestination(
      label: 'Stock',
      railLabel: 'Stock',
      path: '/donor/stock',
      icon: Icons.bar_chart_rounded,
      inMobileBar: false,
    ),
  ],
  AppRole.centre => const [
    AppDestination(
      label: 'Today',
      railLabel: 'Today',
      path: '/centre/today',
      icon: Icons.wb_sunny_outlined,
    ),
    AppDestination(
      label: 'Board',
      railLabel: 'Board',
      path: '/centre/board',
      icon: Icons.view_kanban_outlined,
    ),
    AppDestination(
      label: 'Slots',
      railLabel: 'Slots',
      path: '/centre/slots',
      icon: Icons.calendar_month_outlined,
    ),
    AppDestination(
      label: 'Deferrals',
      railLabel: 'Deferrals',
      path: '/centre/deferrals/new',
      icon: Icons.schedule_outlined,
    ),
  ],
  AppRole.hospital => const [
    AppDestination(
      label: 'New request',
      railLabel: 'New',
      path: '/hospital/requests/new',
      icon: Icons.note_add_outlined,
    ),
    AppDestination(
      label: 'Requests',
      railLabel: 'Requests',
      path: '/hospital/requests',
      icon: Icons.format_list_bulleted_rounded,
    ),
    AppDestination(
      label: 'Stock',
      railLabel: 'Stock',
      path: '/hospital/stock',
      icon: Icons.bar_chart_rounded,
    ),
  ],
  AppRole.admin => const [
    AppDestination(
      label: 'Thresholds',
      railLabel: 'Thresholds',
      path: '/admin/thresholds',
      icon: Icons.tune_outlined,
    ),
    AppDestination(
      label: 'Stock',
      railLabel: 'Stock',
      path: '/admin/stock',
      icon: Icons.bar_chart_rounded,
    ),
  ],
};

/// The path of the destination that owns [location].
///
/// Picks the longest match, so `/hospital/requests/new` selects "New request"
/// and not its parent "Requests".
String? activeDestinationPath(
  List<AppDestination> destinations,
  String location,
) {
  String? best;
  for (final destination in destinations) {
    final matches =
        location == destination.path ||
        location.startsWith('${destination.path}/');
    if (matches &&
        (best == null || destination.path.length > best.length)) {
      best = destination.path;
    }
  }
  return best;
}
