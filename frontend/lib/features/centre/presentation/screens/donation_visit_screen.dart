import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/theme/tokens/color_tokens.dart';
import '../../../../app/theme/tokens/spacing_tokens.dart';
import '../../../../app/theme/tokens/typography_tokens.dart';
import '../../../../core/errors/api_error.dart';
import '../../../../core/format/formatters.dart';
import '../../../../data/models/certificate.dart';
import '../../../../data/models/visit.dart';
import '../../../../shared/widgets/app_badge.dart';
import '../../../../shared/widgets/app_buttons.dart';
import '../../../../shared/widgets/app_card.dart';
import '../../../../shared/widgets/app_chip.dart';
import '../../../../shared/widgets/app_confirm_dialog.dart';
import '../../../../shared/widgets/app_detail_grid.dart';
import '../../../../shared/widgets/app_error_state.dart';
import '../../../../shared/widgets/app_loading_state.dart';
import '../../../../shared/widgets/app_page_header.dart';
import '../../../../shared/widgets/app_snack_bar.dart';
import '../../../../shared/widgets/app_text_field.dart';
import '../providers/centre_providers.dart';

/// C3 · Donation visit — Centre staff, web 1440 and mobile 390.
///
/// One visit, one panel per step: reception, medical interview, collection,
/// rest. Steps run in order (R6), the collection is exactly 450 ml (R8) and the
/// visit ends only after 15 minutes of rest (R9).
class DonationVisitScreen extends ConsumerWidget {
  const DonationVisitScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;
    final route = GoRouterState.of(context);
    final id = route.pathParameters['id'] ?? '';
    // Demo control: `?state=reception|interview|collection|rest|done|refused|
    // rest-not-finished|wrong-step|unavailable`.
    final variant = route.uri.queryParameters['state'];
    final visit = ref.watch(visitProvider((id: id, variant: variant)));

    return SingleChildScrollView(
      padding: wide
          ? const EdgeInsets.fromLTRB(
              AppSpacing.page,
              28,
              AppSpacing.page,
              AppSpacing.xxxl,
            )
          : const EdgeInsets.all(AppSpacing.lg),
      child: visit.when(
        loading: () => const AppLoadingState(label: 'Loading the visit...'),
        error: (error, stackTrace) => AppErrorState(
          title: 'We could not load this visit',
          message: error is ApiError
              ? apiMessageFor(error)
              : 'Check your connection and try again.',
          onRetry: () => ref.invalidate(visitProvider),
        ),
        data: (value) => _Visit(visit: value, wide: wide, variant: variant),
      ),
    );
  }
}

class _Visit extends StatelessWidget {
  const _Visit({required this.visit, required this.wide, this.variant});

  final Visit visit;
  final bool wide;
  final String? variant;

  @override
  Widget build(BuildContext context) {
    final started = AppFormatters.time(visit.startedAt);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        AppPageHeader(
          wide: wide,
          title: wide ? 'Donation visit · ${visit.id}' : 'Donation visit',
          subtitle: wide
              ? '${visit.donorName} · ${visit.bloodGroup.code} · reception '
                    '$started · ${visit.centreName}. Four steps, always in '
                    'order.'
              : '${visit.id} · ${visit.donorName} · '
                    '${visit.bloodGroup.code} · reception $started',
        ),
        const SizedBox(height: AppSpacing.xl),
        if (wide)
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(width: 410, child: _StepsCard(visit: visit)),
              const SizedBox(width: AppSpacing.xxl),
              Expanded(
                child: _StepPanel(
                  visit: visit,
                  wide: true,
                  variant: variant,
                ),
              ),
            ],
          )
        else ...[
          _StepsChips(visit: visit),
          const SizedBox(height: AppSpacing.lg),
          _StepPanel(visit: visit, wide: false, variant: variant),
        ],
        const SizedBox(height: AppSpacing.xl),
        Text(
          wide
              ? 'The reception can start a walk-in visit with no appointment, '
                    'or link the visit to one booked online.'
              : 'Reception records the CIN and the health questionnaire; the '
                    'doctor records blood pressure, haemoglobin and the '
                    'decision.',
          style: AppTypography.textTheme.bodySmall?.copyWith(
            color: AppColors.onSurfaceVariant,
          ),
        ),
      ],
    );
  }
}

// --- steps -----------------------------------------------------------------

enum _StepState { done, current, pending, refused }

/// The left card of the wide layout: the four steps and where the visit stands.
class _StepsCard extends StatelessWidget {
  const _StepsCard({required this.visit});

  final Visit visit;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'STEPS',
            style: text.labelSmall?.copyWith(
              color: AppColors.onSurfaceVariant,
              letterSpacing: 0.5,
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
          for (final step in VisitStep.ordered)
            Padding(
              padding: const EdgeInsets.only(bottom: AppSpacing.lg),
              child: _StepRow(visit: visit, step: step),
            ),
          Text(
            'A step cannot be skipped or repeated.',
            style: text.bodySmall?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

/// The mobile layout: the same four steps as chips, fill up to the current one.
class _StepsChips extends StatelessWidget {
  const _StepsChips({required this.visit});

  final Visit visit;

  @override
  Widget build(BuildContext context) {
    final current = _currentIndex(visit.step);
    return Wrap(
      spacing: AppSpacing.sm,
      runSpacing: AppSpacing.sm,
      children: [
        for (var i = 0; i < VisitStep.ordered.length; i++)
          AppChip(
            label: '${i + 1} ${VisitStep.ordered[i].boardTitle}',
            selected: i <= current,
          ),
      ],
    );
  }
}

class _StepRow extends StatelessWidget {
  const _StepRow({required this.visit, required this.step});

  final Visit visit;
  final VisitStep step;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    final state = _stateFor(visit, step);
    final (label, tone) = switch (state) {
      _StepState.done => ('Done', AppBadgeTone.success),
      _StepState.current => ('In progress', AppBadgeTone.warning),
      _StepState.pending => ('Pending', AppBadgeTone.neutral),
      _StepState.refused => ('Refused', AppBadgeTone.critical),
    };
    final number = step.position ?? 0;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 26,
          height: 26,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: state == _StepState.pending
                ? AppColors.surfaceContainer
                : AppColors.primary,
            shape: BoxShape.circle,
          ),
          child: Text(
            '$number',
            style: text.labelMedium?.copyWith(
              color: state == _StepState.pending
                  ? AppColors.onSurfaceVariant
                  : AppColors.onPrimary,
            ),
          ),
        ),
        const SizedBox(width: AppSpacing.md),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(step.label, style: text.titleSmall),
              const SizedBox(height: 2),
              Text(
                _subtitle(visit, step),
                style: text.bodySmall?.copyWith(
                  color: AppColors.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(width: AppSpacing.sm),
        AppBadge(label: label, tone: tone),
      ],
    );
  }
}

// --- the working panel -----------------------------------------------------

class _StepPanel extends StatelessWidget {
  const _StepPanel({
    required this.visit,
    required this.wide,
    required this.variant,
  });

  final Visit visit;
  final bool wide;
  final String? variant;

  @override
  Widget build(BuildContext context) {
    return switch (visit.step) {
      VisitStep.reception => _ReceptionPanel(visit: visit, wide: wide),
      VisitStep.interview => _InterviewPanel(
        visit: visit,
        wide: wide,
        variant: variant,
      ),
      VisitStep.collection => _CollectionPanel(
        visit: visit,
        wide: wide,
        variant: variant,
      ),
      VisitStep.rest => _RestPanel(visit: visit, wide: wide, variant: variant),
      VisitStep.done => _CompletedPanel(visit: visit, wide: wide),
      VisitStep.refused => _RefusedPanel(visit: visit, wide: wide),
    };
  }
}

/// The card around a step panel: `STEP 2 · MEDICAL INTERVIEW`, a description and
/// the body.
class _PanelCard extends StatelessWidget {
  const _PanelCard({
    required this.visit,
    required this.description,
    required this.body,
    this.footNote,
  });

  final Visit visit;
  final String description;
  final List<Widget> body;
  final String? footNote;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    final label = visit.step == VisitStep.done
        ? 'VISIT COMPLETE'
        : visit.step == VisitStep.refused
        ? 'VISIT REFUSED'
        : visit.step.boardLabel;

    return AppCard(
      padding: const EdgeInsets.all(AppSpacing.xl),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: text.labelSmall?.copyWith(letterSpacing: 0.5)),
          const SizedBox(height: AppSpacing.sm),
          Text(
            description,
            style: text.bodyMedium?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
          ...body,
          if (footNote != null) ...[
            const SizedBox(height: AppSpacing.lg),
            Text(
              footNote!,
              style: text.bodySmall?.copyWith(
                color: AppColors.onSurfaceVariant,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// The quiet note panel used before a decision or an action.
class _Note extends StatelessWidget {
  const _Note({required this.message, this.tone = AppBadgeTone.critical});

  final String message;
  final AppBadgeTone tone;

  @override
  Widget build(BuildContext context) {
    final (background, foreground) = switch (tone) {
      AppBadgeTone.critical => (
        AppColors.errorContainer,
        AppColors.onErrorContainer,
      ),
      AppBadgeTone.warning => (
        AppColors.warningContainer,
        AppColors.onWarningContainer,
      ),
      _ => (AppColors.surfaceContainerLow, AppColors.onSurface),
    };

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(AppRadius.small),
      ),
      child: Text(
        message,
        style: AppTypography.textTheme.bodyMedium?.copyWith(color: foreground),
      ),
    );
  }
}

// --- reception --------------------------------------------------------------

class _ReceptionPanel extends ConsumerStatefulWidget {
  const _ReceptionPanel({required this.visit, required this.wide});

  final Visit visit;
  final bool wide;

  @override
  ConsumerState<_ReceptionPanel> createState() => _ReceptionPanelState();
}

class _ReceptionPanelState extends ConsumerState<_ReceptionPanel> {
  late final TextEditingController _cin;
  late List<VisitQuestion> _questions;
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    _cin = TextEditingController();
    _questions = List.of(widget.visit.questionnaire);
  }

  @override
  void dispose() {
    _cin.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final wide = widget.wide;

    return _PanelCard(
      visit: widget.visit,
      description:
          'Reception records the CIN and the health questionnaire. The visit '
          'then moves to the medical interview.',
      body: [
        AppTextField(
          label: 'CIN',
          controller: _cin,
          hint: '8 digits',
          keyboardType: TextInputType.number,
        ),
        const SizedBox(height: AppSpacing.lg),
        Text('QUESTIONNAIRE', style: AppTypography.textTheme.labelSmall),
        const SizedBox(height: AppSpacing.sm),
        for (var i = 0; i < _questions.length; i++)
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              SizedBox(
                width: 48,
                height: 48,
                child: Checkbox(
                  value: _questions[i].answer,
                  onChanged: (value) => setState(() {
                    _questions[i] = VisitQuestion(
                      label: _questions[i].label,
                      answer: value ?? false,
                    );
                  }),
                ),
              ),
              const SizedBox(width: AppSpacing.sm),
              Expanded(
                child: Text(
                  _questions[i].label,
                  style: AppTypography.textTheme.bodyMedium,
                ),
              ),
            ],
          ),
        const SizedBox(height: AppSpacing.lg),
        _Actions(
          wide: wide,
          children: [
            AppFilledButton(
              label: 'Confirm reception',
              height: wide ? 44 : 48,
              onPressed: _submitting ? null : _confirm,
            ),
          ],
        ),
      ],
      footNote:
          'A walk-in has no appointment; an online booking is linked by its '
          'reference.',
    );
  }

  Future<void> _confirm() async {
    setState(() => _submitting = true);
    try {
      await ref
          .read(visitRepositoryProvider)
          .recordReception(
            widget.visit.id,
            cin: _cin.text.trim(),
            questionnaire: _questions,
          );
      _refresh(ref);
      if (!mounted) return;
      showAppSnack(context, 'Reception recorded.');
    } on ApiError catch (error) {
      if (!mounted) return;
      showAppSnack(context, apiMessageFor(error), tone: AppSnackTone.critical);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }
}

// --- medical interview ------------------------------------------------------

class _InterviewPanel extends ConsumerStatefulWidget {
  const _InterviewPanel({
    required this.visit,
    required this.wide,
    required this.variant,
  });

  final Visit visit;
  final bool wide;
  final String? variant;

  @override
  ConsumerState<_InterviewPanel> createState() => _InterviewPanelState();
}

class _InterviewPanelState extends ConsumerState<_InterviewPanel> {
  late final TextEditingController _systolic;
  late final TextEditingController _diastolic;
  late final TextEditingController _haemoglobin;
  VisitDecision _decision = VisitDecision.fit;
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    final visit = widget.visit;
    _systolic = TextEditingController(text: visit.systolic?.toString() ?? '');
    _diastolic = TextEditingController(text: visit.diastolic?.toString() ?? '');
    _haemoglobin = TextEditingController(
      text: visit.haemoglobin?.toString() ?? '',
    );
    _decision = visit.decision ?? VisitDecision.fit;
  }

  @override
  void dispose() {
    _systolic.dispose();
    _diastolic.dispose();
    _haemoglobin.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final wide = widget.wide;

    final fields = [
      AppTextField(
        label: 'BLOOD PRESSURE (SYSTOLIC)',
        controller: _systolic,
        keyboardType: TextInputType.number,
      ),
      AppTextField(
        label: wide ? '(DIASTOLIC)' : 'DIASTOLIC',
        controller: _diastolic,
        keyboardType: TextInputType.number,
      ),
      AppTextField(
        label: 'HAEMOGLOBIN (G/DL)',
        controller: _haemoglobin,
        keyboardType: const TextInputType.numberWithOptions(decimal: true),
      ),
    ];

    return _PanelCard(
      visit: widget.visit,
      description:
          'Doctor records blood pressure and haemoglobin, then decides fit or '
          'unfit.',
      body: [
        if (wide)
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              for (var i = 0; i < fields.length; i++) ...[
                if (i > 0) const SizedBox(width: AppSpacing.md),
                Expanded(child: fields[i]),
              ],
            ],
          )
        else ...[
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(child: fields[0]),
              const SizedBox(width: AppSpacing.md),
              Expanded(child: fields[1]),
            ],
          ),
          const SizedBox(height: AppSpacing.lg),
          fields[2],
        ],
        const SizedBox(height: AppSpacing.lg),
        Text('DECISION', style: AppTypography.textTheme.labelSmall),
        const SizedBox(height: AppSpacing.sm),
        Wrap(
          spacing: AppSpacing.sm,
          runSpacing: AppSpacing.sm,
          children: [
            AppChip(
              label: wide ? VisitDecision.fit.label : VisitDecision.fit.shortLabel,
              selected: _decision == VisitDecision.fit,
              onTap: () => setState(() => _decision = VisitDecision.fit),
            ),
            AppChip(
              label: VisitDecision.unfit.label,
              selected: _decision == VisitDecision.unfit,
              onTap: () => setState(() => _decision = VisitDecision.unfit),
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.lg),
        _Note(
          message: wide
              ? 'An unfit decision ends the visit: no collection and no '
                    'certificate. It can also add a temporary deferral for the '
                    'donor.'
              : 'An unfit decision ends the visit: no collection and no '
                    'certificate.',
        ),
        const SizedBox(height: AppSpacing.lg),
        _Actions(
          wide: wide,
          children: [
            AppFilledButton(
              label: 'Confirm fit',
              height: wide ? 44 : 48,
              onPressed: _submitting ? null : () => _submit(VisitDecision.fit),
            ),
            AppOutlinedButton(
              label: 'Record deferral',
              height: wide ? 44 : 48,
              onPressed: _submitting
                  ? null
                  : () => context.go('/centre/deferrals/new'),
            ),
            AppOutlinedButton(
              label: wide ? 'Refuse visit' : 'Refuse',
              height: wide ? 44 : 48,
              critical: true,
              onPressed: _submitting ? null : _refuse,
            ),
          ],
        ),
      ],
      footNote:
          'Collection records the pouch and returns the national donation '
          'number. The certificate is prepared when the visit ends.',
    );
  }

  Future<void> _submit(VisitDecision decision) async {
    setState(() => _submitting = true);
    try {
      final interview = VisitInterview(
        systolic: int.tryParse(_systolic.text.trim()) ?? 0,
        diastolic: int.tryParse(_diastolic.text.trim()) ?? 0,
        haemoglobin: double.tryParse(_haemoglobin.text.trim()) ?? 0,
        decision: decision,
      );
      final result = await ref
          .read(visitRepositoryProvider)
          .recordInterview(
            widget.visit.id,
            interview,
            variant: widget.variant,
          );
      _refresh(ref);
      if (!mounted) return;
      showAppSnack(
        context,
        result.step == VisitStep.refused
            ? 'Visit refused. No collection and no certificate.'
            : 'Interview recorded. The visit moves to collection.',
      );
    } on ApiError catch (error) {
      if (!mounted) return;
      showAppSnack(context, apiMessageFor(error), tone: AppSnackTone.critical);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<void> _refuse() async {
    final confirmed = await showAppConfirm(
      context,
      title: 'Refuse this visit?',
      message: 'An unfit decision ends the visit as REFUSE. There is no '
          'collection and no certificate.',
      confirmLabel: 'Refuse visit',
      destructive: true,
    );
    if (!confirmed || !mounted) return;
    await _submit(VisitDecision.unfit);
  }
}

// --- collection -------------------------------------------------------------

class _CollectionPanel extends ConsumerStatefulWidget {
  const _CollectionPanel({
    required this.visit,
    required this.wide,
    required this.variant,
  });

  final Visit visit;
  final bool wide;
  final String? variant;

  @override
  ConsumerState<_CollectionPanel> createState() => _CollectionPanelState();
}

class _CollectionPanelState extends ConsumerState<_CollectionPanel> {
  late final TextEditingController _duration;
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    _duration = TextEditingController(
      text: widget.visit.durationMinutes?.toString() ?? '10',
    );
  }

  @override
  void dispose() {
    _duration.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final wide = widget.wide;

    return _PanelCard(
      visit: widget.visit,
      description:
          'Nurse records the pouch. The volume is fixed at 450 ml, and the '
          'national donation number comes back with the collection.',
      body: [
        if (wide)
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: AppTextField(
                  label: 'VOLUME (ML)',
                  controller: TextEditingController(text: '450'),
                  enabled: false,
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: AppTextField(
                  label: 'DURATION (MIN)',
                  controller: _duration,
                  keyboardType: TextInputType.number,
                ),
              ),
            ],
          )
        else ...[
          AppTextField(
            label: 'VOLUME (ML)',
            controller: TextEditingController(text: '450'),
            enabled: false,
          ),
          const SizedBox(height: AppSpacing.lg),
          AppTextField(
            label: 'DURATION (MIN)',
            controller: _duration,
            keyboardType: TextInputType.number,
          ),
        ],
        const SizedBox(height: AppSpacing.lg),
        _Note(
          message:
              'The volume must equal 450 ml. Any other value is refused and '
              'the pouch is not recorded.',
          tone: AppBadgeTone.warning,
        ),
        const SizedBox(height: AppSpacing.lg),
        _Actions(
          wide: wide,
          children: [
            AppFilledButton(
              label: 'Confirm collection',
              height: wide ? 44 : 48,
              onPressed: _submitting ? null : _confirm,
            ),
          ],
        ),
      ],
      footNote:
          'After collection the donor rests at least 15 minutes before the '
          'visit can end.',
    );
  }

  Future<void> _confirm() async {
    setState(() => _submitting = true);
    try {
      await ref
          .read(visitRepositoryProvider)
          .recordCollection(
            widget.visit.id,
            VisitCollection(
              volumeMl: 450,
              durationMinutes: int.tryParse(_duration.text.trim()) ?? 10,
            ),
            variant: widget.variant,
          );
      _refresh(ref);
      if (!mounted) return;
      showAppSnack(context, 'Collection recorded. The donor can rest.');
    } on ApiError catch (error) {
      if (!mounted) return;
      showAppSnack(context, apiMessageFor(error), tone: AppSnackTone.critical);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }
}

// --- rest -------------------------------------------------------------------

class _RestPanel extends ConsumerStatefulWidget {
  const _RestPanel({
    required this.visit,
    required this.wide,
    required this.variant,
  });

  final Visit visit;
  final bool wide;
  final String? variant;

  @override
  ConsumerState<_RestPanel> createState() => _RestPanelState();
}

class _RestPanelState extends ConsumerState<_RestPanel> {
  bool _submitting = false;

  @override
  Widget build(BuildContext context) {
    final wide = widget.wide;
    final visit = widget.visit;
    final left = visit.restMinutesLeft ?? 0;
    final canEnd = left <= 0;

    return _PanelCard(
      visit: visit,
      description:
          'The donor rests at least 15 minutes. The visit ends when the rest '
          'is over, and the certificate is prepared.',
      body: [
        AppDetailGrid(
          columns: wide ? 3 : 2,
          entries: [
            AppDetailEntry(
              label: 'COLLECTED AT',
              value: visit.collectedAt == null
                  ? '—'
                  : AppFormatters.time(visit.collectedAt!),
            ),
            AppDetailEntry(
              label: 'REST ENDS AT',
              value: visit.restEndsAt == null
                  ? '—'
                  : AppFormatters.time(visit.restEndsAt!),
            ),
            AppDetailEntry(
              label: 'MINUTES LEFT',
              value: '$left',
            ),
          ],
          tabular: true,
        ),
        const SizedBox(height: AppSpacing.lg),
        _Note(
          message: canEnd
              ? 'The minimum rest is done. The visit can end.'
              : '$left min left. The visit ends only after the minimum rest of '
                    '15 minutes.',
        ),
        const SizedBox(height: AppSpacing.lg),
        _Actions(
          wide: wide,
          children: [
            AppFilledButton(
              label: 'End the visit',
              height: wide ? 44 : 48,
              onPressed: _submitting || !canEnd ? null : _end,
            ),
          ],
        ),
      ],
      footNote:
          'A refused visit never reaches this step and gets no certificate.',
    );
  }

  Future<void> _end() async {
    setState(() => _submitting = true);
    try {
      final visit = widget.visit;
      final restStartedAt =
          visit.restStartedAt ?? visit.collectedAt ?? visit.startedAt;
      final restEndsAt =
          visit.restEndsAt ?? restStartedAt.add(const Duration(minutes: 15));
      await ref
          .read(visitRepositoryProvider)
          .finishRest(
            visit.id,
            restStartedAt: restStartedAt,
            restEndsAt: restEndsAt,
            variant: widget.variant,
          );
      _refresh(ref);
      if (!mounted) return;
      showAppSnack(context, 'Visit complete. The certificate is being prepared.');
    } on ApiError catch (error) {
      if (!mounted) return;
      showAppSnack(context, apiMessageFor(error), tone: AppSnackTone.critical);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }
}

// --- terminal states --------------------------------------------------------

class _CompletedPanel extends StatelessWidget {
  const _CompletedPanel({required this.visit, required this.wide});

  final Visit visit;
  final bool wide;

  @override
  Widget build(BuildContext context) {
    final issued = visit.certificateStatus == CertificateStatus.issued;
    return _PanelCard(
      visit: visit,
      description:
          'The visit is complete. The donation is registered and the '
          'certificate is prepared for the donor.',
      body: [
        AppDetailGrid(
          columns: wide ? 2 : 1,
          entries: [
            AppDetailEntry(
              label: 'NATIONAL DONATION N°',
              value: visit.nationalDonationNumber ?? '—',
            ),
            AppDetailEntry(label: 'VOLUME', value: '${visit.volumeMl ?? 450} ml'),
            AppDetailEntry(
              label: 'CERTIFICATE',
              value: issued ? 'Issued' : 'Preparing',
            ),
            AppDetailEntry(label: 'DONOR', value: visit.donorName),
          ],
          tabular: true,
        ),
        const SizedBox(height: AppSpacing.lg),
        _Actions(
          wide: wide,
          children: [
            AppOutlinedButton(
              label: 'Back to the board',
              height: wide ? 44 : 48,
              onPressed: () => context.go('/centre/board'),
            ),
          ],
        ),
      ],
    );
  }
}

class _RefusedPanel extends StatelessWidget {
  const _RefusedPanel({required this.visit, required this.wide});

  final Visit visit;
  final bool wide;

  @override
  Widget build(BuildContext context) {
    return _PanelCard(
      visit: visit,
      description:
          'The doctor decided the donor is unfit. The visit ends here: no '
          'collection and no certificate.',
      body: [
        AppDetailGrid(
          columns: wide ? 2 : 1,
          entries: [
            AppDetailEntry(label: 'DONOR', value: visit.donorName),
            AppDetailEntry(label: 'DECISION', value: 'Unfit — REFUSE'),
          ],
        ),
        const SizedBox(height: AppSpacing.lg),
        _Note(
          message:
              'A temporary deferral can be recorded so the donor knows when '
              'they may donate again.',
        ),
        const SizedBox(height: AppSpacing.lg),
        _Actions(
          wide: wide,
          children: [
            AppFilledButton(
              label: 'Record deferral',
              height: wide ? 44 : 48,
              onPressed: () => context.go('/centre/deferrals/new'),
            ),
            AppOutlinedButton(
              label: 'Back to the board',
              height: wide ? 44 : 48,
              onPressed: () => context.go('/centre/board'),
            ),
          ],
        ),
      ],
    );
  }
}

// --- shared -----------------------------------------------------------------

/// The action row of a panel: equal columns on web, stacked on mobile.
class _Actions extends StatelessWidget {
  const _Actions({required this.wide, required this.children});

  final bool wide;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    if (wide) {
      return Row(
        children: [
          for (var i = 0; i < children.length; i++) ...[
            if (i > 0) const SizedBox(width: AppSpacing.md),
            Expanded(child: children[i]),
          ],
        ],
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var i = 0; i < children.length; i++) ...[
          if (i > 0) const SizedBox(height: AppSpacing.md),
          children[i],
        ],
      ],
    );
  }
}

void _refresh(WidgetRef ref) {
  ref.invalidate(visitProvider);
  ref.invalidate(donationBoardProvider);
  ref.invalidate(centreDayProvider);
}

int _currentIndex(VisitStep step) {
  final position = step.position;
  return position == null ? VisitStep.ordered.length - 1 : position - 1;
}

_StepState _stateFor(Visit visit, VisitStep step) {
  if (visit.step == VisitStep.refused) {
    return step == VisitStep.interview
        ? _StepState.refused
        : step.index < VisitStep.interview.index
        ? _StepState.done
        : _StepState.pending;
  }
  if (visit.step == VisitStep.done) return _StepState.done;
  final current = _currentIndex(visit.step);
  final index = VisitStep.ordered.indexOf(step);
  if (index < current) return _StepState.done;
  if (index == current) return _StepState.current;
  return _StepState.pending;
}

String _subtitle(Visit visit, VisitStep step) => switch (step) {
  VisitStep.reception =>
    'CIN checked · questionnaire recorded '
        '${AppFormatters.time(visit.startedAt)}',
  VisitStep.interview => 'Blood pressure, haemoglobin, fit or unfit',
  VisitStep.collection => '450 ml · duration · national donation number',
  VisitStep.rest => 'At least 15 minutes, then the visit ends',
  _ => 'Visit closed · certificate queued',
};
