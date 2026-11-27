/// A place that can take a donation (study §3, `Centre`).
enum CentreKind {
  permanent('CENTRE_PERMANENT', 'Permanent centre'),
  hospitalBank('BANQUE_HOSPITALIERE', 'Hospital bank'),
  mobileCollection('COLLECTE_MOBILE', 'Mobile collection');

  const CentreKind(this.apiValue, this.label);

  /// The value the Centres service returns.
  final String apiValue;

  /// Plain-language label.
  final String label;

  static CentreKind fromApi(String value) {
    for (final kind in values) {
      if (kind.apiValue == value) return kind;
    }
    return CentreKind.permanent;
  }
}

/// A donation centre, hospital bank or mobile collection point.
class Centre {
  const Centre({
    required this.id,
    required this.name,
    required this.address,
    required this.city,
    required this.kind,
    required this.openUntil,
    required this.distanceKm,
  });

  final String id;
  final String name;
  final String address;
  final String city;
  final CentreKind kind;

  /// Closing time today, `HH:mm`.
  final String openUntil;

  /// Straight-line distance from the donor's saved city.
  final double distanceKm;

  /// `9 Avenue Habib Bourguiba · Sfax` — the mobile line under the name.
  String get addressLine => '$address · $city';

  /// `Open today until 15:00 · distance 0.8 km` — the web line.
  String get openLine =>
      'Open today until $openUntil · distance '
      '${distanceKm.toStringAsFixed(1)} km';

  factory Centre.fromJson(Map<String, dynamic> json) => Centre(
    id: json['id'] as String,
    name: json['name'] as String,
    address: json['address'] as String,
    city: json['city'] as String,
    kind: CentreKind.fromApi(json['kind'] as String),
    openUntil: json['openUntil'] as String,
    distanceKm: (json['distanceKm'] as num).toDouble(),
  );
}
