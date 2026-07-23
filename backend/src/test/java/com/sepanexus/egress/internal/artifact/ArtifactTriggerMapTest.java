package com.sepanexus.egress.internal.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * EPIC-44 Story 44.3: the outbound artifact-type/trigger/renderer/priority taxonomy is a pure,
 * source-backed, immutable catalog (§6.9) — no database, no Spring context, no profile/CSM lookup.
 * Deliberately narrowed to depend only on {@code EPIC-43} Story 43.1 (the {@code egress} module
 * existing as a home package), not the still-{@code [CAPABILITY-BLOCKED]} egress-profile catalog
 * (Story 44.1) — this taxonomy needs neither an {@code egress_profile} row nor a database table.
 */
@org.junit.jupiter.api.Tag("fast")
class ArtifactTriggerMapTest {

    @Test
    void everySourceRowFromSection69Exists() {
        assertThat(ArtifactTriggerCatalog.definitionFor(OutboundArtifactType.PACS_002_STATUS_REPORT)).isPresent();
        assertThat(ArtifactTriggerCatalog.definitionFor(OutboundArtifactType.PAIN_002_RESULT_FILE)).isPresent();
        assertThat(ArtifactTriggerCatalog.definitionFor(OutboundArtifactType.JSON_STATUS_REPORT)).isPresent();
        assertThat(ArtifactTriggerCatalog.definitionFor(OutboundArtifactType.OPERATOR_NOTIFICATION)).isPresent();
        assertThat(ArtifactTriggerCatalog.definitionFor(OutboundArtifactType.PACS_008_FORWARD)).isPresent();
        assertThat(ArtifactTriggerCatalog.definitionFor(OutboundArtifactType.CAMT_029_RECALL_RESOLUTION)).isPresent();
        assertThat(ArtifactTriggerCatalog.definitionFor(OutboundArtifactType.PACS_004_RETURN)).isPresent();
        assertThat(ArtifactTriggerCatalog.definitionFor(OutboundArtifactType.CAMT_053_STATEMENT)).isPresent();
    }

    @Test
    void noAdditionalArtifactTypeWasInvented() {
        assertThat(ArtifactTriggerCatalog.allDefinitions()).hasSize(8);
        assertThat(ArtifactTriggerCatalog.allDefinitions())
                .extracting(ArtifactTriggerDefinition::artifactType)
                .containsExactlyInAnyOrder(OutboundArtifactType.values());
    }

    @Test
    void pacs002StatusReportHasAllThreeSourceDefinedTriggers() {
        ArtifactTriggerDefinition definition =
                ArtifactTriggerCatalog.definitionFor(OutboundArtifactType.PACS_002_STATUS_REPORT).orElseThrow();
        assertThat(definition.triggers())
                .extracting(TriggerName::value)
                .containsExactlyInAnyOrder("settlement.completed", "settlement.failed", "payment.status.reported");
    }

    @Test
    void everyDefinitionHasTheExactSourcePriority() {
        assertThat(priorityOf(OutboundArtifactType.PACS_002_STATUS_REPORT)).isEqualTo(ArtifactPriority.MVP);
        assertThat(priorityOf(OutboundArtifactType.PAIN_002_RESULT_FILE)).isEqualTo(ArtifactPriority.MVP);
        assertThat(priorityOf(OutboundArtifactType.JSON_STATUS_REPORT)).isEqualTo(ArtifactPriority.MVP);
        assertThat(priorityOf(OutboundArtifactType.OPERATOR_NOTIFICATION)).isEqualTo(ArtifactPriority.P1);
        assertThat(priorityOf(OutboundArtifactType.PACS_008_FORWARD)).isEqualTo(ArtifactPriority.P1);
        assertThat(priorityOf(OutboundArtifactType.CAMT_029_RECALL_RESOLUTION)).isEqualTo(ArtifactPriority.P1);
        assertThat(priorityOf(OutboundArtifactType.PACS_004_RETURN)).isEqualTo(ArtifactPriority.P1);
        assertThat(priorityOf(OutboundArtifactType.CAMT_053_STATEMENT)).isEqualTo(ArtifactPriority.P2);
    }

    @Test
    void everyDefinitionHasTheExactSourceRendererOwner() {
        assertThat(rendererOf(OutboundArtifactType.PACS_002_STATUS_REPORT)).isEqualTo(ArtifactRendererOwner.ISO_ADAPTER);
        assertThat(rendererOf(OutboundArtifactType.PAIN_002_RESULT_FILE))
                .isEqualTo(ArtifactRendererOwner.ISO_ADAPTER_AND_COLLECTOR);
        assertThat(rendererOf(OutboundArtifactType.JSON_STATUS_REPORT)).isEqualTo(ArtifactRendererOwner.EGRESS_TEMPLATING);
        assertThat(rendererOf(OutboundArtifactType.OPERATOR_NOTIFICATION)).isEqualTo(ArtifactRendererOwner.EGRESS_TEMPLATING);
        assertThat(rendererOf(OutboundArtifactType.PACS_008_FORWARD)).isEqualTo(ArtifactRendererOwner.ISO_ADAPTER);
        assertThat(rendererOf(OutboundArtifactType.CAMT_029_RECALL_RESOLUTION)).isEqualTo(ArtifactRendererOwner.ISO_ADAPTER);
        assertThat(rendererOf(OutboundArtifactType.PACS_004_RETURN)).isEqualTo(ArtifactRendererOwner.ISO_ADAPTER);
        assertThat(rendererOf(OutboundArtifactType.CAMT_053_STATEMENT))
                .isEqualTo(ArtifactRendererOwner.REPORTING_AND_ISO_ADAPTER);
    }

    @Test
    void lookupDoesNotReturnTheFirstIncidentalEntry() {
        // A defective lookup (e.g. always returning definitions.get(0)) would make every artifact
        // type resolve to PACS_002_STATUS_REPORT — assert a later entry resolves to itself, not to
        // the first one.
        ArtifactTriggerDefinition definition =
                ArtifactTriggerCatalog.definitionFor(OutboundArtifactType.CAMT_053_STATEMENT).orElseThrow();
        assertThat(definition.artifactType()).isEqualTo(OutboundArtifactType.CAMT_053_STATEMENT);
        assertThat(definition.priority()).isEqualTo(ArtifactPriority.P2);
    }

    @Test
    void catalogIsImmutable() {
        List<ArtifactTriggerDefinition> definitions = ArtifactTriggerCatalog.allDefinitions();
        assertThatThrownBy(() -> definitions.add(
                new ArtifactTriggerDefinition(OutboundArtifactType.PACS_002_STATUS_REPORT, List.of(),
                        ArtifactRendererOwner.ISO_ADAPTER, ArtifactPriority.MVP)))
                .isInstanceOf(UnsupportedOperationException.class);

        ArtifactTriggerDefinition definition =
                ArtifactTriggerCatalog.definitionFor(OutboundArtifactType.PACS_002_STATUS_REPORT).orElseThrow();
        assertThatThrownBy(() -> definition.triggers().add(new TriggerName("invented.trigger")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void noSelectionByCsmOrProfileName() {
        // structural guard at the value level: no trigger/renderer/priority literal in the catalog
        // is a CSM/profile name (TIPS/RT1/STEP2/STET/KIR/ELIXIR-style), matching the frozen
        // (settlement_basis, liquidity_mode) selection rule elsewhere in this codebase.
        Set<String> forbidden = Set.of("TIPS", "RT1", "STEP2", "STET", "KIR", "ELIXIR");
        for (ArtifactTriggerDefinition definition : ArtifactTriggerCatalog.allDefinitions()) {
            for (TriggerName trigger : definition.triggers()) {
                assertThat(forbidden).noneMatch(name -> trigger.value().toUpperCase().contains(name));
            }
        }
    }

    private static ArtifactPriority priorityOf(OutboundArtifactType type) {
        return ArtifactTriggerCatalog.definitionFor(type).orElseThrow().priority();
    }

    private static ArtifactRendererOwner rendererOf(OutboundArtifactType type) {
        return ArtifactTriggerCatalog.definitionFor(type).orElseThrow().renderer();
    }
}
