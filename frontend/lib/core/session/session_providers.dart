import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../constants/app_role.dart';
import 'session_repository.dart';

final sessionRepositoryProvider = Provider<SessionRepository>(
  (ref) => MockSessionRepository(),
);

/// The role currently in use. Kept in step with the route by `AppShell`, so a
/// deep link to `/centre/board` shows "Centre" in the AppBar switcher.
class ActiveRoleController extends Notifier<AppRole> {
  @override
  AppRole build() => ref.watch(sessionRepositoryProvider).activeRole();

  void select(AppRole role) {
    ref.read(sessionRepositoryProvider).selectRole(role);
    state = role;
  }
}

final activeRoleProvider = NotifierProvider<ActiveRoleController, AppRole>(
  ActiveRoleController.new,
);
