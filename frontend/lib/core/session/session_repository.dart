import '../constants/app_role.dart';

/// Who is using the app.
///
/// There is no sign-in screen yet; the AppBar role switcher is the demo control.
/// When real auth lands this interface keeps its shape and the mock is swapped
/// for a token-backed implementation.
abstract interface class SessionRepository {
  AppRole activeRole();

  void selectRole(AppRole role);
}

/// In-memory session, seeded with the donor so the app opens on the donor area.
class MockSessionRepository implements SessionRepository {
  MockSessionRepository({AppRole initial = AppRole.donor}) : _activeRole = initial;

  AppRole _activeRole;

  @override
  AppRole activeRole() => _activeRole;

  @override
  void selectRole(AppRole role) => _activeRole = role;
}
