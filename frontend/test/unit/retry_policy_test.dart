import 'package:blood_network/core/errors/api_error.dart';
import 'package:blood_network/core/providers/core_providers.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('a contract answer is never retried', () {
    expect(noRetry(0, const ApiError(status: 400, message: 'BAD_REQUEST')), isNull);
    expect(noRetry(0, const ApiError(status: 404, message: 'NOT_FOUND')), isNull);
    expect(noRetry(0, const ApiError(status: 409, message: 'CONFLICT')), isNull);
    expect(noRetry(0, const ApiError(status: 503, message: 'UNAVAILABLE')), isNull);
    expect(noRetry(3, const ApiError(status: 503, message: 'UNAVAILABLE')), isNull);
  });
}
