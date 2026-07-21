package com.sepanexus.evidenceaudit;

/** A denial cannot be reported as an ordinary authorization failure when its required evidence fails. */
public class DeniedCommandAuditUnavailableException extends RuntimeException {
    public DeniedCommandAuditUnavailableException(Throwable cause) { super("Denied command audit is unavailable", cause); }
}
