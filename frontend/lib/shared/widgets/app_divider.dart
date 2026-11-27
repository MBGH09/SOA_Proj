import 'package:flutter/material.dart';

import '../../app/theme/tokens/color_tokens.dart';

/// 1 px rule in `outlineVariant` (tokens.md §5).
class AppDivider extends StatelessWidget {
  const AppDivider({
    super.key,
    this.spacing = 0,
    this.indent = 0,
    this.endIndent = 0,
    this.color,
  });

  /// Vertical breathing room above and below the rule.
  final double spacing;
  final double indent;
  final double endIndent;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.symmetric(vertical: spacing),
      child: Divider(
        height: 1,
        thickness: 1,
        indent: indent,
        endIndent: endIndent,
        color: color ?? AppColors.outlineVariant,
      ),
    );
  }
}
