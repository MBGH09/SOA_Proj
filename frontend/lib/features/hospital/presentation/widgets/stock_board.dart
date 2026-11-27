import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../../../app/theme/tokens/color_tokens.dart';
import '../../../../app/theme/tokens/spacing_tokens.dart';
import '../../../../app/theme/tokens/typography_tokens.dart';
import '../../../../core/format/formatters.dart';
import '../../../../data/models/blood_component.dart';
import '../../../../data/models/blood_group.dart';
import '../../../../data/models/stock_level.dart';
import '../../../../shared/widgets/app_badge.dart';
import 'request_status.dart';

/// The dense 8 × 3 stock board (H4): one row per blood group, one cell per
/// component, each cell carrying its quantity, threshold and alert.
///
/// On a narrow screen the board scrolls sideways rather than squeezing the
/// figures below a readable size, like [AppDataTable].
class StockBoard extends StatelessWidget {
  const StockBoard({super.key, required this.snapshot, this.minWidth = 980});

  final StockSnapshot snapshot;
  final double minWidth;

  @override
  Widget build(BuildContext context) {
    return Container(
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(AppRadius.card),
        border: Border.all(color: AppColors.outline),
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final width = math.max(constraints.maxWidth, minWidth);
          return SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: SizedBox(
              width: width,
              child: Column(
                children: [
                  const _HeaderRow(),
                  for (var i = 0; i < BloodGroup.values.length; i++) ...[
                    if (i > 0)
                      const Divider(
                        height: 1,
                        thickness: 1,
                        color: AppColors.outlineVariant,
                      ),
                    _GroupRow(
                      group: BloodGroup.values[i],
                      levels: snapshot.levels,
                    ),
                  ],
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _HeaderRow extends StatelessWidget {
  const _HeaderRow();

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 44,
      color: AppColors.surfaceContainerLow,
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
      child: Row(
        children: [
          SizedBox(
            width: _groupColumn,
            child: Text('BLOOD GROUP', style: _headerStyle),
          ),
          for (final component in BloodComponent.values)
            Expanded(
              child: Padding(
                padding: const EdgeInsets.only(left: AppSpacing.sm),
                child: Text(component.columnLabel, style: _headerStyle),
              ),
            ),
        ],
      ),
    );
  }

  static const double _groupColumn = 220;
  static final TextStyle _headerStyle = AppTypography.textTheme.labelSmall!
      .copyWith(color: AppColors.onSurfaceVariant, letterSpacing: 0.5);
}

class _GroupRow extends StatelessWidget {
  const _GroupRow({required this.group, required this.levels});

  final BloodGroup group;
  final List<StockLevel> levels;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return SizedBox(
      height: 56,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
        child: Row(
          children: [
            SizedBox(
              width: _HeaderRow._groupColumn,
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      group.plainName[0].toUpperCase() +
                          group.plainName.substring(1),
                      style: text.bodyLarge,
                    ),
                  ),
                  Text(
                    group.code,
                    style: text.bodyMedium?.copyWith(
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(width: AppSpacing.sm),
                ],
              ),
            ),
            for (final component in BloodComponent.values)
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.only(left: AppSpacing.sm),
                  child: _LevelCell(
                    level: _levelFor(component),
                    group: group,
                    component: component,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  StockLevel? _levelFor(BloodComponent component) {
    for (final level in levels) {
      if (level.bloodGroup == group && level.component == component) {
        return level;
      }
    }
    return null;
  }
}

class _LevelCell extends StatelessWidget {
  const _LevelCell({
    required this.level,
    required this.group,
    required this.component,
  });

  final StockLevel? level;
  final BloodGroup group;
  final BloodComponent component;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    final value = level;
    if (value == null) {
      return Text(
        '—',
        style: text.bodyLarge?.copyWith(color: AppColors.onSurfaceVariant),
      );
    }

    final (background, foreground) = switch (value.alert) {
      StockAlert.inStock => (Colors.transparent, AppColors.onSurface),
      StockAlert.near => (
        AppColors.warningContainer,
        AppColors.onWarningContainer,
      ),
      StockAlert.low => (AppColors.errorContainer, AppColors.onErrorContainer),
    };
    final bold = value.alert != StockAlert.inStock;

    return Semantics(
      label:
          '${group.plainName} ${component.label.toLowerCase()}, '
          '${value.quantity} of ${value.threshold} units, ${value.alert.legend}',
      excludeSemantics: true,
      child: Container(
        height: 44,
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md),
        decoration: BoxDecoration(
          color: background,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Row(
          children: [
            Text(
              '${value.quantity}',
              style: AppFormatters.tabular(
                text.titleSmall?.copyWith(
                  color: foreground,
                  fontWeight: bold ? FontWeight.w700 : FontWeight.w600,
                ),
              ),
            ),
            const SizedBox(width: 6),
            Text(
              '/ ${value.threshold}',
              style: AppFormatters.tabular(
                text.bodySmall?.copyWith(
                  color: foreground.withValues(alpha: 0.75),
                ),
              ),
            ),
            const SizedBox(width: AppSpacing.md),
            Flexible(
              child: Text(
                value.alert.label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: text.bodySmall?.copyWith(color: foreground),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// The legend under the stock figures: at/above, near, below.
class StockLegend extends StatelessWidget {
  const StockLegend({super.key});

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    Widget item(String label, Color color, {bool outlined = false}) => Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 12,
          height: 12,
          decoration: BoxDecoration(
            color: color,
            borderRadius: BorderRadius.circular(3),
            border: outlined ? Border.all(color: AppColors.outline) : null,
          ),
        ),
        const SizedBox(width: AppSpacing.sm),
        Text(
          label,
          style: text.bodySmall?.copyWith(color: AppColors.onSurfaceVariant),
        ),
      ],
    );

    return Wrap(
      spacing: AppSpacing.xxl,
      runSpacing: AppSpacing.sm,
      children: [
        item(
          'At or above threshold',
          AppColors.surfaceContainerLowest,
          outlined: true,
        ),
        item('Near threshold', AppColors.warningContainer),
        item('Below threshold', AppColors.errorContainer),
      ],
    );
  }
}

/// The list under the board: every level below its threshold, with its ratio.
class BelowThresholdList extends StatelessWidget {
  const BelowThresholdList({super.key, required this.levels});

  final List<StockLevel> levels;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return Container(
      padding: const EdgeInsets.all(AppSpacing.xl),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(AppRadius.card),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'BELOW THRESHOLD · ORDER OR ASK FOR A COLLECTION',
            style: text.labelSmall?.copyWith(
              color: AppColors.onSurfaceVariant,
              letterSpacing: 0.5,
            ),
          ),
          const SizedBox(height: AppSpacing.md),
          if (levels.isEmpty)
            Text(
              'Every group and component is at or above its threshold.',
              style: text.bodyMedium?.copyWith(
                color: AppColors.onSurfaceVariant,
              ),
            )
          else
            for (var i = 0; i < levels.length; i++) ...[
              if (i > 0) const SizedBox(height: AppSpacing.md),
              _Row(level: levels[i]),
            ],
        ],
      ),
    );
  }
}

class _Row extends StatelessWidget {
  const _Row({required this.level});

  final StockLevel level;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    final line =
        '${level.bloodGroup.plainName[0].toUpperCase()}'
        '${level.bloodGroup.plainName.substring(1)} · '
        '${level.component.label}';

    return Semantics(
      label:
          '$line, ${level.quantity} of ${level.threshold} units, below threshold',
      excludeSemantics: true,
      child: Row(
        children: [
          Expanded(child: Text(line, style: text.bodyLarge)),
          SizedBox(
            width: 90,
            child: Text(
              '${level.quantity} of ${level.threshold}',
              style: AppFormatters.tabular(
                text.bodyMedium?.copyWith(color: AppColors.onSurfaceVariant),
              ),
            ),
          ),
          AppBadge(label: level.alert.label, tone: toneForAlert(level.alert)),
        ],
      ),
    );
  }
}
