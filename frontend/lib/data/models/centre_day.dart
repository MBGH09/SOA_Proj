import 'blood_group.dart';
import 'visit.dart';

/// What the reception sees on the agenda row (C1).
///
/// `Waiting` and `Absent` are appointment states; the others name the step the
/// visit is on right now.
enum AgendaStatus {
  waiting('Waiting', 'Check in'),
  interview('Interview', 'Open'),
  collected('Collected', 'Open'),
  rest('Rest', 'Open'),
  absent('Absent', 'Close');

  const AgendaStatus(this.label, this.action);

  final String label;

  /// Label of the row's button.
  final String action;

  static AgendaStatus fromApi(String value) {
    for (final status in values) {
      if (status.name == value) return status;
    }
    return AgendaStatus.waiting;
  }
}

/// One row of the day's agenda.
class AgendaRow {
  const AgendaRow({
    required this.time,
    required this.reference,
    required this.channel,
    required this.status,
    this.cin,
    this.visitId,
  });

  /// `08:30`.
  final String time;

  /// `D-2026-1189 · walk-in`.
  final String reference;
  final VisitChannel channel;
  final AgendaStatus status;

  /// CIN to type when checking the donor in, for a row that has no visit yet.
  final String? cin;

  /// The visit to open, when the row is one.
  final String? visitId;

  factory AgendaRow.fromJson(Map<String, dynamic> json) => AgendaRow(
    time: json['time'] as String,
    reference: json['reference'] as String,
    channel: VisitChannel.fromApi(json['channel'] as String),
    status: AgendaStatus.fromApi(json['status'] as String),
    cin: json['cin'] as String?,
    visitId: json['visitId'] as String?,
  );
}

/// A donor waiting in the reception room, with the minutes already waited.
class QueueEntry {
  const QueueEntry({
    required this.reference,
    required this.bloodGroup,
    required this.waitingMinutes,
  });

  final String reference;
  final BloodGroup bloodGroup;
  final int waitingMinutes;

  factory QueueEntry.fromJson(Map<String, dynamic> json) => QueueEntry(
    reference: json['reference'] as String,
    bloodGroup:
        BloodGroup.fromCode(json['bloodGroup'] as String) ?? BloodGroup.oPos,
    waitingMinutes: json['waitingMinutes'] as int,
  );
}

/// Units of one blood group in the centre's own stock.
class CentreStockLine {
  const CentreStockLine({required this.bloodGroup, required this.units});

  final BloodGroup bloodGroup;
  final int units;

  factory CentreStockLine.fromJson(Map<String, dynamic> json) =>
      CentreStockLine(
        bloodGroup:
            BloodGroup.fromCode(json['bloodGroup'] as String) ??
            BloodGroup.oPos,
        units: json['units'] as int,
      );
}

/// The four figures above the agenda (C1).
class CentreDayStats {
  const CentreDayStats({
    required this.booked,
    required this.online,
    required this.walkIn,
    required this.receptionDone,
    required this.inInterview,
    required this.unfit,
    required this.collectedUnits,
    required this.collectedMl,
    this.mlPerDonation = 450,
  });

  final int booked;
  final int online;
  final int walkIn;

  /// Visits whose reception step is recorded.
  final int receptionDone;

  /// Visits sitting on the medical interview step right now.
  final int inInterview;

  /// Unfit decisions so far today.
  final int unfit;
  final int collectedUnits;
  final int collectedMl;

  /// Configured collection volume (`VOLUME_PRELEVEMENT_ML`), 450 ml.
  final int mlPerDonation;

  /// `10 online · 2 walk-in`.
  String get bookedDetail => '$online online · $walkIn walk-in';

  /// `3 150 ml · 450 ml each`.
  String get collectedDetail =>
      '${_grouped(collectedMl)} ml · $mlPerDonation ml each';

  static String _grouped(int value) {
    final digits = value.toString();
    final buffer = StringBuffer();
    for (var i = 0; i < digits.length; i++) {
      if (i > 0 && (digits.length - i) % 3 == 0) buffer.write(' ');
      buffer.write(digits[i]);
    }
    return buffer.toString();
  }

  factory CentreDayStats.fromJson(Map<String, dynamic> json) => CentreDayStats(
    booked: json['booked'] as int,
    online: json['online'] as int,
    walkIn: json['walkIn'] as int,
    receptionDone: json['receptionDone'] as int,
    inInterview: json['inInterview'] as int? ?? 0,
    unfit: json['unfit'] as int,
    collectedUnits: json['collectedUnits'] as int,
    collectedMl: json['collectedMl'] as int,
    mlPerDonation: json['mlPerDonation'] as int? ?? 450,
  );
}

/// Everything the "Today at the centre" screen shows for one day.
class CentreDay {
  const CentreDay({
    required this.centreName,
    required this.date,
    required this.stats,
    required this.agenda,
    required this.queue,
    required this.stock,
    this.lowStockGroups = const [],
  });

  final String centreName;
  final DateTime date;
  final CentreDayStats stats;
  final List<AgendaRow> agenda;

  /// Donors waiting in the reception room.
  final List<QueueEntry> queue;

  /// The centre's own stock by blood group.
  final List<CentreStockLine> stock;

  /// Groups under their alert threshold, if any.
  final List<BloodGroup> lowStockGroups;

  /// The amber note under the stock card.
  String? get lowStockMessage {
    if (lowStockGroups.isEmpty) return null;
    final groups = lowStockGroups.map((group) => group.code).join(', ');
    return '$groups is below its alert threshold at $centreName. '
        'Consider adding a mobile collection.';
  }

  factory CentreDay.fromJson(Map<String, dynamic> json) => CentreDay(
    centreName: json['centreName'] as String,
    date: DateTime.parse(json['date'] as String),
    stats: CentreDayStats.fromJson(json['stats'] as Map<String, dynamic>),
    agenda: (json['agenda'] as List<dynamic>)
        .map((item) => AgendaRow.fromJson(item as Map<String, dynamic>))
        .toList(growable: false),
    queue: (json['queue'] as List<dynamic>)
        .map((item) => QueueEntry.fromJson(item as Map<String, dynamic>))
        .toList(growable: false),
    stock: (json['stock'] as List<dynamic>)
        .map((item) => CentreStockLine.fromJson(item as Map<String, dynamic>))
        .toList(growable: false),
    lowStockGroups: (json['lowStockGroups'] as List<dynamic>? ?? const [])
        .map((code) => BloodGroup.fromCode(code as String) ?? BloodGroup.oNeg)
        .toList(growable: false),
  );
}

/// One column of the donation board: a step and the visits sitting on it.
class BoardColumn {
  const BoardColumn({required this.step, required this.visits});

  final VisitStep step;
  final List<Visit> visits;

  int get count => visits.length;

  /// The line under the column title, naming who works this step.
  String get caption => switch (step) {
    VisitStep.reception => 'Reception · CIN + questionnaire',
    VisitStep.interview => 'Doctor · BP, Hb, decision',
    VisitStep.collection => 'Nurse · 450 ml + national number',
    VisitStep.rest => 'Rest · minimum 15 minutes',
    _ => 'Visit closed · certificate queued',
  };

  factory BoardColumn.fromJson(
    Map<String, dynamic> json,
    Map<String, Visit> visits,
  ) => BoardColumn(
    step: VisitStep.fromApi(json['step'] as String),
    visits: (json['visitIds'] as List<dynamic>)
        .map((id) => visits[id as String])
        .whereType<Visit>()
        .toList(growable: false),
  );
}
