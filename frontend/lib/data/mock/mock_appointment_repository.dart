import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;

import '../../core/errors/api_error.dart';
import '../models/appointment.dart';
import '../models/appointment_summary.dart';
import '../models/centre.dart';
import '../models/slot.dart';
import '../repositories/appointment_repository.dart';

/// In-memory Donneurs service for centres, slots and appointments.
///
/// Bookings, moves and cancellations change the in-memory list only, so the
/// demo behaves like a real session without a backend.
class MockAppointmentRepository implements AppointmentRepository {
  List<Appointment>? _upcoming;
  List<Appointment>? _past;
  AppointmentSummary? _summary;
  List<Centre>? _centres;
  List<SlotDay>? _centreDays;
  int _sequence = 460;

  @override
  Future<List<Appointment>> upcoming() async {
    await _latency();
    return List<Appointment>.unmodifiable(
      _upcoming ??= await _loadAppointments('upcoming'),
    );
  }

  @override
  Future<List<Appointment>> past() async {
    await _latency();
    return List<Appointment>.unmodifiable(
      _past ??= await _loadAppointments('past'),
    );
  }

  @override
  Future<AppointmentSummary> summary() async {
    await _latency();
    return _summary ??= AppointmentSummary.fromJson(
      (await _loadDocument())['summary'] as Map<String, dynamic>,
    );
  }

  @override
  Future<List<Centre>> centres() async {
    await _latency();
    if (_centres != null) return List<Centre>.unmodifiable(_centres!);
    final raw = await rootBundle.loadString('lib/data/mock/fixtures/centres.json');
    final centres = (jsonDecode(raw) as List<dynamic>)
        .map((item) => Centre.fromJson(item as Map<String, dynamic>))
        .toList(growable: false);
    _centres = centres;
    return List<Centre>.unmodifiable(centres);
  }

  @override
  Future<List<SlotDay>> days({
    required String centreId,
    bool forMove = false,
  }) async {
    await _latency();
    final name = forMove ? 'slot_days_move' : 'slot_days';
    final raw = await rootBundle.loadString('lib/data/mock/fixtures/$name.json');
    return (jsonDecode(raw) as List<dynamic>)
        .map((item) => SlotDay.fromJson(item as Map<String, dynamic>))
        .toList(growable: false);
  }

  @override
  Future<List<SlotDay>> centreDays({required String centreId}) async {
    await _latency();
    return List<SlotDay>.unmodifiable(_centreDays ??= await _loadCentreDays());
  }

  @override
  Future<SlotDay> createSlots({
    required String centreId,
    required DateTime date,
    required String from,
    required String to,
    required int capacity,
    SlotRepeat repeat = SlotRepeat.single,
    String? variant,
  }) async {
    await _latency();
    switch (variant) {
      case 'overlap':
        throw const ApiError(
          status: 409,
          message: 'SLOT_OVERLAP',
          fieldErrors: {
            'range': 'Those hours already have slots. Choose another range.',
          },
        );
      case 'invalid-capacity':
        throw const ApiError(
          status: 400,
          message: 'INVALID_CAPACITY',
          fieldErrors: {'capacity': 'A slot needs at least one place.'},
        );
      case 'unavailable':
        throw const ApiError(
          status: 503,
          message: 'SLOT_SERVICE_UNAVAILABLE',
        );
    }

    final times = _hourly(from, to, capacity);
    if (times.isEmpty) {
      throw const ApiError(
        status: 400,
        message: 'INVALID_RANGE',
        fieldErrors: {'range': 'The end time must be after the start time.'},
      );
    }

    // `repeat` is a staff convenience: the contract creates one slot per
    // `CreneauRequest`, so a daily or weekly run is several requests.
    final dates = switch (repeat) {
      SlotRepeat.single => [date],
      SlotRepeat.daily => [
        for (var i = 0; i < 7; i++) DateTime(date.year, date.month, date.day + i),
      ],
      SlotRepeat.weekly => [
        for (var i = 0; i < 4; i++)
          DateTime(date.year, date.month, date.day + i * 7),
      ],
    };

    final days = _centreDays ??= await _loadCentreDays();
    for (final day in dates) {
      final index = days.indexWhere(
        (item) =>
            item.date.year == day.year &&
            item.date.month == day.month &&
            item.date.day == day.day,
      );
      if (index >= 0) {
        days[index] = SlotDay(
          date: days[index].date,
          times: [...days[index].times, ...times],
        );
      } else {
        days.add(SlotDay(date: day, times: times));
      }
    }
    days.sort((a, b) => a.date.compareTo(b.date));
    return SlotDay(date: date, times: times);
  }

  @override
  Future<Appointment> book({
    required String centreId,
    required DateTime startsAt,
    String? variant,
  }) async {
    await _latency();
    final centres = await this.centres();
    final centre = centres.firstWhere(
      (item) => item.id == centreId,
      orElse: () => centres.first,
    );
    _guard(variant);

    final appointment = Appointment(
      id: 'RDV-2026-0${_sequence++}',
      centreName: centre.name,
      centreKind: centre.kind,
      startsAt: startsAt,
      channel: BookingChannel.online,
      component: 'Whole blood',
      volumeMl: 450,
      status: AppointmentStatus.planned,
    );
    (_upcoming ??= await _loadAppointments('upcoming')).add(appointment);
    return appointment;
  }

  @override
  Future<Appointment> move({
    required String appointmentId,
    required DateTime startsAt,
    String? variant,
  }) async {
    await _latency();
    _guard(variant);
    final list = _upcoming ??= await _loadAppointments('upcoming');
    final index = list.indexWhere((item) => item.id == appointmentId);
    if (index < 0) {
      throw const ApiError(status: 404, message: 'APPOINTMENT_NOT_FOUND');
    }
    final current = list[index];
    final moved = Appointment(
      id: current.id,
      centreName: current.centreName,
      centreKind: current.centreKind,
      startsAt: startsAt,
      channel: current.channel,
      component: current.component,
      volumeMl: current.volumeMl,
      status: current.status,
    );
    list[index] = moved;
    return moved;
  }

  @override
  Future<void> cancel(String appointmentId) async {
    await _latency();
    final list = _upcoming ??= await _loadAppointments('upcoming');
    final index = list.indexWhere((item) => item.id == appointmentId);
    if (index >= 0) list.removeAt(index);
  }

  /// Hourly slots between [from] and [to]: `09:00` → `12:00` gives three.
  List<SlotTime> _hourly(String from, String to, int capacity) {
    final start = _minutes(from);
    final end = _minutes(to);
    if (start == null || end == null || end <= start) return const [];
    final times = <SlotTime>[];
    for (var cursor = start; cursor < end; cursor += 60) {
      final next = cursor + 60 > end ? end : cursor + 60;
      times.add(
        SlotTime(
          start: _hhmm(cursor),
          end: _hhmm(next),
          capacity: capacity,
          placesLeft: capacity,
        ),
      );
    }
    return times;
  }

  int? _minutes(String value) {
    final parts = value.split(':');
    if (parts.length != 2) return null;
    final hour = int.tryParse(parts[0]);
    final minute = int.tryParse(parts[1]);
    if (hour == null || minute == null) return null;
    return hour * 60 + minute;
  }

  String _hhmm(int minutes) =>
      '${(minutes ~/ 60).toString().padLeft(2, '0')}:'
      '${(minutes % 60).toString().padLeft(2, '0')}';

  /// The prepared contract failures, reachable with `?state=` on the screens.
  void _guard(String? variant) {    switch (variant) {
      case 'slot-full':
        throw const ApiError(
          status: 409,
          message: 'SLOT_FULL',
          fieldErrors: {'slot': 'That time is full. Choose another slot.'},
        );
      case 'day-taken':
        throw const ApiError(
          status: 409,
          message: 'ONE_PER_DAY',
          fieldErrors: {
            'day': 'You already have an appointment that day. Cancel it first.',
          },
        );
      case 'deferred':
        throw const ApiError(
          status: 409,
          message: 'ACTIVE_DEFERRAL',
          fieldErrors: {
            'slot': 'You cannot book while a deferral is active.',
          },
        );
    }
  }

  Future<List<SlotDay>> _loadCentreDays() async {
    final raw = await rootBundle.loadString(
      'lib/data/mock/fixtures/centre_slots.json',
    );
    return (jsonDecode(raw) as List<dynamic>)
        .map((item) => SlotDay.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  Future<List<Appointment>> _loadAppointments(String key) async {
    final raw = (await _loadDocument())[key] as List<dynamic>;
    return raw
        .map((item) => Appointment.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  Future<Map<String, dynamic>> _loadDocument() async {
    final raw = await rootBundle.loadString(
      'lib/data/mock/fixtures/appointments.json',
    );
    return jsonDecode(raw) as Map<String, dynamic>;
  }

  Future<void> _latency() async {
    await Future<void>.delayed(const Duration(milliseconds: 300));
  }
}
