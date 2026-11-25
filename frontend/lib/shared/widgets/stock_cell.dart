import 'package:flutter/material.dart';

import '../../app/theme/tokens/color_tokens.dart';
import '../../app/theme/tokens/spacing_tokens.dart';
import '../../app/theme/tokens/typography_tokens.dart';

/// Stock level of one blood group + component (tokens.md §5).
enum StockState { inStock, near, low }

/// A stock-board cell: group, component, quantity, and a threshold state.
///
/// In stock → white / ink, Near → amber, Low → red. No pill, per the design.
class StockCell extends StatelessWidget {
  const StockCell({
    super.key,
    required this.bloodGroup,
    required this.component,
    required this.level,
    this.unit = 'units',
    this.state = StockState.inStock,
    this.onTap,
  });

  final String bloodGroup;
  final String component;
  final int level;
  final String unit;
  final StockState state;
  final VoidCallback? onTap;

  (Color, Color) get _colors => switch (state) {
    StockState.inStock => (
      AppColors.surfaceContainerLowest,
      AppColors.onSurface,
    ),
    StockState.near => (
      AppColors.warningContainer,
      AppColors.onWarningContainer,
    ),
    StockState.low => (AppColors.errorContainer, AppColors.onErrorContainer),
  };

  String get _stateLabel => switch (state) {
    StockState.inStock => 'In stock',
    StockState.near => 'Near threshold',
    StockState.low => 'Low',
  };

  @override
  Widget build(BuildContext context) {
    final (background, foreground) = _colors;
    final text = AppTypography.textTheme;
    final borderColor = state == StockState.inStock
        ? AppColors.outline
        : Colors.transparent;

    return Material(
      color: background,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppRadius.small),
        side: BorderSide(color: borderColor),
      ),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppRadius.small),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                bloodGroup,
                style: text.titleMedium?.copyWith(color: foreground),
              ),
              const SizedBox(height: 2),
              Text(
                component.toUpperCase(),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: text.labelSmall?.copyWith(
                  fontSize: 10,
                  color: foreground.withValues(alpha: 0.75),
                ),
              ),
              const SizedBox(height: AppSpacing.sm),
              Row(
                crossAxisAlignment: CrossAxisAlignment.baseline,
                textBaseline: TextBaseline.alphabetic,
                children: [
                  Text(
                    '$level',
                    style: text.headlineSmall?.copyWith(
                      color: foreground,
                      fontFeatures: const [FontFeature.tabularFigures()],
                    ),
                  ),
                  const SizedBox(width: 4),
                  Text(
                    unit,
                    style: text.bodySmall?.copyWith(
                      color: foreground.withValues(alpha: 0.75),
                    ),
                  ),
                ],
              ),
              if (state != StockState.inStock) ...[
                const SizedBox(height: 6),
                Text(
                  _stateLabel,
                  style: text.labelSmall?.copyWith(color: foreground),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
