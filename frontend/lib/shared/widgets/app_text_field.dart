import 'package:flutter/material.dart';

import '../../app/theme/tokens/color_tokens.dart';
import '../../app/theme/tokens/spacing_tokens.dart';
import '../../app/theme/tokens/typography_tokens.dart';

/// Labelled text field: micro-label above, 1 px outline box, helper or error
/// line below. The input is ≥ 48 dp tall on mobile.
class AppTextField extends StatelessWidget {
  const AppTextField({
    super.key,
    required this.label,
    this.controller,
    this.hint,
    this.helperText,
    this.errorText,
    this.keyboardType,
    this.textInputAction,
    this.autofillHints,
    this.obscureText = false,
    this.enabled = true,
    this.maxLines = 1,
    this.prefixIcon,
    this.suffixIcon,
    this.onChanged,
    this.onSubmitted,
  });

  final String label;
  final TextEditingController? controller;
  final String? hint;
  final String? helperText;
  final String? errorText;
  final TextInputType? keyboardType;
  final TextInputAction? textInputAction;
  final Iterable<String>? autofillHints;
  final bool obscureText;
  final bool enabled;
  final int maxLines;
  final Widget? prefixIcon;
  final Widget? suffixIcon;
  final ValueChanged<String>? onChanged;
  final ValueChanged<String>? onSubmitted;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    final hasError = errorText != null && errorText!.isNotEmpty;

    final base = OutlineInputBorder(
      borderRadius: BorderRadius.circular(AppRadius.small),
      borderSide: BorderSide(
        color: hasError ? AppColors.error : AppColors.outline,
      ),
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          label,
          style: text.labelMedium?.copyWith(
            color: AppColors.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: AppSpacing.xs),
        TextField(
          controller: controller,
          enabled: enabled,
          obscureText: obscureText,
          maxLines: maxLines,
          keyboardType: keyboardType,
          textInputAction: textInputAction,
          autofillHints: autofillHints,
          onChanged: onChanged,
          onSubmitted: onSubmitted,
          style: text.bodyLarge,
          cursorColor: AppColors.onSurface,
          decoration: InputDecoration(
            isDense: true,
            filled: true,
            fillColor: enabled
                ? AppColors.surfaceContainerLowest
                : AppColors.surfaceContainerLow,
            hintText: hint,
            hintStyle: text.bodyLarge?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
            prefixIcon: prefixIcon,
            suffixIcon: suffixIcon,
            contentPadding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.md,
              vertical: 15,
            ),
            enabledBorder: base,
            disabledBorder: base,
            focusedBorder: base.copyWith(
              borderSide: BorderSide(
                color: hasError ? AppColors.error : AppColors.onSurface,
                width: 1.4,
              ),
            ),
            errorBorder: base.copyWith(
              borderSide: const BorderSide(
                color: AppColors.error,
                width: 1.4,
              ),
            ),
            border: base,
          ),
        ),
        if (hasError) ...[
          const SizedBox(height: AppSpacing.xs),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Padding(
                padding: EdgeInsets.only(top: 1),
                child: Icon(
                  Icons.error_outline,
                  size: 15,
                  color: AppColors.error,
                ),
              ),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  errorText!,
                  style: text.bodySmall?.copyWith(color: AppColors.error),
                ),
              ),
            ],
          ),
        ] else if (helperText != null) ...[
          const SizedBox(height: AppSpacing.xs),
          Text(
            helperText!,
            style: text.bodySmall?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ],
    );
  }
}
