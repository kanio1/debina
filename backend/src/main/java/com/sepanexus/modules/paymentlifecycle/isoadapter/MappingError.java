package com.sepanexus.modules.paymentlifecycle.isoadapter;

/**
 * {@code fieldPath} is a structural pointer (e.g. {@code CdtTrfTxInf/PmtId/EndToEndId}) for
 * operator/log diagnosis — never raw XML content, never a full document dump.
 */
public record MappingError(MappingErrorCode code, String fieldPath, String detail) {
}
