import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/theme/tokens/color_tokens.dart';
import '../../../../app/theme/tokens/spacing_tokens.dart';
import '../../../../app/theme/tokens/typography_tokens.dart';
import '../../../../core/errors/api_error.dart';
import '../../../../core/format/formatters.dart';
import '../../../../data/models/eligibility.dart';
import '../../../../shared/widgets/app_badge.dart';
import '../../../../shared/widgets/app_buttons.dart';
import '../../../../shared/widgets/app_card.dart';
import '../../../../shared/widgets/app_error_state.dart';
import '../../../../shared/widgets/app_loading_state.dart';
import '../../../../shared/widgets/app_page_header.dart';
import '../providers/donor_providers.dart';

/// The national whole-blood intervals, shown beside the result (tokens §8).
const _nationalRules = [
  'Whole blood — every 8 weeks (56 days)',
  'Platelets — every 4 weeks',
  'Plasma — every 2 weeks',
  'Tattoo or piercing — wait 4 months',
  'Dental extraction — wait 7 days',
  'Surgery or endoscopy — wait 4 to 6 months',
];

/// 03 · Check your eligibility — Donor, mobile 390 and web 1440.
///
/// Three outcomes: eligible, temporarily deferred, not eligible.
class EligibilityScreen extends ConsumerWidget {
  const EligibilityScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;
    // Demo control: `?state=eligible` / `?state=not-eligible` reviews each
    // outcome. Without it, the donor's real result is shown.
    final variant = GoRouterState.of(context).uri.queryParameters['state'];
    final eligibility = ref.watch(eligibilityProvider(variant));

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
            title: 'Check your eligibility',
            subtitle: wide
                ? 'Your questionnaire answers are compared with the national rules — the result below is for today.'
                : 'Your questionnaire answers are compared with the national rules. Result for today.',
          ),
          const SizedBox(height: AppSpacing.xl),
          eligibility.when(
            data: (value) => _Result(eligibility: value, wide: wide),
            loading: () => const AppLoadingState(
              label: 'Checking the national rules…',
            ),
            error: (error, stackTrace) => AppErrorState(
              title: 'We could not check your eligibility',
              message: error is ApiError
                  ? apiMessageFor(error)
                  : 'Check your connection and try again.',
              onRetry: () => ref.invalidate(eligibilityProvider(variant)),
            ),
          ),
        ],
      ),
    );
  }
}

class _Result extends StatelessWidget {
  const _Result({required this.eligibility, required this.wide});

  final Eligibility eligibility;
  final bool wide;

  String? get _actionLabel {
    final from = eligibility.eligibleFrom;
    return switch (eligibility.decision) {
      EligibilityDecision.eligible => 'See available slots',
      EligibilityDecision.deferred => from == null
          ? 'See available slots'
          : wide
          ? 'See available slots after ${AppFormatters.dayMonth(from)}'
          : 'See slots after ${AppFormatters.dayMonth(from)}',
      EligibilityDecision.notEligible => null,
    };
  }

  AppBadgeTone get _badgeTone => switch (eligibility.decision) {
    EligibilityDecision.eligible => AppBadgeTone.success,
    EligibilityDecision.deferred => AppBadgeTone.warning,
    EligibilityDecision.notEligible => AppBadgeTone.critical,
  };

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;
    final action = _actionLabel;
    final note = Text(
      wide
          ? 'A physician re-checks eligibility at the centre before each '
                'donation. Booking does not guarantee eligibility.'
          : 'A physician re-checks eligibility at the centre before each '
                'donation.',
      style: text.bodySmall?.copyWith(color: AppColors.onSurfaceVariant),
    );

    final resultCard = AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Text(eligibility.headline, style: text.headlineSmall),
              ),
              const SizedBox(width: AppSpacing.md),
              AppBadge(
                label: eligibility.decision.badgeLabel,
                tone: _badgeTone,
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            eligibility.summary,
            style: text.bodyMedium?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
          for (var i = 0; i < eligibility.checks.length; i++) ...[
            if (i > 0) const SizedBox(height: AppSpacing.md),
            _CheckRow(check: eligibility.checks[i]),
          ],
          if (action != null) ...[
            const SizedBox(height: AppSpacing.lg),
            AppFilledButton(
              label: action,
              height: wide ? 44 : 48,
              onPressed: () => context.go('/donor/book'),
            ),
          ],
        ],
      ),
    );

    const rulesCard = _NationalRules();

    if (wide) {
      return Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                resultCard,
                const SizedBox(height: AppSpacing.md),
                note,
              ],
            ),
          ),
          const SizedBox(width: AppSpacing.xxl),
          const Expanded(child: rulesCard),
        ],
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        resultCard,
        const SizedBox(height: AppSpacing.lg),
        rulesCard,
        const SizedBox(height: AppSpacing.lg),
        note,
      ],
    );
  }
}

class _CheckRow extends StatelessWidget {
  const _CheckRow({required this.check});

  final EligibilityCheck check;

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(
          check.satisfied ? Icons.check : Icons.timelapse,
          size: 22,
          color: check.satisfied
              ? AppColors.onSurface
              : AppColors.onSurfaceVariant,
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(check.label, style: text.titleMedium),
              const SizedBox(height: 2),
              Text(
                check.detail,
                style: text.bodyMedium?.copyWith(
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

class _NationalRules extends StatelessWidget {
  const _NationalRules();

  @override
  Widget build(BuildContext context) {
    final text = AppTypography.textTheme;

    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'NATIONAL RULES · WHOLE BLOOD',
            style: text.labelSmall?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: AppSpacing.md),
          for (final rule in _nationalRules)
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
                  Expanded(child: Text(rule, style: text.bodyMedium)),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
