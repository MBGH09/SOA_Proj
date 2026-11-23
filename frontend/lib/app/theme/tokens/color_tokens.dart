import 'package:flutter/material.dart';

/// Design tokens — colours (tokens.md §1, mode Light).
///
/// One red only: [error] is reserved for critical states.
abstract final class AppColors {
  static const surface = Color(0xFFFAF8F5);
  static const surfaceContainerLowest = Color(0xFFFFFFFF);
  static const surfaceContainerLow = Color(0xFFF2EFEA);
  static const surfaceContainer = Color(0xFFEBE7E1);
  static const surfaceContainerHighest = Color(0xFFE2DDD5);

  static const onSurface = Color(0xFF1B1815);
  static const onSurfaceVariant = Color(0xFF655F58);

  static const outline = Color(0xFFD9D3CB);
  static const outlineVariant = Color(0xFFE9E4DE);

  static const primary = Color(0xFF26201D);
  static const onPrimary = Color(0xFFFFFFFF);
  static const primaryContainer = Color(0xFFE6E1DB);
  static const onPrimaryContainer = Color(0xFF2A2420);

  static const secondary = Color(0xFF4A4643);

  static const error = Color(0xFFC0262D);
  static const onError = Color(0xFFFFFFFF);
  static const errorContainer = Color(0xFFF7E5E5);
  static const onErrorContainer = Color(0xFF8E2026);

  static const success = Color(0xFF2E6A45);
  static const successContainer = Color(0xFFE3EEE7);
  static const onSuccessContainer = Color(0xFF1F4730);

  static const warning = Color(0xFF8F5C0E);
  static const warningContainer = Color(0xFFF6EEDC);
  static const onWarningContainer = Color(0xFF6B4410);
}