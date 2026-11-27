import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/theme/tokens/color_tokens.dart';
import '../../../../app/theme/tokens/spacing_tokens.dart';
import '../../../../app/theme/tokens/typography_tokens.dart';
import '../../../../core/errors/api_error.dart';
import '../../../../core/format/formatters.dart';
import '../../../../core/providers/core_providers.dart';
import '../../../../data/models/blood_request.dart';
import '../../../../shared/widgets/app_card.dart';
import '../../../../shared/widgets/app_chip.dart';
import '../../../../shared/widgets/app_data_table.dart';
import '../../../../shared/widgets/app_empty_state.dart';
import '../../../../shared/widgets/app_error_state.dart';
import '../../../../shared/widgets/app_loading_state.dart';
import '../../../../shared/widgets/app_page_header.dart';
import '../../../../shared/widgets/app_stat_card.dart';
import '../../../../shared/widgets/app_text_field.dart';
import '../providers/hospital_providers.dart';
import '../widgets/request_status.dart';

/// The filters above the request table (H2).
enum RequestFilter {
  all('All'),
  open('Open'),
  reserved('Reserved'),
  inDelivery('In delivery'),
  delivered('Delivered'),
  critical('Critical');

  const RequestFilter(this.label);

  final String label;

  bool matches(BloodRequest request) => switch (this) {
    RequestFilter.all => true,
    RequestFilter.open => request.status.isOpen,
    RequestFilter.reserved =>
      request.status == RequestStatus.satisfied &&
          request.deliveryStage == DeliveryStage.reserved,
    RequestFilter.inDelivery =>
      request.status == RequestStatus.supplying ||
          request.deliveryStage == DeliveryStage.inTransit,
    RequestFilter.delivered => request.status == RequestStatus.satisfied,
    RequestFilter.critical => request.urgency.isCritical,
  };
}

/// H2 · Requests — Hospital.
class RequestsListScreen extends ConsumerStatefulWidget {
  const RequestsListScreen({super.key});

  @override
  ConsumerState<RequestsListScreen> createState() => _RequestsListScreenState();
}

class _RequestsListScreenState extends ConsumerState<RequestsListScreen> {
  final _search = TextEditingController();
  RequestFilter _filter = RequestFilter.all;

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;
    final variant = GoRouterState.of(context).uri.queryParameters['state'];
    final requests = ref.watch(bloodRequestsProvider(variant));
    final summary = ref.watch(requestSummaryProvider(variant));
    final now = ref.watch(clockProvider).now();

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(bloodRequestsProvider);
        ref.invalidate(requestSummaryProvider);
        await ref.read(bloodRequestsProvider(variant).future);
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
              title: 'Requests',
              subtitle:
                  'Every blood request from this hospital. A request uses the '
                  'local bank first, then the regional centre or the national '
                  'service.',
            ),
            const SizedBox(height: AppSpacing.xl),
            _Filters(
              wide: wide,
              selected: _filter,
              search: _search,
              onSelected: (filter) => setState(() => _filter = filter),
              onSearch: () => setState(() {}),
            ),
            const SizedBox(height: AppSpacing.xl),
            requests.when(
              loading: () =>
                  const AppLoadingState(label: 'Loading blood requests...'),
              error: (error, stackTrace) => AppErrorState(
                title: 'We could not load the requests',
                message: error is ApiError
                    ? apiMessageFor(error)
                    : 'Check your connection and try again.',
                onRetry: () {
                  ref.invalidate(bloodRequestsProvider);
                  ref.invalidate(requestSummaryProvider);
                },
              ),
              data: (list) {
                final visible = _visible(list);
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (summary.value != null)
                      _Stats(summary: summary.value!, wide: wide)
                    else
                      Text(
                        'The summary figures are unavailable right now.',
                        style: AppTypography.textTheme.bodySmall?.copyWith(
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    const SizedBox(height: AppSpacing.xl),
                    if (visible.isEmpty)
                      AppEmptyState(
                        title: list.isEmpty
                            ? 'No request yet'
                            : 'No request matches this filter',
                        message: list.isEmpty
                            ? 'A request appears here as soon as the hospital '
                                  'sends it.'
                            : 'Try All, or clear the search.',
                        icon: Icons.inbox_outlined,
                        action: list.isEmpty
                            ? AppChip(
                                label: 'New request',
                                onTap: () =>
                                    context.go('/hospital/requests/new'),
                              )
                            : AppChip(
                                label: 'Clear filters',
                                onTap: () => setState(() {
                                  _filter = RequestFilter.all;
                                  _search.clear();
                                }),
                              ),
                      )
                    else if (wide)
                      _RequestTable(
                        requests: visible,
                        now: now,
                        onOpen: (id) => context.go('/hospital/requests/$id'),
                      )
                    else
                      _RequestCards(
                        requests: visible,
                        now: now,
                        onOpen: (id) => context.go('/hospital/requests/$id'),
                      ),
                    const SizedBox(height: AppSpacing.xl),
                    Text(
                      'A request is checked against stock and reserved in one '
                      'operation, so two hospitals can never take the same '
                      'pouches.',
                      style: AppTypography.textTheme.bodySmall?.copyWith(
                        color: AppColors.onSurfaceVariant,
                      ),
                    ),
                  ],
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  List<BloodRequest> _visible(List<BloodRequest> all) {
    final query = _search.text.trim().toLowerCase();
    return all
        .where(_filter.matches)
        .where(
          (request) =>
              query.isEmpty ||
              request.id.toLowerCase().contains(query) ||
              request.patientRef.toLowerCase().contains(query),
        )
        .toList(growable: false);
  }
}

class _Filters extends StatelessWidget {
  const _Filters({
    required this.wide,
    required this.selected,
    required this.search,
    required this.onSelected,
    required this.onSearch,
  });

  final bool wide;
  final RequestFilter selected;
  final TextEditingController search;
  final ValueChanged<RequestFilter> onSelected;
  final VoidCallback onSearch;

  @override
  Widget build(BuildContext context) {
    final chips = Wrap(
      spacing: AppSpacing.sm,
      runSpacing: AppSpacing.sm,
      children: [
        for (final filter in RequestFilter.values)
          AppChip(
            label: filter.label,
            selected: filter == selected,
            onTap: () => onSelected(filter),
          ),
      ],
    );

    final field = AppTextField(
      label: 'SEARCH',
      controller: search,
      hint: 'Search by reference',
      prefixIcon: const Icon(
        Icons.search,
        size: 20,
        color: AppColors.onSurfaceVariant,
      ),
      onChanged: (_) => onSearch(),
    );

    if (!wide) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          chips,
          const SizedBox(height: AppSpacing.md),
          field,
        ],
      );
    }

    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Expanded(child: chips),
        const SizedBox(width: AppSpacing.xxl),
        SizedBox(width: 320, child: field),
      ],
    );
  }
}

class _Stats extends StatelessWidget {
  const _Stats({required this.summary, required this.wide});

  final RequestSummary summary;
  final bool wide;

  @override
  Widget build(BuildContext context) {
    final cards = [
      AppStatCard(
        label: 'Open requests',
        value: '${summary.openRequests}',
        caption: 'Waiting or on the way',
        background: AppColors.surfaceContainerLow,
        bordered: false,
      ),
      AppStatCard(
        label: 'In delivery',
        value: '${summary.inDelivery}',
        caption: 'Pouches in transit',
        background: AppColors.surfaceContainerLow,
        bordered: false,
      ),
      AppStatCard(
        label: 'Delivered this week',
        value: '${summary.deliveredThisWeek}',
        caption: 'Counted in the bank',
        background: AppColors.surfaceContainerLow,
        bordered: false,
      ),
      AppStatCard(
        label: 'Units needed today',
        value: '${summary.unitsNeededToday}',
        caption: 'Across open requests',
        background: AppColors.surfaceContainerLow,
        bordered: false,
      ),
    ];

    if (wide) {
      return Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (var i = 0; i < cards.length; i++) ...[
            if (i > 0) const SizedBox(width: AppSpacing.md),
            Expanded(child: cards[i]),
          ],
        ],
      );
    }

    return LayoutBuilder(
      builder: (context, constraints) {
        final width = (constraints.maxWidth - AppSpacing.md) / 2;
        return Wrap(
          spacing: AppSpacing.md,
          runSpacing: AppSpacing.md,
          children: [
            for (final card in cards) SizedBox(width: width, child: card),
          ],
        );
      },
    );
  }
}

class _RequestTable extends StatelessWidget {
  const _RequestTable({
    required this.requests,
    required this.now,
    required this.onOpen,
  });

  final List<BloodRequest> requests;
  final DateTime now;
  final ValueChanged<String> onOpen;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        return AppDataTable(
          minWidth: constraints.maxWidth,
          columns: [
            appColumn('REFERENCE'),
            appColumn('NEEDED'),
            appColumn('UNITS', numeric: true),
            appColumn('URGENCY'),
            appColumn('STATUS'),
            appColumn('ETA'),
            appColumn('UPDATED'),
            appColumn(''),
          ],
          rows: [
            for (final request in requests)
              DataRow(
                onSelectChanged: (_) => onOpen(request.id),
                cells: [
                  DataCell(
                    Text(
                      request.id,
                      style: AppFormatters.tabular(
                        AppTypography.textTheme.titleSmall,
                      ),
                    ),
                  ),
                  DataCell(Text(request.neededLine)),
                  DataCell(
                    Text(
                      '${request.units}',
                      style: AppFormatters.tabular(
                        AppTypography.textTheme.bodyMedium,
                      ),
                    ),
                  ),
                  DataCell(urgencyBadge(request.urgency)),
                  DataCell(statusBadge(request.status)),
                  DataCell(
                    Text(
                      request.etaLabel,
                      style: AppFormatters.tabular(
                        AppTypography.textTheme.bodyMedium,
                      ),
                    ),
                  ),
                  DataCell(
                    Text(
                      AppFormatters.ageFrom(request.updatedAt, now),
                      style: AppTypography.textTheme.bodyMedium?.copyWith(
                        color: AppColors.onSurfaceVariant,
                      ),
                    ),
                  ),
                  const DataCell(
                    Icon(
                      Icons.chevron_right,
                      size: 20,
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
          ],
        );
      },
    );
  }
}

class _RequestCards extends StatelessWidget {
  const _RequestCards({
    required this.requests,
    required this.now,
    required this.onOpen,
  });

  final List<BloodRequest> requests;
  final DateTime now;
  final ValueChanged<String> onOpen;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return Column(
      children: [
        for (var i = 0; i < requests.length; i++) ...[
          if (i > 0) const SizedBox(height: AppSpacing.md),
          Material(
            color: Colors.transparent,
            child: InkWell(
              onTap: () => onOpen(requests[i].id),
              borderRadius: BorderRadius.circular(AppRadius.card),
              child: AppCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            requests[i].id,
                            style: AppFormatters.tabular(text.titleSmall),
                          ),
                        ),
                        const Icon(
                          Icons.chevron_right,
                          size: 20,
                          color: AppColors.onSurfaceVariant,
                        ),
                      ],
                    ),
                    const SizedBox(height: AppSpacing.xs),
                    Text(requests[i].neededLine, style: text.bodyMedium),
                    const SizedBox(height: AppSpacing.sm),
                    Wrap(
                      spacing: AppSpacing.sm,
                      runSpacing: AppSpacing.sm,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        urgencyBadge(requests[i].urgency),
                        statusBadge(requests[i].status),
                      ],
                    ),
                    const SizedBox(height: AppSpacing.md),
                    Row(
                      children: [
                        Text(
                          requests[i].unitsLine,
                          style: text.bodySmall?.copyWith(
                            color: AppColors.onSurfaceVariant,
                          ),
                        ),
                        const Spacer(),
                        Text(
                          AppFormatters.ageFrom(requests[i].updatedAt, now),
                          style: text.bodySmall?.copyWith(
                            color: AppColors.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ],
    );
  }
}
