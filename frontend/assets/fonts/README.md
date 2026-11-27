# Fonts

The design system uses **Inter**: Regular, Italic, SemiBold, Bold (tokens.md §2).

Inter is not shipped with this repository yet. When implementation starts:

1. Drop the four `.ttf` files here, e.g.
   - `Inter-Regular.ttf`
   - `Inter-Italic.ttf`
   - `Inter-SemiBold.ttf`
   - `Inter-Bold.ttf`
2. Declare them in `pubspec.yaml`:

```yaml
flutter:
  fonts:
    - family: Inter
      fonts:
        - asset: assets/fonts/Inter-Regular.ttf
        - asset: assets/fonts/Inter-Italic.ttf
          style: italic
        - asset: assets/fonts/Inter-SemiBold.ttf
          weight: 600
        - asset: assets/fonts/Inter-Bold.ttf
          weight: 700
```

Until then Flutter falls back to the platform sans-serif; do not substitute another family
in the theme — the type scale and line heights are tuned for Inter.

Numeric text (references, times, quantities) must use
`fontFeatures: [FontFeature.tabularFigures()]`.