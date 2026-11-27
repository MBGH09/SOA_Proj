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

  /// The prepared contract failures, reachable with `?state=` on the screens.
  void _guard(String? variant) {
    switch (variant) {
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
