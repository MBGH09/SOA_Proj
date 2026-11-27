import 'package:flutter/material.dart';

import '../../../../data/models/blood_request.dart';
import '../../../../data/models/stock_level.dart';
import '../../../../shared/widgets/app_badge.dart';

/// The badge tone of a request's status.
AppBadgeTone toneForStatus(RequestStatus status) => switch (status) {
  RequestStatus.waiting => AppBadgeTone.neutral,
  RequestStatus.satisfied => AppBadgeTone.success,
  RequestStatus.supplying => AppBadgeTone.warning,
  RequestStatus.shortage => AppBadgeTone.critical,
  RequestStatus.cancelled => AppBadgeTone.neutral,
};

/// The badge tone of a request's urgency.
AppBadgeTone toneForUrgency(RequestUrgency urgency) => switch (urgency) {
  RequestUrgency.normal => AppBadgeTone.neutral,
  RequestUrgency.high => AppBadgeTone.warning,
  RequestUrgency.critical => AppBadgeTone.critical,
};

/// The badge tone of a stock alert.
AppBadgeTone toneForAlert(StockAlert alert) => switch (alert) {
  StockAlert.inStock => AppBadgeTone.success,
  StockAlert.near => AppBadgeTone.warning,
  StockAlert.low => AppBadgeTone.critical,
};

/// `In delivery`, `Delivered`, … as the table's status pill.
AppBadge statusBadge(RequestStatus status) =>
    AppBadge(label: status.label, tone: toneForStatus(status));

/// `Critical`, `High`, `Normal` as the table's urgency pill.
AppBadge urgencyBadge(RequestUrgency urgency) =>
    AppBadge(label: urgency.label, tone: toneForUrgency(urgency));

/// The tone of the reservation line (H3).
AppBadgeTone toneForReservation(String? status) {
  if (status == null) return AppBadgeTone.neutral;
  final lower = status.toLowerCase();
  if (lower.contains('reserved') || lower.contains('confirmed')) {
    return AppBadgeTone.success;
  }
  if (lower.contains('placed') ||
      lower.contains('ordered') ||
      lower.contains('progress')) {
    return AppBadgeTone.warning;
  }
  return AppBadgeTone.neutral;
}

/// Plain icon for a status, used on the mobile cards.
IconData iconForStatus(RequestStatus status) => switch (status) {
  RequestStatus.waiting => Icons.schedule_outlined,
  RequestStatus.satisfied => Icons.check_circle_outline,
  RequestStatus.supplying => Icons.local_shipping_outlined,
  RequestStatus.shortage => Icons.warning_amber_rounded,
  RequestStatus.cancelled => Icons.cancel_outlined,
};
