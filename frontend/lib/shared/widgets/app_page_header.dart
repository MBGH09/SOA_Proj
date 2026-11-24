import 'package:flutter/material.dart';

import '../../app/theme/tokens/color_tokens.dart';
import '../../app/theme/tokens/spacing_tokens.dart';
import '../../app/theme/tokens/typography_tokens.dart';

/// Screen title block: one H1 + one subtitle, optional trailing actions.
///
/// Web 30 / 38 / −0.4, mobile 24 / 30 (tokens.md §2).
class AppPageHeader extends StatelessWidget {
  const AppPageHeader({
    super.key,
    required this.title,
    this.subtitle,
    this.actions = const [],
    this.wide,
  });

  final String title;
  final String? subtitle;
  final List<Widget> actions;

  /// Overrides the width breakpoint; by default it is read from the viewport.
  final bool? wide;

  @override
  Widget build(BuildContext context) {
    final isWide =
        wide ?? MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;
    final text = AppTypography.textTheme;

    final heading = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          title,
          style: isWide
              ? AppTypography.screenTitle
              : AppTypography.screenTitleMobile,
        ),
        if (subtitle != null) ...[
          const SizedBox(height: AppSpacing.sm),
          Text(
            subtitle!,
            style: text.bodyMedium?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ],
    );

    if (actions.isEmpty) return heading;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(child: heading),
        const SizedBox(width: AppSpacing.lg),
        for (var i = 0; i < actions.length; i++) ...[
          if (i > 0) const SizedBox(width: AppSpacing.md),
          actions[i],
        ],
      ],
    );
  }
}
