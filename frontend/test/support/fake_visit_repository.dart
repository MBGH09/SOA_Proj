import 'package:blood_network/core/errors/api_error.dart';
import 'package:blood_network/data/models/blood_group.dart';
import 'package:blood_network/data/models/centre_day.dart';
import 'package:blood_network/data/models/certificate.dart';
import 'package:blood_network/data/models/visit.dart';
import 'package:blood_network/data/repositories/visit_repository.dart';

/// The visit of the FigCraft frames, on any step.
Visit sampleVisit({
  String id = 'D-2026-1189',
  String donorName = 'Amira Ben Salah',
  BloodGroup bloodGroup = BloodGroup.oNeg,
  String centreName = 'CRTS Sfax',
  VisitChannel channel = VisitChannel.walkIn,
  VisitStep step = VisitStep.interview,
  DateTime? startedAt,
  List<VisitQuestion>? questionnaire,
  VisitDecision? decision,
  int? systolic = 128,
  int? diastolic = 82,
  double? haemoglobin = 13.4,
  DateTime? collectedAt,
  int? durationMinutes,
  int? volumeMl,
  String? nationalDonationNumber,
  DateTime? restStartedAt,
  DateTime? restEndsAt,
  int? restMinutesLeft,
  CertificateStatus certificateStatus = CertificateStatus.preparing,
  int? elapsedMinutes = 26,
}) => Visit(
  id: id,
  donorName: donorName,
  bloodGroup: bloodGroup,
  centreName: centreName,
  channel: channel,
  step: step,
  startedAt: startedAt ?? DateTime(2026, 11, 12, 9, 12),
  questionnaire:
      questionnaire ??
      const [
        VisitQuestion(label: 'Feels well today', answer: true),
        VisitQuestion(label: 'Weighs at least 50 kg', answer: true),
        VisitQuestion(label: 'No tattoo in the last 4 months', answer: true),
      ],
  decision: decision,
  systolic: systolic,
  diastolic: diastolic,
  haemoglobin: haemoglobin,
  collectedAt: collectedAt,
  durationMinutes: durationMinutes,
  volumeMl: volumeMl,
  nationalDonationNumber: nationalDonationNumber,
  restStartedAt: restStartedAt,
  restEndsAt: restEndsAt,
  restMinutesLeft: restMinutesLeft,
  certificateStatus: certificateStatus,
  elapsedMinutes: elapsedMinutes,
);

/// The day of the FigCraft C1 frame.
CentreDay sampleCentreDay() => CentreDay(
  centreName: 'CRTS Sfax',
  date: DateTime(2026, 11, 12),
  stats: const CentreDayStats(
    booked: 12,
    online: 10,
    walkIn: 2,
    receptionDone: 11,
    inInterview: 3,
    unfit: 1,
    collectedUnits: 7,
    collectedMl: 3150,
  ),
  agenda: const [
    AgendaRow(
      time: '08:30',
      reference: 'D-2026-1189 · walk-in',
      channel: VisitChannel.walkIn,
      status: AgendaStatus.interview,
      visitId: 'D-2026-1189',
    ),
    AgendaRow(
      time: '09:00',
      reference: 'RDV-2026-0442 · online',
      channel: VisitChannel.online,
      status: AgendaStatus.waiting,
      cin: '08123457',
    ),
    AgendaRow(
      time: '10:30',
      reference: 'RDV-2026-0446 · online',
      channel: VisitChannel.online,
      status: AgendaStatus.absent,
      cin: '08123459',
    ),
  ],
  queue: const [
    QueueEntry(
      reference: 'D-2026-1191',
      bloodGroup: BloodGroup.oNeg,
      waitingMinutes: 6,
    ),
    QueueEntry(
      reference: 'D-2026-1192',
      bloodGroup: BloodGroup.aPos,
      waitingMinutes: 13,
    ),
  ],
  stock: const [
    CentreStockLine(bloodGroup: BloodGroup.oPos, units: 3),
    CentreStockLine(bloodGroup: BloodGroup.oNeg, units: 1),
  ],
  lowStockGroups: const [BloodGroup.oNeg],
);

/// The five board columns of the FigCraft C2 frame.
List<BoardColumn> sampleBoardColumns() => [
  BoardColumn(
    step: VisitStep.reception,
    visits: [sampleVisit(step: VisitStep.reception, elapsedMinutes: 25)],
  ),
  BoardColumn(
    step: VisitStep.interview,
    visits: [sampleVisit(step: VisitStep.interview, elapsedMinutes: 26)],
  ),
  BoardColumn(
    step: VisitStep.collection,
    visits: [sampleVisit(step: VisitStep.collection, elapsedMinutes: 48)],
  ),
  BoardColumn(
    step: VisitStep.rest,
    visits: [sampleVisit(step: VisitStep.rest, restMinutesLeft: 12)],
  ),
  BoardColumn(
    step: VisitStep.done,
    visits: [
      sampleVisit(
        step: VisitStep.done,
        certificateStatus: CertificateStatus.issued,
      ),
    ],
  ),
];

/// Test double for the Donneurs visit service.
class FakeVisitRepository implements VisitRepository {
  FakeVisitRepository({
    CentreDay? day,
    List<BoardColumn>? board,
    Visit? visit,
    this.dayError,
    this.visitError,
  }) : _day = day ?? sampleCentreDay(),
       _board = board ?? sampleBoardColumns(),
       _visit = visit ?? sampleVisit();

  CentreDay _day;
  final List<BoardColumn> _board;
  Visit _visit;

  final ApiError? dayError;
  final ApiError? visitError;

  final List<String> absentReferences = [];
  final List<String> startedCins = [];
  VisitInterview? lastInterview;
  VisitCollection? lastCollection;
  ReceptionProbe? lastReception;
  DateTime? lastRestEndsAt;
  String? lastVariant;

  @override
  Future<CentreDay> today({String? variant}) async {
    final error = dayError;
    if (error != null) throw error;
    return _day;
  }

  @override
  Future<List<BoardColumn>> board({String? variant}) async => _board;

  @override
  Future<Visit> byId(String id, {String? variant}) async {
    final error = visitError;
    if (error != null) throw error;
    return _visit;
  }

  @override
  Future<Visit> start({
    required String cin,
    String? rendezVousId,
    String? variant,
  }) async {
    startedCins.add(cin);
    return _visit = sampleVisit(step: VisitStep.reception, elapsedMinutes: 0);
  }

  @override
  Future<Visit> recordReception(
    String visitId, {
    required String cin,
    required List<VisitQuestion> questionnaire,
    String? variant,
  }) async {
    lastReception = ReceptionProbe(cin: cin, answers: questionnaire.length);
    return _visit = _visit.copyWith(step: VisitStep.interview);
  }

  @override
  Future<Visit> recordInterview(
    String visitId,
    VisitInterview interview, {
    String? variant,
  }) async {
    if (variant == 'wrong-step') {
      throw const ApiError(status: 409, message: 'WRONG_STEP');
    }
    lastInterview = interview;
    lastVariant = variant;
    return _visit = _visit.copyWith(
      step: interview.decision == VisitDecision.fit
          ? VisitStep.collection
          : VisitStep.refused,
      decision: interview.decision,
    );
  }

  @override
  Future<Visit> recordCollection(
    String visitId,
    VisitCollection collection, {
    String? variant,
  }) async {
    lastCollection = collection;
    lastVariant = variant;
    return _visit = _visit.copyWith(
      step: VisitStep.rest,
      volumeMl: collection.volumeMl,
      durationMinutes: collection.durationMinutes,
      restMinutesLeft: 15,
    );
  }

  @override
  Future<Visit> finishRest(
    String visitId, {
    required DateTime restStartedAt,
    required DateTime restEndsAt,
    String? variant,
  }) async {
    lastRestEndsAt = restEndsAt;
    lastVariant = variant;
    return _visit = _visit.copyWith(
      step: VisitStep.done,
      certificateStatus: CertificateStatus.preparing,
    );
  }

  @override
  Future<Visit> refuse(String visitId, {String? variant}) async {
    lastVariant = variant;
    return _visit = _visit.copyWith(
      step: VisitStep.refused,
      decision: VisitDecision.unfit,
    );
  }

  @override
  Future<CentreDay> markAbsent(String reference, {String? variant}) async {
    absentReferences.add(reference);
    _day = CentreDay(
      centreName: _day.centreName,
      date: _day.date,
      stats: _day.stats,
      agenda: _day.agenda
          .where((row) => row.reference != reference)
          .toList(growable: false),
      queue: _day.queue,
      stock: _day.stock,
      lowStockGroups: _day.lowStockGroups,
    );
    return _day;
  }
}

/// What a reception confirm carried, for the tests to assert on.
class ReceptionProbe {
  const ReceptionProbe({required this.cin, required this.answers});

  final String cin;
  final int answers;
}
