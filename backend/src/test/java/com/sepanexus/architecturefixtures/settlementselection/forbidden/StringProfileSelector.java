package com.sepanexus.architecturefixtures.settlementselection.forbidden;

/**
 * EPIC-35 Story 35.2 fixture: exactly the pattern `[FREEZE]` forbids — selecting a settlement
 * strategy by a raw profile-name string instead of the typed {@code (SettlementBasis,
 * LiquidityMode)} pair. Must be detected by {@code NoProfileNameSwitchTest}.
 */
public class StringProfileSelector {

    public String resolve(String profileName) {
        return switch (profileName) {
            case "TIPS_LIKE" -> "GROSS_INSTANT";
            case "STET_LIKE" -> "NET_DEFERRED";
            default -> throw new IllegalArgumentException("unknown profile: " + profileName);
        };
    }
}
