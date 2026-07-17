package com.sepanexus.egress.internal.artifact;

/**
 * Which module renders an outbound artifact, per §6.9's "Renderer" column — controlled values
 * only, never a per-CSM/profile-named renderer class (`[REJECT per-CSM engine]`, §6.8).
 */
public enum ArtifactRendererOwner {
    ISO_ADAPTER,
    EGRESS_TEMPLATING,
    ISO_ADAPTER_AND_COLLECTOR,
    REPORTING_AND_ISO_ADAPTER
}
