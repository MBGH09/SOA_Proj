/// The eight blood groups. [code] is the short label; [label] is the
/// plain-language label shown under the code.
enum BloodGroup {
  oNeg('O−', 'O RH NEGATIVE', 'O_NEG', 'O negative'),
  oPos('O+', 'O RH POSITIVE', 'O_POS', 'O positive'),
  aNeg('A−', 'A RH NEGATIVE', 'A_NEG', 'A negative'),
  aPos('A+', 'A RH POSITIVE', 'A_POS', 'A positive'),
  bNeg('B−', 'B RH NEGATIVE', 'B_NEG', 'B negative'),
  bPos('B+', 'B RH POSITIVE', 'B_POS', 'B positive'),
  abNeg('AB−', 'AB RH NEGATIVE', 'AB_NEG', 'AB negative'),
  abPos('AB+', 'AB RH POSITIVE', 'AB_POS', 'AB positive');

  const BloodGroup(this.code, this.label, this.apiValue, this.plainName);

  final String code;
  final String label;

  /// The value the Demandes contract uses (`groupeSanguin`).
  final String apiValue;

  /// `O negative` — the name used inside a sentence.
  final String plainName;

  bool get isNegative => code.endsWith('−');

  static BloodGroup? fromCode(String code) {
    for (final group in values) {
      if (group.code == code) return group;
    }
    return null;
  }

  /// Reads the contract value (`O_NEG`), falling back to the display code.
  static BloodGroup? fromApi(String value) {
    for (final group in values) {
      if (group.apiValue == value) return group;
    }
    return fromCode(value);
  }
}
