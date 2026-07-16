package com.sepanexus.modules.paymentlifecycle.isoadapter;

import java.util.UUID;

/** EPIC-27 Story 27.2B: one candidate payment a match strategy resolved to. */
public record CorrelationCandidate(UUID paymentId) {
}
