package com.sepanexus.settlement;

import java.util.Set;

/** ADR-N10 policy selection without simulated CSM, transport, receipt or ISO-status triggers. */
public final class FinalityRulePolicy {

    public static final String ON_LEDGER_POST = "ON_LEDGER_POST";
    public static final String ON_CYCLE_SETTLED = "ON_CYCLE_SETTLED";
    public static final String ON_NET_POSITION_SETTLED = "ON_NET_POSITION_SETTLED";
    public static final String ON_INTERNAL_BOOK_POST = "ON_INTERNAL_BOOK_POST";

    private static final Set<String> CATALOG_RULES = Set.of(
            ON_LEDGER_POST, ON_CYCLE_SETTLED, ON_NET_POSITION_SETTLED, ON_INTERNAL_BOOK_POST);

    public boolean isCatalogued(String code, int version) {
        return version == 1 && CATALOG_RULES.contains(code);
    }

    /** Only the real LedgerPort POST source exists in the current laboratory build. */
    public boolean isExecutableNow(String code) {
        return ON_LEDGER_POST.equals(code);
    }
}
