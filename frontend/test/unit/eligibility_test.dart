import 'package:blood_network/data/models/eligibility.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('maps the API decision and reads the checks', () {
    final eligibility = Eligibility.fromJson({
      'decision': 'AJOURNE_TEMPORAIREMENT',
      'headline': 'Take a break for now',
      'summary': 'The 8-week interval ends on 22 November.',
      'eligibleFrom': '2026-11-22',
      'checks': [
        {
          'label': '8 weeks since last whole-blood donation',
          'detail': '36 days left',
          'satisfied': false,
        },
      ],
    });

    expect(eligibility.decision, EligibilityDecision.deferred);
    expect(eligibility.decision.badgeLabel, 'Deferred');
    expect(eligibility.eligibleFrom, DateTime(2026, 11, 22));
    expect(eligibility.checks.single.satisfied, isFalse);
  });

  test('falls back to eligible for an unknown decision', () {
    expect(EligibilityDecision.fromApi('SOMETHING'), EligibilityDecision.eligible);
    expect(EligibilityDecision.notEligible.badgeLabel, 'Not eligible');
  });
}
