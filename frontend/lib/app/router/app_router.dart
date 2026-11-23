import 'package:go_router/go_router.dart';

import '../../features/donor/presentation/screens/eligibility_screen.dart';
import '../../features/donor/presentation/screens/open_appeals_screen.dart';
import '../../features/donor/presentation/screens/register_donor_screen.dart';
import '../../shared/layouts/app_shell.dart';
import '../../shared/widgets/placeholder_screen.dart';

GoRoute _placeholder(String path, String title) {
  return GoRoute(
    path: path,
    builder: (context, state) => PlaceholderScreen(title: title),
  );
}

final appRouter = GoRouter(
  initialLocation: '/donor/appeals',
  routes: [
    ShellRoute(
      builder: (context, state, child) =>
          AppShell(location: state.uri.path, child: child),
      routes: [
        // Donor
        GoRoute(
          path: '/donor/appeals',
          builder: (context, state) => const OpenAppealsScreen(),
        ),
        GoRoute(
          path: '/donor/register',
          builder: (context, state) => const RegisterDonorScreen(),
        ),
        GoRoute(
          path: '/donor/eligibility',
          builder: (context, state) => const EligibilityScreen(),
        ),
        _placeholder('/donor/book', 'Book a slot'),
        _placeholder('/donor/appointments/move', 'Move appointment'),
        _placeholder('/donor/appointments', 'My appointments'),
        _placeholder('/donor/certificates', 'My certificates'),
        _placeholder('/donor/verify', 'Verify a certificate'),
        _placeholder('/donor/stock', 'Stock levels'),
        // Centre staff
        _placeholder('/centre/today', 'Today at the centre'),
        _placeholder('/centre/board', 'Donation board'),
        _placeholder('/centre/visits/:id', 'Donation visit'),
        _placeholder('/centre/deferrals/new', 'Record a deferral'),
        _placeholder('/centre/slots', 'Slot management'),
        // Hospital
        _placeholder('/hospital/requests/new', 'New request'),
        _placeholder('/hospital/requests/:id', 'Request detail'),
        _placeholder('/hospital/requests', 'Requests'),
        _placeholder('/hospital/stock', 'Stock dashboard'),
        // Admin
        _placeholder('/admin/thresholds', 'Stock thresholds'),
        _placeholder('/admin/stock', 'Stock dashboard'),
      ],
    ),
  ],
);