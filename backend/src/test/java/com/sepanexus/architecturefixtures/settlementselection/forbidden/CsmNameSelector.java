package com.sepanexus.architecturefixtures.settlementselection.forbidden;

/**
 * EPIC-35 Story 35.2 fixture: selecting a strategy by a raw CSM-name {@link CharSequence} —
 * forbidden exactly like a {@code String} profile name. Must be detected by {@code
 * NoProfileNameSwitchTest}.
 */
public class CsmNameSelector {

    public String resolve(CharSequence csmName) {
        if ("STEP2".contentEquals(csmName)) {
            return "NET_DEFERRED";
        }
        throw new IllegalArgumentException("unknown CSM: " + csmName);
    }
}
