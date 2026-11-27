import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/theme/tokens/color_tokens.dart';
import '../../../../app/theme/tokens/spacing_tokens.dart';
import '../../../../app/theme/tokens/typography_tokens.dart';
import '../../../../core/errors/api_error.dart';
import '../../../../core/format/formatters.dart';
import '../../../../data/models/certificate.dart';
import '../../../../shared/widgets/app_badge.dart';
import '../../../../shared/widgets/app_buttons.dart';
import '../../../../shared/widgets/app_card.dart';
import '../../../../shared/widgets/app_error_state.dart';
import '../../../../shared/widgets/app_loading_state.dart';
import '../../../../shared/widgets/app_page_header.dart';
import '../../../../shared/widgets/app_text_field.dart';
import '../providers/certificate_providers.dart';

/// The number each demo state starts with, so the field shows the same example
/// as the design frame. Without a `?state=` the field is empty.
const _demoNumbers = <String, String>{
  'valid': 'CERT-2026-000314',
  'invalid': 'CERT-2026-000201',
  'not-found': 'CERT-2025-999999',
  'unavailable': 'CERT-2026-000314',
};

/// 08 · Verify a certificate — public check, mobile 390 and web 1440.
///
/// One number in, one answer out: valid, not valid, or not found. The answer
/// carries the donation date and the centre only, never a name or health data.
class VerifyCertificateScreen extends ConsumerWidget {
  const VerifyCertificateScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;
    // Demo control: `?state=valid`, `?state=invalid`, `?state=not-found` and
    // `?state=unavailable` open the screen on that answer.
    final variant = GoRouterState.of(context).uri.queryParameters['state'];

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
            title: 'Verify a certificate',
            subtitle: wide
                ? 'Anyone can check a certificate number. The answer shows the '
                      'donation date and centre only — no personal data.'
                : 'Only the donation date and centre are returned — no personal '
                      'data.',
          ),
          const SizedBox(height: AppSpacing.xl),
          _VerifyForm(variant: variant, wide: wide),
          const SizedBox(height: AppSpacing.lg),
          Text(
            'Any hospital can check a certificate before counting it for '
            'compensation. Each certificate counts once.',
            style: AppTypography.textTheme.bodySmall?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

class _VerifyForm extends ConsumerStatefulWidget {
  const _VerifyForm({required this.variant, required this.wide});

  final String? variant;
  final bool wide;

  @override
  ConsumerState<_VerifyForm> createState() => _VerifyFormState();
}

class _VerifyFormState extends ConsumerState<_VerifyForm> {
  final TextEditingController _controller = TextEditingController();

  /// The last submitted check; null until the first "Verify certificate".
  CertificateCheck? _check;

  /// Height of the field's label line, so the buttons on the same row line up
  /// with the input box instead of its label.
  static const double _labelHeight = 20;

  @override
  void initState() {
    super.initState();
    final numero = _demoNumbers[widget.variant];
    if (numero == null) return;
    _controller.text = numero;
    _check = (numero: numero, variant: widget.variant);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() {
    final numero = _controller.text.trim();
    if (numero.isEmpty) return;
    setState(() => _check = (numero: numero, variant: widget.variant));
  }

  void _clear() {
    setState(() {
      _controller.clear();
      _check = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    final check = _check;
    final answer = check == null
        ? null
        : ref.watch(certificateVerificationProvider(check));
    final error = answer?.error;
    final notFound = error is ApiError && error.isNotFound;

    final field = AppTextField(
      label: 'CERTIFICATE NUMBER',
      hint: 'CERT-2026-000314',
      controller: _controller,
      textInputAction: TextInputAction.search,
      errorText: notFound ? 'No certificate found with this number.' : null,
      onChanged: (_) => setState(() {}),
      onSubmitted: (_) => _submit(),
    );

    final canSubmit = _controller.text.trim().isNotEmpty;
    final verify = AppFilledButton(
      label: 'Verify certificate',
      height: widget.wide ? 44 : 48,
      onPressed: canSubmit ? _submit : null,
    );
    final clear = AppOutlinedButton(
      label: 'Clear',
      height: 44,
      onPressed: !canSubmit && check == null ? null : _clear,
    );
    final hint = Text(
      'Try CERT-2026-000314 (valid) or CERT-2025-999999 (not found).',
      style: text.bodySmall?.copyWith(color: AppColors.onSurfaceVariant),
    );

    // The frames put the field, the buttons and that hint on one line at the
    // 1440 layout; narrower windows drop the hint under the row.
    final roomy = MediaQuery.sizeOf(context).width >= 1200;

    final form = widget.wide
        ? Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(flex: 5, child: field),
                  const SizedBox(width: AppSpacing.md),
                  Padding(
                    padding: const EdgeInsets.only(top: _labelHeight),
                    child: SizedBox(width: 200, child: verify),
                  ),
                  const SizedBox(width: AppSpacing.md),
                  Padding(
                    padding: const EdgeInsets.only(top: _labelHeight),
                    child: SizedBox(width: 110, child: clear),
                  ),
                  if (roomy) ...[
                    const SizedBox(width: AppSpacing.lg),
                    Expanded(flex: 3, child: Padding(
                      padding: const EdgeInsets.only(top: _labelHeight + 12),
                      child: hint,
                    )),
                  ],
                ],
              ),
              if (!roomy) ...[
                const SizedBox(height: AppSpacing.md),
                hint,
              ],
            ],
          )
        : AppCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                field,
                const SizedBox(height: AppSpacing.md),
                verify,
              ],
            ),
          );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        form,
        const SizedBox(height: AppSpacing.lg),
        _result(answer, wide: widget.wide),
      ],
    );
  }

  Widget _result(
    AsyncValue<CertificateVerification>? answer, {
    required bool wide,
  }) {
    final check = _check;
    if (check == null || answer == null) return _EmptyAnswer(wide: wide);

    return answer.when(
      loading: () => const AppLoadingState(
        label: 'Checking the certificate...',
      ),
      error: (error, stackTrace) {
        if (error is ApiError && error.isNotFound) {
          return _NotFoundAnswer(wide: wide);
        }
        return AppErrorState(
          title: 'We could not check this certificate',
          message: error is ApiError
              ? apiMessageFor(error)
              : 'Check your connection and try again.',
          onRetry: () =>
              ref.invalidate(certificateVerificationProvider(check)),
        );
      },
      data: (value) => value.valid
          ? _AnswerCard(
              badge: 'Valid',
              tone: AppBadgeTone.success,
              answer: value,
              wide: wide,
            )
          : _AnswerCard(
              badge: 'Not valid',
              tone: AppBadgeTone.critical,
              answer: value,
              wide: wide,
              note: 'This number cannot be counted for compensation. Check it '
                  'with the donor.',
            ),
    );
  }
}

/// Nothing submitted yet: the quiet beige panel of the frame.
class _EmptyAnswer extends StatelessWidget {
  const _EmptyAnswer({required this.wide});

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
          const AppBadge(label: 'No input'),
          if (wide) ...[
            const SizedBox(height: AppSpacing.md),
            Text('Waiting for a number', style: text.titleMedium),
          ],
          const SizedBox(height: AppSpacing.sm),
          Text(
            'No number entered. The verify button stays disabled until the '
            'field has a value.',
            style: text.bodyMedium?.copyWith(color: AppColors.onSurfaceVariant),
          ),
        ],
      ),
    );
  }
}

/// 404: the number is not in the register.
class _NotFoundAnswer extends StatelessWidget {
  const _NotFoundAnswer({required this.wide});

  final bool wide;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const AppBadge(label: 'Not found', tone: AppBadgeTone.critical),
          const SizedBox(height: AppSpacing.md),
          Text(
            wide
                ? 'No certificate matches this number. Check it with the donor '
                      'and try again.'
                : 'No certificate matches this number. Ask the donor to check '
                      'it and try again.',
            style: text.bodyMedium?.copyWith(color: AppColors.onSurfaceVariant),
          ),
        ],
      ),
    );
  }
}

/// 200: the number exists — [badge] tells whether it can be counted.
class _AnswerCard extends StatelessWidget {
  const _AnswerCard({
    required this.badge,
    required this.tone,
    required this.answer,
    required this.wide,
    this.note,
  });

  final String badge;
  final AppBadgeTone tone;
  final CertificateVerification answer;
  final bool wide;
  final String? note;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    Widget row(String label, String value) => Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 150,
          child: Text(
            label,
            style: text.labelSmall?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ),
        Expanded(child: Text(value, style: text.titleSmall)),
      ],
    );

    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AppBadge(label: badge, tone: tone),
          const SizedBox(height: AppSpacing.md),
          row('DONATION DATE', AppFormatters.longDate(answer.date)),
          const SizedBox(height: AppSpacing.md),
          row('CENTRE', answer.centre),
          // The frames show the issuer at the web layout only.
          if (wide) ...[
            const SizedBox(height: AppSpacing.md),
            row('ISSUING AUTHORITY', answer.authority),
          ],
          const SizedBox(height: AppSpacing.lg),
          Text(
            note ?? 'No personal or health data is returned.',
            style: text.bodySmall?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}
