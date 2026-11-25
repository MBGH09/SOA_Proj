/// The eight blood groups. [code] is the API value; [label] is the plain-language
/// label shown under the code.
enum BloodGroup {
  oNeg('O−', 'O RH NEGATIVE'),
  oPos('O+', 'O RH POSITIVE'),
  aNeg('A−', 'A RH NEGATIVE'),
  aPos('A+', 'A RH POSITIVE'),
  bNeg('B−', 'B RH NEGATIVE'),
  bPos('B+', 'B RH POSITIVE'),
  abNeg('AB−', 'AB RH NEGATIVE'),
  abPos('AB+', 'AB RH POSITIVE');

  const BloodGroup(this.code, this.label);

  final String code;
  final String label;

  bool get isNegative => code.endsWith('−');

  static BloodGroup? fromCode(String code) {
    for (final group in values) {
      if (group.code == code) return group;
    }
    return null;
  }
}
