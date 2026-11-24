import 'package:flutter/material.dart';

import '../../app/theme/tokens/color_tokens.dart';
import '../../app/theme/tokens/spacing_tokens.dart';
import '../../app/theme/tokens/typography_tokens.dart';

/// Dotted progress for a multi-step flow (visit steps, registration, booking).
///
/// Completed = filled `primary`, current = ringed `primary`, next = outline.
class AppStepIndicator extends StatelessWidget {
  const AppStepIndicator({
    super.key,
    required this.steps,
    required this.currentIndex,
  });

  final List<String> steps;

  /// Zero-based index of the step in progress.
  final int currentIndex;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Row(
          children: [
            for (var i = 0; i < steps.length; i++) ...[
              _Dot(
                index: i + 1,
                done: i < currentIndex,
                current: i == currentIndex,
              ),
              if (i < steps.length - 1)
                Expanded(
                  child: Container(
                    height: 2,
                    margin: const EdgeInsets.symmetric(
                      horizontal: AppSpacing.sm,
                    ),
                    color: i < currentIndex
                        ? AppColors.primary
                        : AppColors.outlineVariant,
                  ),
                ),
            ],
          ],
        ),
        const SizedBox(height: AppSpacing.md),
        Text(
          'Step ${currentIndex + 1} of ${steps.length} · ${steps[currentIndex]}',
          style: text.titleSmall,
        ),
      ],
    );
  }
}

class _Dot extends StatelessWidget {
  const _Dot({required this.index, required this.done, required this.current});

  final int index;
  final bool done;
  final bool current;

  @override
  Widget build(BuildContext context) {
    final filled = done || current;

    return Container(
      width: 28,
      height: 28,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: done ? AppColors.primary : Colors.transparent,
        shape: BoxShape.circle,
        border: Border.all(
          color: filled ? AppColors.primary : AppColors.outline,
          width: current ? 2 : 1,
        ),
      ),
      child: done
          ? const Icon(Icons.check, size: 16, color: AppColors.onPrimary)
          : Text(
              '$index',
              style: AppTypography.textTheme.labelMedium?.copyWith(
                color: current
                    ? AppColors.onSurface
                    : AppColors.onSurfaceVariant,
              ),
            ),
    );
  }
}
