package com.sepanexus.modules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** ISO-owned, read-only evidence facts for one payment after trusted payment visibility is checked. */
public interface PaymentIsoEvidenceQuery {
    PaymentIsoEvidence evidence(UUID tenantId, UUID branchId, UUID paymentId);

    record PaymentIsoEvidence(UUID paymentId, List<IsoMessageEvidence> messages,
                              List<IsoIdentifierEvidence> identifiers) { }
    record IsoMessageEvidence(UUID isoMessageId, String messageType, LocalDate versionEffectiveFrom,
                              String lineageRole, Instant lineageRecordedAt) { }
    record IsoIdentifierEvidence(UUID isoMessageId, IsoIdentifierType type, String value) { }
    enum IsoIdentifierType { MSG_ID, PMT_INF_ID, INSTR_ID, END_TO_END_ID, TX_ID, UETR }
}
