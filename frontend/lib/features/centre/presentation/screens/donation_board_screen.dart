import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/theme/tokens/color_tokens.dart';
import '../../../../app/theme/tokens/spacing_tokens.dart';
import '../../../../app/theme/tokens/typography_tokens.dart';
import '../../../../core/errors/api_error.dart';
import '../../../../data/models/centre_day.dart';
import '../../../../data/models/visit.dart';
import '../../../../shared/widgets/app_error_state.dart';
import '../../../../shared/widgets/app_loading_state.dart';
import '../../../../shared/widgets/app_page_header.dart';
import '../providers/centre_providers.dart';

/// C2 · Donation board — Centre staff, web 1440 (+ a scrollable mobile board).
///
/// Every open visit grouped by the step it stands on. Steps run in order, so the
/// board is the working queue of the centre: reception, interview, collection,
/// rest, then done.
class DonationBoardScreen extends ConsumerWidget {
  const DonationBoardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;
    // Demo control: `?state=empty` / `?state=unavailable`.
    final variant = GoRouterState.of(context).uri.queryParameters['state'];
    final board = ref.watch(donationBoardProvider(variant));

    return SingleChildScrollView(
      padding: wide
          ? const EdgeInsets.fromLTRB(
              AppSpacing.page,
              28,
              AppSpacing.page,
              AppSpacing.xxxl,
            )
          : const EdgeInsets.all(AppSpacing.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AppPageHeader(
            wide: wide,
            title: 'Donation board',
            subtitle: wide
                ? 'Every open visit by step, updated live. Steps always run in '
                      'order and cannot be skipped.'
                : 'Every open visit by step. Steps always run in order.',
          ),
          const SizedBox(height: AppSpacing.xl),
          board.when(
            loading: () => const AppLoadingState(label: 'Loading the board...'),
            error: (error, stackTrace) => AppErrorState(
              title: 'We could not load the board',
              message: error is ApiError
                  ? apiMessageFor(error)
                  : 'Check your connection and try again.',
              onRetry: () => ref.invalidate(donationBoardProvider),
            ),
            data: (columns) => _Board(columns: columns, wide: wide),
          ),
          const SizedBox(height: AppSpacing.xl),
          Text(
            'A refused visit leaves the board with no collection and no '
            'certificate. The national donation number is issued after '
            'collection.',
            style: AppTypography.textTheme.bodySmall?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

class _Board extends StatelessWidget {
  const _Board({required this.columns, required this.wide});

  final List<BoardColumn> columns;
  final bool wide;

  @override
  Widget build(BuildContext context) {
    if (wide) {
      return Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (var i = 0; i < columns.length; i++) ...[
            if (i > 0) const SizedBox(width: 14),
            Expanded(child: _Column(column: columns[i])),
          ],
        ],
      );
    }
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (var i = 0; i < columns.length; i++) ...[
            if (i > 0) const SizedBox(width: AppSpacing.md),
            SizedBox(width: 240, child: _Column(column: columns[i])),
          ],
        ],
      ),
    );
  }
}

class _Column extends StatelessWidget {
  const _Column({required this.column});

  final BoardColumn column;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(AppRadius.card),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  column.step.boardTitle,
                  style: text.titleSmall,
                ),
              ),
              const SizedBox(width: AppSpacing.sm),
              Text(
                '${column.count}',
                style: text.titleSmall?.copyWith(
                  color: AppColors.onSurfaceVariant,
                ),
              ),
            ],
          ),
          const SizedBox(height: 2),
          Text(
            column.caption,
            style: text.bodySmall?.copyWith(color: AppColors.onSurfaceVariant),
          ),
          const SizedBox(height: AppSpacing.md),
          if (column.visits.isEmpty)
            Text(
              'No visit',
              style: text.bodySmall?.copyWith(
                color: AppColors.onSurfaceVariant,
              ),
            )
          else
            for (var i = 0; i < column.visits.length; i++) ...[
              if (i > 0) const SizedBox(height: AppSpacing.sm),
              _VisitCard(visit: column.visits[i]),
            ],
        ],
      ),
    );
  }
}

class _VisitCard extends StatelessWidget {
  const _VisitCard({required this.visit});

  final Visit visit;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return Material(
      color: AppColors.surfaceContainerLowest,
      borderRadius: BorderRadius.circular(AppRadius.small),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppRadius.small),
        onTap: () => context.go('/centre/visits/${visit.id}'),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(visit.id, style: text.titleSmall),
              const SizedBox(height: 2),
              Text(
                visit.groupAndStart,
                style: text.bodySmall?.copyWith(
                  color: AppColors.onSurfaceVariant,
                ),
              ),
              if (visit.trailLabel != null) ...[
                const SizedBox(height: 2),
                Text(
                  visit.trailLabel!,
                  style: text.bodySmall?.copyWith(
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
