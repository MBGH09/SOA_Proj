import 'package:flutter/material.dart';

import '../../app/theme/tokens/color_tokens.dart';
import '../../app/theme/tokens/spacing_tokens.dart';
import '../../app/theme/tokens/typography_tokens.dart';
import 'app_card.dart';

enum AppStatTone { neutral, success, warning, critical }

/// Small summary card: micro-label, one big tabular value, optional caption.
class AppStatCard extends StatelessWidget {
  const AppStatCard({
    super.key,
    required this.label,
    required this.value,
    this.unit,
    this.caption,
    this.tone = AppStatTone.neutral,
    this.icon,
  });

  final String label;
  final String value;
  final String? unit;
  final String? caption;
  final AppStatTone tone;
  final IconData? icon;

  Color get _valueColor => switch (tone) {
    AppStatTone.neutral => AppColors.onSurface,
    AppStatTone.success => AppColors.success,
    AppStatTone.warning => AppColors.warning,
    AppStatTone.critical => AppColors.error,
  };

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            children: [
              if (icon != null) ...[
                Icon(icon, size: 16, color: AppColors.onSurfaceVariant),
                const SizedBox(width: 6),
              ],
              Expanded(
                child: Text(
                  label.toUpperCase(),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: text.labelSmall?.copyWith(
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.sm),
          Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              Text(
                value,
                style: AppTypography.textTheme.headlineSmall?.copyWith(
                  fontSize: 30,
                  color: _valueColor,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              ),
              if (unit != null) ...[
                const SizedBox(width: 6),
                Text(
                  unit!,
                  style: text.bodyMedium?.copyWith(
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ],
            ],
          ),
          if (caption != null) ...[
            const SizedBox(height: 6),
            Text(
              caption!,
              style: text.bodySmall?.copyWith(
                color: AppColors.onSurfaceVariant,
              ),
            ),
          ],
        ],
      ),
    );
  }
}
