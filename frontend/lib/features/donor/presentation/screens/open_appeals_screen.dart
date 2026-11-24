import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/theme/tokens/color_tokens.dart';
import '../../../../app/theme/tokens/spacing_tokens.dart';
import '../../../../app/theme/tokens/typography_tokens.dart';
import '../../../../core/errors/api_error.dart';
import '../../../../data/models/appeal.dart';
import '../../../../shared/widgets/app_badge.dart';
import '../../../../shared/widgets/app_buttons.dart';
import '../../../../shared/widgets/app_card.dart';
import '../../../../shared/widgets/app_empty_state.dart';
import '../../../../shared/widgets/app_error_state.dart';
import '../../../../shared/widgets/app_loading_state.dart';
import '../../../../shared/widgets/app_page_header.dart';
import '../../../../shared/widgets/blood_group_tile.dart';
import '../providers/appeal_providers.dart';

/// 01 · Open blood appeals — Donor, mobile 390 and web 1440.
class OpenAppealsScreen extends ConsumerWidget {
  const OpenAppealsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.wide;
    final appeals = ref.watch(openAppealsProvider);
    final text = AppTypography.textTheme;

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
            title: 'Open blood appeals',
            subtitle: wide
                ? 'Centres are short of these blood groups — appeals updated every two minutes. Booking from the donor app fills them in order.'
                : 'Centres are short of these blood groups. Updated 2 min ago.',
          ),
          const SizedBox(height: AppSpacing.xl),
          appeals.when(
            data: (items) => _AppealList(items: items, wide: wide),
            loading: () => const AppLoadingState(label: 'Loading appeals…'),
            error: (error, stackTrace) => AppErrorState(
              title: 'We could not load the appeals',
              message: error is ApiError
                  ? apiMessageFor(error)
                  : 'Check your connection and try again.',
              onRetry: () => ref.invalidate(openAppealsProvider),
            ),
          ),
          const SizedBox(height: AppSpacing.xl),
          Text(
            'Appeals open automatically when a hospital cannot be supplied from stock. Your booking fills the appeal.',
            style: text.bodySmall?.copyWith(color: AppColors.onSurfaceVariant),
          ),
          const SizedBox(height: AppSpacing.md),
          const _VerifyLink(),
        ],
      ),
    );
  }
}

class _AppealList extends StatelessWidget {
  const _AppealList({required this.items, required this.wide});

  final List<Appeal> items;
  final bool wide;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return const AppEmptyState(
        title: 'No open appeals right now',
        message: 'Centres have enough stock for the groups you can give. '
            'Check back later.',
        icon: Icons.notifications_none_rounded,
      );
    }

    if (wide) {
      return Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (var i = 0; i < items.length; i++) ...[
            if (i > 0) const SizedBox(width: AppSpacing.lg),
            Expanded(child: _AppealCard(appeal: items[i], wide: true)),
          ],
        ],
      );
    }

    return Column(
      children: [
        for (var i = 0; i < items.length; i++) ...[
          if (i > 0) const SizedBox(height: AppSpacing.md),
          _AppealCard(appeal: items[i], wide: false),
        ],
      ],
    );
  }
}

class _AppealCard extends StatelessWidget {
  const _AppealCard({required this.appeal, required this.wide});

  final Appeal appeal;
  final bool wide;

  @override
  Widget build(BuildContext context) {
    final buttonHeight = wide ? 44.0 : 48.0;

    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              BloodGroupTile(
                group: appeal.bloodGroup,
                label: appeal.bloodGroupLabel,
                critical: appeal.isCritical,
              ),
              const Spacer(),
              AppBadge(
                label: appeal.isCritical ? 'Critical' : 'Open',
                tone: appeal.isCritical
                    ? AppBadgeTone.critical
                    : AppBadgeTone.neutral,
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.md),
          Text(
            '${appeal.component} · ${appeal.place}',
            style: AppTypography.textTheme.titleMedium,
          ),
          const SizedBox(height: 6),
          Text(
            appeal.metaLine,
            style: AppTypography.textTheme.bodyMedium?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: AppSpacing.md),
          if (appeal.isCritical)
            AppFilledButton(
              label: 'Book a slot',
              height: buttonHeight,
              onPressed: () => context.go('/donor/book'),
            )
          else
            AppOutlinedButton(
              label: 'Details',
              height: buttonHeight,
              onPressed: () => context.go('/donor/book'),
            ),
        ],
      ),
    );
  }
}

class _VerifyLink extends StatelessWidget {
  const _VerifyLink();

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: () => context.go('/donor/verify'),
      borderRadius: BorderRadius.circular(AppRadius.small),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 14),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.search, size: 20, color: AppColors.onSurface),
            const SizedBox(width: 10),
            Text(
              'Verify a certificate by number',
              style: AppTypography.textTheme.titleSmall,
            ),
          ],
        ),
      ),
    );
  }
}