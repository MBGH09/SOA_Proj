import 'package:blood_network/core/format/formatters.dart';
import 'package:blood_network/data/models/appointment.dart';
import 'package:blood_network/data/models/appointment_summary.dart';
import 'package:blood_network/data/models/centre.dart';
import 'package:blood_network/data/models/slot.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Centre', () {
    test('reads the API kind and builds both address lines', () {
      final centre = Centre.fromJson({
        'id': 'C-SFAX',
        'name': 'CRTS Sfax',
        'address': '9 Avenue Habib Bourguiba',
        'city': 'Sfax',
        'kind': 'COLLECTE_MOBILE',
        'openUntil': '15:00',
        'distanceKm': 0.8,
      });

      expect(centre.kind, CentreKind.mobileCollection);
      expect(centre.addressLine, '9 Avenue Habib Bourguiba · Sfax');
      expect(
        centre.openLine,
        'Open today until 15:00 · distance 0.8 km',
      );
    });

    test('falls back to a permanent centre for an unknown kind', () {
      expect(CentreKind.fromApi('SOMETHING'), CentreKind.permanent);
    });
  });

  group('SlotTime', () {
    test('labels a free and a full slot', () {
      expect(
        const SlotTime(start: '09:00', placesLeft: 2).label,
        '09:00 · 2 left',
      );
      expect(
        const SlotTime(start: '10:30', placesLeft: 0).label,
        '10:30 · Full',
      );
      expect(const SlotTime(start: '10:30', placesLeft: 0).isFull, isTrue);
    });
  });

  group('SlotDay', () {
    test('has a free place only when one slot is not full', () {
      final full = SlotDay(
        date: DateTime(2026, 11, 11),
        times: const [
          SlotTime(start: '09:00', placesLeft: 0),
          SlotTime(start: '10:30', placesLeft: 0),
        ],
      );
      final mixed = SlotDay(
        date: DateTime(2026, 11, 12),
        times: const [
          SlotTime(start: '09:00', placesLeft: 0),
          SlotTime(start: '14:00', placesLeft: 3),
        ],
      );

      expect(full.hasFreePlace, isFalse);
      expect(mixed.hasFreePlace, isTrue);
    });
  });

  group('Appointment', () {
    test('maps the API values and builds the meta lines', () {
      final appointment = Appointment.fromJson({
        'id': 'RDV-2026-0442',
        'centreName': 'CRTS Sfax',
        'centreKind': 'CENTRE_PERMANENT',
        'startsAt': '2026-11-12T10:30',
        'channel': 'EN_LIGNE',
        'component': 'Whole blood',
        'volumeMl': 450,
        'status': 'PLANIFIE',
      });

      expect(appointment.status, AppointmentStatus.planned);
      expect(appointment.channel, BookingChannel.online);
      expect(appointment.isUpcoming, isTrue);
      expect(
        appointment.metaLine,
        'Whole blood · 450 ml · reference RDV-2026-0442 · booked online',
      );
      expect(appointment.metaLineMobile, 'Whole blood · 450 ml · RDV-2026-0442');
    });

    test('a mobile collection names the kind and drops the volume', () {
      final appointment = Appointment.fromJson({
        'id': 'RDV-2026-0451',
        'centreName': 'Collecte mobile · campus',
        'centreKind': 'COLLECTE_MOBILE',
        'startsAt': '2026-11-14T09:00',
        'channel': 'EN_LIGNE',
        'component': 'Whole blood',
        'volumeMl': null,
        'status': 'PLANIFIE',
      });

      expect(appointment.sourceLabel, 'mobile collection');
      expect(appointment.componentLine(), 'Whole blood');
      expect(
        appointment.metaLine,
        'Whole blood · reference RDV-2026-0451 · mobile collection',
      );
    });

    test('unknown status falls back to planned', () {
      expect(AppointmentStatus.fromApi('??'), AppointmentStatus.planned);
      expect(AppointmentStatus.completed.label, 'Completed');
      expect(AppointmentStatus.missed.label, 'Missed');
    });
  });

  group('AppointmentSummary', () {
    test('reads a null deferral and next appointment', () {
      final summary = AppointmentSummary.fromJson({
        'totalDonations': 7,
        'lastDonation': '2026-08-12',
        'nextAt': null,
        'activeDeferral': null,
        'certificateReady': true,
      });

      expect(summary.totalDonations, 7);
      expect(summary.nextAt, isNull);
      expect(summary.activeDeferral, isNull);
      expect(summary.certificateReady, isTrue);
    });
  });

  group('AppFormatters dates', () {
    final date = DateTime(2026, 11, 12, 10, 30);

    test('renders the shapes used by booking and appointments', () {
      expect(AppFormatters.weekdayDay(date), 'Thu 12');
      expect(AppFormatters.weekdayDayMonth(date), 'Thu 12 Nov');
      expect(AppFormatters.weekdayFull(date), 'Thursday 12 November');
      expect(
        AppFormatters.weekdayFullTime(date),
        'Thursday 12 November · 10:30',
      );
      expect(AppFormatters.weekdayDayMonthTime(date), 'Thu 12 Nov · 10:30');
      expect(AppFormatters.longDayMonth(date), '12 November');
      expect(AppFormatters.longDate(date), '12 November 2026');
    });
  });
}
