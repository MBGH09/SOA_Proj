import 'package:flutter/material.dart';

import 'color_tokens.dart';

/// Design tokens — type scale (tokens.md §2).
///
/// Inter is declared as the family even before the .ttf files are bundled;
/// Flutter falls back to the platform sans until then.
abstract final class AppTypography {
  static const String family = 'Inter';

  static TextStyle _style({
    required double size,
    required double lineHeight,
    required FontWeight weight,
    double letterSpacing = 0,
    Color? color,
  }) {
    return TextStyle(
      fontFamily: family,
      fontSize: size,
      height: lineHeight / size,
      fontWeight: weight,
      letterSpacing: letterSpacing,
      color: color ?? AppColors.onSurface,
    );
  }

  static final TextTheme textTheme = TextTheme(
    displaySmall: _style(
      size: 36,
      lineHeight: 44,
      weight: FontWeight.w600,
      letterSpacing: -0.5,
    ),
    headlineSmall: _style(
      size: 24,
      lineHeight: 32,
      weight: FontWeight.w600,
      letterSpacing: -0.25,
    ),
    titleLarge: _style(
      size: 20,
      lineHeight: 28,
      weight: FontWeight.w600,
      letterSpacing: -0.15,
    ),
    titleMedium: _style(
      size: 16,
      lineHeight: 24,
      weight: FontWeight.w600,
      letterSpacing: -0.1,
    ),
    titleSmall: _style(size: 14, lineHeight: 20, weight: FontWeight.w600),
    bodyLarge: _style(size: 16, lineHeight: 24, weight: FontWeight.w400),
    bodyMedium: _style(size: 14, lineHeight: 20, weight: FontWeight.w400),
    bodySmall: _style(size: 12, lineHeight: 16, weight: FontWeight.w400),
    labelLarge: _style(
      size: 14,
      lineHeight: 20,
      weight: FontWeight.w600,
      letterSpacing: 0.1,
    ),
    labelMedium: _style(
      size: 12,
      lineHeight: 16,
      weight: FontWeight.w600,
      letterSpacing: 0.2,
    ),
    labelSmall: _style(
      size: 11,
      lineHeight: 15,
      weight: FontWeight.w600,
      letterSpacing: 0.5,
    ),
  );

  /// Web screen title: 30 / SemiBold / 38 / −0.4.
  static TextStyle get screenTitle => _style(
        size: 30,
        lineHeight: 38,
        weight: FontWeight.w600,
        letterSpacing: -0.4,
      );

  /// Mobile screen title: 24 / SemiBold / 30.
  static TextStyle get screenTitleMobile => _style(
        size: 24,
        lineHeight: 30,
        weight: FontWeight.w600,
        letterSpacing: -0.25,
      );

  /// Tabular figures for references, times and quantities.
  static TextStyle get tableFigures => _style(
        size: 14,
        lineHeight: 20,
        weight: FontWeight.w600,
      ).copyWith(fontFeatures: const [FontFeature.tabularFigures()]);
}