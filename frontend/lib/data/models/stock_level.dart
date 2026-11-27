import 'blood_component.dart';
import 'blood_group.dart';

/// How a level compares with its alert threshold (`NiveauAlerte`).
///
/// The API names the height of the level, not the danger: `HAUT` is at or above
/// the threshold, `MOYEN` is near it, `BAS` is below it.
enum StockAlert {
  inStock('HAUT', 'In stock'),
  near('MOYEN', 'Near'),
  low('BAS', 'Low');

  const StockAlert(this.apiValue, this.label);

  final String apiValue;
  final String label;

  /// The legend line under the figures.
  String get legend => switch (this) {
    StockAlert.inStock => 'At or above threshold',
    StockAlert.near => 'Near threshold',
    StockAlert.low => 'Below threshold',
  };

  static StockAlert fromApi(String? value) {
    for (final alert in values) {
      if (alert.apiValue == value) return alert;
    }
    return StockAlert.inStock;
  }
}

/// One place that holds stock: the hospital's own bank, a CRTS, the CNTS.
class StockSite {
  const StockSite({required this.id, required this.name, this.shortName});

  final String id;
  final String name;

  /// `This hospital` on the place chips.
  final String? shortName;

  String get chipLabel => shortName ?? name;

  factory StockSite.fromJson(Map<String, dynamic> json) => StockSite(
    id: json['id'] as String,
    name: json['name'] as String,
    shortName: json['shortName'] as String?,
  );
}

/// The level of one blood group + component at one place (`NiveauStock`).
class StockLevel {
  const StockLevel({
    required this.bloodGroup,
    required this.component,
    required this.quantity,
    required this.threshold,
    required this.alert,
  });

  final BloodGroup bloodGroup;
  final BloodComponent component;
  final int quantity;
  final int threshold;
  final StockAlert alert;

  /// `2 / 8` — quantity over threshold.
  String get ratioLabel => '$quantity / $threshold';

  /// True when the level sits below its own threshold.
  bool get isLow => alert == StockAlert.low;

  factory StockLevel.fromJson(Map<String, dynamic> json) => StockLevel(
    bloodGroup:
        BloodGroup.fromApi(json['bloodGroup'] as String) ?? BloodGroup.oNeg,
    component: BloodComponent.fromApi(json['component'] as String),
    quantity: json['quantity'] as int,
    threshold: json['threshold'] as int,
    alert: StockAlert.fromApi(json['alert'] as String?),
  );
}

/// The four figures above the stock board (H4).
class StockStats {
  const StockStats({
    required this.available,
    required this.reserved,
    required this.inQualification,
    required this.belowThreshold,
  });

  final int available;
  final int reserved;
  final int inQualification;
  final int belowThreshold;

  factory StockStats.fromJson(Map<String, dynamic> json) => StockStats(
    available: json['available'] as int,
    reserved: json['reserved'] as int,
    inQualification: json['inQualification'] as int,
    belowThreshold: json['belowThreshold'] as int,
  );
}

/// Everything the stock dashboard shows for one place.
class StockSnapshot {
  const StockSnapshot({
    required this.site,
    required this.levels,
    required this.stats,
    required this.updatedAt,
  });

  final StockSite site;
  final List<StockLevel> levels;
  final StockStats stats;
  final DateTime updatedAt;

  /// The level of one group and component, or null.
  StockLevel? levelFor(BloodGroup group, BloodComponent component) {
    for (final level in levels) {
      if (level.bloodGroup == group && level.component == component) {
        return level;
      }
    }
    return null;
  }

  /// Every level under its threshold, in table order.
  List<StockLevel> get belowThreshold =>
      levels.where((level) => level.isLow).toList(growable: false);

  factory StockSnapshot.fromJson(Map<String, dynamic> json, StockSite site) =>
      StockSnapshot(
        site: site,
        levels: (json['levels'] as List<dynamic>)
            .map((item) => StockLevel.fromJson(item as Map<String, dynamic>))
            .toList(growable: false),
        stats: StockStats.fromJson(json['stats'] as Map<String, dynamic>),
        updatedAt: DateTime.parse(json['updatedAt'] as String),
      );
}

/// One editable alert threshold (A1).
class ThresholdRule {
  const ThresholdRule({
    required this.bloodGroup,
    required this.component,
    required this.threshold,
    required this.current,
    this.changedBy,
    this.changedAt,
  });

  final BloodGroup bloodGroup;
  final BloodComponent component;
  final int threshold;

  /// Units in stock right now at this place (from the stock snapshot).
  final int current;
  final String? changedBy;
  final DateTime? changedAt;

  /// True when the current level sits below the alert threshold.
  bool get isBelow => current < threshold;

  ThresholdRule copyWith({int? threshold}) => ThresholdRule(
    bloodGroup: bloodGroup,
    component: component,
    threshold: threshold ?? this.threshold,
    current: current,
    changedBy: changedBy,
    changedAt: changedAt,
  );

  /// `O negative · red cells` — the audit trail's line.
  String get keyLine =>
      '${bloodGroup.plainName} · ${component.label.toLowerCase()}';

  /// `12 Nov 2026 · 09:12`.
  String get changedLabel => changedAt == null
      ? '—'
      : '${changedAt!.day} ${_months[changedAt!.month - 1]} ${changedAt!.year} · '
            '${changedAt!.hour.toString().padLeft(2, '0')}:'
            '${changedAt!.minute.toString().padLeft(2, '0')}';

  static const List<String> _months = [
    'Jan',
    'Feb',
    'Mar',
    'Apr',
    'May',
    'Jun',
    'Jul',
    'Aug',
    'Sep',
    'Oct',
    'Nov',
    'Dec',
  ];

  factory ThresholdRule.fromJson(
    Map<String, dynamic> json, {
    required int current,
  }) => ThresholdRule(
    bloodGroup:
        BloodGroup.fromApi(json['bloodGroup'] as String) ?? BloodGroup.oNeg,
    component: BloodComponent.fromApi(json['component'] as String),
    threshold: json['threshold'] as int,
    current: current,
    changedBy: json['changedBy'] as String?,
    changedAt: json['changedAt'] == null
        ? null
        : DateTime.parse(json['changedAt'] as String),
  );
}

/// One line of the audit trail: who changed a threshold, from what to what.
class ThresholdChange {
  const ThresholdChange({
    required this.who,
    required this.ruleKey,
    required this.from,
    required this.to,
    required this.at,
  });

  final String who;

  /// `O negative · red cells`.
  final String ruleKey;
  final int from;
  final int to;
  final DateTime at;

  String get changeLabel => 'threshold $from → $to';

  String get atLabel {
    final hour = at.hour.toString().padLeft(2, '0');
    final minute = at.minute.toString().padLeft(2, '0');
    return '${at.day} ${ThresholdRule._months[at.month - 1]} ${at.year} · '
        '$hour:$minute';
  }

  factory ThresholdChange.fromJson(Map<String, dynamic> json) =>
      ThresholdChange(
        who: json['who'] as String,
        ruleKey: json['ruleKey'] as String,
        from: json['from'] as int,
        to: json['to'] as int,
        at: DateTime.parse(json['at'] as String),
      );
}

/// Every threshold of one place, plus the last changes.
class ThresholdSnapshot {
  const ThresholdSnapshot({
    required this.site,
    required this.rules,
    required this.audit,
  });

  final StockSite site;
  final List<ThresholdRule> rules;
  final List<ThresholdChange> audit;

  /// The rules as A1 lists them: most recently changed first, then the rules
  /// that have never changed, in blood-group order.
  ///
  /// The frame puts the eight edited rows at the top of the table, so the demo
  /// reproduces that order while every rule stays editable.
  List<ThresholdRule> get rulesByLastChange {
    final changed = rules.where((rule) => rule.changedAt != null).toList()
      ..sort((a, b) {
        final byDate = b.changedAt!.compareTo(a.changedAt!);
        if (byDate != 0) return byDate;
        final byGroup = a.bloodGroup.index.compareTo(b.bloodGroup.index);
        if (byGroup != 0) return byGroup;
        return a.component.index.compareTo(b.component.index);
      });
    final never = rules.where((rule) => rule.changedAt == null);
    return [...changed, ...never];
  }

  factory ThresholdSnapshot.fromJson(
    Map<String, dynamic> json, {
    required StockSite site,
    required StockSnapshot stock,
  }) => ThresholdSnapshot(
    site: site,
    rules: (json['rules'] as List<dynamic>)
        .map((item) {
          final map = item as Map<String, dynamic>;
          final group =
              BloodGroup.fromApi(map['bloodGroup'] as String) ??
              BloodGroup.oNeg;
          final component = BloodComponent.fromApi(map['component'] as String);
          final level = stock.levelFor(group, component);
          return ThresholdRule.fromJson(map, current: level?.quantity ?? 0);
        })
        .toList(growable: false),
    audit: (json['audit'] as List<dynamic>? ?? const [])
        .map((item) => ThresholdChange.fromJson(item as Map<String, dynamic>))
        .toList(growable: false),
  );
}
