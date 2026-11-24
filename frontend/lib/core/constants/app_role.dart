/// The four demo roles and the root route of each area.
enum AppRole {
  donor('Donor', '/donor/appeals'),
  centre('Centre', '/centre/today'),
  hospital('Hospital', '/hospital/requests'),
  admin('Admin', '/admin/thresholds');

  const AppRole(this.label, this.rootPath);

  final String label;
  final String rootPath;
}

/// Role of the area a route belongs to, taken from the first path segment.
///
/// The route is the source of truth for which shell to draw, so a deep link
/// always renders the right rail even before the session is updated.
AppRole roleFromLocation(String location) {
  for (final role in AppRole.values) {
    final prefix = '/${role.name}';
    if (location == prefix || location.startsWith('$prefix/')) return role;
  }
  return AppRole.donor;
}
