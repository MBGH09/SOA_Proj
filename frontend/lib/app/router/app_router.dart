import 'package:go_router/go_router.dart';

import '../../features/admin/presentation/screens/admin_stock_screen.dart';
import '../../features/admin/presentation/screens/stock_thresholds_screen.dart';
import '../../features/centre/presentation/screens/donation_board_screen.dart';
import '../../features/centre/presentation/screens/donation_visit_screen.dart';
import '../../features/centre/presentation/screens/record_deferral_screen.dart';
import '../../features/centre/presentation/screens/slot_management_screen.dart';
import '../../features/centre/presentation/screens/today_screen.dart';
import '../../features/donor/presentation/screens/book_slot_screen.dart';
import '../../features/donor/presentation/screens/eligibility_screen.dart';
import '../../features/donor/presentation/screens/move_appointment_screen.dart';
import '../../features/donor/presentation/screens/my_appointments_screen.dart';
import '../../features/donor/presentation/screens/my_certificates_screen.dart';
import '../../features/donor/presentation/screens/open_appeals_screen.dart';
import '../../features/donor/presentation/screens/register_donor_screen.dart';
import '../../features/donor/presentation/screens/verify_certificate_screen.dart';
import '../../features/hospital/presentation/screens/new_request_screen.dart';
import '../../features/hospital/presentation/screens/request_detail_screen.dart';
import '../../features/hospital/presentation/screens/requests_list_screen.dart';
import '../../features/hospital/presentation/screens/stock_dashboard_screen.dart';
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
        GoRoute(
          path: '/donor/book',
          builder: (context, state) => const BookSlotScreen(),
        ),
        GoRoute(
          path: '/donor/appointments/move',
          builder: (context, state) => const MoveAppointmentScreen(),
        ),
        GoRoute(
          path: '/donor/appointments',
          builder: (context, state) => const MyAppointmentsScreen(),
        ),
        GoRoute(
          path: '/donor/certificates',
          builder: (context, state) => const MyCertificatesScreen(),
        ),
        GoRoute(
          path: '/donor/verify',
          builder: (context, state) => const VerifyCertificateScreen(),
        ),
        _placeholder('/donor/stock', 'Stock levels'),
        // Centre staff
        GoRoute(
          path: '/centre/today',
          builder: (context, state) => const TodayScreen(),
        ),
        GoRoute(
          path: '/centre/board',
          builder: (context, state) => const DonationBoardScreen(),
        ),
        GoRoute(
          path: '/centre/visits/:id',
          builder: (context, state) => const DonationVisitScreen(),
        ),
        GoRoute(
          path: '/centre/deferrals/new',
          builder: (context, state) => const RecordDeferralScreen(),
        ),
        GoRoute(
          path: '/centre/slots',
          builder: (context, state) => const SlotManagementScreen(),
        ),
        // Hospital
        GoRoute(
          path: '/hospital/requests/new',
          builder: (context, state) => const NewRequestScreen(),
        ),
        GoRoute(
          path: '/hospital/requests/:id',
          builder: (context, state) =>
              RequestDetailScreen(id: state.pathParameters['id'] ?? ''),
        ),
        GoRoute(
          path: '/hospital/requests',
          builder: (context, state) => const RequestsListScreen(),
        ),
        GoRoute(
          path: '/hospital/stock',
          builder: (context, state) => const StockDashboardScreen(),
        ),
        // Admin
        GoRoute(
          path: '/admin/thresholds',
          builder: (context, state) => const StockThresholdsScreen(),
        ),
        GoRoute(
          path: '/admin/stock',
          builder: (context, state) => const AdminStockScreen(),
        ),
      ],
    ),
  ],
);
