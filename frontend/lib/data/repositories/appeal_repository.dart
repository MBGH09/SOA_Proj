import '../models/appeal.dart';

abstract interface class AppealRepository {
  Future<List<Appeal>> fetchOpenAppeals();
}