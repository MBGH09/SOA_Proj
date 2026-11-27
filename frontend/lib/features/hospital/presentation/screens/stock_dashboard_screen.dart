import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/theme/tokens/color_tokens.dart';
import '../../../../app/theme/tokens/spacing_tokens.dart';
import '../../../../app/theme/tokens/typography_tokens.dart';
import '../../../../core/errors/api_error.dart';
import '../../../../core/format/formatters.dart';
import '../../../../data/mock/mock_request_repository.dart';
import '../../../../data/models/stock_level.dart';
import '../../../../shared/widgets/app_badge.dart';
import '../../../../shared/widgets/app_chip.dart';
import '../../../../shared/widgets/app_error_state.dart';
import '../../../../shared/widgets/app_loading_state.dart';
import '../../../../shared/widgets/app_page_header.dart';
import '../providers/hospital_providers.dart';
import '../widgets/stock_board.dart';

/// H4 · Stock dashboard — Hospital (also the admin read-only view).
///
/// Live levels for one place, the four figures, the 8 × 3 board and every
/// level below its alert threshold.
class StockDashboardScreen extends ConsumerStatefulWidget {
  const StockDashboardScreen({super.key});

  @override
  ConsumerState<StockDashboardScreen> createState() =>
      _StockDashboardScreenState();
}

class _StockDashboardScreenState extends ConsumerState<StockDashboardScreen> {
  String _siteId = hospitalId;

  @override
  Widget build(BuildContext context) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;
    final variant = GoRouterState.of(context).uri.queryParameters['state'];
    final sites = ref.watch(stockSitesProvider);
    final snapshot = ref.watch(
      stockSnapshotProvider((siteId: _siteId, variant: variant)),
    );

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(stockSitesProvider);
        ref.invalidate(stockSnapshotProvider);
        await ref.read(
          stockSnapshotProvider((siteId: _siteId, variant: variant)).future,
        );
      },
      child: SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
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
              title: 'Stock',
              subtitle:
                  'Live levels for every place, blood group and component. '
                  'Each level is compared with its alert threshold.',
            ),
            const SizedBox(height: AppSpacing.lg),
            sites.when(
              loading: () => const AppLoadingState(
                label: 'Loading places...',
                padding: EdgeInsets.symmetric(vertical: AppSpacing.sm),
              ),
              error: (error, stackTrace) => AppErrorState(
                title: 'We could not load the places',
                message: error is ApiError
                    ? apiMessageFor(error)
                    : 'Check your connection and try again.',
                onRetry: () => ref.invalidate(stockSitesProvider),
              ),
              data: (places) => _Places(
                places: places,
                selectedId: _siteId,
                wide: wide,
                onSelected: (id) => setState(() => _siteId = id),
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            snapshot.when(
              loading: () =>
                  const AppLoadingState(label: 'Reading the stock levels...'),
              error: (error, stackTrace) => AppErrorState(
                title: 'We could not load the stock',
                message: error is ApiError
                    ? apiMessageFor(error)
                    : 'Check your connection and try again.',
                onRetry: () => ref.invalidate(stockSnapshotProvider),
              ),
              data: (value) => _Board(snapshot: value, wide: wide),
            ),
            const SizedBox(height: AppSpacing.xl),
            Text(
              'This hospital keeps a small just-in-time stock. The alert '
              'threshold is set per place, blood group and component.',
              style: AppTypography.textTheme.bodySmall?.copyWith(
                color: AppColors.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Places extends StatelessWidget {
  const _Places({
    required this.places,
    required this.selectedId,
    required this.wide,
    required this.onSelected,
  });

  final List<StockSite> places;
  final String selectedId;
  final bool wide;
  final ValueChanged<String> onSelected;

  @override
  Widget build(BuildContext context) {
    final chips = Wrap(
      spacing: AppSpacing.sm,
      runSpacing: AppSpacing.sm,
      children: [
        for (final place in places)
          AppChip(
            label: place.chipLabel,
            selected: place.id == selectedId,
            onTap: () => onSelected(place.id),
          ),
      ],
    );

    final live = const AppBadge(
      label: 'Updating live',
      tone: AppBadgeTone.success,
    );

    if (!wide) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          chips,
          const SizedBox(height: AppSpacing.md),
          live,
        ],
      );
    }

    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Expanded(child: chips),
        live,
      ],
    );
  }
}

class _Board extends StatelessWidget {
  const _Board({required this.snapshot, required this.wide});

  final StockSnapshot snapshot;
  final bool wide;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _BoardStats(stats: snapshot.stats, wide: wide),
        const SizedBox(height: AppSpacing.lg),
        const StockLegend(),
        const SizedBox(height: AppSpacing.sm),
        if (!wide)
          Text(
            'Swipe sideways to see every component.',
            style: AppTypography.textTheme.bodySmall?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
        const SizedBox(height: AppSpacing.lg),
        StockBoard(snapshot: snapshot),
        const SizedBox(height: AppSpacing.xl),
        BelowThresholdList(levels: snapshot.belowThreshold),
      ],
    );
  }
}

/// The four bare figures above the board, as in the frame (no cards).
class _BoardStats extends StatelessWidget {
  const _BoardStats({required this.stats, required this.wide});

  final StockStats stats;
  final bool wide;

  @override
  Widget build(BuildContext context) {
    final figures = [
      _Figure(label: 'UNITS AVAILABLE', value: stats.available),
      _Figure(label: 'RESERVED', value: stats.reserved),
      _Figure(label: 'IN QUALIFICATION', value: stats.inQualification),
      _Figure(
        label: 'BELOW THRESHOLD',
        value: stats.belowThreshold,
        critical: true,
      ),
    ];

    if (wide) {
      return Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (var i = 0; i < figures.length; i++) ...[
            if (i > 0) const SizedBox(width: AppSpacing.xxl),
            Expanded(child: figures[i]),
          ],
        ],
      );
    }

    return LayoutBuilder(
      builder: (context, constraints) {
        final width = (constraints.maxWidth - AppSpacing.xxl) / 2;
        return Wrap(
          spacing: AppSpacing.xxl,
          runSpacing: AppSpacing.lg,
          children: [
            for (final figure in figures) SizedBox(width: width, child: figure),
          ],
        );
      },
    );
  }
}

class _Figure extends StatelessWidget {
  const _Figure({
    required this.label,
    required this.value,
    this.critical = false,
  });

  final String label;
  final int value;
  final bool critical;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          '$value',
          style: AppFormatters.tabular(
            text.headlineSmall?.copyWith(
              fontSize: 30,
              color: critical ? AppColors.error : AppColors.onSurface,
            ),
          ),
        ),
        const SizedBox(height: 2),
        Text(
          label,
          style: text.labelSmall?.copyWith(
            color: AppColors.onSurfaceVariant,
            letterSpacing: 0.5,
          ),
        ),
      ],
    );
  }
}
