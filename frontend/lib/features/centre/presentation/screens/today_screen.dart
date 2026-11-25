import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/theme/tokens/color_tokens.dart';
import '../../../../app/theme/tokens/spacing_tokens.dart';
import '../../../../app/theme/tokens/typography_tokens.dart';
import '../../../../core/errors/api_error.dart';
import '../../../../core/format/formatters.dart';
import '../../../../data/models/centre_day.dart';
import '../../../../shared/widgets/app_badge.dart';
import '../../../../shared/widgets/app_buttons.dart';
import '../../../../shared/widgets/app_card.dart';
import '../../../../shared/widgets/app_chip.dart';
import '../../../../shared/widgets/app_confirm_dialog.dart';
import '../../../../shared/widgets/app_data_table.dart';
import '../../../../shared/widgets/app_empty_state.dart';
import '../../../../shared/widgets/app_error_state.dart';
import '../../../../shared/widgets/app_loading_state.dart';
import '../../../../shared/widgets/app_page_header.dart';
import '../../../../shared/widgets/app_snack_bar.dart';
import '../../../../shared/widgets/app_stat_card.dart';
import '../providers/centre_providers.dart';

/// C1 · Today at the centre — Centre staff, web 1440 (+ a mobile layout).
///
/// The day's figures, the agenda with the one action each row needs, the walk-in
/// queue, the stock by group and the alert when a group is below its threshold.
class TodayScreen extends ConsumerWidget {
  const TodayScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;
    // Demo control: `?state=quiet` / `?state=unavailable`.
    final variant = GoRouterState.of(context).uri.queryParameters['state'];
    final day = ref.watch(centreDayProvider(variant));

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(centreDayProvider);
        await ref.read(centreDayProvider(variant).future);
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
            day.when(
              loading: () => const AppLoadingState(label: 'Loading the day...'),
              error: (error, stackTrace) => AppErrorState(
                title: 'We could not load the day',
                message: error is ApiError
                    ? apiMessageFor(error)
                    : 'Check your connection and try again.',
                onRetry: () => ref.invalidate(centreDayProvider),
              ),
              data: (value) => _Day(day: value, wide: wide),
            ),
          ],
        ),
      ),
    );
  }
}

class _Day extends ConsumerWidget {
  const _Day({required this.day, required this.wide});

  final CentreDay day;
  final bool wide;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        AppPageHeader(
          wide: wide,
          title: 'Today at the centre',
          subtitle: wide
              ? '${day.centreName} · '
                    '${AppFormatters.weekdayFullYear(day.date)} · '
                    'reception, medical interview and collection.'
              : '${day.centreName} · '
                    '${AppFormatters.weekdayFullYear(day.date)}',
        ),
        const SizedBox(height: AppSpacing.xl),
        _Stats(stats: day.stats, wide: wide),
        const SizedBox(height: AppSpacing.xl),
        if (wide)
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(flex: 780, child: _AgendaCard(day: day)),
              const SizedBox(width: AppSpacing.xxl),
              Expanded(flex: 448, child: _Side(day: day)),
            ],
          )
        else ...[
          _AgendaList(day: day),
          const SizedBox(height: AppSpacing.xl),
          _Side(day: day),
        ],
        const SizedBox(height: AppSpacing.xl),
        Text(
          'A step cannot be skipped or repeated. Each donation is 450 ml and the '
          'visit ends only after the minimum rest of 15 minutes.',
          style: AppTypography.textTheme.bodySmall?.copyWith(
            color: AppColors.onSurfaceVariant,
          ),
        ),
      ],
    );
  }
}

/// The four figures of the day.
class _Stats extends StatelessWidget {
  const _Stats({required this.stats, required this.wide});

  final CentreDayStats stats;
  final bool wide;

  @override
  Widget build(BuildContext context) {
    final cards = [
      AppStatCard(
        label: 'Arrivals expected',
        value: '${stats.booked}',
        caption: stats.bookedDetail,
      ),
      AppStatCard(
        label: 'Checked in',
        value: '${stats.receptionDone}',
        caption: 'reception step done',
      ),
      AppStatCard(
        label: 'In interview',
        value: '${stats.inInterview}',
        caption: '${stats.unfit} unfit so far',
      ),
      AppStatCard(
        label: 'Collected today',
        value: '${stats.collectedUnits}',
        caption: stats.collectedDetail,
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

// --- agenda ---------------------------------------------------------------

class _AgendaCard extends ConsumerWidget {
  const _AgendaCard({required this.day});

  final CentreDay day;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (day.agenda.isEmpty) {
      return const AppEmptyState(
        title: 'No appointment left today',
        message: 'Every arrival has been seen or closed.',
        icon: Icons.event_available_outlined,
      );
    }

    return AppDataTable(
      minWidth: 760,
      columns: [
        appColumn('TIME'),
        appColumn('DONOR'),
        appColumn('CANAL'),
        appColumn('STATUS'),
        appColumn('ACTION'),
      ],
      rows: [
        for (final row in day.agenda)
          DataRow(cells: _cells(context, ref, row)),
      ],
    );
  }

  List<DataCell> _cells(BuildContext context, WidgetRef ref, AgendaRow row) {
    return [
      DataCell(
        Text(row.time, style: AppFormatters.tabular(AppTypography.textTheme.titleSmall)),
      ),
      DataCell(
        Text(
          row.reference,
          style: AppFormatters.tabular(AppTypography.textTheme.bodyMedium),
        ),
      ),
      DataCell(AppChip(label: row.channel.label)),
      DataCell(AppBadge(label: row.status.label, tone: _tone(row.status))),
      DataCell(
        SizedBox(
          width: 108,
          child: AppOutlinedButton(
            label: row.status.action,
            height: 36,
            onPressed: () => _handleAgendaAction(context, ref, row),
          ),
        ),
      ),
    ];
  }

  static AppBadgeTone _tone(AgendaStatus status) => switch (status) {
    AgendaStatus.waiting => AppBadgeTone.neutral,
    AgendaStatus.interview => AppBadgeTone.warning,
    AgendaStatus.collected => AppBadgeTone.success,
    AgendaStatus.rest => AppBadgeTone.neutral,
    AgendaStatus.absent => AppBadgeTone.critical,
  };
}

/// The mobile agenda: one hairline row per arrival, with its action.
class _AgendaList extends ConsumerWidget {
  const _AgendaList({required this.day});

  final CentreDay day;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (day.agenda.isEmpty) {
      return const AppEmptyState(
        title: 'No appointment left today',
        message: 'Every arrival has been seen or closed.',
        icon: Icons.event_available_outlined,
      );
    }

    final text = AppTypography.textTheme;

    return AppCard(
      padding: const EdgeInsets.all(AppSpacing.lg),
      child: Column(
        children: [
          for (var i = 0; i < day.agenda.length; i++) ...[
            if (i > 0) const Divider(height: 1, color: AppColors.outlineVariant),
            Padding(
              padding: const EdgeInsets.symmetric(vertical: AppSpacing.md),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SizedBox(
                    width: 52,
                    child: Text(
                      day.agenda[i].time,
                      style: AppFormatters.tabular(text.titleSmall),
                    ),
                  ),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          day.agenda[i].reference,
                          style: AppFormatters.tabular(text.bodyMedium),
                        ),
                        const SizedBox(height: AppSpacing.sm),
                        Wrap(
                          spacing: AppSpacing.sm,
                          runSpacing: AppSpacing.sm,
                          crossAxisAlignment: WrapCrossAlignment.center,
                          children: [
                            AppChip(label: day.agenda[i].channel.label),
                            AppBadge(
                              label: day.agenda[i].status.label,
                              tone: _AgendaCard._tone(day.agenda[i].status),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: AppSpacing.sm),
                  SizedBox(
                    width: 92,
                    height: 48,
                    child: AppOutlinedButton(
                      label: day.agenda[i].status.action,
                      height: 48,
                      onPressed: () =>
                          _handleAgendaAction(context, ref, day.agenda[i]),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// What a row's one action does: open the visit, check the donor in or close an
/// absent appointment.
Future<void> _handleAgendaAction(
  BuildContext context,
  WidgetRef ref,
  AgendaRow row,
) async {
  try {
    if (row.visitId != null) {
      context.go('/centre/visits/${row.visitId}');
      return;
    }
    if (row.status == AgendaStatus.absent) {
      final confirmed = await showAppConfirm(
        context,
        title: 'Close this appointment?',
        message:
            '${row.reference} will be marked absent. This cannot be undone.',
        confirmLabel: 'Mark absent',
        destructive: true,
      );
      if (!confirmed || !context.mounted) return;
      await ref.read(visitRepositoryProvider).markAbsent(row.reference);
      ref.invalidate(centreDayProvider);
      if (!context.mounted) return;
      showAppSnack(
        context,
        '${row.reference} marked absent.',
        tone: AppSnackTone.critical,
      );
      return;
    }

    // Waiting → the reception checks the donor in and opens the visit.
    final visit = await ref
        .read(visitRepositoryProvider)
        .start(cin: row.cin ?? '', rendezVousId: row.reference);
    ref.invalidate(centreDayProvider);
    if (!context.mounted) return;
    showAppSnack(context, '${visit.donorName} checked in.');
    context.go('/centre/visits/${visit.id}');
  } on ApiError catch (error) {
    if (!context.mounted) return;
    showAppSnack(context, apiMessageFor(error), tone: AppSnackTone.critical);
  }
}

// --- side column ----------------------------------------------------------

class _Side extends StatelessWidget {
  const _Side({required this.day});

  final CentreDay day;

  @override
  Widget build(BuildContext context) {
    final message = day.lowStockMessage;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (day.queue.isNotEmpty)
          Semantics(
            label: 'Donors waiting in reception',
            child: AppCard(
              child: Column(
                children: [
                  for (var i = 0; i < day.queue.length; i++) ...[
                    if (i > 0) const SizedBox(height: AppSpacing.sm),
                    _QueueRow(entry: day.queue[i]),
                  ],
                ],
              ),
            ),
          ),
        if (day.stock.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.md),
          Semantics(
            label: 'Collected units by blood group',
            child: AppCard(
              child: Column(
                children: [
                  for (var i = 0; i < day.stock.length; i++) ...[
                    if (i > 0) const SizedBox(height: AppSpacing.sm),
                    _StockRow(line: day.stock[i]),
                  ],
                ],
              ),
            ),
          ),
        ],
        if (message != null) ...[
          const SizedBox(height: AppSpacing.md),
          _Alert(message: message),
        ],
      ],
    );
  }
}

class _QueueRow extends StatelessWidget {
  const _QueueRow({required this.entry});

  final QueueEntry entry;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    return Row(
      children: [
        Expanded(
          child: Text(
            '${entry.reference}  ·  ${entry.bloodGroup.code}',
            style: AppFormatters.tabular(text.bodyMedium),
          ),
        ),
        const SizedBox(width: AppSpacing.md),
        Text(
          '${entry.waitingMinutes} min',
          style: AppFormatters.tabular(text.titleSmall),
        ),
      ],
    );
  }
}

class _StockRow extends StatelessWidget {
  const _StockRow({required this.line});

  final CentreStockLine line;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    return Row(
      children: [
        Expanded(child: Text(line.bloodGroup.code, style: text.bodyMedium)),
        Text('${line.units}', style: AppFormatters.tabular(text.titleSmall)),
      ],
    );
  }
}

class _Alert extends StatelessWidget {
  const _Alert({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: AppColors.warningContainer,
        borderRadius: BorderRadius.circular(AppRadius.card),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(
            Icons.warning_amber_rounded,
            size: 18,
            color: AppColors.onWarningContainer,
          ),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: Text(
              message,
              style: AppTypography.textTheme.bodyMedium?.copyWith(
                color: AppColors.onWarningContainer,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
