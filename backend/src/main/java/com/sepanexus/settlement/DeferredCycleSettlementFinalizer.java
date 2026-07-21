package com.sepanexus.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Narrow in-process completion command for the source-backed deferred trigger. It first persists
 * SETTLED through the cycle owner, then records one idempotent finality authority fact for every
 * immutable item; no Kafka contract or cross-schema write is introduced.
 */
public final class DeferredCycleSettlementFinalizer {

    private final DeferredSettlementCycleService cycles;
    private final SettlementFinalityService finality;

    public DeferredCycleSettlementFinalizer(DeferredSettlementCycleService cycles, SettlementFinalityService finality) {
        this.cycles = Objects.requireNonNull(cycles, "cycles");
        this.finality = Objects.requireNonNull(finality, "finality");
    }

    public CompletionResult settleAndFinalize(CompletionCommand command) {
        Objects.requireNonNull(command, "command");
        DeferredSettlementCycleService.SettledCycle settled = cycles.settle(command.settlementCommand());
        List<SettlementFinalityService.FinalityOutcome> outcomes = new ArrayList<>();
        for (DeferredSettlementCycleService.SettledItem item : settled.items()) {
            byte[] evidence = command.evidenceByItemId().get(item.itemId());
            if (evidence == null || evidence.length == 0) {
                throw new IllegalArgumentException("each settled item requires immutable finality evidence");
            }
            outcomes.add(finality.recordCycleItemSettled(command.tenantId(), settled.cycleId(), item.itemId(),
                    item.settlementAttemptId(), item.paymentId(), evidence));
        }
        return new CompletionResult(settled, List.copyOf(outcomes));
    }

    public record CompletionCommand(UUID tenantId, DeferredSettlementCycleService.CycleSettlementCommand settlementCommand,
            Map<UUID, byte[]> evidenceByItemId) {
        public CompletionCommand {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(settlementCommand, "settlementCommand");
            evidenceByItemId = Map.copyOf(Objects.requireNonNull(evidenceByItemId, "evidenceByItemId"));
        }
    }

    public record CompletionResult(DeferredSettlementCycleService.SettledCycle settledCycle,
            List<SettlementFinalityService.FinalityOutcome> finalityOutcomes) { }
}
