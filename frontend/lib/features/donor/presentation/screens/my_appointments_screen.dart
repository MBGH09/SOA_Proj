import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/theme/tokens/color_tokens.dart';
import '../../../../app/theme/tokens/spacing_tokens.dart';
import '../../../../app/theme/tokens/typography_tokens.dart';
import '../../../../core/format/formatters.dart';
import '../../../../data/models/appointment.dart';
import '../../../../data/models/appointment_summary.dart';
import '../../../../shared/widgets/app_badge.dart';
import '../../../../shared/widgets/app_buttons.dart';
import '../../../../shared/widgets/app_card.dart';
import '../../../../shared/widgets/app_chip.dart';
import '../../../../shared/widgets/app_confirm_dialog.dart';
import '../../../../shared/widgets/app_divider.dart';
import '../../../../shared/widgets/app_empty_state.dart';
import '../../../../shared/widgets/app_error_state.dart';
import '../../../../shared/widgets/app_icon_avatar.dart';
import '../../../../shared/widgets/app_loading_state.dart';
import '../../../../shared/widgets/app_page_header.dart';
import '../../../../shared/widgets/app_snack_bar.dart';
import '../providers/appointment_providers.dart';

/// 05 · My appointments — Donor, mobile 390 and web 1440.
class MyAppointmentsScreen extends ConsumerStatefulWidget {
  const MyAppointmentsScreen({super.key});

  @override
  ConsumerState<MyAppointmentsScreen> createState() =>
      _MyAppointmentsScreenState();
}

class _MyAppointmentsScreenState extends ConsumerState<MyAppointmentsScreen> {
  /// Mobile only: 0 = Upcoming, 1 = Past.
  int _tab = 0;

  @override
  Widget build(BuildContext context) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;

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
            title: 'My appointments',
            subtitle: wide
                ? 'One active appointment per day. Cancel or move it from home until the visit starts.'
                : 'One active appointment per day. You can cancel or move it until the visit starts.',
          ),
          const SizedBox(height: AppSpacing.xl),
          if (wide) _wideBody() else _mobileBody(),
        ],
      ),
    );
  }

  // --- web -----------------------------------------------------------------

  Widget _wideBody() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(flex: 5, child: _listColumn(wide: true)),
            const SizedBox(width: AppSpacing.xxl),
            Expanded(flex: 3, child: _sideColumn()),
          ],
        ),
        const SizedBox(height: AppSpacing.lg),
        _footnote(),
      ],
    );
  }

  // --- mobile --------------------------------------------------------------

  Widget _mobileBody() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            AppChip(
              label: 'Upcoming',
              selected: _tab == 0,
              onTap: () => setState(() => _tab = 0),
            ),
            const SizedBox(width: AppSpacing.md),
            AppChip(
              label: 'Past',
              selected: _tab == 1,
              onTap: () => setState(() => _tab = 1),
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.lg),
        if (_tab == 1)
          _pastSection(wide: false)
        else ...[
          _upcomingSection(wide: false),
          const SizedBox(height: AppSpacing.xl),
          _pastSection(wide: false),
        ],
        const SizedBox(height: AppSpacing.lg),
        _footnote(),
      ],
    );
  }

  // --- columns -------------------------------------------------------------

  Widget _listColumn({required bool wide}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _upcomingSection(wide: wide),
        const SizedBox(height: AppSpacing.xl),
        _pastSection(wide: wide),
      ],
    );
  }

  Widget _upcomingSection({required bool wide}) {
    final upcoming = ref.watch(upcomingAppointmentsProvider);

    return upcoming.when(
      loading: () => const AppLoadingState(label: 'Loading appointments...'),
      error: (error, stackTrace) => AppErrorState(
        title: 'We could not load your appointments',
        message: 'Check your connection and try again.',
        onRetry: () => ref.invalidate(upcomingAppointmentsProvider),
      ),
      data: (items) {
        if (items.isEmpty) {
          return AppEmptyState(
            title: 'No appointment booked',
            message: 'Book a slot at a centre near you. You can change it until '
                'the visit starts.',
            icon: Icons.event_available_outlined,
            action: wide
                ? SizedBox(
                    width: 200,
                    child: AppOutlinedButton(
                      label: 'Book a slot',
                      onPressed: () => context.go('/donor/book'),
                    ),
                  )
                : AppFilledButton(
                    label: 'Book a slot',
                    height: 48,
                    onPressed: () => context.go('/donor/book'),
                  ),
          );
        }
        return Column(
          children: [
            for (var i = 0; i < items.length; i++) ...[
              if (i > 0) const SizedBox(height: AppSpacing.lg),
              _UpcomingCard(
                appointment: items[i],
                wide: wide,
                onMove: () => context.go(
                  '/donor/appointments/move?id=${items[i].id}',
                ),
                onCancel: () => _cancel(items[i]),
              ),
            ],
          ],
        );
      },
    );
  }

  Widget _pastSection({required bool wide}) {
    final past = ref.watch(pastAppointmentsProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          wide ? 'PAST VISITS' : 'PAST',
          style: AppTypography.textTheme.labelSmall?.copyWith(
            color: AppColors.onSurfaceVariant,
            letterSpacing: 0.8,
          ),
        ),
        const SizedBox(height: AppSpacing.md),
        past.when(
          loading: () => const AppLoadingState(label: 'Loading past visits...'),
          error: (error, stackTrace) => AppErrorState(
            title: 'We could not load your past visits',
            message: 'Check your connection and try again.',
            onRetry: () => ref.invalidate(pastAppointmentsProvider),
          ),
          data: (items) {
            if (items.isEmpty) {
              return AppCard(
                child: Text(
                  'Your completed donations will be listed here.',
                  style: AppTypography.textTheme.bodyMedium?.copyWith(
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              );
            }
            return Column(
              children: [
                for (var i = 0; i < items.length; i++) ...[
                  if (i > 0) const SizedBox(height: AppSpacing.md),
                  _PastTile(appointment: items[i], wide: wide),
                ],
              ],
            );
          },
        ),
      ],
    );
  }

  Widget _sideColumn() {
    final summary = ref.watch(appointmentSummaryProvider);

    return summary.when(
      loading: () => const AppLoadingState(label: 'Loading your summary...'),
      error: (error, stackTrace) => AppErrorState(
        title: 'We could not load your summary',
        message: 'Check your connection and try again.',
        onRetry: () => ref.invalidate(appointmentSummaryProvider),
      ),
      data: (value) => Column(
        children: [
          _SummaryCard(summary: value),
          const SizedBox(height: AppSpacing.lg),
          const _RulesCard(),
        ],
      ),
    );
  }

  Widget _footnote() {
    return Text(
      'A missed appointment is recorded the day after. Completed visits keep '
      'their certificate.',
      style: AppTypography.textTheme.bodySmall?.copyWith(
        color: AppColors.onSurfaceVariant,
      ),
    );
  }

  Future<void> _cancel(Appointment appointment) async {
    final confirmed = await showAppConfirm(
      context,
      title: 'Cancel this appointment?',
      message:
          'Your slot on ${AppFormatters.weekdayFullTime(appointment.startsAt)} '
          'at ${appointment.centreName} will be released for other donors.',
      confirmLabel: 'Cancel appointment',
      cancelLabel: 'Keep it',
      destructive: true,
    );
    if (!confirmed || !mounted) return;

    await ref.read(appointmentRepositoryProvider).cancel(appointment.id);
    if (!mounted) return;
    ref.invalidate(upcomingAppointmentsProvider);
    ref.invalidate(appointmentSummaryProvider);
    showAppSnack(context, 'Appointment cancelled.', tone: AppSnackTone.neutral);
  }
}

// --- cards ------------------------------------------------------------------

class _UpcomingCard extends StatelessWidget {
  const _UpcomingCard({
    required this.appointment,
    required this.wide,
    required this.onMove,
    required this.onCancel,
  });

  final Appointment appointment;
  final bool wide;
  final VoidCallback onMove;
  final VoidCallback onCancel;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(child: Text(appointment.centreName, style: text.titleMedium)),
              const SizedBox(width: AppSpacing.md),
              AppBadge(label: appointment.status.label),
            ],
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            AppFormatters.weekdayFullTime(appointment.startsAt),
            style: text.titleLarge,
          ),
          const SizedBox(height: 6),
          Text(
            wide ? appointment.metaLine : appointment.metaLineMobile,
            style: text.bodyMedium?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
          if (wide)
            Row(
              children: [
                SizedBox(
                  width: 180,
                  child: AppOutlinedButton(label: 'Move', onPressed: onMove),
                ),
                const SizedBox(width: AppSpacing.md),
                SizedBox(
                  width: 180,
                  child: AppOutlinedButton(
                    label: 'Cancel',
                    critical: true,
                    onPressed: onCancel,
                  ),
                ),
              ],
            )
          else
            Row(
              children: [
                Expanded(
                  child: AppOutlinedButton(
                    label: 'Move',
                    height: 48,
                    onPressed: onMove,
                  ),
                ),
                const SizedBox(width: AppSpacing.md),
                Expanded(
                  child: AppOutlinedButton(
                    label: 'Cancel',
                    height: 48,
                    critical: true,
                    onPressed: onCancel,
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }
}

class _PastTile extends StatelessWidget {
  const _PastTile({required this.appointment, required this.wide});

  final Appointment appointment;
  final bool wide;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    final day = appointment.collectedAt ?? appointment.startsAt;
    final certificate = appointment.certificateNumber;

    final title = wide
        ? appointment.centreName
        : '${appointment.centreName} — ${AppFormatters.longDate(day)}';
    final subtitle = wide
        ? [
            AppFormatters.fullDate(day),
            appointment.component.toLowerCase(),
            ?certificate,
          ].join(' · ')
        : [appointment.component, ?certificate].join(' · ');

    return Container(
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(AppRadius.card),
        border: Border.all(color: AppColors.outline),
      ),
      child: Row(
        children: [
          const AppIconAvatar(size: 44, iconSize: 20),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: text.titleSmall),
                const SizedBox(height: 2),
                Text(
                  subtitle,
                  style: text.bodyMedium?.copyWith(
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({required this.summary});

  final AppointmentSummary summary;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    final next = summary.nextAt;

    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'SUMMARY',
            style: text.labelSmall?.copyWith(
              color: AppColors.onSurfaceVariant,
              letterSpacing: 0.8,
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
          _SummaryLine(label: 'Total donations', value: '${summary.totalDonations}'),
          const SizedBox(height: AppSpacing.md),
          _SummaryLine(
            label: 'Last donation',
            value: AppFormatters.fullDate(summary.lastDonation),
          ),
          const SizedBox(height: AppSpacing.md),
          _SummaryLine(
            label: 'Next appointment',
            value: next == null
                ? 'None'
                : AppFormatters.weekdayDayMonthTime(next),
          ),
          const SizedBox(height: AppSpacing.md),
          _SummaryLine(
            label: 'Active deferral',
            value: summary.activeDeferral ?? 'None',
          ),
          if (summary.certificateReady) ...[
            const SizedBox(height: AppSpacing.lg),
            const AppDivider(),
            const SizedBox(height: AppSpacing.lg),
            Text(
              'Your certificate for the '
              '${AppFormatters.longDayMonth(summary.lastDonation)} donation is '
              'ready.',
              style: text.bodyMedium,
            ),
            const SizedBox(height: AppSpacing.md),
            SizedBox(
              width: 240,
              child: AppOutlinedButton(
                label: 'View certificate',
                onPressed: () => context.go('/donor/certificates'),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _SummaryLine extends StatelessWidget {
  const _SummaryLine({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Text(
            label,
            style: text.bodyMedium?.copyWith(color: AppColors.onSurfaceVariant),
          ),
        ),
        const SizedBox(width: AppSpacing.md),
        Text(value, style: AppFormatters.tabular(text.titleSmall)),
      ],
    );
  }
}

class _RulesCard extends StatelessWidget {
  const _RulesCard();

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(AppRadius.card),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'RULES',
            style: text.labelSmall?.copyWith(
              color: AppColors.onSurfaceVariant,
              letterSpacing: 0.8,
            ),
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            'You can hold one active appointment per day. A slot can only take '
            'as many appointments as it has places.',
            style: text.bodyMedium,
          ),
        ],
      ),
    );
  }
}
