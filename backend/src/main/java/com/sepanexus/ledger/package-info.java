/**
 * Ledger module: the sole runtime writer of {@code ledger.*}. Settlement reaches it exclusively
 * through {@link com.sepanexus.ledger.LedgerPort}; it never receives a ledger database grant.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared"})
package com.sepanexus.ledger;
