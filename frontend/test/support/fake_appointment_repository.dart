import 'package:blood_network/data/models/appointment.dart';
import 'package:blood_network/data/models/appointment_summary.dart';
import 'package:blood_network/data/models/centre.dart';
import 'package:blood_network/data/models/slot.dart';
import 'package:blood_network/data/repositories/appointment_repository.dart';

/// A ready-made appointment, matching the FigCraft frames unless overridden.
Appointment sampleAppointment({
  String id = 'RDV-2026-0442',
  String centreName = 'CRTS Sfax',
  CentreKind centreKind = CentreKind.permanent,
  DateTime? startsAt,
  BookingChannel channel = BookingChannel.online,
  String component = 'Whole blood',
  int? volumeMl = 450,
  AppointmentStatus status = AppointmentStatus.planned,
  DateTime? collectedAt,
  String? certificateNumber,
}) => Appointment(
  id: id,
  centreName: centreName,
  centreKind: centreKind,
  startsAt: startsAt ?? DateTime(2026, 11, 12, 10, 30),
  channel: channel,
  component: component,
  volumeMl: volumeMl,
  status: status,
  collectedAt: collectedAt,
  certificateNumber: certificateNumber,
);

Centre sampleCentre({
  String id = 'C-SFAX',
  String name = 'CRTS Sfax',
  String address = '9 Avenue Habib Bourguiba',
  String city = 'Sfax',
  CentreKind kind = CentreKind.permanent,
  String openUntil = '15:00',
  double distanceKm = 0.8,
}) => Centre(
  id: id,
  name: name,
  address: address,
  city: city,
  kind: kind,
  openUntil: openUntil,
  distanceKm: distanceKm,
);

/// A short week of slots shaped like the booking fixture.
List<SlotDay> sampleDays() => [
  SlotDay(
    date: DateTime(2026, 11, 11),
    times: const [
      SlotTime(start: '09:00', placesLeft: 2),
      SlotTime(start: '10:30', placesLeft: 0),
      SlotTime(start: '14:00', placesLeft: 5),
    ],
  ),
  SlotDay(
    date: DateTime(2026, 11, 12),
    times: const [
      SlotTime(start: '09:00', placesLeft: 2),
      SlotTime(start: '14:00', placesLeft: 5),
      SlotTime(start: '17:00', placesLeft: 2),
    ],
  ),
];

/// Test double for the Centres / appointments service.
class FakeAppointmentRepository implements AppointmentRepository {
  FakeAppointmentRepository({
    List<Appointment>? upcoming,
    List<Appointment>? past,
    AppointmentSummary? summary,
    List<Centre>? centres,
    List<SlotDay>? days,
  }) : _upcoming = upcoming ?? [sampleAppointment()],
       _past =
           past ??
           [
             sampleAppointment(
               status: AppointmentStatus.completed,
               collectedAt: DateTime(2026, 8, 12),
               certificateNumber: 'CERT-2026-000314',
             ),
           ],
       _summary =
           summary ??
           AppointmentSummary(
             totalDonations: 7,
             lastDonation: DateTime(2026, 8, 12),
             nextAt: DateTime(2026, 11, 12, 10, 30),
             certificateReady: true,
           ),
       _centres = centres ?? [sampleCentre()],
       _days = days ?? sampleDays();

  final List<Appointment> _upcoming;
  final List<Appointment> _past;
  final AppointmentSummary _summary;
  final List<Centre> _centres;
  final List<SlotDay> _days;

  int bookCalls = 0;
  int moveCalls = 0;
  int cancelCalls = 0;
  DateTime? bookedAt;
  DateTime? movedAt;
  String? cancelledId;
  String? bookVariant;
  String? moveVariant;

  @override
  Future<List<Appointment>> upcoming() async => List.of(_upcoming);

  @override
  Future<List<Appointment>> past() async => List.of(_past);

  @override
  Future<AppointmentSummary> summary() async => _summary;

  @override
  Future<List<Centre>> centres() async => List.of(_centres);

  @override
  Future<List<SlotDay>> days({
    required String centreId,
    bool forMove = false,
  }) async => List.of(_days);

  @override
  Future<Appointment> book({
    required String centreId,
    required DateTime startsAt,
    String? variant,
  }) async {
    bookCalls++;
    bookedAt = startsAt;
    bookVariant = variant;
    return sampleAppointment(startsAt: startsAt);
  }

  @override
  Future<Appointment> move({
    required String appointmentId,
    required DateTime startsAt,
    String? variant,
  }) async {
    moveCalls++;
    movedAt = startsAt;
    moveVariant = variant;
    return sampleAppointment(id: appointmentId, startsAt: startsAt);
  }

  @override
  Future<void> cancel(String appointmentId) async {
    cancelCalls++;
    cancelledId = appointmentId;
    _upcoming.removeWhere((item) => item.id == appointmentId);
  }
}
