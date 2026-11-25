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

  /// The centre's own week of slots, with capacity and fill, as the staff see
  /// it (`GET /api/v1/centres/{id}/creneaux`).
  Future<List<SlotDay>> centreDays({required String centreId});

  /// Create the slots of a period (`POST /api/v1/centres/{id}/creneaux`,
  /// staff or admin). A slot can only take as many appointments as it has
  /// places (R22).
  ///
  /// [variant] is a demo control for the contract failures (`overlap` → 409,
  /// `invalid-capacity` → 400, `unavailable` → 503).
  Future<SlotDay> createSlots({
    required String centreId,
    required DateTime date,
    required String from,
    required String to,
    required int capacity,
    SlotRepeat repeat,
    String? variant,
  });

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
