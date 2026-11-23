import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../data/mock/mock_appointment_repository.dart';
import '../../../../data/models/appointment.dart';
import '../../../../data/models/appointment_summary.dart';
import '../../../../data/models/centre.dart';
import '../../../../data/models/slot.dart';
import '../../../../data/repositories/appointment_repository.dart';

final appointmentRepositoryProvider = Provider<AppointmentRepository>(
  (ref) => MockAppointmentRepository(),
);

final upcomingAppointmentsProvider = FutureProvider<List<Appointment>>(
  (ref) => ref.watch(appointmentRepositoryProvider).upcoming(),
);

final pastAppointmentsProvider = FutureProvider<List<Appointment>>(
  (ref) => ref.watch(appointmentRepositoryProvider).past(),
);

final appointmentSummaryProvider = FutureProvider<AppointmentSummary>(
  (ref) => ref.watch(appointmentRepositoryProvider).summary(),
);

final centresProvider = FutureProvider<List<Centre>>(
  (ref) => ref.watch(appointmentRepositoryProvider).centres(),
);

/// Free slots of a centre for the booking flow.
final slotDaysProvider = FutureProvider.family<List<SlotDay>, String>(
  (ref, centreId) =>
      ref.watch(appointmentRepositoryProvider).days(centreId: centreId),
);

/// Free slots offered when moving an existing appointment.
final moveDaysProvider = FutureProvider.family<List<SlotDay>, String>(
  (ref, centreId) => ref
      .watch(appointmentRepositoryProvider)
      .days(centreId: centreId, forMove: true),
);
