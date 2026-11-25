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
import '../../../../shared/widgets/app_detail_grid.dart';
import '../../../../shared/widgets/app_empty_state.dart';
import '../../../../shared/widgets/app_error_state.dart';
import '../../../../shared/widgets/app_loading_state.dart';
import '../../../../shared/widgets/app_page_header.dart';
import '../../../../shared/widgets/app_snack_bar.dart';
import '../providers/certificate_providers.dart';

/// 07 · My certificates — Donor, mobile 390 and web 1440.
///
/// One card per donation: the issued certificate with its number and fields,
/// or the card of a certificate that is still being prepared. Pull to refresh
/// re-reads the list, which is how a donor sees a pending certificate appear.
class MyCertificatesScreen extends ConsumerWidget {
  const MyCertificatesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;
    // Demo control: `?state=empty` / `?state=unavailable` reviews those two
    // states. Without it the donor's real certificates are listed.
    final variant = GoRouterState.of(context).uri.queryParameters['state'];
    final certificates = ref.watch(myCertificatesProvider(variant));

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(myCertificatesProvider(variant));
        await ref.read(myCertificatesProvider(variant).future);
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
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            AppPageHeader(
              wide: wide,
              title: 'My certificates',
              subtitle: wide
                  ? 'One certificate per completed donation. Certificates are '
                        'issued by CNTS and contain no health data.'
                  : 'One certificate per completed donation, issued by CNTS '
                        'after the visit ends.',
            ),
            const SizedBox(height: AppSpacing.xl),
            certificates.when(
              loading: () =>
                  const AppLoadingState(label: 'Loading your certificates...'),
              error: (error, stackTrace) => AppErrorState(
                title: 'We could not load your certificates',
                message: error is ApiError
                    ? apiMessageFor(error)
                    : 'Check your connection and try again.',
                onRetry: () => ref.invalidate(myCertificatesProvider(variant)),
              ),
              data: (items) => items.isEmpty
                  ? _Empty(wide: wide)
                  : _Cards(items: items, wide: wide),
            ),
            const SizedBox(height: AppSpacing.xl),
            _Footer(wide: wide),
          ],
        ),
      ),
    );
  }
}

class _Empty extends StatelessWidget {
  const _Empty({required this.wide});

  final bool wide;

  @override
  Widget build(BuildContext context) {
    return AppEmptyState(
      title: 'No certificate yet',
      message:
          'A certificate is issued automatically after each completed '
          'donation. A refused visit gets none.',
      icon: Icons.description_outlined,
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
}

/// Web shows the cards in two columns, mobile stacks them.
class _Cards extends StatelessWidget {
  const _Cards({required this.items, required this.wide});

  final List<Certificate> items;
  final bool wide;

  @override
  Widget build(BuildContext context) {
    if (!wide) {
      return Column(
        children: [
          for (var i = 0; i < items.length; i++) ...[
            if (i > 0) const SizedBox(height: AppSpacing.lg),
            _CertificateCard(certificate: items[i], wide: wide),
          ],
        ],
      );
    }

    final split = (items.length + 1) ~/ 2;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(child: _Column(items: items.take(split).toList())),
        const SizedBox(width: AppSpacing.xxl),
        Expanded(child: _Column(items: items.skip(split).toList())),
      ],
    );
  }
}

class _Column extends StatelessWidget {
  const _Column({required this.items});

  final List<Certificate> items;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        for (var i = 0; i < items.length; i++) ...[
          if (i > 0) const SizedBox(height: AppSpacing.lg),
          _CertificateCard(certificate: items[i], wide: true),
        ],
      ],
    );
  }
}

class _CertificateCard extends ConsumerWidget {
  const _CertificateCard({required this.certificate, required this.wide});

  final Certificate certificate;
  final bool wide;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return AppCard(
      child: certificate.isIssued ? _issued(context, ref) : _preparing(context),
    );
  }

  // --- issued ---------------------------------------------------------------

  Widget _issued(BuildContext context, WidgetRef ref) {
    final text = AppTypography.textTheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _title(
          badge: certificate.status.label,
          tone: AppBadgeTone.success,
        ),
        const SizedBox(height: AppSpacing.sm),
        Text(
          certificate.number ?? '—',
          style: AppFormatters.tabular(
            wide ? text.headlineSmall : text.titleLarge,
          ),
        ),
        const SizedBox(height: AppSpacing.lg),
        AppDetailGrid(
          entries: [
            AppDetailEntry(
              label: 'NATIONAL DONATION N°',
              value: certificate.nationalDonation ?? '—',
            ),
            AppDetailEntry(label: 'DONOR', value: certificate.donorName),
            AppDetailEntry(label: 'CIN', value: certificate.maskedCin),
            AppDetailEntry(
              label: 'DONATION DATE',
              value: AppFormatters.longDate(certificate.donationDate),
            ),
            AppDetailEntry(label: 'CENTRE', value: certificate.centre),
            AppDetailEntry(
              label: 'VOLUME',
              value: certificate.volumeMl == null
                  ? '—'
                  : '${certificate.volumeMl} ml',
            ),
          ],
          tabular: true,
        ),
        const SizedBox(height: AppSpacing.lg),
        _issuedActions(context, ref),
        const SizedBox(height: AppSpacing.md),
        _note(
          'Contains no health data: no blood pressure, haemoglobin or '
          'interview decision.',
        ),
      ],
    );
  }

  Widget _issuedActions(BuildContext context, WidgetRef ref) {
    final download = AppFilledButton(
      label: 'Download PDF',
      height: wide ? 44 : 48,
      onPressed: () => _requestFile(context, ref, print: false),
    );
    final print = AppOutlinedButton(
      label: 'Print',
      height: wide ? 44 : 48,
      onPressed: () => _requestFile(context, ref, print: true),
    );

    if (wide) {
      return Row(
        children: [
          SizedBox(width: 176, child: download),
          const SizedBox(width: AppSpacing.md),
          SizedBox(width: 112, child: print),
        ],
      );
    }
    return Row(
      children: [
        Expanded(flex: 3, child: download),
        const SizedBox(width: AppSpacing.md),
        Expanded(flex: 2, child: print),
      ],
    );
  }

  // --- being prepared -------------------------------------------------------

  Widget _preparing(BuildContext context) {
    final text = AppTypography.textTheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _title(
          badge: certificate.status.label,
          tone: AppBadgeTone.warning,
        ),
        const SizedBox(height: AppSpacing.sm),
        Text(
          'Number pending',
          style: wide ? text.titleLarge : text.titleMedium,
        ),
        const SizedBox(height: AppSpacing.sm),
        Text(
          wide
              ? 'Donation ${AppFormatters.longDate(certificate.donationDate)} · '
                    '${certificate.centre}. The national service did not answer '
                    'in time, so the certificate is still being prepared.'
              : 'Donation ${AppFormatters.longDate(certificate.donationDate)} · '
                    '${certificate.centre}. The national service did not answer '
                    'in time.',
          style: text.bodyMedium?.copyWith(color: AppColors.onSurfaceVariant),
        ),
        const SizedBox(height: AppSpacing.lg),
        _pendingActions(context),
        const SizedBox(height: AppSpacing.md),
        _note(
          wide
              ? 'The retry job issues pending certificates automatically. A '
                    'refused visit gets none.'
              : 'A refused visit gets no certificate. The retry job issues '
                    'pending certificates automatically.',
        ),
      ],
    );
  }

  Widget _pendingActions(BuildContext context) {
    final download = AppFilledButton(
      label: 'Download PDF',
      height: wide ? 44 : 48,
      onPressed: null,
    );
    final print = AppOutlinedButton(
      label: 'Print',
      height: wide ? 44 : 48,
      onPressed: () => showAppSnack(
        context,
        'There is no file yet — the certificate number is still being '
        'prepared.',
        tone: AppSnackTone.warning,
      ),
    );

    if (wide) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(width: 220, child: download),
          const SizedBox(height: AppSpacing.md),
          SizedBox(width: 160, child: print),
        ],
      );
    }
    return download;
  }

  // --- shared parts ---------------------------------------------------------

  Widget _title({required String badge, required AppBadgeTone tone}) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Text('Donation certificate', style: AppTypography.textTheme.titleMedium),
        ),
        const SizedBox(width: AppSpacing.md),
        AppBadge(label: badge, tone: tone),
      ],
    );
  }

  Widget _note(String message) => Text(
    message,
    style: AppTypography.textTheme.bodySmall?.copyWith(
      color: AppColors.onSurfaceVariant,
    ),
  );

  Future<void> _requestFile(
    BuildContext context,
    WidgetRef ref, {
    required bool print,
  }) async {
    final service = ref.read(certificatePdfServiceProvider);
    final file = print
        ? await service.print(certificate)
        : await service.download(certificate);
    if (!context.mounted) return;
    showAppSnack(
      context,
      print ? 'Requested $file for printing.' : 'Requested $file.',
    );
  }
}

class _Footer extends StatelessWidget {
  const _Footer({required this.wide});

  final bool wide;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Any hospital can check a certificate before counting it for '
          'compensation.',
          style: AppTypography.textTheme.bodySmall?.copyWith(
            color: AppColors.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: AppSpacing.md),
        SizedBox(
          width: wide ? 240 : double.infinity,
          child: AppOutlinedButton(
            label: 'Verify a certificate',
            height: wide ? 44 : 48,
            onPressed: () => context.go('/donor/verify'),
          ),
        ),
      ],
    );
  }
}

