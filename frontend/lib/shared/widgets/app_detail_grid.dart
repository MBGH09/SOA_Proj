import 'package:flutter/material.dart';

import '../../app/theme/tokens/color_tokens.dart';
import '../../app/theme/tokens/spacing_tokens.dart';
import '../../app/theme/tokens/typography_tokens.dart';
import '../../core/format/formatters.dart';

/// One label/value pair of an [AppDetailGrid].
class AppDetailEntry {
  const AppDetailEntry({required this.label, required this.value});

  final String label;
  final String value;
}

/// Micro-label above its value, laid out in [columns] equal columns.
///
/// The donation certificate and the verification answer both print their
/// fields this way (tokens.md §5): quiet label ink, reading ink for the value,
/// optional tabular figures for references and quantities.
class AppDetailGrid extends StatelessWidget {
  const AppDetailGrid({
    super.key,
    required this.entries,
    this.columns = 2,
    this.gap = AppSpacing.lg,
    this.runGap = AppSpacing.md,
    this.tabular = false,
  });

  final List<AppDetailEntry> entries;
  final int columns;
  final double gap;
  final double runGap;
  final bool tabular;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    final columnCount = columns < 1 ? 1 : columns;

    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.hasBoundedWidth
            ? (constraints.maxWidth - gap * (columnCount - 1)) / columnCount
            : null;

        Widget cell(AppDetailEntry entry) => Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              entry.label,
              style: text.labelSmall?.copyWith(
                color: AppColors.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              entry.value,
              style: tabular
                  ? AppFormatters.tabular(text.titleSmall)
                  : text.titleSmall,
            ),
          ],
        );

        return Wrap(
          spacing: gap,
          runSpacing: runGap,
          children: [
            for (final entry in entries)
              if (width == null)
                cell(entry)
              else
                SizedBox(width: width, child: cell(entry)),
          ],
        );
      },
    );
  }
}
