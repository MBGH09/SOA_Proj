/// The standard error the contract returns from every service.
///
/// ```json
/// { "timestamp": "...", "status": 409, "error": "CONFLICT",
///   "message": "...", "path": "..." }
/// ```
class ApiError implements Exception {
  const ApiError({
    required this.status,
    required this.message,
    this.fieldErrors = const <String, String>{},
  });

  final int status;

  /// Technical message from the service. Screens show [apiMessageFor] copy
  /// instead, so no HTTP code or rule id reaches the UI.
  final String message;

  /// Field name → message, for 400 responses that highlight form inputs.
  final Map<String, String> fieldErrors;

  bool get isBadRequest => status == 400;
  bool get isNotFound => status == 404;
  bool get isConflict => status == 409;
  bool get isUnavailable => status == 503 || status == 504;

  @override
  String toString() => 'ApiError($status): $message';
}

/// Plain-language copy for each contract status (study §8).
///
/// Used as the fallback when a repository does not supply a specific message.
String apiMessageFor(ApiError error) => switch (error.status) {
  400 => 'Some details are missing or not valid. Please check the form and try again.',
  401 => 'Your session has ended. Please sign in again.',
  403 => 'You do not have access to this.',
  404 => 'We could not find what you asked for. It may have been removed.',
  409 => 'This is not possible right now. Refresh the page and try again.',
  503 => 'The service is busy. Please try again in a moment.',
  504 => 'The service is taking too long to answer. Please try again.',
  _ => 'Something went wrong. Please try again.',
};

/// Copy for the 202 "accepted, still processing" outcomes of a blood request.
const String acceptedPendingMessage =
    'Accepted. We are arranging the delivery and will update this request on its own.';

/// Copy for the 202 outcome where nobody can supply the blood.
const String acceptedShortageMessage =
    'Accepted. No stock could be reserved yet, so a public appeal has been opened.';
