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