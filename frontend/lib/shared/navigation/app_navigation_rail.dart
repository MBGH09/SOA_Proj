import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../app/theme/tokens/color_tokens.dart';
import '../../app/theme/tokens/spacing_tokens.dart';
import '../../app/theme/tokens/typography_tokens.dart';
import '../../core/constants/app_role.dart';
import 'app_destination.dart';

/// Web rail — 76 dp wide, right hairline, pill indicator.
///
/// One rail for every role; the destinations come from [destinationsFor].
class AppNavigationRail extends StatelessWidget {
  const AppNavigationRail({
    super.key,
    required this.role,
    required this.location,
  });

  final AppRole role;
  final String location;

  @override
  Widget build(BuildContext context) {
    final destinations = destinationsFor(role);
    final active = activeDestinationPath(destinations, location);

    return Container(
      width: 76,
      decoration: const BoxDecoration(
        color: AppColors.surface,
        border: Border(right: BorderSide(color: AppColors.outlineVariant)),
      ),
      child: Column(
        children: [
          const SizedBox(height: AppSpacing.xl),
          const Icon(
            Icons.water_drop_outlined,
            size: 24,
            color: AppColors.onSurface,
          ),
          const SizedBox(height: AppSpacing.lg),
          Container(width: 40, height: 1, color: AppColors.outlineVariant),
          const SizedBox(height: AppSpacing.sm),
          for (final destination in destinations)
            _RailDestination(
              destination: destination,
              selected: destination.path == active,
            ),
        ],
      ),
    );
  }
}

class _RailDestination extends StatelessWidget {
  const _RailDestination({required this.destination, required this.selected});

  final AppDestination destination;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: () => context.go(destination.path),
      child: SizedBox(
        width: 76,
        height: 100,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 56,
              height: 32,
              decoration: BoxDecoration(
                color: selected
                    ? AppColors.primaryContainer
                    : Colors.transparent,
                borderRadius: BorderRadius.circular(AppRadius.pill),
              ),
              child: Icon(
                destination.icon,
                size: 22,
                color: selected
                    ? AppColors.onPrimaryContainer
                    : AppColors.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              destination.railLabel,
              textAlign: TextAlign.center,
              style: AppTypography.textTheme.labelSmall?.copyWith(
                fontSize: 10,
                height: 1.2,
                letterSpacing: 0,
                fontWeight: selected ? FontWeight.w600 : FontWeight.w400,
                color: selected
                    ? AppColors.onSurface
                    : AppColors.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
