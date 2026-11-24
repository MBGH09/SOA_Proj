import 'package:blood_network/core/constants/app_role.dart';
import 'package:blood_network/shared/navigation/app_destination.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('roleFromLocation', () {
    test('maps each area prefix to its role', () {
      expect(roleFromLocation('/donor/appeals'), AppRole.donor);
      expect(roleFromLocation('/centre/board'), AppRole.centre);
      expect(roleFromLocation('/hospital/requests/new'), AppRole.hospital);
      expect(roleFromLocation('/admin/thresholds'), AppRole.admin);
    });

    test('defaults to donor for an unknown path', () {
      expect(roleFromLocation('/'), AppRole.donor);
    });
  });

  group('destinationsFor', () {
    test('donor keeps Stock in the rail but not in the four-item bar', () {
      final destinations = destinationsFor(AppRole.donor);
      expect(destinations.map((d) => d.railLabel), contains('Stock'));
      expect(destinations.where((d) => d.inMobileBar), hasLength(4));
    });

    test('every role has at least two destinations', () {
      for (final role in AppRole.values) {
        expect(destinationsFor(role).length, greaterThanOrEqualTo(2));
      }
    });

    test('admin reaches the two shared admin routes', () {
      final paths = destinationsFor(AppRole.admin).map((d) => d.path);
      expect(paths, containsAll(['/admin/thresholds', '/admin/stock']));
    });
  });

  group('activeDestinationPath', () {
    final destinations = destinationsFor(AppRole.hospital);

    test('prefers the most specific match', () {
      expect(
        activeDestinationPath(destinations, '/hospital/requests/new'),
        '/hospital/requests/new',
      );
      expect(
        activeDestinationPath(destinations, '/hospital/requests'),
        '/hospital/requests',
      );
    });

    test('matches nested detail routes to their parent', () {
      expect(
        activeDestinationPath(destinations, '/hospital/requests/RD-1'),
        '/hospital/requests',
      );
    });

    test('returns null when nothing matches', () {
      expect(activeDestinationPath(destinations, '/donor/appeals'), isNull);
    });
  });
}
