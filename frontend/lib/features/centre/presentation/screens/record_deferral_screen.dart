import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/theme/tokens/color_tokens.dart';
import '../../../../app/theme/tokens/spacing_tokens.dart';
import '../../../../app/theme/tokens/typography_tokens.dart';
import '../../../../core/errors/api_error.dart';
import '../../../../core/format/formatters.dart';
import '../../../../core/providers/core_providers.dart';
import '../../../../data/models/deferral.dart';
import '../../../../shared/widgets/app_badge.dart';
import '../../../../shared/widgets/app_buttons.dart';
import '../../../../shared/widgets/app_card.dart';
import '../../../../shared/widgets/app_chip.dart';
import '../../../../shared/widgets/app_empty_state.dart';
import '../../../../shared/widgets/app_error_state.dart';
import '../../../../shared/widgets/app_loading_state.dart';
import '../../../../shared/widgets/app_page_header.dart';
import '../../../../shared/widgets/app_snack_bar.dart';
import '../../../../shared/widgets/app_text_field.dart';
import '../providers/centre_providers.dart';

/// C4 · Record a deferral — Centre staff, web 1440 (+ a stacked mobile layout).
///
/// A doctor records a temporary deferral: the donor cannot book and cannot
/// donate until the end date (R4). The CIN lookup brings the donor and any
/// deferral they already have.
class RecordDeferralScreen extends ConsumerWidget {
  const RecordDeferralScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;
    // Demo control: `?state=unavailable`, `?state=unknown`.
    final variant = GoRouterState.of(context).uri.queryParameters['state'];
    final reasons = ref.watch(deferralReasonsProvider);

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
            title: 'Record a deferral',
            subtitle: 'A doctor records a temporary deferral. The donor cannot '
                'book or donate until the end date.',
          ),
          const SizedBox(height: AppSpacing.xl),
          reasons.when(
            loading: () => const AppLoadingState(label: 'Loading reasons...'),
            error: (error, stackTrace) => AppErrorState(
              title: 'We could not load the form',
              message: error is ApiError
                  ? apiMessageFor(error)
                  : 'Check your connection and try again.',
              onRetry: () => ref.invalidate(deferralReasonsProvider),
            ),
            data: (list) => list.isEmpty
                ? const AppEmptyState(
                    title: 'No deferral reasons',
                    message: 'The reference list is empty.',
                  )
                : _Form(reasons: list, wide: wide, variant: variant),
          ),
          const SizedBox(height: AppSpacing.xl),
          Text(
            'Only a doctor or centre staff can record a deferral. It takes '
            'effect immediately.',
            style: AppTypography.textTheme.bodySmall?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

class _Form extends ConsumerStatefulWidget {
  const _Form({
    required this.reasons,
    required this.wide,
    required this.variant,
  });

  final List<DeferralReason> reasons;
  final bool wide;
  final String? variant;

  @override
  ConsumerState<_Form> createState() => _FormState();
}

class _FormState extends ConsumerState<_Form> {
  final _cin = TextEditingController();
  final _note = TextEditingController();

  String? _lookedUpCin;
  DeferralReason? _reason;
  int? _durationMonths;
  late DateTime _start;
  DateTime? _end;
  bool _submitting = false;

  static const List<int> _durations = [1, 3, 6, 12];

  @override
  void initState() {
    super.initState();
    final now = ref.read(clockProvider).now();
    _start = DateTime(now.year, now.month, now.day);
    _reason = widget.reasons.isEmpty ? null : widget.reasons.first;
  }

  @override
  void dispose() {
    _cin.dispose();
    _note.dispose();
    super.dispose();
  }

  DeferralReason get _effectiveReason => _reason ?? widget.reasons.first;

  int get _effectiveMonths {
    if (_durationMonths != null) return _durationMonths!;
    final weeks = _effectiveReason.weeks;
    final months = (weeks * 7 / 30.44).round();
    return months < 1 ? 1 : months;
  }

  DateTime get _effectiveEnd =>
      _end ?? DateTime(_start.year, _start.month + _effectiveMonths, _start.day);

  bool get _rangeValid => !_effectiveEnd.isBefore(_start);

  @override
  Widget build(BuildContext context) {
    final wide = widget.wide;
    final lookup = _lookedUpCin == null
        ? null
        : ref.watch(deferralLookupProvider(_lookedUpCin!));

    final form = _formCard(wide, lookup);
    final side = _Side(
      deferral: lookup?.value?.current,
      donorFound: lookup?.hasValue ?? false,
      reasons: widget.reasons,
    );

    if (wide) {
      return Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(flex: 784, child: form),
          const SizedBox(width: AppSpacing.xxl),
          Expanded(flex: 448, child: side),
        ],
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        form,
        const SizedBox(height: AppSpacing.xl),
        side,
      ],
    );
  }

  Widget _formCard(bool wide, AsyncValue<DeferralLookup?>? lookup) {
    final startField = AppTextField(
      label: 'START DATE',
      readOnly: true,
      controller: TextEditingController(text: AppFormatters.longDate(_start)),
      onTap: () => _pickDate(start: true),
    );
    final endField = AppTextField(
      label: 'END DATE',
      readOnly: true,
      errorText: _rangeValid
          ? null
          : 'The end date cannot be before the start date.',
      controller: TextEditingController(text: AppFormatters.longDate(_effectiveEnd)),
      onTap: () => _pickDate(start: false),
    );

    return AppCard(
      padding: const EdgeInsets.all(AppSpacing.xl),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('DONOR', style: AppTypography.textTheme.labelSmall),
          const SizedBox(height: AppSpacing.sm),
          if (wide)
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SizedBox(
                  width: 310,
                  child: AppTextField(
                    label: 'CIN',
                    controller: _cin,
                    hint: '8 digits',
                    keyboardType: TextInputType.number,
                    onSubmitted: _lookUp,
                  ),
                ),
                const SizedBox(width: AppSpacing.lg),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.only(top: 26),
                    child: _donorLine(lookup),
                  ),
                ),
              ],
            )
          else ...[
            AppTextField(
              label: 'CIN',
              controller: _cin,
              hint: '8 digits',
              keyboardType: TextInputType.number,
              onSubmitted: _lookUp,
            ),
            const SizedBox(height: AppSpacing.sm),
            _donorLine(lookup),
          ],
          const SizedBox(height: AppSpacing.lg),
          Text('REASON', style: AppTypography.textTheme.labelSmall),
          const SizedBox(height: AppSpacing.sm),
          Wrap(
            spacing: AppSpacing.sm,
            runSpacing: AppSpacing.sm,
            children: [
              for (final reason in widget.reasons)
                AppChip(
                  label: reason.code,
                  selected: _effectiveReason.code == reason.code,
                  onTap: () => setState(() {
                    _reason = reason;
                    _durationMonths = null;
                    _end = null;
                  }),
                ),
            ],
          ),
          const SizedBox(height: AppSpacing.lg),
          Text('DURATION', style: AppTypography.textTheme.labelSmall),
          const SizedBox(height: AppSpacing.sm),
          Wrap(
            spacing: AppSpacing.sm,
            runSpacing: AppSpacing.sm,
            children: [
              for (final months in _durations)
                AppChip(
                  label: months == 1 ? '1 month' : '$months months',
                  selected: _effectiveMonths == months,
                  onTap: () => setState(() {
                    _durationMonths = months;
                    _end = null;
                  }),
                ),
            ],
          ),
          const SizedBox(height: AppSpacing.lg),
          if (wide)
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(child: startField),
                const SizedBox(width: AppSpacing.lg),
                Expanded(child: endField),
              ],
            )
          else ...[
            startField,
            const SizedBox(height: AppSpacing.lg),
            endField,
          ],
          const SizedBox(height: AppSpacing.lg),
          AppTextField(
            label: 'NOTE (STAFF ONLY)',
            controller: _note,
            maxLines: 3,
            hint: 'Never shown to the donor',
          ),
          const SizedBox(height: AppSpacing.xl),
          if (wide)
            Row(
              children: [
                SizedBox(
                  width: 240,
                  child: AppFilledButton(
                    label: 'Record deferral',
                    height: 44,
                    onPressed: _canSubmit ? _submit : null,
                  ),
                ),
                const SizedBox(width: AppSpacing.md),
                SizedBox(
                  width: 140,
                  child: AppOutlinedButton(
                    label: 'Cancel',
                    height: 44,
                    onPressed: () => context.go('/centre/today'),
                  ),
                ),
              ],
            )
          else
            Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                AppFilledButton(
                  label: 'Record deferral',
                  height: 48,
                  onPressed: _canSubmit ? _submit : null,
                ),
                const SizedBox(height: AppSpacing.md),
                AppOutlinedButton(
                  label: 'Cancel',
                  height: 48,
                  onPressed: () => context.go('/centre/today'),
                ),
              ],
            ),
        ],
      ),
    );
  }

  Widget _donorLine(AsyncValue<DeferralLookup?>? lookup) {
    if (lookup == null) {
      return Text(
        'Type a CIN and press enter to find the donor.',
        style: AppTypography.textTheme.bodySmall?.copyWith(
          color: AppColors.onSurfaceVariant,
        ),
      );
    }
    final muted = AppTypography.textTheme.bodySmall?.copyWith(
      color: AppColors.onSurfaceVariant,
    );
    return lookup.when(
      loading: () => Text('Looking up...', style: muted),
      error: (error, stackTrace) => Text(
        error is ApiError ? apiMessageFor(error) : 'No donor found with this CIN.',
        style: AppTypography.textTheme.bodySmall?.copyWith(
          color: AppColors.error,
        ),
      ),
      data: (value) => value == null
          ? Text(
              'No donor found with this CIN.',
              style: AppTypography.textTheme.bodySmall?.copyWith(
                color: AppColors.error,
              ),
            )
          : Text(
              value.donor.line,
              style: AppTypography.textTheme.bodyMedium,
            ),
    );
  }

  bool get _canSubmit =>
      !_submitting &&
      _lookedUpCin != null &&
      _rangeValid &&
      widget.reasons.isNotEmpty;

  Future<void> _lookUp(String value) async {
    final cin = value.trim();
    if (cin.isEmpty) return;
    setState(() => _lookedUpCin = cin);
    await ref.read(deferralLookupProvider(cin).future);
    if (!mounted) return;
  }

  Future<void> _pickDate({required bool start}) async {
    final initial = start ? _start : _effectiveEnd;
    final picked = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime(2020),
      lastDate: DateTime(2035),
    );
    if (picked == null) return;
    setState(() {
      if (start) {
        _start = DateTime(picked.year, picked.month, picked.day);
        _end = null;
      } else {
        _end = DateTime(picked.year, picked.month, picked.day);
      }
    });
  }

  Future<void> _submit() async {
    final cin = _lookedUpCin;
    if (cin == null) return;
    setState(() => _submitting = true);
    try {
      await ref
          .read(deferralRepositoryProvider)
          .record(
            DeferralDraft(
              cin: cin,
              reasonCode: _effectiveReason.code,
              startDate: _start,
              endDate: _effectiveEnd,
              note: _note.text.trim(),
            ),
            variant: widget.variant,
          );
      ref.invalidate(deferralLookupProvider(cin));
      if (!mounted) return;
      showAppSnack(context, 'Deferral recorded. It takes effect immediately.');
    } on ApiError catch (error) {
      if (!mounted) return;
      showAppSnack(context, apiMessageFor(error), tone: AppSnackTone.critical);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }
}

// --- right column -----------------------------------------------------------

class _Side extends StatelessWidget {
  const _Side({
    required this.deferral,
    required this.donorFound,
    required this.reasons,
  });

  final Deferral? deferral;
  final bool donorFound;
  final List<DeferralReason> reasons;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        AppCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'CURRENT DEFERRAL',
                style: text.labelSmall?.copyWith(
                  color: AppColors.onSurfaceVariant,
                  letterSpacing: 0.5,
                ),
              ),
              const SizedBox(height: AppSpacing.md),
              if (deferral != null) ...[
                Row(
                  children: [
                    Expanded(
                      child: Text(deferral!.title, style: text.titleMedium),
                    ),
                    const SizedBox(width: AppSpacing.sm),
                    const AppBadge(
                      label: 'Active',
                      tone: AppBadgeTone.warning,
                    ),
                  ],
                ),
                const SizedBox(height: AppSpacing.sm),
                Text(
                  'Active until ${AppFormatters.longDate(deferral!.endDate)}. '
                  'The donor cannot book or donate until then.',
                  style: text.bodyMedium?.copyWith(
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ] else
                Text(
                  donorFound
                      ? 'This donor has no active deferral.'
                      : 'Look a donor up to see their deferral.',
                  style: text.bodyMedium?.copyWith(
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.md),
        Container(
          padding: const EdgeInsets.all(AppSpacing.lg),
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLow,
            borderRadius: BorderRadius.circular(AppRadius.card),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'REASONS · REFERENCE',
                style: text.labelSmall?.copyWith(
                  color: AppColors.onSurfaceVariant,
                  letterSpacing: 0.5,
                ),
              ),
              const SizedBox(height: AppSpacing.md),
              for (var i = 0; i < reasons.length; i++) ...[
                if (i > 0) const SizedBox(height: AppSpacing.sm),
                Row(
                  children: [
                    Expanded(child: Text(reasons[i].label, style: text.bodyMedium)),
                    Text(
                      reasons[i].durationLabel,
                      style: AppFormatters.tabular(text.bodyMedium),
                    ),
                  ],
                ),
              ],
              const SizedBox(height: AppSpacing.md),
              Text(
                'Durations come from the reference list and are configurable '
                '(section 16).',
                style: text.bodySmall?.copyWith(
                  color: AppColors.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
