/// The three outcomes of the eligibility rules (study §9).
enum EligibilityDecision {
  eligible('ELIGIBLE'),
  deferred('AJOURNE_TEMPORAIREMENT'),
  notEligible('NON_ELIGIBLE');

  const EligibilityDecision(this.apiValue);

  /// The value the Donneurs service returns.
  final String apiValue;

  static EligibilityDecision fromApi(String value) {
    for (final decision in values) {
      if (decision.apiValue == value) return decision;
    }
    return EligibilityDecision.eligible;
  }

  /// Plain-language badge label.
  String get badgeLabel => switch (this) {
    EligibilityDecision.eligible => 'Eligible',
    EligibilityDecision.deferred => 'Deferred',
    EligibilityDecision.notEligible => 'Not eligible',
  };
}

/// One rule that was checked, with the answer read back to the donor.
class EligibilityCheck {
  const EligibilityCheck({
    required this.label,
    required this.detail,
    required this.satisfied,
  });

  final String label;
  final String detail;
  final bool satisfied;

  factory EligibilityCheck.fromJson(Map<String, dynamic> json) =>
      EligibilityCheck(
        label: json['label'] as String,
        detail: json['detail'] as String,
        satisfied: json['satisfied'] as bool,
      );
}

/// The result of comparing the donor's answers with the national rules.
class Eligibility {
  const Eligibility({
    required this.decision,
    required this.headline,
    required this.summary,
    required this.checks,
    this.eligibleFrom,
  });

  final EligibilityDecision decision;

  /// One line above the rule list, e.g. "Take a break for now".
  final String headline;
  final String summary;
  final List<EligibilityCheck> checks;

  /// Set when [decision] is [EligibilityDecision.deferred].
  final DateTime? eligibleFrom;

  factory Eligibility.fromJson(Map<String, dynamic> json) => Eligibility(
    decision: EligibilityDecision.fromApi(json['decision'] as String),
    headline: json['headline'] as String,
    summary: json['summary'] as String,
    checks: (json['checks'] as List<dynamic>)
        .map((item) => EligibilityCheck.fromJson(item as Map<String, dynamic>))
        .toList(growable: false),
    eligibleFrom: json['eligibleFrom'] == null
        ? null
        : DateTime.parse(json['eligibleFrom'] as String),
  );
}
