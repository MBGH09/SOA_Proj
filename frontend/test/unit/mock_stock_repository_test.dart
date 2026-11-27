import 'package:blood_network/core/errors/api_error.dart';
import 'package:blood_network/core/time/clock.dart';
import 'package:blood_network/data/mock/mock_stock_repository.dart';
import 'package:blood_network/data/models/blood_component.dart';
import 'package:blood_network/data/models/blood_group.dart';
import 'package:blood_network/data/models/stock_level.dart';
import 'package:flutter_test/flutter_test.dart';

/// The stock figures and the editable alert thresholds (H4, A1).
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final now = DateTime(2026, 11, 12, 10, 24);
  late MockStockRepository stock;

  setUp(() => stock = MockStockRepository(FrozenClock(now)));

  Future<ApiError> failure(Future<void> Function() action) async {
    try {
      await action();
    } on ApiError catch (error) {
      return error;
    }
    fail('the call should have failed');
  }

  test('the three places are named for the chips', () async {
    final sites = await stock.sites();

    expect(sites.map((site) => site.id), ['HOP-SFX-01', 'C-SFAX', 'CNTS']);
    expect(sites.first.chipLabel, 'This hospital');
    expect(sites[1].chipLabel, 'CRTS Sfax');
  });

  test(
    'the hospital snapshot carries the 8 × 3 levels and its figures',
    () async {
      final snapshot = await stock.snapshot('HOP-SFX-01');

      expect(snapshot.levels.length, 24);
      expect(snapshot.stats.available, 214);
      expect(snapshot.stats.reserved, 38);
      expect(snapshot.belowThreshold.length, 6);

      final level = snapshot.levelFor(BloodGroup.oNeg, BloodComponent.redCells);
      expect(level, isNotNull);
      expect(level!.quantity, 2);
      expect(level.threshold, 8);
      expect(level.alert, StockAlert.low);
      expect(level.ratioLabel, '2 / 8');
    },
  );

  test('the thresholds are ordered with the last changes first (A1)', () async {
    final snapshot = await stock.thresholds('HOP-SFX-01');

    expect(snapshot.rules.length, 24);
    final first = snapshot.rulesByLastChange.first;
    expect(first.bloodGroup, BloodGroup.oNeg);
    expect(first.component, BloodComponent.redCells);
    expect(first.threshold, 8);
    expect(first.current, 2);
    expect(first.changedBy, 'Amine K.');
    expect(first.isBelow, isTrue);

    final never = snapshot.rulesByLastChange.last;
    expect(never.changedBy, isNull);
  });

  test('saving a threshold appends who and from what to what', () async {
    final before = await stock.thresholds('HOP-SFX-01');
    final rules = before.rules
        .map(
          (rule) =>
              rule.bloodGroup == BloodGroup.oNeg &&
                  rule.component == BloodComponent.redCells
              ? rule.copyWith(threshold: 10)
              : rule,
        )
        .toList(growable: false);

    final saved = await stock.saveThresholds('HOP-SFX-01', rules);

    expect(saved.rules.first.threshold, 10);
    expect(saved.rules.first.changedBy, 'You');
    expect(saved.audit.first.who, 'You');
    expect(saved.audit.first.ruleKey, 'O negative · red cells');
    expect(saved.audit.first.from, 8);
    expect(saved.audit.first.to, 10);
    expect(saved.audit.first.at, now);

    // Saving the same numbers again does not add a second audit line.
    final again = await stock.saveThresholds('HOP-SFX-01', rules);
    expect(again.audit.length, saved.audit.length);
  });

  test('an unknown place is a 404', () async {
    final error = await failure(() => stock.snapshot('NOPE'));
    expect(error.status, 404);
  });

  test('the stock service can be unavailable', () async {
    final error = await failure(
      () => stock.snapshot('HOP-SFX-01', variant: 'unavailable'),
    );
    expect(error.status, 503);
  });
}
