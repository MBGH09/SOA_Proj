import 'package:blood_network/core/errors/api_error.dart';
import 'package:blood_network/data/models/blood_component.dart';
import 'package:blood_network/data/models/blood_group.dart';
import 'package:blood_network/data/models/stock_level.dart';
import 'package:blood_network/data/repositories/request_repository.dart';

/// A synchronous stock service for widget tests, seeded with the same hospital
/// board and threshold rows as the fixture.
class FakeStockRepository implements StockRepository {
  FakeStockRepository({DateTime? now})
    : now = now ?? DateTime(2026, 11, 12, 10, 24) {
    _snapshots['HOP-SFX-01'] = _hospitalSnapshot();
    _snapshots['C-SFAX'] = _otherSnapshot('C-SFAX', _sites[1], 368);
    _snapshots['CNTS'] = _otherSnapshot('CNTS', _sites[2], 1180);
    _thresholds['HOP-SFX-01'] = _hospitalThresholds();
    _thresholds['C-SFAX'] = _flatThresholds(_sites[1]);
    _thresholds['CNTS'] = _flatThresholds(_sites[2]);
  }

  final DateTime now;

  static const List<StockSite> _sites = [
    StockSite(
      id: 'HOP-SFX-01',
      name: 'CHU Habib Bourguiba Sfax',
      shortName: 'This hospital',
    ),
    StockSite(id: 'C-SFAX', name: 'CRTS Sfax'),
    StockSite(id: 'CNTS', name: 'CNTS national'),
  ];

  static const List<List<int>> _hospitalValues = [
    [2, 8],
    [5, 6],
    [1, 2],
    [18, 10],
    [12, 8],
    [6, 4],
    [4, 6],
    [3, 4],
    [2, 3],
    [22, 10],
    [14, 8],
    [8, 4],
    [3, 5],
    [2, 4],
    [1, 2],
    [16, 10],
    [9, 8],
    [5, 4],
    [1, 3],
    [2, 3],
    [1, 2],
    [9, 6],
    [7, 6],
    [4, 3],
  ];

  static const List<String> _alerts = [
    'BAS',
    'MOYEN',
    'BAS',
    'HAUT',
    'HAUT',
    'HAUT',
    'MOYEN',
    'MOYEN',
    'MOYEN',
    'HAUT',
    'HAUT',
    'HAUT',
    'MOYEN',
    'BAS',
    'BAS',
    'HAUT',
    'HAUT',
    'HAUT',
    'BAS',
    'MOYEN',
    'BAS',
    'HAUT',
    'HAUT',
    'HAUT',
  ];

  static const Map<String, List<Object>> _changes = {
    'O_NEG|CGR': ['Amine K.', '2026-11-12T09:12:00'],
    'O_NEG|PLASMA': ['Amine K.', '2026-11-12T09:12:00'],
    'O_POS|CGR': ['Sonia B.', '2026-11-04T14:02:00'],
    'A_NEG|CGR': ['Sonia B.', '2026-11-04T14:02:00'],
    'A_POS|PLASMA': ['Amine K.', '2026-10-28T11:40:00'],
    'B_NEG|PLAQUETTES': ['Amine K.', '2026-10-28T11:40:00'],
    'AB_NEG|CGR': ['Sonia B.', '2026-10-21T16:25:00'],
    'AB_POS|PLAQUETTES': ['Sonia B.', '2026-10-21T16:25:00'],
  };

  final Map<String, StockSnapshot> _snapshots = {};
  final Map<String, ThresholdSnapshot> _thresholds = {};

  @override
  Future<List<StockSite>> sites() async => _sites;

  @override
  Future<StockSnapshot> snapshot(String siteId, {String? variant}) async {
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'STOCK_SERVICE_UNAVAILABLE');
    }
    final found = _snapshots[siteId];
    if (found == null) {
      throw ApiError(status: 404, message: 'STOCK_SITE_NOT_FOUND: $siteId');
    }
    return found;
  }

  @override
  Future<ThresholdSnapshot> thresholds(String siteId, {String? variant}) async {
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'STOCK_SERVICE_UNAVAILABLE');
    }
    final found = _thresholds[siteId];
    if (found == null) {
      throw ApiError(status: 404, message: 'STOCK_SITE_NOT_FOUND: $siteId');
    }
    return found;
  }

  @override
  Future<ThresholdSnapshot> saveThresholds(
    String siteId,
    List<ThresholdRule> rules, {
    String? variant,
  }) async {
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'STOCK_SERVICE_UNAVAILABLE');
    }
    final current = await thresholds(siteId);
    final audit = <ThresholdChange>[...current.audit];
    final saved = <ThresholdRule>[];

    for (final rule in rules) {
      final old = current.rules.firstWhere(
        (item) =>
            item.bloodGroup == rule.bloodGroup &&
            item.component == rule.component,
      );
      if (old.threshold == rule.threshold) {
        saved.add(old);
        continue;
      }
      audit.insert(
        0,
        ThresholdChange(
          who: 'You',
          ruleKey: rule.keyLine,
          from: old.threshold,
          to: rule.threshold,
          at: now,
        ),
      );
      saved.add(
        ThresholdRule(
          bloodGroup: rule.bloodGroup,
          component: rule.component,
          threshold: rule.threshold,
          current: rule.current,
          changedBy: 'You',
          changedAt: now,
        ),
      );
    }

    return _thresholds[siteId] = ThresholdSnapshot(
      site: current.site,
      rules: saved,
      audit: audit,
    );
  }

  StockSnapshot _hospitalSnapshot() {
    final levels = <StockLevel>[];
    for (var index = 0; index < _hospitalValues.length; index++) {
      final group = BloodGroup.values[index ~/ 3];
      final component = BloodComponent.values[index % 3];
      levels.add(
        StockLevel(
          bloodGroup: group,
          component: component,
          quantity: _hospitalValues[index][0],
          threshold: _hospitalValues[index][1],
          alert: StockAlert.fromApi(_alerts[index]),
        ),
      );
    }
    return StockSnapshot(
      site: _sites[0],
      levels: levels,
      stats: const StockStats(
        available: 214,
        reserved: 38,
        inQualification: 22,
        belowThreshold: 6,
      ),
      updatedAt: now,
    );
  }

  StockSnapshot _otherSnapshot(String id, StockSite site, int available) {
    final levels = <StockLevel>[];
    for (final group in BloodGroup.values) {
      for (final component in BloodComponent.values) {
        levels.add(
          StockLevel(
            bloodGroup: group,
            component: component,
            quantity: 20,
            threshold: 8,
            alert: StockAlert.inStock,
          ),
        );
      }
    }
    return StockSnapshot(
      site: site,
      levels: levels,
      stats: StockStats(
        available: available,
        reserved: 44,
        inQualification: 31,
        belowThreshold: 0,
      ),
      updatedAt: now,
    );
  }

  ThresholdSnapshot _hospitalThresholds() {
    final rules = <ThresholdRule>[];
    for (var index = 0; index < _hospitalValues.length; index++) {
      final group = BloodGroup.values[index ~/ 3];
      final component = BloodComponent.values[index % 3];
      final change = _changes['${group.apiValue}|${component.apiValue}'];
      rules.add(
        ThresholdRule(
          bloodGroup: group,
          component: component,
          threshold: _hospitalValues[index][1],
          current: _hospitalValues[index][0],
          changedBy: change?[0] as String?,
          changedAt: change == null
              ? null
              : DateTime.parse(change[1] as String),
        ),
      );
    }
    return ThresholdSnapshot(
      site: _sites[0],
      rules: rules,
      audit: [
        ThresholdChange(
          who: 'Amine K.',
          ruleKey: 'O negative · red cells',
          from: 6,
          to: 8,
          at: DateTime(2026, 11, 12, 9, 12),
        ),
        ThresholdChange(
          who: 'Sonia B.',
          ruleKey: 'A negative · red cells',
          from: 8,
          to: 6,
          at: DateTime(2026, 11, 4, 14, 2),
        ),
      ],
    );
  }

  ThresholdSnapshot _flatThresholds(StockSite site) {
    final rules = <ThresholdRule>[];
    for (final group in BloodGroup.values) {
      for (final component in BloodComponent.values) {
        rules.add(
          ThresholdRule(
            bloodGroup: group,
            component: component,
            threshold: 8,
            current: 20,
          ),
        );
      }
    }
    return ThresholdSnapshot(site: site, rules: rules, audit: const []);
  }
}
