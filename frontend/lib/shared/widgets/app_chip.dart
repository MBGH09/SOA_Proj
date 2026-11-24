import 'package:flutter/material.dart';

import '../../app/theme/tokens/color_tokens.dart';
import '../../app/theme/tokens/spacing_tokens.dart';
import '../../app/theme/tokens/typography_tokens.dart';

/// Pill chip. 48 dp tall on mobile, 36 dp on web (tokens.md §5).
///
/// Selected chips are solid `primary` with `onPrimary` text, as in the
/// FigCraft frames (day, time, step and filter chips).
class AppChip extends StatelessWidget {
  const AppChip({
    super.key,
    required this.label,
    this.selected = false,
    this.onTap,
    this.icon,
    this.disabled = false,
  });

  final String label;
  final bool selected;

  /// Null when the chip is only a label.
  final VoidCallback? onTap;
  final IconData? icon;

  /// A full slot: faded and not tappable.
  final bool disabled;

  @override
  Widget build(BuildContext context) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;
    final height = wide ? 36.0 : 48.0;

    final background = disabled
        ? AppColors.surfaceContainerLowest
        : selected
        ? AppColors.primary
        : AppColors.surfaceContainerLowest;
    final foreground = disabled
        ? AppColors.onSurfaceVariant.withValues(alpha: 0.5)
        : selected
        ? AppColors.onPrimary
        : AppColors.onSurface;
    final border = disabled
        ? AppColors.outlineVariant
        : selected
        ? AppColors.primary
        : AppColors.outline;

    return Material(
      color: background,
      shape: StadiumBorder(side: BorderSide(color: border)),
      child: InkWell(
        onTap: disabled ? null : onTap,
        customBorder: const StadiumBorder(),
        child: Container(
          height: height,
          padding: const EdgeInsets.symmetric(horizontal: 14),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (icon != null) ...[
                Icon(icon, size: 16, color: foreground),
                const SizedBox(width: 6),
              ],
              Text(
                label,
                style: AppTypography.textTheme.labelLarge?.copyWith(
                  color: foreground,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
