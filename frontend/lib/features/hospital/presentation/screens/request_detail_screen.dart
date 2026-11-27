import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/theme/tokens/color_tokens.dart';
import '../../../../app/theme/tokens/spacing_tokens.dart';
import '../../../../app/theme/tokens/typography_tokens.dart';
import '../../../../core/errors/api_error.dart';
import '../../../../core/providers/core_providers.dart';
import '../../../../data/models/blood_request.dart';
import '../../../../shared/widgets/app_badge.dart';
import '../../../../shared/widgets/app_buttons.dart';
import '../../../../shared/widgets/app_card.dart';
import '../../../../shared/widgets/app_chip.dart';
import '../../../../shared/widgets/app_error_state.dart';
import '../../../../shared/widgets/app_loading_state.dart';
import '../../../../shared/widgets/app_snack_bar.dart';
import '../../../../shared/widgets/app_text_field.dart';
import '../providers/hospital_providers.dart';
import '../widgets/request_status.dart';

/// H3 · Request detail — Hospital.
///
/// Where the pouches come from, the order, the delivery and the family-donor
/// compensation, with the certificate counter as the one editable part.
class RequestDetailScreen extends ConsumerStatefulWidget {
  const RequestDetailScreen({super.key, required this.id});

  final String id;

  @override
  ConsumerState<RequestDetailScreen> createState() =>
      _RequestDetailScreenState();
}

class _RequestDetailScreenState extends ConsumerState<RequestDetailScreen> {
  final _certificate = TextEditingController(text: 'CERT-2026-');
  String? _certificateError;
  bool _adding = false;

  @override
  void dispose() {
    _certificate.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;
    final variant = GoRouterState.of(context).uri.queryParameters['state'];
    final query = (id: widget.id, variant: variant);
    final request = ref.watch(bloodRequestProvider(query));
    final now = ref.watch(clockProvider).now();

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(bloodRequestProvider);
        await ref.read(bloodRequestProvider(query).future);
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
        child: request.when(
          loading: () => const AppLoadingState(label: 'Loading the request...'),
          error: (error, stackTrace) => AppErrorState(
            title: 'We could not load this request',
            message: error is ApiError
                ? apiMessageFor(error)
                : 'Check your connection and try again.',
            onRetry: () {
              ref.invalidate(bloodRequestProvider);
            },
          ),
          data: (value) => _Detail(
            request: value,
            wide: wide,
            now: now,
            certificate: _certificate,
            certificateError: _certificateError,
            adding: _adding,
            onAddCertificate: () => _add(value, variant),
            onCertificateChanged: () {
              if (_certificateError == null) return;
              setState(() => _certificateError = null);
            },
            onCorrect: () => _correct(value, variant),
          ),
        ),
      ),
    );
  }

  Future<void> _add(BloodRequest request, String? variant) async {
    if (_adding) return;
    final number = _certificate.text.trim();
    if (number.isEmpty || number == 'CERT-2026-') {
      setState(() => _certificateError = 'Enter the certificate number.');
      return;
    }

    setState(() {
      _adding = true;
      _certificateError = null;
    });

    try {
      final updated = await ref
          .read(requestRepositoryProvider)
          .addCompensation(request.id, number, variant: variant);
      ref.invalidate(bloodRequestProvider);
      ref.invalidate(bloodRequestsProvider);
      if (!mounted) return;
      _certificate.text = 'CERT-2026-';
      showAppSnack(
        context,
        updated.compensationComplete
            ? 'Certificate $number counted. All the donors asked for are in.'
            : 'Certificate $number counted. '
                  '${updated.compensationProgress} donors so far.',
        tone: AppSnackTone.success,
      );
    } on ApiError catch (error) {
      if (!mounted) return;
      setState(() {
        _certificateError =
            error.fieldErrors['certificate'] ?? apiMessageFor(error);
      });
      showAppSnack(context, apiMessageFor(error), tone: AppSnackTone.critical);
    } finally {
      if (mounted) setState(() => _adding = false);
    }
  }

  Future<void> _correct(BloodRequest request, String? variant) async {
    final asked = request.compensationRequested ?? 0;
    final value = await showDialog<int>(
      context: context,
      builder: (context) =>
          _CorrectDialog(asked: asked, initial: request.compensationReceived),
    );
    if (value == null || !mounted) return;

    if (value < 0 || value > asked) {
      showAppSnack(
        context,
        'Enter a number between 0 and $asked.',
        tone: AppSnackTone.critical,
      );
      return;
    }

    try {
      await ref
          .read(requestRepositoryProvider)
          .correctCompensation(request.id, value, variant: variant);
      ref.invalidate(bloodRequestProvider);
      ref.invalidate(bloodRequestsProvider);
      if (!mounted) return;
      showAppSnack(context, 'Certificate count corrected to $value.');
    } on ApiError catch (error) {
      if (!mounted) return;
      showAppSnack(context, apiMessageFor(error), tone: AppSnackTone.critical);
    }
  }
}

/// The small dialog that owns its text controller, so the controller outlives
/// the dialog's closing animation.
class _CorrectDialog extends StatefulWidget {
  const _CorrectDialog({required this.asked, required this.initial});

  final int asked;
  final int initial;

  @override
  State<_CorrectDialog> createState() => _CorrectDialogState();
}

class _CorrectDialogState extends State<_CorrectDialog> {
  late final TextEditingController _controller = TextEditingController(
    text: '${widget.initial}',
  );

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return AlertDialog(
      backgroundColor: AppColors.surfaceContainerLowest,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppRadius.card),
        side: const BorderSide(color: AppColors.outline),
      ),
      title: Text('Correct the number', style: text.titleMedium),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'When the national service is slow, staff can set the number '
            'of counted certificates by hand. The request asks for '
            '${widget.asked}.',
            style: text.bodyMedium?.copyWith(color: AppColors.onSurfaceVariant),
          ),
          const SizedBox(height: AppSpacing.lg),
          AppTextField(
            label: 'CERTIFICATES RECEIVED',
            controller: _controller,
            keyboardType: TextInputType.number,
          ),
        ],
      ),
      actionsPadding: const EdgeInsets.fromLTRB(
        AppSpacing.lg,
        0,
        AppSpacing.lg,
        AppSpacing.lg,
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          style: TextButton.styleFrom(
            foregroundColor: AppColors.onSurface,
            textStyle: text.labelLarge,
          ),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: () =>
              Navigator.of(context).pop(int.tryParse(_controller.text.trim())),
          style: FilledButton.styleFrom(
            backgroundColor: AppColors.primary,
            foregroundColor: AppColors.onPrimary,
            textStyle: text.labelLarge,
          ),
          child: const Text('Save'),
        ),
      ],
    );
  }
}

class _Detail extends StatelessWidget {
  const _Detail({
    required this.request,
    required this.wide,
    required this.now,
    required this.certificate,
    required this.certificateError,
    required this.adding,
    required this.onAddCertificate,
    required this.onCertificateChanged,
    required this.onCorrect,
  });

  final BloodRequest request;
  final bool wide;
  final DateTime now;
  final TextEditingController certificate;
  final String? certificateError;
  final bool adding;
  final VoidCallback onAddCertificate;
  final VoidCallback onCertificateChanged;
  final VoidCallback onCorrect;

  @override
  Widget build(BuildContext context) {
    final main = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _ReservationCard(request: request),
        const SizedBox(height: AppSpacing.lg),
        _OrderCard(request: request),
        const SizedBox(height: AppSpacing.lg),
        _DeliveryCard(request: request),
      ],
    );

    final compensation = _CompensationCard(
      request: request,
      wide: wide,
      certificate: certificate,
      certificateError: certificateError,
      adding: adding,
      onAdd: onAddCertificate,
      onChanged: onCertificateChanged,
      onCorrect: onCorrect,
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _Header(request: request, now: now, wide: wide),
        const SizedBox(height: AppSpacing.xl),
        if (wide)
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(flex: 780, child: main),
              const SizedBox(width: AppSpacing.xxl),
              Expanded(flex: 448, child: compensation),
            ],
          )
        else ...[
          main,
          const SizedBox(height: AppSpacing.lg),
          compensation,
        ],
        const SizedBox(height: AppSpacing.xl),
        Text(
          'When the national service is slow, staff can also correct the '
          'number of certificates by hand.',
          style: AppTypography.textTheme.bodySmall?.copyWith(
            color: AppColors.onSurfaceVariant,
          ),
        ),
      ],
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.request, required this.now, required this.wide});

  final BloodRequest request;
  final DateTime now;
  final bool wide;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Wrap(
          spacing: AppSpacing.md,
          runSpacing: AppSpacing.sm,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            Text(
              'Request ${request.id}',
              style: wide
                  ? AppTypography.screenTitle
                  : AppTypography.screenTitleMobile,
            ),
            urgencyBadge(request.urgency),
            statusBadge(request.status),
          ],
        ),
        const SizedBox(height: AppSpacing.sm),
        Text(
          request.summaryLine(now),
          style: text.bodyMedium?.copyWith(color: AppColors.onSurfaceVariant),
        ),
      ],
    );
  }
}

// --- cards ----------------------------------------------------------------

class _ReservationCard extends StatelessWidget {
  const _ReservationCard({required this.request});

  final BloodRequest request;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    final source = request.supplySource;
    final reserved =
        '${request.unitsLine} · ${request.component.label} · '
        '${request.bloodGroup.plainName}';

    return AppCard(
      padding: const EdgeInsets.all(AppSpacing.xl),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _CardLabel('RESERVATION'),
          const SizedBox(height: AppSpacing.md),
          Wrap(
            spacing: AppSpacing.sm,
            runSpacing: AppSpacing.sm,
            children: [
              for (final item in SupplySource.values)
                AppChip(label: item.label, selected: item == source),
            ],
          ),
          const SizedBox(height: AppSpacing.lg),
          if (source == null)
            Text(
              request.status == RequestStatus.shortage
                  ? 'No place has the blood yet. An emergency appeal is open '
                        'and the request stays in shortage until pouches are '
                        'found.'
                  : 'No reservation yet. The request is waiting for the stock '
                        'service to answer.',
              style: text.bodyMedium?.copyWith(
                color: AppColors.onSurfaceVariant,
              ),
            )
          else ...[
            _DetailRow(label: 'Supplied by', value: request.suppliedBy ?? '—'),
            _DetailRow(
              label: 'Reservation reference',
              value: request.reservationReference ?? '—',
              tabular: true,
            ),
            _DetailRow(label: 'Reserved', value: reserved),
            if (request.reservationStatus != null)
              _DetailRow(
                label: 'Status',
                child: AppBadge(
                  label: request.reservationStatus!,
                  tone: toneForReservation(request.reservationStatus),
                ),
              ),
          ],
        ],
      ),
    );
  }
}

class _OrderCard extends StatelessWidget {
  const _OrderCard({required this.request});

  final BloodRequest request;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      padding: const EdgeInsets.all(AppSpacing.xl),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _CardLabel('ORDER'),
          const SizedBox(height: AppSpacing.sm),
          _DetailRow(label: 'Component', value: request.component.label),
          _DetailRow(label: 'Blood group', value: request.bloodGroup.plainName),
          _DetailRow(label: 'Units', value: '${request.units}'),
          _DetailRow(label: 'Reason', value: request.reasonLine),
          _DetailRow(
            label: 'Patient reference',
            value: '${request.patientRef} (anonymous)',
            tabular: true,
          ),
        ],
      ),
    );
  }
}

class _DeliveryCard extends StatelessWidget {
  const _DeliveryCard({required this.request});

  final BloodRequest request;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    final hasDelivery =
        request.deliveryFrom != null ||
        request.deliveryTo != null ||
        request.estimatedMinutes != null;

    return AppCard(
      padding: const EdgeInsets.all(AppSpacing.xl),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _CardLabel('DELIVERY'),
          const SizedBox(height: AppSpacing.sm),
          if (!hasDelivery)
            Text(
              'No delivery yet. The pouches appear here as soon as they are '
              'reserved and on their way.',
              style: text.bodyMedium?.copyWith(
                color: AppColors.onSurfaceVariant,
              ),
            )
          else ...[
            if (request.estimatedMinutes != null)
              _DetailRow(
                label: 'Estimated arrival',
                value: '${request.estimatedMinutes} minutes',
              ),
            if (request.deliveryFrom != null)
              _DetailRow(label: 'From', value: request.deliveryFrom!),
            if (request.deliveryTo != null)
              _DetailRow(label: 'To', value: request.deliveryTo!),
            if (request.trackingReference != null)
              _DetailRow(
                label: 'Tracking reference',
                value: request.trackingReference!,
                tabular: true,
              ),
            _DetailRow(
              label: 'Status',
              child: Wrap(
                spacing: AppSpacing.sm,
                runSpacing: AppSpacing.sm,
                children: [
                  for (final stage in DeliveryStage.values)
                    AppBadge(
                      label: stage.label,
                      tone: _stageTone(stage, request.deliveryStage),
                      background:
                          _stageTone(stage, request.deliveryStage) ==
                              AppBadgeTone.neutral
                          ? AppColors.surfaceContainer
                          : null,
                    ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  static AppBadgeTone _stageTone(DeliveryStage stage, DeliveryStage? current) {
    if (current == null) return AppBadgeTone.neutral;
    if (stage == current) {
      return stage == DeliveryStage.delivered
          ? AppBadgeTone.success
          : AppBadgeTone.warning;
    }
    return stage.index < current.index
        ? AppBadgeTone.success
        : AppBadgeTone.neutral;
  }
}

class _CompensationCard extends StatelessWidget {
  const _CompensationCard({
    required this.request,
    required this.wide,
    required this.certificate,
    required this.certificateError,
    required this.adding,
    required this.onAdd,
    required this.onChanged,
    required this.onCorrect,
  });

  final BloodRequest request;
  final bool wide;
  final TextEditingController certificate;
  final String? certificateError;
  final bool adding;
  final VoidCallback onAdd;
  final VoidCallback onChanged;
  final VoidCallback onCorrect;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    final asked = request.compensationRequested;

    return AppCard(
      padding: const EdgeInsets.all(AppSpacing.xl),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _CardLabel('COMPENSATION'),
          const SizedBox(height: AppSpacing.md),
          Text(
            'Families of the patient bring donors for this request. Each valid '
            'donation certificate counts once.',
            style: text.bodyMedium?.copyWith(color: AppColors.onSurfaceVariant),
          ),
          if (asked == null) ...[
            const SizedBox(height: AppSpacing.md),
            Text(
              'No family donors were asked for this request.',
              style: text.bodyMedium,
            ),
          ] else ...[
            const SizedBox(height: AppSpacing.lg),
            Row(
              children: [
                Expanded(
                  child: Text('Certificates received', style: text.bodyMedium),
                ),
                Text(
                  '${request.compensationReceived} of $asked',
                  style: text.titleSmall,
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            ClipRRect(
              borderRadius: BorderRadius.circular(AppRadius.pill),
              child: LinearProgressIndicator(
                value: request.compensationRatio,
                minHeight: 8,
                backgroundColor: AppColors.surfaceContainer,
                color: AppColors.primary,
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            for (var i = 0; i < request.certificates.length; i++) ...[
              if (i > 0) const SizedBox(height: AppSpacing.md),
              _CertificateRow(certificate: request.certificates[i]),
            ],
            const SizedBox(height: AppSpacing.lg),
            const Divider(height: 1, color: AppColors.outlineVariant),
            const SizedBox(height: AppSpacing.lg),
            const _CardLabel('ADD A CERTIFICATE'),
            const SizedBox(height: AppSpacing.sm),
            AppTextField(
              label: 'CERTIFICATE NUMBER',
              controller: certificate,
              hint: 'CERT-2026-000001',
              errorText: certificateError,
              onChanged: (_) => onChanged(),
            ),
            const SizedBox(height: AppSpacing.md),
            SizedBox(
              width: wide ? 200 : double.infinity,
              child: AppFilledButton(
                label: adding ? 'Adding...' : 'Add certificate',
                onPressed: adding ? null : onAdd,
                height: wide ? 44 : 48,
              ),
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(
              'A certificate counts once, so a number cannot be used twice.',
              style: text.bodySmall?.copyWith(
                color: AppColors.onSurfaceVariant,
              ),
            ),
            if (request.compensationReceived > 0) ...[
              const SizedBox(height: AppSpacing.xs),
              TextButton(
                onPressed: onCorrect,
                style: TextButton.styleFrom(
                  foregroundColor: AppColors.onSurface,
                  textStyle: text.labelMedium,
                  padding: EdgeInsets.zero,
                  minimumSize: Size(48, wide ? 40 : 48),
                  alignment: Alignment.centerLeft,
                ),
                child: const Text('Correct the number by hand'),
              ),
            ],
          ],
        ],
      ),
    );
  }
}

class _CertificateRow extends StatelessWidget {
  const _CertificateRow({required this.certificate});

  final CompensationCertificate certificate;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(
            certificate.number,
            style: AppTypography.textTheme.titleSmall,
          ),
        ),
        AppBadge(
          label: certificate.valid ? 'Valid' : 'Not valid',
          tone: certificate.valid
              ? AppBadgeTone.success
              : AppBadgeTone.critical,
        ),
      ],
    );
  }
}

class _CardLabel extends StatelessWidget {
  const _CardLabel(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    return Text(
      label,
      style: AppTypography.textTheme.labelSmall?.copyWith(
        color: AppColors.onSurfaceVariant,
        letterSpacing: 0.5,
      ),
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({
    required this.label,
    this.value,
    this.child,
    this.tabular = false,
  });

  final String label;
  final String? value;
  final Widget? child;
  final bool tabular;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    final valueWidget =
        child ??
        Text(
          value ?? '—',
          style: tabular
              ? AppTypography.textTheme.bodyMedium?.copyWith(
                  fontFeatures: const [FontFeature.tabularFigures()],
                )
              : text.bodyMedium,
        );

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.sm),
      child: LayoutBuilder(
        builder: (context, constraints) {
          if (constraints.maxWidth < 420) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: text.bodySmall?.copyWith(
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 2),
                valueWidget,
              ],
            );
          }

          return Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(
                width: 160,
                child: Text(
                  label,
                  style: text.bodyMedium?.copyWith(
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: valueWidget,
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
