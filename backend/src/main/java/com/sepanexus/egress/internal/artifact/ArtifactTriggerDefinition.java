package com.sepanexus.egress.internal.artifact;

import java.util.List;

/** One row of §6.9's outbound artifact taxonomy table. {@code triggers} is immutable. */
public record ArtifactTriggerDefinition(
        OutboundArtifactType artifactType,
        List<TriggerName> triggers,
        ArtifactRendererOwner renderer,
        ArtifactPriority priority) {

    public ArtifactTriggerDefinition {
        triggers = List.copyOf(triggers);
    }
}
