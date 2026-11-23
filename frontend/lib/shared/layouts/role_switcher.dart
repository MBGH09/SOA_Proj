import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/theme/tokens/color_tokens.dart';
import '../../app/theme/tokens/spacing_tokens.dart';
import '../../app/theme/tokens/typography_tokens.dart';
import '../../core/constants/app_role.dart';
import '../../core/session/session_providers.dart';

/// AppBar role control. Same 48 dp hit area on mobile and web.
class RoleSwitcher extends ConsumerWidget {
  const RoleSwitcher({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final role = ref.watch(activeRoleProvider);
    final text = AppTypography.textTheme;

    return PopupMenuButton<AppRole>(
      tooltip: 'Switch role',
      elevation: 0,
      color: AppColors.surfaceContainerLowest,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppRadius.card),
        side: const BorderSide(color: AppColors.outline),
      ),
      onSelected: (next) {
        ref.read(activeRoleProvider.notifier).select(next);
        context.go(next.rootPath);
      },
      itemBuilder: (context) => [
        PopupMenuItem<AppRole>(
          enabled: false,
          height: 34,
          child: Text(
            'SWITCH ROLE',
            style: text.labelSmall?.copyWith(color: AppColors.onSurfaceVariant),
          ),
        ),
        for (final option in AppRole.values)
          PopupMenuItem<AppRole>(
            value: option,
            height: 44,
            child: Row(
              children: [
                Icon(
                  Icons.person_outline,
                  size: 18,
                  color: option == role
                      ? AppColors.onSurface
                      : AppColors.onSurfaceVariant,
                ),
                const SizedBox(width: AppSpacing.sm),
                Text(
                  option.label,
                  style: text.titleSmall?.copyWith(
                    fontWeight: option == role
                        ? FontWeight.w600
                        : FontWeight.w400,
                  ),
                ),
                const Spacer(),
                if (option == role)
                  const Icon(
                    Icons.check,
                    size: 18,
                    color: AppColors.onSurface,
                  ),
              ],
            ),
          ),
      ],
      child: Container(
        height: 48,
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.md),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(AppRadius.card),
          border: Border.all(color: AppColors.outline),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(
              Icons.person_outline,
              size: 20,
              color: AppColors.onSurface,
            ),
            const SizedBox(width: AppSpacing.sm),
            Text(role.label, style: AppTypography.textTheme.titleSmall),
            const SizedBox(width: AppSpacing.xs),
            const Icon(
              Icons.keyboard_arrow_down,
              size: 20,
              color: AppColors.onSurface,
            ),
          ],
        ),
      ),
    );
  }
}