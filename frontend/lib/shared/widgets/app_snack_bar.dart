import 'package:flutter/material.dart';

import '../../app/theme/tokens/color_tokens.dart';
import '../../app/theme/tokens/spacing_tokens.dart';
import '../../app/theme/tokens/typography_tokens.dart';

enum AppSnackTone { neutral, success, warning, critical }

/// One-line feedback after an action (booked, moved, saved, failed).
void showAppSnack(
  BuildContext context,
  String message, {
  AppSnackTone tone = AppSnackTone.neutral,
  String? actionLabel,
  VoidCallback? onAction,
}) {
  final (background, foreground) = switch (tone) {
    AppSnackTone.neutral => (AppColors.onSurface, AppColors.surface),
    AppSnackTone.success => (
      AppColors.successContainer,
      AppColors.onSuccessContainer,
    ),
    AppSnackTone.warning => (
      AppColors.warningContainer,
      AppColors.onWarningContainer,
    ),
    AppSnackTone.critical => (
      AppColors.errorContainer,
      AppColors.onErrorContainer,
    ),
  };

  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(
      SnackBar(
        behavior: SnackBarBehavior.floating,
        backgroundColor: background,
        elevation: 0,
        margin: const EdgeInsets.all(AppSpacing.lg),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.small),
        ),
        content: Text(
          message,
          style: AppTypography.textTheme.bodyMedium?.copyWith(
            color: foreground,
          ),
        ),
        action: actionLabel == null || onAction == null
            ? null
            : SnackBarAction(
                label: actionLabel,
                textColor: foreground,
                onPressed: onAction,
              ),
      ),
    );
}
