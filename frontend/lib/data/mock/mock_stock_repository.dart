import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;

import '../../core/errors/api_error.dart';
import '../../core/time/clock.dart';
import '../models/stock_level.dart';
import '../repositories/request_repository.dart';

/// In-memory stock service for the hospital dashboard (H4) and the admin
/// thresholds screen (A1).
///
/// Saving a threshold changes the in-memory rules and appends the audit line,
/// so the screen behaves like a real session. The contract has no endpoint for
/// that update yet — see `docs/progress.md`.
class MockStockRepository implements StockRepository {
  MockStockRepository(this._clock);

  final Clock _clock;

  List<StockSite>? _sites;
  final Map<String, StockSnapshot> _snapshots = {};
  final Map<String, ThresholdSnapshot> _thresholds = {};

  @override
  Future<List<StockSite>> sites() async {
    await _latency();
    return List<StockSite>.unmodifiable(_sites ??= await _loadSites());
  }

  @override
  Future<StockSnapshot> snapshot(String siteId, {String? variant}) async {
    await _latency();
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'STOCK_SERVICE_UNAVAILABLE');
    }
    final cached = _snapshots[siteId];
    if (cached != null) return cached;
    final site = await _site(siteId);
    return _snapshots[siteId] = await _loadSnapshot(site);
  }

  @override
  Future<ThresholdSnapshot> thresholds(String siteId, {String? variant}) async {
    await _latency();
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'STOCK_SERVICE_UNAVAILABLE');
    }
    final cached = _thresholds[siteId];
    if (cached != null) return cached;
    final site = await _site(siteId);
    final stock = await snapshot(siteId);
    return _thresholds[siteId] = await _loadThresholds(site, stock);
  }

  @override
  Future<ThresholdSnapshot> saveThresholds(
    String siteId,
    List<ThresholdRule> rules, {
    String? variant,
  }) async {
    await _latency();
    if (variant == 'unavailable') {
      throw const ApiError(status: 503, message: 'STOCK_SERVICE_UNAVAILABLE');
    }
    final current = await thresholds(siteId);
    final now = _clock.now();
    final audit = <ThresholdChange>[...current.audit];

    final saved = <ThresholdRule>[];
    for (final rule in rules) {
      final before = current.rules.where(
        (item) =>
            item.bloodGroup == rule.bloodGroup &&
            item.component == rule.component,
      );
      final old = before.isEmpty ? null : before.first;
      if (old == null || old.threshold == rule.threshold) {
        saved.add(old ?? rule);
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
      saved.add(rule.copyWith().withChange(who: 'You', at: now));
    }

    return _thresholds[siteId] = ThresholdSnapshot(
      site: current.site,
      rules: saved,
      audit: audit,
    );
  }

  Future<StockSite> _site(String siteId) async {
    final sites = await _loadSites();
    for (final site in sites) {
      if (site.id == siteId) return site;
    }
    throw ApiError(status: 404, message: 'STOCK_SITE_NOT_FOUND: $siteId');
  }

  Future<List<StockSite>> _loadSites() async {
    final document = await _loadDocument();
    return (document['sites'] as List<dynamic>)
        .map((item) => StockSite.fromJson(item as Map<String, dynamic>))
        .toList(growable: false);
  }

  Future<StockSnapshot> _loadSnapshot(StockSite site) async {
    final document = await _loadDocument();
    final snapshots = document['snapshots'] as Map<String, dynamic>;
    final raw = snapshots[site.id] as Map<String, dynamic>;
    return StockSnapshot.fromJson(raw, site);
  }

  Future<ThresholdSnapshot> _loadThresholds(
    StockSite site,
    StockSnapshot stock,
  ) async {
    final raw = await rootBundle.loadString(
      'lib/data/mock/fixtures/stock_thresholds.json',
    );
    final document = jsonDecode(raw) as Map<String, dynamic>;
    final sites = document['sites'] as Map<String, dynamic>;
    return ThresholdSnapshot.fromJson(
      sites[site.id] as Map<String, dynamic>,
      site: site,
      stock: stock,
    );
  }

  Future<Map<String, dynamic>> _loadDocument() async {
    final raw = await rootBundle.loadString(
      'lib/data/mock/fixtures/stock.json',
    );
    return jsonDecode(raw) as Map<String, dynamic>;
  }

  Future<void> _latency() async {
    await Future<void>.delayed(const Duration(milliseconds: 300));
  }
}

/// A copy of a rule with who changed it and when.
extension on ThresholdRule {
  ThresholdRule withChange({required String who, required DateTime at}) =>
      ThresholdRule(
        bloodGroup: bloodGroup,
        component: component,
        threshold: threshold,
        current: current,
        changedBy: who,
        changedAt: at,
      );
}
