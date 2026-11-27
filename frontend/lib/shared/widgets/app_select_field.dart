import 'package:flutter/material.dart';

import '../../app/theme/tokens/color_tokens.dart';
import '../../app/theme/tokens/spacing_tokens.dart';
import '../../app/theme/tokens/typography_tokens.dart';

/// A field styled exactly like [AppTextField] that opens a menu instead of a
/// keyboard: blood group, place, reason, any closed list.
class AppSelectField<T> extends StatelessWidget {
  const AppSelectField({
    super.key,
    required this.label,
    required this.value,
    required this.items,
    required this.labelOf,
    required this.onSelected,
    this.hint,
    this.helperText,
    this.errorText,
    this.menuWidth,
  });

  final String label;
  final T? value;
  final List<T> items;
  final String Function(T) labelOf;
  final ValueChanged<T> onSelected;
  final String? hint;
  final String? helperText;
  final String? errorText;
  final double? menuWidth;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    final hasError = errorText != null && errorText!.isNotEmpty;
    final borderColor = hasError ? AppColors.error : AppColors.outline;

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
        PopupMenuButton<T>(
          tooltip: label,
          elevation: 0,
          color: AppColors.surfaceContainerLowest,
          surfaceTintColor: Colors.transparent,
          position: PopupMenuPosition.under,
          constraints: BoxConstraints(
            minWidth: menuWidth ?? 200,
            maxWidth: menuWidth == null ? 320 : menuWidth! + 40,
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AppRadius.small),
            side: const BorderSide(color: AppColors.outline),
          ),
          onSelected: onSelected,
          itemBuilder: (context) => [
            for (final item in items)
              PopupMenuItem<T>(
                value: item,
                height: 44,
                child: Text(labelOf(item), style: text.bodyLarge),
              ),
          ],
          child: Container(
            height: 52,
            padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLowest,
              borderRadius: BorderRadius.circular(AppRadius.small),
              border: Border.all(color: borderColor),
            ),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    value == null ? (hint ?? '') : labelOf(value as T),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: text.bodyLarge?.copyWith(
                      color: value == null
                          ? AppColors.onSurfaceVariant
                          : AppColors.onSurface,
                    ),
                  ),
                ),
                const Icon(
                  Icons.keyboard_arrow_down,
                  size: 20,
                  color: AppColors.onSurfaceVariant,
                ),
              ],
            ),
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
