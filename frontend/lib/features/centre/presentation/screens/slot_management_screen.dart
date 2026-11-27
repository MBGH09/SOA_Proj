import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/theme/tokens/color_tokens.dart';
import '../../../../app/theme/tokens/spacing_tokens.dart';
import '../../../../app/theme/tokens/typography_tokens.dart';
import '../../../../core/errors/api_error.dart';
import '../../../../core/format/formatters.dart';
import '../../../../data/models/slot.dart';
import '../../../../shared/widgets/app_badge.dart';
import '../../../../shared/widgets/app_buttons.dart';
import '../../../../shared/widgets/app_card.dart';
import '../../../../shared/widgets/app_chip.dart';
import '../../../../shared/widgets/app_data_table.dart';
import '../../../../shared/widgets/app_error_state.dart';
import '../../../../shared/widgets/app_loading_state.dart';
import '../../../../shared/widgets/app_page_header.dart';
import '../../../../shared/widgets/app_snack_bar.dart';
import '../../../../shared/widgets/app_text_field.dart';
import '../../../donor/presentation/providers/appointment_providers.dart';
import '../providers/centre_providers.dart';

/// C5 · Slot management — Centre staff, web 1440 (+ a stacked mobile layout).
///
/// The centre's own week of slots with capacity and fill, and the form that
/// creates the slots donors see when booking from home. A slot can only take as
/// many appointments as it has places (R22).
class SlotManagementScreen extends ConsumerWidget {
  const SlotManagementScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;
    // Demo control: `?state=unavailable`.
    final variant = GoRouterState.of(context).uri.queryParameters['state'];
    final days = ref.watch(centreSlotDaysProvider(centreId));

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
            title: 'Slot management',
            subtitle: 'Create slots with a capacity and watch how full they are. '
                'A slot can only take as many appointments as it places.',
          ),
          const SizedBox(height: AppSpacing.xl),
          days.when(
            loading: () => const AppLoadingState(label: 'Loading slots...'),
            error: (error, stackTrace) => AppErrorState(
              title: 'We could not load the slots',
              message: error is ApiError
                  ? apiMessageFor(error)
                  : 'Check your connection and try again.',
              onRetry: () => ref.invalidate(centreSlotDaysProvider),
            ),
            data: (list) =>
                _Week(days: list, wide: wide, variant: variant),
          ),
          const SizedBox(height: AppSpacing.xl),
          Text(
            'A booking can be made from home or at the centre. A donor can hold '
            'only one active appointment per day.',
            style: AppTypography.textTheme.bodySmall?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

class _Week extends ConsumerStatefulWidget {
  const _Week({required this.days, required this.wide, required this.variant});

  final List<SlotDay> days;
  final bool wide;
  final String? variant;

  @override
  ConsumerState<_Week> createState() => _WeekState();
}

class _WeekState extends ConsumerState<_Week> {
  int _selected = 0;

  @override
  void initState() {
    super.initState();
    final index = widget.days.indexWhere(
      (day) => day.date.year == 2026 && day.date.month == 11 && day.date.day == 12,
    );
    if (index >= 0) _selected = index;
  }

  @override
  Widget build(BuildContext context) {
    if (widget.days.isEmpty) {
      return const AppCard(child: Text('No slot in the coming days.'));
    }
    final day = widget.days[_selected.clamp(0, widget.days.length - 1)];

    final table = _DayTable(
      day: day,
      days: widget.days,
      selected: _selected,
      onSelect: (index) => setState(() => _selected = index),
    );
    final side = _Side(day: day, variant: widget.variant);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (widget.wide)
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(flex: 784, child: table),
              const SizedBox(width: AppSpacing.xxl),
              Expanded(flex: 448, child: side),
            ],
          )
        else ...[
          table,
          const SizedBox(height: AppSpacing.xl),
          side,
        ],
      ],
    );
  }
}

class _DayTable extends StatelessWidget {
  const _DayTable({
    required this.day,
    required this.days,
    required this.selected,
    required this.onSelect,
  });

  final SlotDay day;
  final List<SlotDay> days;
  final int selected;
  final ValueChanged<int> onSelect;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return AppCard(
      padding: const EdgeInsets.all(AppSpacing.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: AppSpacing.sm,
            runSpacing: AppSpacing.sm,
            children: [
              for (var i = 0; i < days.length; i++)
                AppChip(
                  label: AppFormatters.weekdayDay(days[i].date),
                  selected: i == selected,
                  onTap: () => onSelect(i),
                ),
            ],
          ),
          const SizedBox(height: AppSpacing.lg),
          AppDataTable(
            minWidth: 700,
            columns: [
              appColumn('TIME'),
              appColumn('CAPACITY'),
              appColumn('BOOKED'),
              appColumn('FILL'),
              appColumn('STATUS'),
            ],
            rows: [
              for (final time in day.times)
                DataRow(
                  cells: [
                    DataCell(
                      Text(
                        time.range,
                        style: AppFormatters.tabular(text.titleSmall),
                      ),
                    ),
                    DataCell(Text('${time.capacity}')),
                    DataCell(
                      Text(
                        '${time.booked} / ${time.capacity}',
                        style: AppFormatters.tabular(text.bodyMedium),
                      ),
                    ),
                    DataCell(_FillBar(value: time.fill, full: time.isFull)),
                    DataCell(
                      AppBadge(
                        label: time.isFull ? 'Full' : 'Open',
                        tone: time.isFull
                            ? AppBadgeTone.critical
                            : AppBadgeTone.neutral,
                      ),
                    ),
                  ],
                ),
            ],
          ),
          const SizedBox(height: AppSpacing.md),
          Text(
            'A full slot cannot be booked. Online bookings keep their place '
            'until the donor confirms.',
            style: text.bodySmall?.copyWith(color: AppColors.onSurfaceVariant),
          ),
        ],
      ),
    );
  }
}

class _FillBar extends StatelessWidget {
  const _FillBar({required this.value, required this.full});

  final double value;
  final bool full;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 150,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(4),
        child: LinearProgressIndicator(
          value: value,
          minHeight: 8,
          backgroundColor: AppColors.outlineVariant,
          color: full ? AppColors.onSurface : AppColors.outline,
        ),
      ),
    );
  }
}

// --- right column -----------------------------------------------------------

class _Side extends ConsumerStatefulWidget {
  const _Side({required this.day, required this.variant});

  final SlotDay day;
  final String? variant;

  @override
  ConsumerState<_Side> createState() => _SideState();
}

class _SideState extends ConsumerState<_Side> {
  final _from = TextEditingController(text: '09:00');
  final _to = TextEditingController(text: '12:00');
  final _capacity = TextEditingController(text: '6');
  SlotRepeat _repeat = SlotRepeat.single;
  bool _submitting = false;

  @override
  void dispose() {
    _from.dispose();
    _to.dispose();
    _capacity.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final day = widget.day;
    final text = AppTypography.textTheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        AppCard(
          padding: const EdgeInsets.all(AppSpacing.xl),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'CREATE SLOTS',
                style: text.labelSmall?.copyWith(
                  color: AppColors.onSurfaceVariant,
                  letterSpacing: 0.5,
                ),
              ),
              const SizedBox(height: AppSpacing.md),
              AppTextField(
                label: 'DATE',
                readOnly: true,
                controller: TextEditingController(
                  text: AppFormatters.weekdayFullYear(day.date),
                ),
              ),
              const SizedBox(height: AppSpacing.lg),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: AppTextField(
                      label: 'FROM',
                      controller: _from,
                      hint: '09:00',
                    ),
                  ),
                  const SizedBox(width: AppSpacing.md),
                  Expanded(
                    child: AppTextField(
                      label: 'TO',
                      controller: _to,
                      hint: '12:00',
                    ),
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.lg),
              AppTextField(
                label: 'CAPACITY PER SLOT',
                controller: _capacity,
                keyboardType: TextInputType.number,
              ),
              const SizedBox(height: AppSpacing.lg),
              Text('REPEAT', style: text.labelSmall),
              const SizedBox(height: AppSpacing.sm),
              Wrap(
                spacing: AppSpacing.sm,
                runSpacing: AppSpacing.sm,
                children: [
                  for (final repeat in SlotRepeat.values)
                    AppChip(
                      label: repeat.label,
                      selected: _repeat == repeat,
                      onTap: () => setState(() => _repeat = repeat),
                    ),
                ],
              ),
              const SizedBox(height: AppSpacing.lg),
              AppFilledButton(
                label: 'Create slots',
                height: 48,
                onPressed: _submitting ? null : _create,
              ),
              const SizedBox(height: AppSpacing.md),
              Text(
                'Staff and admin create the slots that donors see when booking '
                'from home.',
                style: text.bodySmall?.copyWith(
                  color: AppColors.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.md),
        _DaySummary(day: day),
      ],
    );
  }

  Future<void> _create() async {
    setState(() => _submitting = true);
    try {
      await ref
          .read(appointmentRepositoryProvider)
          .createSlots(
            centreId: centreId,
            date: widget.day.date,
            from: _from.text.trim(),
            to: _to.text.trim(),
            capacity: int.tryParse(_capacity.text.trim()) ?? 4,
            repeat: _repeat,
            variant: widget.variant,
          );
      ref.invalidate(centreSlotDaysProvider);
      if (!mounted) return;
      showAppSnack(context, 'Slots created for ${_from.text} – ${_to.text}.');
    } on ApiError catch (error) {
      if (!mounted) return;
      showAppSnack(context, apiMessageFor(error), tone: AppSnackTone.critical);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }
}

class _DaySummary extends StatelessWidget {
  const _DaySummary({required this.day});

  final SlotDay day;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    Widget row(String label, String value) => Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: Row(
        children: [
          Expanded(child: Text(label, style: text.bodyMedium)),
          Text(value, style: AppFormatters.tabular(text.titleSmall)),
        ],
      ),
    );

    return Container(
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(AppRadius.card),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'TODAY · ${AppFormatters.weekdayDay(day.date).toUpperCase()}',
            style: text.labelSmall?.copyWith(
              color: AppColors.onSurfaceVariant,
              letterSpacing: 0.5,
            ),
          ),
          const SizedBox(height: AppSpacing.md),
          row('Slots', '${day.times.length}'),
          row('Places', '${day.places}'),
          row('Booked', '${day.booked}'),
          row('Fill', '${(day.fill * 100).round()} %'),
        ],
      ),
    );
  }
}
