import 'package:flutter/material.dart';

import '../../app/theme/tokens/color_tokens.dart';
import '../../app/theme/tokens/spacing_tokens.dart';
import '../../app/theme/tokens/typography_tokens.dart';

/// Hairline card around a Material [DataTable], so wide tables scroll
/// horizontally instead of overflowing a 390-wide screen.
class AppDataTable extends StatelessWidget {
  const AppDataTable({
    super.key,
    required this.columns,
    required this.rows,
    this.minWidth,
  });

  final List<DataColumn> columns;
  final List<DataRow> rows;
  final double? minWidth;

  @override
  Widget build(BuildContext context) {
    return Container(
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(AppRadius.card),
        border: Border.all(color: AppColors.outline),
      ),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: ConstrainedBox(
          constraints: BoxConstraints(minWidth: minWidth ?? 0),
          child: DataTable(
            columns: columns,
            rows: rows,
            showCheckboxColumn: false,
            headingRowHeight: 44,
            dataRowMinHeight: 56,
            dataRowMaxHeight: 72,
            horizontalMargin: AppSpacing.lg,
            columnSpacing: AppSpacing.xxl,
            dividerThickness: 1,
            headingRowColor: const WidgetStatePropertyAll(
              AppColors.surfaceContainerLow,
            ),
            headingTextStyle: AppTypography.textTheme.labelSmall?.copyWith(
              color: AppColors.onSurfaceVariant,
              letterSpacing: 0.5,
            ),
            dataTextStyle: AppTypography.textTheme.bodyMedium,
          ),
        ),
      ),
    );
  }
}

/// A left-aligned or right-aligned numeric column header.
DataColumn appColumn(String label, {bool numeric = false}) =>
    DataColumn(label: Text(label), numeric: numeric);
