import 'package:flutter/material.dart';

import '../../../hospital/presentation/screens/stock_dashboard_screen.dart';

/// The admin's read-only stock view: the same board as the hospital, under the
/// admin shell.
class AdminStockScreen extends StatelessWidget {
  const AdminStockScreen({super.key});

  @override
  Widget build(BuildContext context) => const StockDashboardScreen();
}
