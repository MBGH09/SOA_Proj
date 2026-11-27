/// A blood component (`Composant` in the Demandes contract).
enum BloodComponent {
  redCells('CGR', 'Red cells'),
  plasma('PLASMA', 'Plasma'),
  platelets('PLAQUETTES', 'Platelets');

  const BloodComponent(this.apiValue, this.label);

  final String apiValue;
  final String label;

  /// `RED CELLS` — a stock-board column header.
  String get columnLabel => label.toUpperCase();

  static BloodComponent fromApi(String value) {
    for (final component in values) {
      if (component.apiValue == value) return component;
    }
    return BloodComponent.redCells;
  }
}
