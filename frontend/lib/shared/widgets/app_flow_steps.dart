import 'package:flutter/material.dart';

import '../../app/theme/tokens/spacing_tokens.dart';
import 'app_chip.dart';

/// The `1 · Centre  2 · Time  3 · Confirm` chip row used by the booking and
/// move flows. The step in progress is the solid chip; steps already passed
/// can be tapped to go back.
class AppFlowSteps extends StatelessWidget {
  const AppFlowSteps({
    super.key,
    required this.steps,
    required this.currentIndex,
    this.onStepTapped,
  });

  final List<String> steps;

  /// Zero-based index of the step in progress.
  final int currentIndex;

  /// Called when a step that is already reachable is tapped.
  final ValueChanged<int>? onStepTapped;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: AppSpacing.md,
      runSpacing: AppSpacing.md,
      children: [
        for (var i = 0; i < steps.length; i++)
          AppChip(
            label: '${i + 1} · ${steps[i]}',
            selected: i == currentIndex,
            onTap: onStepTapped != null && i < currentIndex
                ? () => onStepTapped!(i)
                : null,
          ),
      ],
    );
  }
}
