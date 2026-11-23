enum AppealLevel { open, critical }

/// A public appeal for one blood group + component at a place.
class Appeal {
  const Appeal({
    required this.id,
    required this.bloodGroup,
    required this.bloodGroupLabel,
    required this.component,
    required this.place,
    required this.openedLabel,
    required this.donorsNeeded,
    required this.level,
    this.donorsBooked,
  });

  final String id;
  final String bloodGroup;
  final String bloodGroupLabel;
  final String component;
  final String place;
  final String openedLabel;
  final int donorsNeeded;
  final int? donorsBooked;
  final AppealLevel level;

  bool get isCritical => level == AppealLevel.critical;

  String get metaLine {
    final booked = donorsBooked == null ? '' : ' · $donorsBooked booked';
    return '$openedLabel · $donorsNeeded donors needed$booked';
  }

  factory Appeal.fromJson(Map<String, dynamic> json) {
    return Appeal(
      id: json['id'] as String,
      bloodGroup: json['bloodGroup'] as String,
      bloodGroupLabel: json['bloodGroupLabel'] as String,
      component: json['component'] as String,
      place: json['place'] as String,
      openedLabel: json['openedLabel'] as String,
      donorsNeeded: json['donorsNeeded'] as int,
      donorsBooked: json['donorsBooked'] as int?,
      level: json['level'] == 'critical'
          ? AppealLevel.critical
          : AppealLevel.open,
    );
  }
}