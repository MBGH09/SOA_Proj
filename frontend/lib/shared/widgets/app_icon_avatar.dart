import 'package:flutter/material.dart';

import '../../app/theme/tokens/color_tokens.dart';

/// A soft circular tile holding one icon: a centre marker on booking cards and
/// a past visit in the appointments list.
class AppIconAvatar extends StatelessWidget {
  const AppIconAvatar({
    super.key,
    this.icon = Icons.place_outlined,
    this.size = 52,
    this.iconSize = 24,
  });

  final IconData icon;
  final double size;
  final double iconSize;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: const BoxDecoration(
        color: AppColors.surfaceContainer,
        shape: BoxShape.circle,
      ),
      child: Icon(icon, size: iconSize, color: AppColors.onSurface),
    );
  }
}
