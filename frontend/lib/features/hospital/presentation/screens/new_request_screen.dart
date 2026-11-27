import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/theme/tokens/color_tokens.dart';
import '../../../../app/theme/tokens/spacing_tokens.dart';
import '../../../../app/theme/tokens/typography_tokens.dart';
import '../../../../core/errors/api_error.dart';
import '../../../../data/mock/mock_request_repository.dart';
import '../../../../data/models/blood_component.dart';
import '../../../../data/models/blood_group.dart';
import '../../../../data/models/blood_request.dart';
import '../../../../data/models/stock_level.dart';
import '../../../../shared/widgets/app_buttons.dart';
import '../../../../shared/widgets/app_card.dart';
import '../../../../shared/widgets/app_chip.dart';
import '../../../../shared/widgets/app_page_header.dart';
import '../../../../shared/widgets/app_select_field.dart';
import '../../../../shared/widgets/app_snack_bar.dart';
import '../../../../shared/widgets/app_text_field.dart';
import '../providers/hospital_providers.dart';

/// H1 · New request — Hospital.
///
/// One anonymous patient reference, the group, component, units and urgency.
/// The local bank is checked first; the side column says what will happen and
/// how much of that group the hospital holds right now.
class NewRequestScreen extends ConsumerStatefulWidget {
  const NewRequestScreen({super.key});

  @override
  ConsumerState<NewRequestScreen> createState() => _NewRequestScreenState();
}

class _NewRequestScreenState extends ConsumerState<NewRequestScreen> {
  final _patientReference = TextEditingController(text: 'P20260442');
  final _units = TextEditingController(text: '3');
  final _note = TextEditingController(
    text: 'Orthopaedic surgery tomorrow morning.',
  );

  BloodGroup _group = BloodGroup.oNeg;
  BloodComponent _component = BloodComponent.redCells;
  RequestUrgency _urgency = RequestUrgency.high;
  Map<String, String> _errors = const {};
  bool _sending = false;

  @override
  void dispose() {
    _patientReference.dispose();
    _units.dispose();
    _note.dispose();
    super.dispose();
  }

  int get _unitCount => int.tryParse(_units.text.trim()) ?? 0;

  @override
  Widget build(BuildContext context) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;
    // Demo control: `?state=shortage|invalid|unavailable`.
    final variant = GoRouterState.of(context).uri.queryParameters['state'];

    final local = ref.watch(
      stockSnapshotProvider((siteId: hospitalId, variant: null)),
    );
    final regional = ref.watch(
      stockSnapshotProvider((siteId: 'C-SFAX', variant: null)),
    );
    final national = ref.watch(
      stockSnapshotProvider((siteId: 'CNTS', variant: null)),
    );

    final form = _FormCard(
      patientReference: _patientReference,
      units: _units,
      note: _note,
      group: _group,
      component: _component,
      urgency: _urgency,
      errors: _errors,
      sending: _sending,
      onGroup: (group) => setState(() => _group = group),
      onComponent: (component) => setState(() => _component = component),
      onUrgency: (urgency) => setState(() => _urgency = urgency),
      onChanged: _clearError,
      onSend: () => _send(variant),
      onClear: _clear,
    );

    final side = _SideColumn(
      group: _group,
      component: _component,
      units: _unitCount,
      local: local.value,
      regional: regional.value,
      national: national.value,
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
            title: 'New request',
            subtitle:
                'A hospital asks for blood. The local bank is checked first, '
                'then the regional CRTS or the national service is asked to '
                'supply.',
          ),
          const SizedBox(height: AppSpacing.xl),
          if (wide)
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(flex: 780, child: form),
                const SizedBox(width: AppSpacing.xxl),
                Expanded(flex: 448, child: side),
              ],
            )
          else ...[
            form,
            const SizedBox(height: AppSpacing.lg),
            side,
          ],
          const SizedBox(height: AppSpacing.xl),
          Text(
            'The request is saved first, then stock is checked and reserved in '
            'one operation, so two hospitals can never take the same pouches.',
            style: AppTypography.textTheme.bodySmall?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }

  void _clearError(String field) {
    if (!_errors.containsKey(field)) return;
    setState(() => _errors = {..._errors}..remove(field));
  }

  void _clear() {
    setState(() {
      _patientReference.text = '';
      _units.text = '1';
      _note.text = '';
      _group = BloodGroup.oNeg;
      _component = BloodComponent.redCells;
      _urgency = RequestUrgency.normal;
      _errors = const {};
    });
  }

  Future<void> _send(String? variant) async {
    if (_sending) return;
    final reference = _patientReference.text.trim();
    final fields = <String, String>{};

    if (reference.isEmpty) {
      fields['patientRef'] = 'Enter the patient reference.';
    } else if (!RegExp(r'^[A-Za-z0-9]+$').hasMatch(reference)) {
      fields['patientRef'] =
          'Use letters and digits only — no spaces, dashes or accents.';
    }
    if (_unitCount < 1) {
      fields['units'] = 'A request needs at least one unit.';
    }
    if (fields.isNotEmpty) {
      setState(() => _errors = fields);
      return;
    }

    setState(() {
      _sending = true;
      _errors = const {};
    });

    final draft = BloodRequestDraft(
      patientRef: reference,
      reason: RequestReason.surgery,
      bloodGroup: _group,
      component: _component,
      units: _unitCount,
      urgency: _urgency,
      note: _note.text.trim().isEmpty ? null : _note.text.trim(),
    );

    try {
      final created = await ref
          .read(requestRepositoryProvider)
          .create(draft, variant: variant);
      ref.invalidate(bloodRequestsProvider);
      ref.invalidate(requestSummaryProvider);
      if (!mounted) return;

      final (message, tone) = switch (created.status) {
        RequestStatus.satisfied => (
          'Request ${created.id} sent. The local bank reserved '
              '${created.unitsLine}.',
          AppSnackTone.success,
        ),
        RequestStatus.supplying => (
          'Request ${created.id} sent. '
              '${created.supplySource?.label ?? 'The central service'} will '
              'supply ${created.unitsLine}.',
          AppSnackTone.neutral,
        ),
        RequestStatus.shortage => (
          'Request ${created.id} sent. No place has enough blood, so an '
              'emergency appeal is open.',
          AppSnackTone.warning,
        ),
        _ => (
          'Request ${created.id} saved. The stock service could not be '
              'reached; it will be checked again automatically.',
          AppSnackTone.neutral,
        ),
      };
      showAppSnack(context, message, tone: tone);
      context.go('/hospital/requests/${created.id}');
    } on ApiError catch (error) {
      if (!mounted) return;
      setState(() {
        _errors = {
          for (final entry in error.fieldErrors.entries)
            switch (entry.key) {
              'quantitePoches' => 'units',
              _ => entry.key,
            }: entry.value,
        };
      });
      showAppSnack(context, apiMessageFor(error), tone: AppSnackTone.critical);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }
}

// --- form -----------------------------------------------------------------

class _FormCard extends StatelessWidget {
  const _FormCard({
    required this.patientReference,
    required this.units,
    required this.note,
    required this.group,
    required this.component,
    required this.urgency,
    required this.errors,
    required this.sending,
    required this.onGroup,
    required this.onComponent,
    required this.onUrgency,
    required this.onChanged,
    required this.onSend,
    required this.onClear,
  });

  final TextEditingController patientReference;
  final TextEditingController units;
  final TextEditingController note;
  final BloodGroup group;
  final BloodComponent component;
  final RequestUrgency urgency;
  final Map<String, String> errors;
  final bool sending;
  final ValueChanged<BloodGroup> onGroup;
  final ValueChanged<BloodComponent> onComponent;
  final ValueChanged<RequestUrgency> onUrgency;
  final ValueChanged<String> onChanged;
  final VoidCallback onSend;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return AppCard(
      padding: const EdgeInsets.all(AppSpacing.xxl),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const _SectionLabel('PATIENT'),
          AppTextField(
            label: 'PATIENT REFERENCE',
            controller: patientReference,
            hint: 'PAT20260042',
            helperText:
                'An anonymous reference only — no patient name is stored.',
            errorText: errors['patientRef'],
            textInputAction: TextInputAction.next,
            onChanged: (_) => onChanged('patientRef'),
          ),
          const SizedBox(height: AppSpacing.xl),
          const _SectionLabel('WHAT IS NEEDED'),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                flex: 2,
                child: AppSelectField<BloodGroup>(
                  label: 'BLOOD GROUP',
                  value: group,
                  items: BloodGroup.values,
                  labelOf: (item) => item.plainName,
                  onSelected: onGroup,
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: AppTextField(
                  label: 'UNITS',
                  controller: units,
                  hint: '1',
                  keyboardType: TextInputType.number,
                  errorText: errors['units'],
                  onChanged: (_) => onChanged('units'),
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.lg),
          Text(
            'COMPONENT',
            style: text.labelMedium?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: AppSpacing.sm),
          Wrap(
            spacing: AppSpacing.sm,
            runSpacing: AppSpacing.sm,
            children: [
              for (final item in BloodComponent.values)
                AppChip(
                  label: item.label,
                  selected: item == component,
                  onTap: () => onComponent(item),
                ),
            ],
          ),
          const SizedBox(height: AppSpacing.lg),
          Text(
            'URGENCY',
            style: text.labelMedium?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: AppSpacing.sm),
          Wrap(
            spacing: AppSpacing.sm,
            runSpacing: AppSpacing.sm,
            children: [
              for (final item in RequestUrgency.values)
                AppChip(
                  label: item.label,
                  selected: item == urgency,
                  onTap: () => onUrgency(item),
                ),
            ],
          ),
          if (urgency.isCritical) ...[
            const SizedBox(height: AppSpacing.md),
            const _CriticalNote(),
          ],
          const SizedBox(height: AppSpacing.lg),
          AppTextField(
            label: 'NOTE (OPTIONAL)',
            controller: note,
            hint: 'e.g. Orthopaedic surgery tomorrow morning.',
            maxLines: 3,
          ),
          const SizedBox(height: AppSpacing.xl),
          Row(
            children: [
              Expanded(
                flex: 2,
                child: AppFilledButton(
                  label: sending ? 'Sending...' : 'Send request',
                  onPressed: sending ? null : onSend,
                  height: 52,
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: AppOutlinedButton(
                  label: 'Clear',
                  onPressed: sending ? null : onClear,
                  height: 52,
                ),
              ),
            ],
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
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: Text(
        label,
        style: AppTypography.textTheme.labelSmall?.copyWith(
          color: AppColors.onSurfaceVariant,
          letterSpacing: 0.5,
        ),
      ),
    );
  }
}

class _CriticalNote extends StatelessWidget {
  const _CriticalNote();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: AppColors.errorContainer,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        'Critical · this request is treated as an emergency and gets a '
        'shorter delivery time.',
        style: AppTypography.textTheme.bodyMedium?.copyWith(
          color: AppColors.onErrorContainer,
        ),
      ),
    );
  }
}

// --- side -----------------------------------------------------------------

class _SideColumn extends StatelessWidget {
  const _SideColumn({
    required this.group,
    required this.component,
    required this.units,
    required this.local,
    required this.regional,
    required this.national,
  });

  final BloodGroup group;
  final BloodComponent component;
  final int units;
  final StockSnapshot? local;
  final StockSnapshot? regional;
  final StockSnapshot? national;

  @override
  Widget build(BuildContext context) {
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
                'WHAT HAPPENS NEXT',
                style: text.labelSmall?.copyWith(
                  color: AppColors.onSurfaceVariant,
                  letterSpacing: 0.5,
                ),
              ),
              const SizedBox(height: AppSpacing.lg),
              const _NumberedStep(
                number: '1',
                text: 'The local bank is checked first.',
              ),
              const SizedBox(height: AppSpacing.md),
              const _NumberedStep(
                number: '2',
                text:
                    'If it is short, the regional CRTS is asked, then the '
                    'national service.',
              ),
              const SizedBox(height: AppSpacing.md),
              const _NumberedStep(
                number: '3',
                text:
                    'The pouches are reserved and delivered to the hospital '
                    'bank.',
              ),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.lg),
        _LocalStock(
          group: group,
          component: component,
          units: units,
          local: local,
          regional: regional,
          national: national,
        ),
      ],
    );
  }
}

class _NumberedStep extends StatelessWidget {
  const _NumberedStep({required this.number, required this.text});

  final String number;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 24,
          child: Text(number, style: AppTypography.textTheme.titleSmall),
        ),
        Expanded(child: Text(text, style: AppTypography.textTheme.bodyMedium)),
      ],
    );
  }
}

class _LocalStock extends StatelessWidget {
  const _LocalStock({
    required this.group,
    required this.component,
    required this.units,
    required this.local,
    required this.regional,
    required this.national,
  });

  final BloodGroup group;
  final BloodComponent component;
  final int units;
  final StockSnapshot? local;
  final StockSnapshot? regional;
  final StockSnapshot? national;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    final snapshot = local;

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
            'LOCAL STOCK · ${group.plainName.toUpperCase()}',
            style: text.labelSmall?.copyWith(
              color: AppColors.onSurfaceVariant,
              letterSpacing: 0.5,
            ),
          ),
          const SizedBox(height: AppSpacing.md),
          if (snapshot == null)
            Text(
              'Stock levels are unavailable right now. The request can still '
              'be sent — the service checks the stock in the same operation.',
              style: text.bodyMedium?.copyWith(
                color: AppColors.onSurfaceVariant,
              ),
            )
          else ...[
            for (final item in BloodComponent.values) ...[
              _StockLine(
                labelled: item.label,
                quantity: snapshot.levelFor(group, item)?.quantity ?? 0,
              ),
              if (item != BloodComponent.values.last)
                const SizedBox(height: AppSpacing.sm),
            ],
            const SizedBox(height: AppSpacing.md),
            Text(
              _supplyNote(),
              style: text.bodySmall?.copyWith(
                color: AppColors.onSurfaceVariant,
              ),
            ),
          ],
        ],
      ),
    );
  }

  /// The one sentence that explains the supply path for the chosen units.
  String _supplyNote() {
    if (units < 1) {
      return 'Enter at least one unit to see how this request will be '
          'supplied.';
    }
    final here = local?.levelFor(group, component)?.quantity ?? 0;
    final regionalUnits = regional?.levelFor(group, component)?.quantity ?? 0;
    final nationalUnits = national?.levelFor(group, component)?.quantity ?? 0;
    final needed = units == 1 ? '1 unit' : '$units units';

    if (here >= units) {
      return 'This request needs $needed, so the local bank can supply it.';
    }
    if (regionalUnits >= units) {
      return 'This request needs $needed, so it will be supplied by the '
          'regional CRTS.';
    }
    if (nationalUnits >= units) {
      return 'This request needs $needed, so it will be supplied by the '
          'national service.';
    }
    return 'No place has $needed of this component right now, so an emergency '
        'appeal will be opened.';
  }
}

class _StockLine extends StatelessWidget {
  const _StockLine({required this.labelled, required this.quantity});

  final String labelled;
  final int quantity;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(labelled, style: AppTypography.textTheme.bodyMedium),
        ),
        Text('$quantity available', style: AppTypography.textTheme.titleSmall),
      ],
    );
  }
}
