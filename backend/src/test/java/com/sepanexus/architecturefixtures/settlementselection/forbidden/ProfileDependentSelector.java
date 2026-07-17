package com.sepanexus.architecturefixtures.settlementselection.forbidden;

import com.sepanexus.architecturefixtures.settlementselection.testprofile.FakeRoutingProfile;

/**
 * EPIC-35 Story 35.2 fixture: a "Selector" that depends on a profile-model type instead of the
 * typed {@code (SettlementBasis, LiquidityMode)} pair — forbidden regardless of parameter type,
 * since the profile object itself carries the CSM identity the `[FREEZE]` forbids switching on.
 * Must be detected by {@code NoProfileNameSwitchTest}.
 */
public class ProfileDependentSelector {

    public String resolve(FakeRoutingProfile profile) {
        return "resolved-for-" + profile.profileCode();
    }
}
