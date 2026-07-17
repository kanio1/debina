package com.sepanexus.egress.internal.artifact;

/**
 * The exact set of outbound artifact rows from
 * {@code sepa-nexus-message-flow-and-data-blueprint.md} §6.9 — no additional type invented.
 */
public enum OutboundArtifactType {
    PACS_002_STATUS_REPORT,
    PAIN_002_RESULT_FILE,
    JSON_STATUS_REPORT,
    OPERATOR_NOTIFICATION,
    PACS_008_FORWARD,
    CAMT_029_RECALL_RESOLUTION,
    PACS_004_RETURN,
    CAMT_053_STATEMENT
}
