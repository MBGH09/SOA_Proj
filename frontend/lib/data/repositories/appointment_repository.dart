import '../models/appointment.dart';
import '../models/appointment_summary.dart';
import '../models/centre.dart';
import '../models/slot.dart';

/// Centres, their free slots and the donor's appointments
/// (Donneurs: `/centres`, `/centres/{id}/creneaux`, `/rendez-vous`).
abstract interface class AppointmentRepository {
  /// Appointments still to come (`statut = PLANIFIE`).
  Future<List<Appointment>> upcoming();

  /// Finished visits, newest first (`TERMINE`, `ABSENT`, `ANNULE`).
  Future<List<Appointment>> past();

  /// The figures shown in the summary card.
  Future<AppointmentSummary> summary();

  /// Centres that accept a booking from home.
  Future<List<Centre>> centres();

  /// Free slots of a centre for the coming days.
  ///
  /// [forMove] selects the availability offered when moving an existing
  /// appointment rather than booking a new one.
  Future<List<SlotDay>> days({required String centreId, bool forMove = false});

  /// Book a slot from home (`canal = EN_LIGNE`).
  ///
  /// [variant] is a demo control that forces one of the contract failures
  /// (`slot-full` → 409, `day-taken` → 409 one active appointment per day,
  /// `deferred` → 409 active deferral). The real service ignores it and
  /// decides from the donor in session.
  Future<Appointment> book({
    required String centreId,
    required DateTime startsAt,
    String? variant,
  });

  /// Move an appointment to another slot of the same centre.
  Future<Appointment> move({
    required String appointmentId,
    required DateTime startsAt,
    String? variant,
  });

  /// Cancel an appointment.
  Future<void> cancel(String appointmentId);
}
