import 'package:flutter/material.dart';

import '../../app/theme/tokens/color_tokens.dart';
import '../../app/theme/tokens/spacing_tokens.dart';

/// Hairline card: white surface, 1 px outline, 12 dp radius, no shadow.
class AppCard extends StatelessWidget {
  const AppCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(AppSpacing.lg),
    this.background,
    this.bordered = true,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;

  /// Overrides the white surface, e.g. the beige summary tiles on H2.
  final Color? background;
  final bool bordered;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: padding,
      decoration: BoxDecoration(
        color: background ?? AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(AppRadius.card),
        border: bordered ? Border.all(color: AppColors.outline) : null,
      ),
      child: child,
    );
  }
}
