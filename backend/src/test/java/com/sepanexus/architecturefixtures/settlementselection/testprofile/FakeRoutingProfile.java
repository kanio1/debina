package com.sepanexus.architecturefixtures.settlementselection.testprofile;

/**
 * EPIC-35 Story 35.2 fixture: stands in for a routing/reference-data profile model type (neither
 * package exists in main sources yet) so {@code NoProfileNameSwitchTest} can prove its
 * no-profile-dependency rule is non-vacuous — it must detect {@link
 * com.sepanexus.architecturefixtures.settlementselection.forbidden.ProfileDependentSelector}'s
 * dependency on this type.
 */
public class FakeRoutingProfile {

    private final String profileCode;

    public FakeRoutingProfile(String profileCode) {
        this.profileCode = profileCode;
    }

    public String profileCode() {
        return profileCode;
    }
}
