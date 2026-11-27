import 'dart:convert';

import 'package:flutter/services.dart' show rootBundle;

import '../models/appeal.dart';
import '../repositories/appeal_repository.dart';

/// In-memory backend for the donor appeals list.
class MockAppealRepository implements AppealRepository {
  const MockAppealRepository();

  @override
  Future<List<Appeal>> fetchOpenAppeals() async {
    await Future<void>.delayed(const Duration(milliseconds: 300));
    final raw = await rootBundle.loadString(
      'lib/data/mock/fixtures/appeals.json',
    );
    final decoded = jsonDecode(raw) as List<dynamic>;
    return decoded
        .map((item) => Appeal.fromJson(item as Map<String, dynamic>))
        .toList(growable: false);
  }
}