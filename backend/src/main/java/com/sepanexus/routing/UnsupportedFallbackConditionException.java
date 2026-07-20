package com.sepanexus.routing;

/** A source-defined condition exists, but no authoritative condition language has been supplied. */
public final class UnsupportedFallbackConditionException extends RuntimeException {
    public UnsupportedFallbackConditionException(FallbackRule rule) {
        super("Fallback rule %s has an unsupported condition".formatted(rule.id()));
    }
}
