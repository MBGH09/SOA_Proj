import 'package:flutter/material.dart';

import '../../app/theme/tokens/color_tokens.dart';
import '../../app/theme/tokens/spacing_tokens.dart';
import '../../app/theme/tokens/typography_tokens.dart';

/// 64 dp top bar: drop mark + product name on the left, role switcher on the right.
class AppTopBar extends StatelessWidget {
  const AppTopBar({
    super.key,
    required this.trailing,
    this.compact = false,
  });

  final Widget trailing;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 64,
      padding: EdgeInsets.symmetric(
        horizontal: compact ? AppSpacing.lg : AppSpacing.xl,
      ),
      decoration: const BoxDecoration(
        color: AppColors.surface,
        border: Border(bottom: BorderSide(color: AppColors.outlineVariant)),
      ),
      child: Row(
        children: [
          const Icon(
            Icons.water_drop_outlined,
            size: 24,
            color: AppColors.onSurface,
          ),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: Text(
              'National Blood Network',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: AppTypography.textTheme.titleMedium,
            ),
          ),
          const SizedBox(width: AppSpacing.sm),
          trailing,
        ],
      ),
    );
  }
}