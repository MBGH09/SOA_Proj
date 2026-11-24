import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/theme/tokens/color_tokens.dart';
import '../../../../app/theme/tokens/spacing_tokens.dart';
import '../../../../app/theme/tokens/typography_tokens.dart';
import '../../../../core/errors/api_error.dart';
import '../../../../core/format/formatters.dart';
import '../../../../data/models/appointment.dart';
import '../../../../data/models/slot.dart';
import '../../../../shared/widgets/app_badge.dart';
import '../../../../shared/widgets/app_buttons.dart';
import '../../../../shared/widgets/app_card.dart';
import '../../../../shared/widgets/app_chip.dart';
import '../../../../shared/widgets/app_empty_state.dart';
import '../../../../shared/widgets/app_error_state.dart';
import '../../../../shared/widgets/app_flow_steps.dart';
import '../../../../shared/widgets/app_loading_state.dart';
import '../../../../shared/widgets/app_page_header.dart';
import '../../../../shared/widgets/app_snack_bar.dart';
import '../providers/appointment_providers.dart';

/// 06 · Move your appointment — Donor, mobile 390 and web 1440.
///
/// The move happens at the same centre; the current slot stays booked until
/// the donor confirms.
class MoveAppointmentScreen extends ConsumerStatefulWidget {
  const MoveAppointmentScreen({super.key});

  @override
  ConsumerState<MoveAppointmentScreen> createState() =>
      _MoveAppointmentScreenState();
}

class _MoveAppointmentScreenState extends ConsumerState<MoveAppointmentScreen> {
  static const List<String> _steps = ['Day', 'Time', 'Confirm'];

  int _dayIndex = -1;
  String? _timeStart;
  bool _submitting = false;

  @override
  Widget build(BuildContext context) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;
    final upcoming = ref.watch(upcomingAppointmentsProvider);

    return upcoming.when(
      loading: () => const Padding(
        padding: EdgeInsets.all(AppSpacing.lg),
        child: AppLoadingState(label: 'Loading your appointment...'),
      ),
      error: (error, stackTrace) => Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: AppErrorState(
          title: 'We could not load your appointment',
          message: error is ApiError
              ? apiMessageFor(error)
              : 'Check your connection and try again.',
          onRetry: () => ref.invalidate(upcomingAppointmentsProvider),
        ),
      ),
      data: (items) {
        final id = GoRouterState.of(context).uri.queryParameters['id'];
        final appointment = items.isEmpty
            ? null
            : items.firstWhere(
                (item) => item.id == id,
                orElse: () => items.first,
              );
        if (appointment == null) {
          return Padding(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: AppEmptyState(
              title: 'Nothing to move',
              message: 'You have no upcoming appointment to move.',
              icon: Icons.event_busy_outlined,
              action: AppFilledButton(
                label: 'Book a slot',
                height: 48,
                onPressed: () => context.go('/donor/book'),
              ),
            ),
          );
        }
        return _body(context, wide: wide, appointment: appointment);
      },
    );
  }

  Widget _body(
    BuildContext context, {
    required bool wide,
    required Appointment appointment,
  }) {
    // The same centre keeps its availability; the mock keys on the centre name.
    final days = ref.watch(moveDaysProvider(appointment.centreName));

    final header = AppPageHeader(
      wide: wide,
      title: 'Move your appointment',
      subtitle: wide
          ? 'Pick another day and slot at the same centre. Your current slot stays booked until you confirm.'
          : 'Pick another day and slot at the same centre. Your current slot stays booked until you confirm.',
    );

    final schedule = days.when(
      loading: () => const AppLoadingState(label: 'Loading free slots...'),
      error: (error, stackTrace) => AppErrorState(
        title: 'We could not load the slots',
        message: error is ApiError
            ? apiMessageFor(error)
            : 'Check your connection and try again.',
        onRetry: () => ref.invalidate(moveDaysProvider(appointment.centreName)),
      ),
      data: (list) => _schedule(context, wide: wide, days: list),
    );

    if (wide) {
      return SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.page,
          28,
          AppSpacing.page,
          AppSpacing.xxxl,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            header,
            const SizedBox(height: AppSpacing.xl),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  flex: 3,
                  child: Column(
                    children: [
                      _CurrentlyBooked(
                        appointment: appointment,
                        wide: true,
                      ),
                      const SizedBox(height: AppSpacing.lg),
                      const _BeforeYouConfirm(),
                    ],
                  ),
                ),
                const SizedBox(width: AppSpacing.xxl),
                Expanded(
                  flex: 4,
                  child: AppCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        AppFlowSteps(steps: _steps, currentIndex: 0),
                        const SizedBox(height: AppSpacing.xl),
                        schedule,
                        const SizedBox(height: AppSpacing.xl),
                        _actions(
                          wide: true,
                          appointment: appointment,
                          days: days.value ?? const [],
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.lg),
            Text(
              'The move is applied immediately. The old slot is released for '
              'other donors.',
              style: AppTypography.textTheme.bodySmall?.copyWith(
                color: AppColors.onSurfaceVariant,
              ),
            ),
          ],
        ),
      );
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.all(AppSpacing.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          header,
          const SizedBox(height: AppSpacing.lg),
          AppFlowSteps(steps: _steps, currentIndex: 0),
          const SizedBox(height: AppSpacing.lg),
          _CurrentlyBooked(appointment: appointment, wide: false),
          const SizedBox(height: AppSpacing.xl),
          schedule,
          const SizedBox(height: AppSpacing.xl),
          _actions(
            wide: false,
            appointment: appointment,
            days: days.value ?? const [],
          ),
          const SizedBox(height: AppSpacing.md),
          Text(
            'One active appointment per day. Moving an appointment keeps your '
            'place in the appeal queue.',
            style: AppTypography.textTheme.bodySmall?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }

  Widget _schedule(
    BuildContext context, {
    required bool wide,
    required List<SlotDay> days,
  }) {
    if (days.isEmpty) {
      return const AppErrorState(
        title: 'No free slot this week',
        message: 'Every slot is taken. Try again later.',
      );
    }
    final dayIndex = _resolvedDay(days.length);
    final day = days[dayIndex];
    final time = _effectiveTime(day);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _SectionLabel('DAY'),
        const SizedBox(height: AppSpacing.sm),
        Wrap(
          spacing: AppSpacing.md,
          runSpacing: AppSpacing.md,
          children: [
            for (var i = 0; i < days.length; i++)
              AppChip(
                label: AppFormatters.weekdayDay(days[i].date),
                selected: i == dayIndex,
                disabled: !days[i].hasFreePlace,
                onTap: () => setState(() {
                  _dayIndex = i;
                  _timeStart = null;
                }),
              ),
          ],
        ),
        const SizedBox(height: AppSpacing.xl),
        _SectionLabel(
          wide ? 'TIME · ${AppFormatters.weekdayDayMonth(day.date).toUpperCase()}' : 'TIME',
        ),
        const SizedBox(height: AppSpacing.sm),
        Wrap(
          spacing: AppSpacing.md,
          runSpacing: AppSpacing.md,
          children: [
            for (final slot in day.times)
              AppChip(
                label: slot.label,
                selected: slot.start == time,
                disabled: slot.isFull,
                onTap: () => setState(() => _timeStart = slot.start),
              ),
          ],
        ),
      ],
    );
  }

  /// The chosen time, or the frame's default (14:30 when it is free).
  String? _effectiveTime(SlotDay day) {
    if (_timeStart != null) return _timeStart;
    final free = day.times.where((slot) => !slot.isFull);
    if (free.isEmpty) return null;
    return free
        .firstWhere((slot) => slot.start == '14:30', orElse: () => free.first)
        .start;
  }

  /// The selected day, or the frame's default (the second day).
  int _resolvedDay(int length) {
    if (length == 0) return 0;
    final base = _dayIndex >= 0 ? _dayIndex : (length > 1 ? 1 : 0);
    return base >= length ? length - 1 : base;
  }

  Widget _actions({
    required bool wide,
    required Appointment appointment,
    required List<SlotDay> days,
  }) {
    final keep = AppOutlinedButton(
      label: 'Keep current',
      height: wide ? 44 : 48,
      onPressed: () => context.go('/donor/appointments'),
    );
    final move = AppFilledButton(
      label: _submitting ? 'Moving...' : 'Move here',
      height: wide ? 44 : 48,
      onPressed: _submitting
          ? null
          : () => _move(appointment: appointment, days: days),
    );

    if (!wide) {
      return Row(
        children: [
          Expanded(child: keep),
          const SizedBox(width: AppSpacing.md),
          Expanded(child: move),
        ],
      );
    }
    return Row(
      children: [
        SizedBox(width: 215, child: keep),
        const SizedBox(width: AppSpacing.md),
        SizedBox(width: 280, child: move),
      ],
    );
  }

  Future<void> _move({
    required Appointment appointment,
    required List<SlotDay> days,
  }) async {
    if (days.isEmpty) return;
    final dayIndex = _resolvedDay(days.length);
    final day = days[dayIndex];
    final time = _effectiveTime(day);
    if (time == null) return;
    final parts = time.split(':');
    final startsAt = DateTime(
      day.date.year,
      day.date.month,
      day.date.day,
      int.parse(parts[0]),
      int.parse(parts[1]),
    );
    final variant = GoRouterState.of(context).uri.queryParameters['state'];

    setState(() => _submitting = true);
    try {
      await ref
          .read(appointmentRepositoryProvider)
          .move(
            appointmentId: appointment.id,
            startsAt: startsAt,
            variant: variant,
          );
      if (!mounted) return;
      ref.invalidate(upcomingAppointmentsProvider);
      ref.invalidate(appointmentSummaryProvider);
      showAppSnack(
        context,
        'Appointment moved to ${AppFormatters.weekdayDayMonthTime(startsAt)}.',
        tone: AppSnackTone.success,
      );
      context.go('/donor/appointments');
    } on ApiError catch (error) {
      if (!mounted) return;
      showAppSnack(
        context,
        error.fieldErrors.values.isNotEmpty
            ? error.fieldErrors.values.first
            : apiMessageFor(error),
        tone: AppSnackTone.critical,
      );
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }
}

// --- cards ------------------------------------------------------------------

class _CurrentlyBooked extends StatelessWidget {
  const _CurrentlyBooked({required this.appointment, required this.wide});

  final Appointment appointment;
  final bool wide;

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
            'CURRENTLY BOOKED',
            style: text.labelSmall?.copyWith(
              color: AppColors.onSurfaceVariant,
              letterSpacing: 0.8,
            ),
          ),
          const SizedBox(height: AppSpacing.md),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(child: Text(appointment.centreName, style: text.titleMedium)),
              const SizedBox(width: AppSpacing.md),
              AppBadge(
                label: appointment.status.label,
                background: AppColors.surfaceContainerLowest,
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            wide
                ? AppFormatters.weekdayFullTime(appointment.startsAt)
                : AppFormatters.weekdayDayMonthTime(appointment.startsAt),
            style: text.titleLarge,
          ),
          const SizedBox(height: 6),
          Text(
            wide
                ? '${appointment.componentLine()} · reference ${appointment.id}'
                : appointment.metaLineMobile,
            style: text.bodyMedium?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

class _BeforeYouConfirm extends StatelessWidget {
  const _BeforeYouConfirm();

  static const List<String> _points = [
    'One active appointment per day.',
    'A slot can only take as many appointments as it has places.',
    'Your place in the appeal queue is kept.',
  ];

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
            'BEFORE YOU CONFIRM',
            style: text.labelSmall?.copyWith(
              color: AppColors.onSurfaceVariant,
              letterSpacing: 0.8,
            ),
          ),
          const SizedBox(height: AppSpacing.md),
          for (final point in _points)
            Padding(
              padding: const EdgeInsets.only(bottom: AppSpacing.sm),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '•',
                    style: text.bodyMedium?.copyWith(
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(width: AppSpacing.sm),
                  Expanded(child: Text(point, style: text.bodyMedium)),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

class _SectionLabel extends StatelessWidget {
  const _SectionLabel(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    return Text(
      label,
      style: AppTypography.textTheme.labelSmall?.copyWith(
        color: AppColors.onSurfaceVariant,
        letterSpacing: 0.8,
      ),
    );
  }
}
