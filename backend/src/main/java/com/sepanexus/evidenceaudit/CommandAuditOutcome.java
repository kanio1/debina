package com.sepanexus.evidenceaudit;

/** Outcome recorded by the application audit plane, never a payment lifecycle status. */
public enum CommandAuditOutcome {
    SUCCESS,
    DENIED
}
