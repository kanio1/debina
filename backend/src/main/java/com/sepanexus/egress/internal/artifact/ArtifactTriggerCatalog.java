package com.sepanexus.egress.internal.artifact;

import java.util.List;
import java.util.Optional;

/**
 * EPIC-44 Story 44.3: the outbound artifact-type/trigger/renderer/priority taxonomy, verbatim from
 * {@code sepa-nexus-message-flow-and-data-blueprint.md} §6.9 — a pure, immutable, in-memory
 * catalog. No database, no Spring context, no {@code egress_profile}/{@code outbound_artifacts}
 * dependency (both still {@code [CAPABILITY-BLOCKED]}, EPIC-44 Story 44.1/44.2) — this taxonomy
 * needs neither.
 */
public final class ArtifactTriggerCatalog {

    private static final List<ArtifactTriggerDefinition> DEFINITIONS = List.of(
            new ArtifactTriggerDefinition(
                    OutboundArtifactType.PACS_002_STATUS_REPORT,
                    List.of(
                            new TriggerName("settlement.completed"),
                            new TriggerName("settlement.failed"),
                            new TriggerName("payment.status.reported")),
                    ArtifactRendererOwner.ISO_ADAPTER,
                    ArtifactPriority.MVP),
            new ArtifactTriggerDefinition(
                    OutboundArtifactType.PAIN_002_RESULT_FILE,
                    List.of(new TriggerName("FileProcessed")),
                    ArtifactRendererOwner.ISO_ADAPTER_AND_COLLECTOR,
                    ArtifactPriority.MVP),
            new ArtifactTriggerDefinition(
                    OutboundArtifactType.JSON_STATUS_REPORT,
                    List.of(
                            new TriggerName("ingress rejection"),
                            new TriggerName("payment.received"),
                            new TriggerName("payment.accepted")),
                    ArtifactRendererOwner.EGRESS_TEMPLATING,
                    ArtifactPriority.MVP),
            new ArtifactTriggerDefinition(
                    OutboundArtifactType.OPERATOR_NOTIFICATION,
                    List.of(
                            new TriggerName("route.failed"),
                            new TriggerName("egress.dead_lettered"),
                            new TriggerName("recon exception")),
                    ArtifactRendererOwner.EGRESS_TEMPLATING,
                    ArtifactPriority.P1),
            new ArtifactTriggerDefinition(
                    OutboundArtifactType.PACS_008_FORWARD,
                    List.of(new TriggerName("payment.routed")),
                    ArtifactRendererOwner.ISO_ADAPTER,
                    ArtifactPriority.P1),
            new ArtifactTriggerDefinition(
                    OutboundArtifactType.CAMT_029_RECALL_RESOLUTION,
                    List.of(new TriggerName("case.resolved")),
                    ArtifactRendererOwner.ISO_ADAPTER,
                    ArtifactPriority.P1),
            new ArtifactTriggerDefinition(
                    OutboundArtifactType.PACS_004_RETURN,
                    List.of(new TriggerName("ReturnInitiated")),
                    ArtifactRendererOwner.ISO_ADAPTER,
                    ArtifactPriority.P1),
            new ArtifactTriggerDefinition(
                    OutboundArtifactType.CAMT_053_STATEMENT,
                    List.of(new TriggerName("settlement.completed (cycle)")),
                    ArtifactRendererOwner.REPORTING_AND_ISO_ADAPTER,
                    ArtifactPriority.P2));

    private ArtifactTriggerCatalog() {
    }

    public static List<ArtifactTriggerDefinition> allDefinitions() {
        return DEFINITIONS;
    }

    public static Optional<ArtifactTriggerDefinition> definitionFor(OutboundArtifactType type) {
        return DEFINITIONS.stream().filter(definition -> definition.artifactType() == type).findFirst();
    }
}
