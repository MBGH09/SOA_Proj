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
import '../../../../shared/widgets/app_buttons.dart';
import '../../../../shared/widgets/app_chip.dart';
import '../../../../shared/widgets/app_error_state.dart';
import '../../../../shared/widgets/app_loading_state.dart';
import '../../../../shared/widgets/app_page_header.dart';
import '../../../../shared/widgets/app_snack_bar.dart';
import '../../../hospital/presentation/providers/hospital_providers.dart';

/// A1 · Stock thresholds — Admin.
///
/// Alert levels per place, blood group and component. Save applies the draft,
/// Reset brings back the saved numbers, and every change appends an audit line.
class StockThresholdsScreen extends ConsumerStatefulWidget {
  const StockThresholdsScreen({super.key});

  @override
  ConsumerState<StockThresholdsScreen> createState() =>
      _StockThresholdsScreenState();
}

class _StockThresholdsScreenState extends ConsumerState<StockThresholdsScreen> {
  String _siteId = hospitalId;
  final Map<String, int> _draft = {};
  bool _saving = false;

  static String _key(ThresholdRule rule) =>
      '${rule.bloodGroup.name}|${rule.component.name}';

  int _valueOf(ThresholdRule rule) => _draft[_key(rule)] ?? rule.threshold;

  int _changedCount(ThresholdSnapshot snapshot) =>
      snapshot.rules.where((rule) => _valueOf(rule) != rule.threshold).length;

  @override
  Widget build(BuildContext context) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;
    final variant = GoRouterState.of(context).uri.queryParameters['state'];
    final sites = ref.watch(stockSitesProvider);
    final thresholds = ref.watch(
      thresholdsProvider((siteId: _siteId, variant: variant)),
    );

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
            title: 'Stock thresholds',
            subtitle:
                'Alert levels used to flag a place, blood group and component. '
                'Only an admin can change them.',
          ),
          const SizedBox(height: AppSpacing.lg),
          thresholds.when(
            loading: () =>
                const AppLoadingState(label: 'Loading the thresholds...'),
            error: (error, stackTrace) => AppErrorState(
              title: 'We could not load the thresholds',
              message: error is ApiError
                  ? apiMessageFor(error)
                  : 'Check your connection and try again.',
              onRetry: () => ref.invalidate(thresholdsProvider),
            ),
            data: (snapshot) => Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _Controls(
                  places: sites.value ?? const [],
                  selectedId: _siteId,
                  wide: wide,
                  saving: _saving,
                  changed: _changedCount(snapshot),
                  onSelected: (id) => setState(() {
                    _siteId = id;
                    _draft.clear();
                  }),
                  onSave: () => _save(snapshot, variant),
                  onReset: () => setState(_draft.clear),
                ),
                const SizedBox(height: AppSpacing.xl),
                if (!wide) ...[
                  Text(
                    'Swipe sideways to reach every column.',
                    style: AppTypography.textTheme.bodySmall?.copyWith(
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: AppSpacing.sm),
                ],
                _ThresholdTable(
                  snapshot: snapshot,
                  draft: _draft,
                  wide: wide,
                  onChanged: (rule, value) => setState(() {
                    _draft[_key(rule)] = value;
                  }),
                ),
                const SizedBox(height: AppSpacing.xl),
                _AuditCard(audit: snapshot.audit),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _save(ThresholdSnapshot snapshot, String? variant) async {
    if (_saving) return;
    final changed = _changedCount(snapshot);
    if (changed == 0) {
      showAppSnack(context, 'No threshold has been changed yet.');
      return;
    }

    setState(() => _saving = true);
    final rules = snapshot.rules
        .map((rule) => rule.copyWith(threshold: _valueOf(rule)))
        .toList(growable: false);

    try {
      await ref
          .read(stockRepositoryProvider)
          .saveThresholds(_siteId, rules, variant: variant);
      if (!mounted) return;
      setState(_draft.clear);
      ref.invalidate(thresholdsProvider);
      showAppSnack(
        context,
        changed == 1
            ? '1 threshold saved for ${snapshot.site.chipLabel}.'
            : '$changed thresholds saved for ${snapshot.site.chipLabel}.',
        tone: AppSnackTone.success,
      );
    } on ApiError catch (error) {
      if (!mounted) return;
      showAppSnack(context, apiMessageFor(error), tone: AppSnackTone.critical);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }
}

class _Controls extends StatelessWidget {
  const _Controls({
    required this.places,
    required this.selectedId,
    required this.wide,
    required this.saving,
    required this.changed,
    required this.onSelected,
    required this.onSave,
    required this.onReset,
  });

  final List<StockSite> places;
  final String selectedId;
  final bool wide;
  final bool saving;
  final int changed;
  final ValueChanged<String> onSelected;
  final VoidCallback onSave;
  final VoidCallback onReset;

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

    final save = AppFilledButton(
      label: saving ? 'Saving...' : 'Save changes',
      onPressed: saving ? null : onSave,
      height: 48,
    );
    final reset = AppOutlinedButton(
      label: 'Reset',
      onPressed: saving ? null : onReset,
      height: 48,
    );

    if (!wide) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          chips,
          const SizedBox(height: AppSpacing.md),
          Row(
            children: [
              Expanded(flex: 2, child: save),
              const SizedBox(width: AppSpacing.md),
              Expanded(child: reset),
            ],
          ),
          if (changed > 0) ...[
            const SizedBox(height: AppSpacing.sm),
            Text(
              '$changed unsaved ${changed == 1 ? 'change' : 'changes'}',
              style: AppTypography.textTheme.bodySmall?.copyWith(
                color: AppColors.onSurfaceVariant,
              ),
            ),
          ],
        ],
      );
    }

    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Expanded(child: chips),
        if (changed > 0) ...[
          Text(
            '$changed unsaved ${changed == 1 ? 'change' : 'changes'}',
            style: AppTypography.textTheme.bodySmall?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
          const SizedBox(width: AppSpacing.lg),
        ],
        SizedBox(width: 170, child: save),
        const SizedBox(width: AppSpacing.md),
        SizedBox(width: 110, child: reset),
      ],
    );
  }
}

class _ThresholdTable extends StatelessWidget {
  const _ThresholdTable({
    required this.snapshot,
    required this.draft,
    required this.wide,
    required this.onChanged,
  });

  final ThresholdSnapshot snapshot;
  final Map<String, int> draft;
  final bool wide;
  final void Function(ThresholdRule rule, int value) onChanged;

  @override
  Widget build(BuildContext context) {
    final rules = snapshot.rulesByLastChange;

    return Container(
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(AppRadius.card),
        border: Border.all(color: AppColors.outline),
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final width = constraints.maxWidth < 980
              ? 980.0
              : constraints.maxWidth;
          return SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: SizedBox(
              width: width,
              child: Column(
                children: [
                  const _Header(),
                  for (var i = 0; i < rules.length; i++) ...[
                    if (i > 0)
                      const Divider(
                        height: 1,
                        thickness: 1,
                        color: AppColors.outline,
                      ),
                    _RuleRow(
                      rule: rules[i],
                      value:
                          draft['${rules[i].bloodGroup.name}|'
                              '${rules[i].component.name}'] ??
                          rules[i].threshold,
                      wide: wide,
                      onChanged: (value) => onChanged(rules[i], value),
                    ),
                  ],
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header();

  @override
  Widget build(BuildContext context) {
    final style = AppTypography.textTheme.labelSmall?.copyWith(
      color: AppColors.onSurfaceVariant,
      letterSpacing: 0.5,
    );

    return Container(
      height: 44,
      color: AppColors.surfaceContainerLow,
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
      child: Row(
        children: [
          Expanded(flex: 3, child: Text('BLOOD GROUP', style: style)),
          Expanded(flex: 2, child: Text('COMPONENT', style: style)),
          Expanded(flex: 3, child: Text('ALERT THRESHOLD', style: style)),
          Expanded(flex: 2, child: Text('CURRENT', style: style)),
          Expanded(flex: 2, child: Text('CHANGED BY', style: style)),
          Expanded(flex: 3, child: Text('CHANGED', style: style)),
        ],
      ),
    );
  }
}

class _RuleRow extends StatelessWidget {
  const _RuleRow({
    required this.rule,
    required this.value,
    required this.wide,
    required this.onChanged,
  });

  final ThresholdRule rule;
  final int value;
  final bool wide;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    final height = wide ? 56.0 : 64.0;

    return SizedBox(
      height: height,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg),
        child: Row(
          children: [
            Expanded(
              flex: 3,
              child: Padding(
                padding: const EdgeInsets.only(right: AppSpacing.lg),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        rule.bloodGroup.plainName[0].toUpperCase() +
                            rule.bloodGroup.plainName.substring(1),
                        style: text.bodyLarge,
                      ),
                    ),
                    Text(
                      rule.bloodGroup.code,
                      style: text.bodyMedium?.copyWith(
                        color: AppColors.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(width: AppSpacing.sm),
                  ],
                ),
              ),
            ),
            Expanded(
              flex: 2,
              child: Text(rule.component.label, style: text.bodyLarge),
            ),
            Expanded(
              flex: 3,
              child: Align(
                alignment: Alignment.centerLeft,
                child: _Stepper(
                  value: value,
                  wide: wide,
                  onChanged: onChanged,
                  ruleId:
                      '${rule.bloodGroup.apiValue}-${rule.component.apiValue}',
                  semanticsLabel: '${rule.keyLine} alert threshold',
                ),
              ),
            ),
            Expanded(
              flex: 2,
              child: Text(
                '${rule.current}',
                style: AppFormatters.tabular(
                  text.bodyLarge?.copyWith(
                    color: rule.current < value
                        ? AppColors.error
                        : AppColors.onSurface,
                  ),
                ),
              ),
            ),
            Expanded(
              flex: 2,
              child: Text(rule.changedBy ?? '—', style: text.bodyMedium),
            ),
            Expanded(
              flex: 3,
              child: Text(
                rule.changedLabel,
                style: text.bodySmall?.copyWith(
                  color: AppColors.onSurfaceVariant,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Stepper extends StatelessWidget {
  const _Stepper({
    required this.value,
    required this.wide,
    required this.onChanged,
    required this.ruleId,
    required this.semanticsLabel,
  });

  final int value;
  final bool wide;
  final ValueChanged<int> onChanged;

  /// `O_NEG-CGR`, so a test can read one stepper among the 24.
  final String ruleId;
  final String semanticsLabel;

  static const int _maximum = 99;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    final size = wide ? 44.0 : 48.0;

    Widget button(IconData icon, int next, String label) => SizedBox(
      width: size,
      height: size,
      child: IconButton(
        tooltip: label,
        onPressed: next < 0 || next > _maximum ? null : () => onChanged(next),
        padding: EdgeInsets.zero,
        iconSize: 16,
        icon: Icon(icon),
        color: AppColors.onSurface,
        style: IconButton.styleFrom(
          shape: const RoundedRectangleBorder(),
          foregroundColor: AppColors.onSurface,
          disabledForegroundColor: AppColors.onSurfaceVariant.withValues(
            alpha: 0.4,
          ),
        ),
      ),
    );

    return Semantics(
      label: semanticsLabel,
      value: '$value',
      child: Container(
        width: wide ? 160 : 176,
        height: size,
        decoration: BoxDecoration(
          color: AppColors.surfaceContainerLowest,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: AppColors.outline),
        ),
        child: Row(
          children: [
            button(Icons.remove, value - 1, 'Decrease threshold'),
            Expanded(
              child: Text(
                '$value',
                key: ValueKey('threshold-$ruleId'),
                textAlign: TextAlign.center,
                style: AppFormatters.tabular(text.titleSmall),
              ),
            ),
            button(Icons.add, value + 1, 'Increase threshold'),
          ],
        ),
      ),
    );
  }
}

class _AuditCard extends StatelessWidget {
  const _AuditCard({required this.audit});

  final List<ThresholdChange> audit;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    final visible = audit.take(5).toList(growable: false);

    return Container(
      padding: const EdgeInsets.all(AppSpacing.xl),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(AppRadius.card),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'AUDIT TRAIL · WHO CHANGED IT LAST',
            style: text.labelSmall?.copyWith(
              color: AppColors.onSurfaceVariant,
              letterSpacing: 0.5,
            ),
          ),
          const SizedBox(height: AppSpacing.md),
          if (visible.isEmpty)
            Text(
              'No threshold has been changed yet.',
              style: text.bodyMedium?.copyWith(
                color: AppColors.onSurfaceVariant,
              ),
            )
          else
            for (var i = 0; i < visible.length; i++) ...[
              if (i > 0) const SizedBox(height: AppSpacing.sm),
              _AuditRow(change: visible[i]),
            ],
        ],
      ),
    );
  }
}

class _AuditRow extends StatelessWidget {
  const _AuditRow({required this.change});

  final ThresholdChange change;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return LayoutBuilder(
      builder: (context, constraints) {
        final narrow = constraints.maxWidth < 640;
        final who = Text(
          '${change.who} · ${change.ruleKey}',
          style: text.bodyMedium,
        );
        final changeLabel = Text(
          change.changeLabel,
          style: AppFormatters.tabular(text.bodyMedium),
        );
        final at = Text(
          change.atLabel,
          style: text.bodySmall?.copyWith(color: AppColors.onSurfaceVariant),
        );

        if (narrow) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              who,
              const SizedBox(height: 2),
              changeLabel,
              const SizedBox(height: 2),
              at,
            ],
          );
        }

        return Row(
          children: [
            Expanded(child: who),
            SizedBox(width: 180, child: changeLabel),
            SizedBox(
              width: 200,
              child: Align(alignment: Alignment.centerRight, child: at),
            ),
          ],
        );
      },
    );
  }
}
