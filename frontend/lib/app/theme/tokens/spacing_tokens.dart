/// Design tokens — spacing, radii and breakpoints (tokens.md §3–4).
abstract final class AppSpacing {
  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 20;
  static const double xxl = 24;
  static const double xxxl = 32;
  static const double page = 56;
}

abstract final class AppRadius {
  static const double card = 12;
  static const double small = 10;
  static const double pill = 999;
}

abstract final class AppBreakpoints {
  /// Web layout at and above this width; mobile layout below it.
  static const double wide = 900;
}