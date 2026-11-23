import 'package:flutter/material.dart';

import '../../app/theme/tokens/color_tokens.dart';
import '../../app/theme/tokens/spacing_tokens.dart';
import '../../app/theme/tokens/typography_tokens.dart';

enum AppBadgeTone { neutral, success, warning, critical }

/// Pill badge, 28 dp tall (tokens.md §5).
class AppBadge extends StatelessWidget {
  const AppBadge({super.key, required this.label, this.tone = AppBadgeTone.neutral});

  final String label;
  final AppBadgeTone tone;

  @override
  Widget build(BuildContext context) {
    final (background, foreground) = switch (tone) {
      AppBadgeTone.neutral => (
          AppColors.surfaceContainer,
          AppColors.onSurfaceVariant,
        ),
      AppBadgeTone.success => (
          AppColors.successContainer,
          AppColors.onSuccessContainer,
        ),
      AppBadgeTone.warning => (
          AppColors.warningContainer,
          AppColors.onWarningContainer,
        ),
      AppBadgeTone.critical => (
          AppColors.errorContainer,
          AppColors.onErrorContainer,
        ),
    };

    return Container(
      height: 28,
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(AppRadius.pill),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 6,
            height: 6,
            decoration: BoxDecoration(color: foreground, shape: BoxShape.circle),
          ),
          const SizedBox(width: 6),
          Text(
            label,
            style: AppTypography.textTheme.labelMedium?.copyWith(
              color: foreground,
            ),
          ),
        ],
      ),
    );
  }
}