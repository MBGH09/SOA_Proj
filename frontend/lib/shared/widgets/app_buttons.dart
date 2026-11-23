import 'package:flutter/material.dart';

import '../../app/theme/tokens/color_tokens.dart';
import '../../app/theme/tokens/spacing_tokens.dart';
import '../../app/theme/tokens/typography_tokens.dart';

/// Primary action. 44 dp on web, 48 dp on mobile.
class AppFilledButton extends StatelessWidget {
  const AppFilledButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.height = 44,
  });

  final String label;
  final VoidCallback? onPressed;
  final double height;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: height,
      child: FilledButton(
        onPressed: onPressed,
        style: FilledButton.styleFrom(
          backgroundColor: AppColors.primary,
          foregroundColor: AppColors.onPrimary,
          disabledBackgroundColor: AppColors.surfaceContainer,
          disabledForegroundColor: AppColors.onSurfaceVariant,
          textStyle: AppTypography.textTheme.labelLarge,
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AppRadius.small),
          ),
        ),
        child: Text(label),
      ),
    );
  }
}

/// Secondary action on the same row as a filled button.
///
/// [critical] renders the label in the reserved red, for a destructive choice
/// such as cancelling an appointment.
class AppOutlinedButton extends StatelessWidget {
  const AppOutlinedButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.height = 44,
    this.critical = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final double height;
  final bool critical;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: height,
      child: OutlinedButton(
        onPressed: onPressed,
        style: OutlinedButton.styleFrom(
          foregroundColor: critical ? AppColors.error : AppColors.onSurface,
          side: const BorderSide(color: AppColors.outline),
          textStyle: AppTypography.textTheme.labelLarge,
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AppRadius.small),
          ),
        ),
        child: Text(label),
      ),
    );
  }
}