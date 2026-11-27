import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/theme/tokens/color_tokens.dart';
import '../../../../app/theme/tokens/spacing_tokens.dart';
import '../../../../app/theme/tokens/typography_tokens.dart';
import '../../../../core/errors/api_error.dart';
import '../../../../core/format/formatters.dart';
import '../../../../data/models/centre.dart';
import '../../../../data/models/slot.dart';
import '../../../../shared/widgets/app_buttons.dart';
import '../../../../shared/widgets/app_card.dart';
import '../../../../shared/widgets/app_chip.dart';
import '../../../../shared/widgets/app_error_state.dart';
import '../../../../shared/widgets/app_flow_steps.dart';
import '../../../../shared/widgets/app_icon_avatar.dart';
import '../../../../shared/widgets/app_loading_state.dart';
import '../../../../shared/widgets/app_page_header.dart';
import '../../../../shared/widgets/app_snack_bar.dart';
import '../providers/appointment_providers.dart';

/// 04 · Book a slot — Donor, mobile 390 and web 1440.
///
/// Three steps in one widget: choose the centre, pick a day and a time, then
/// confirm. The design frame captures the second step.
class BookSlotScreen extends ConsumerStatefulWidget {
  const BookSlotScreen({super.key});

  @override
  ConsumerState<BookSlotScreen> createState() => _BookSlotScreenState();
}

class _BookSlotScreenState extends ConsumerState<BookSlotScreen> {
  static const List<String> _steps = ['Centre', 'Time', 'Confirm'];

  int _step = 1;
  String? _centreId;
  int _dayIndex = -1;
  String? _timeStart;
  bool _submitting = false;

  @override
  Widget build(BuildContext context) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;
    final centres = ref.watch(centresProvider);

    return centres.when(
      loading: () => const Padding(
        padding: EdgeInsets.all(AppSpacing.lg),
        child: AppLoadingState(label: 'Finding centres near you...'),
      ),
      error: (error, stackTrace) => Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: AppErrorState(
          title: 'We could not load the centres',
          message: error is ApiError
              ? apiMessageFor(error)
              : 'Check your connection and try again.',
          onRetry: () => ref.invalidate(centresProvider),
        ),
      ),
      data: (all) {
        if (all.isEmpty) {
          return const Padding(
            padding: EdgeInsets.all(AppSpacing.lg),
            child: AppErrorState(
              title: 'No centre available',
              message: 'No centre is taking bookings right now. Try again later.',
            ),
          );
        }
        final centre = all.firstWhere(
          (item) => item.id == _centreId,
          orElse: () => all.first,
        );
        return _body(context, ref, wide: wide, centres: all, centre: centre);
      },
    );
  }

  Widget _body(
    BuildContext context,
    WidgetRef ref, {
    required bool wide,
    required List<Centre> centres,
    required Centre centre,
  }) {
    final days = ref.watch(slotDaysProvider(centre.id));

    final header = AppPageHeader(
      wide: wide,
      title: 'Book a slot',
      subtitle: wide ? _subtitle : null,
    );

    final content = days.when(
      loading: () => const AppLoadingState(label: 'Loading free slots...'),
      error: (error, stackTrace) => AppErrorState(
        title: 'We could not load the slots',
        message: error is ApiError
            ? apiMessageFor(error)
            : 'Check your connection and try again.',
        onRetry: () => ref.invalidate(slotDaysProvider(centre.id)),
      ),
      data: (list) => _stepsAndContent(
        context,
        wide: wide,
        centres: centres,
        centre: centre,
        days: list,
      ),
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
            AppCard(child: content),
            const SizedBox(height: AppSpacing.lg),
            Text(
              'Eligibility is re-checked at the centre. Bring your National ID '
              'card (CIN) and donation card.',
              style: AppTypography.textTheme.bodySmall?.copyWith(
                color: AppColors.onSurfaceVariant,
              ),
            ),
          ],
        ),
      );
    }

    return Column(
      children: [
        Expanded(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                header,
                const SizedBox(height: AppSpacing.lg),
                content,
              ],
            ),
          ),
        ),
        _bottomBar(wide: false, centre: centre, days: days.value ?? const []),
      ],
    );
  }

  String get _subtitle => switch (_step) {
    0 => 'Step 1 of 3 — choose the centre where you will give blood.',
    2 => 'Step 3 of 3 — check the details and confirm your booking.',
    _ =>
      'Step 2 of 3 — pick a day and a time at your centre. You can change '
          'both until you confirm.',
  };

  Widget _stepsAndContent(
    BuildContext context, {
    required bool wide,
    required List<Centre> centres,
    required Centre centre,
    required List<SlotDay> days,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        AppFlowSteps(
          steps: _steps,
          currentIndex: _step,
          onStepTapped: (index) => setState(() => _step = index),
        ),
        const SizedBox(height: AppSpacing.xl),
        switch (_step) {
          0 => _CentreStep(
            centres: centres,
            selectedId: centre.id,
            onSelected: (picked) => setState(() {
              _centreId = picked.id;
              _dayIndex = -1;
              _timeStart = null;
              _step = 1;
            }),
          ),
          2 => _confirmStep(centre: centre, days: days, wide: wide),
          _ => _timeStep(centre: centre, days: days, wide: wide),
        },
        if (wide) ...[
          const SizedBox(height: AppSpacing.xl),
          _actions(wide: true, centre: centre, days: days),
        ],
      ],
    );
  }

  // --- step 2 · time --------------------------------------------------------

  Widget _timeStep({
    required Centre centre,
    required List<SlotDay> days,
    required bool wide,
  }) {
    if (days.isEmpty) {
      return const AppErrorState(
        title: 'No free slot this week',
        message: 'Every slot is taken. Try again later or pick another centre.',
      );
    }
    final day = days[_resolvedDay(days.length)];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _SectionLabel('CENTRE'),
        const SizedBox(height: AppSpacing.sm),
        _CentreCard(centre: centre, wide: wide),
        const SizedBox(height: AppSpacing.xl),
        const _SectionLabel('DAY'),
        const SizedBox(height: AppSpacing.sm),
        Wrap(
          spacing: AppSpacing.md,
          runSpacing: AppSpacing.md,
          children: [
            for (var i = 0; i < days.length; i++)
              AppChip(
                label: AppFormatters.weekdayDay(days[i].date),
                selected: i == _resolvedDay(days.length),
                disabled: !days[i].hasFreePlace,
                onTap: () => setState(() {
                  _dayIndex = i;
                  _timeStart = null;
                }),
              ),
          ],
        ),
        const SizedBox(height: AppSpacing.xl),
        const _SectionLabel('TIME'),
        const SizedBox(height: AppSpacing.sm),
        Wrap(
          spacing: AppSpacing.md,
          runSpacing: AppSpacing.md,
          children: [
            for (final slot in day.times)
              AppChip(
                label: slot.label,
                selected: slot.start == _effectiveTime(day),
                disabled: slot.isFull,
                onTap: () => setState(() => _timeStart = slot.start),
              ),
          ],
        ),
      ],
    );
  }

  /// The chosen time, or the frame's default (14:00 when it is free).
  String? _effectiveTime(SlotDay day) {
    if (_timeStart != null) return _timeStart;
    final free = day.times.where((slot) => !slot.isFull);
    if (free.isEmpty) return null;
    return free
        .firstWhere((slot) => slot.start == '14:00', orElse: () => free.first)
        .start;
  }

  /// The selected day, or the frame's default (the second day).
  int _resolvedDay(int length) {
    if (length == 0) return 0;
    final base = _dayIndex >= 0 ? _dayIndex : (length > 1 ? 1 : 0);
    return base >= length ? length - 1 : base;
  }

  // --- step 3 · confirm -----------------------------------------------------

  Widget _confirmStep({
    required Centre centre,
    required List<SlotDay> days,
    required bool wide,
  }) {
    final day = days.isEmpty ? null : days[_resolvedDay(days.length)];
    final time = day == null ? null : _effectiveTime(day);
    final startsAt = _startsAt(day, time);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _SectionLabel('CHECK YOUR BOOKING'),
        const SizedBox(height: AppSpacing.sm),
        AppCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _SummaryRow(label: 'Centre', value: centre.name),
              const SizedBox(height: AppSpacing.md),
              _SummaryRow(
                label: 'Day',
                value: day == null
                    ? '—'
                    : AppFormatters.weekdayFull(day.date),
              ),
              const SizedBox(height: AppSpacing.md),
              _SummaryRow(
                label: 'Time',
                value: startsAt == null ? '—' : AppFormatters.time(startsAt),
              ),
              const SizedBox(height: AppSpacing.md),
              const _SummaryRow(label: 'Donation', value: 'Whole blood · 450 ml'),
            ],
          ),
        ),
      ],
    );
  }

  // --- actions --------------------------------------------------------------

  Widget _actions({
    required bool wide,
    required Centre centre,
    required List<SlotDay> days,
  }) {
    final (secondaryLabel, onSecondary) = switch (_step) {
      0 => ('Back to time', () => setState(() => _step = 1)),
      2 => ('Change time', () => setState(() => _step = 1)),
      _ => ('Change centre', () => setState(() => _step = 0)),
    };
    final primaryLabel = switch (_step) {
      0 => 'Continue',
      2 => wide ? 'Book this slot' : 'Book slot',
      _ => wide ? 'Confirm booking' : 'Confirm',
    };
    final onPrimary = switch (_step) {
      0 => () => setState(() => _step = 1),
      2 => _submitting ? null : () => _confirm(centre: centre, days: days),
      _ => () => setState(() => _step = 2),
    };

    final secondary = AppOutlinedButton(
      label: secondaryLabel,
      height: wide ? 44 : 48,
      onPressed: onSecondary,
    );
    final primary = AppFilledButton(
      label: _submitting ? 'Booking...' : primaryLabel,
      height: wide ? 44 : 48,
      onPressed: onPrimary,
    );

    if (!wide) {
      return Row(
        children: [
          Expanded(child: secondary),
          const SizedBox(width: AppSpacing.md),
          Expanded(child: primary),
        ],
      );
    }

    return Row(
      children: [
        SizedBox(width: 220, child: secondary),
        const SizedBox(width: AppSpacing.md),
        SizedBox(width: 280, child: primary),
      ],
    );
  }

  Widget _bottomBar({
    required bool wide,
    required Centre centre,
    required List<SlotDay> days,
  }) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.lg,
        AppSpacing.md,
        AppSpacing.lg,
        AppSpacing.lg,
      ),
      child: _actions(wide: false, centre: centre, days: days),
    );
  }

  // --- booking --------------------------------------------------------------

  DateTime? _startsAt(SlotDay? day, String? time) {
    if (day == null || time == null) return null;
    final parts = time.split(':');
    return DateTime(
      day.date.year,
      day.date.month,
      day.date.day,
      int.parse(parts[0]),
      int.parse(parts[1]),
    );
  }

  Future<void> _confirm({
    required Centre centre,
    required List<SlotDay> days,
  }) async {
    if (days.isEmpty) return;
    final day = days[_resolvedDay(days.length)];
    final startsAt = _startsAt(day, _effectiveTime(day));
    if (startsAt == null) return;

    final variant = GoRouterState.of(context).uri.queryParameters['state'];
    setState(() => _submitting = true);
    try {
      await ref
          .read(appointmentRepositoryProvider)
          .book(centreId: centre.id, startsAt: startsAt, variant: variant);
      if (!mounted) return;
      ref.invalidate(upcomingAppointmentsProvider);
      ref.invalidate(appointmentSummaryProvider);
      showAppSnack(
        context,
        'Booked at ${centre.name} on '
        '${AppFormatters.weekdayDayMonthTime(startsAt)}.',
        tone: AppSnackTone.success,
        actionLabel: 'View',
        onAction: () => context.go('/donor/appointments'),
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

// --- step 1 · centre --------------------------------------------------------

class _CentreStep extends StatelessWidget {
  const _CentreStep({
    required this.centres,
    required this.selectedId,
    required this.onSelected,
  });

  final List<Centre> centres;
  final String selectedId;
  final ValueChanged<Centre> onSelected;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const _SectionLabel('CENTRE'),
        const SizedBox(height: AppSpacing.sm),
        for (var i = 0; i < centres.length; i++) ...[
          if (i > 0) const SizedBox(height: AppSpacing.md),
          _CentreOption(
            centre: centres[i],
            selected: centres[i].id == selectedId,
            onTap: () => onSelected(centres[i]),
          ),
        ],
      ],
    );
  }
}

class _CentreOption extends StatelessWidget {
  const _CentreOption({
    required this.centre,
    required this.selected,
    required this.onTap,
  });

  final Centre centre;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return Material(
      color: AppColors.surfaceContainerLowest,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppRadius.card),
        side: BorderSide(
          color: selected ? AppColors.primary : AppColors.outline,
          width: selected ? 1.5 : 1,
        ),
      ),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppRadius.card),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Row(
            children: [
              const AppIconAvatar(),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(centre.name, style: text.titleMedium),
                    const SizedBox(height: 2),
                    Text(
                      centre.addressLine,
                      style: text.bodyMedium?.copyWith(
                        color: AppColors.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              if (selected)
                const Icon(Icons.check, size: 20, color: AppColors.onSurface),
            ],
          ),
        ),
      ),
    );
  }
}

/// The chosen centre shown at the top of the time step.
class _CentreCard extends StatelessWidget {
  const _CentreCard({required this.centre, required this.wide});

  final Centre centre;
  final bool wide;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return Container(
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(AppRadius.card),
        border: Border.all(color: AppColors.outline),
      ),
      child: Row(
        children: [
          const AppIconAvatar(),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  wide ? '${centre.name} — ${centre.address}' : centre.name,
                  style: text.titleMedium,
                ),
                const SizedBox(height: 2),
                Text(
                  wide ? centre.openLine : centre.addressLine,
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

// --- small pieces -----------------------------------------------------------

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

class _SummaryRow extends StatelessWidget {
  const _SummaryRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return Row(
      children: [
        SizedBox(
          width: 120,
          child: Text(
            label,
            style: text.bodyMedium?.copyWith(color: AppColors.onSurfaceVariant),
          ),
        ),
        Expanded(
          child: Text(value, style: AppFormatters.tabular(text.titleSmall)),
        ),
      ],
    );
  }
}
