import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/constants/app_role.dart';

/// Active role for the demo (no sign-in yet).
class RoleController extends Notifier<AppRole> {
  @override
  AppRole build() => AppRole.donor;

  void select(AppRole role) => state = role;
}

final roleProvider = NotifierProvider<RoleController, AppRole>(
  RoleController.new,
);