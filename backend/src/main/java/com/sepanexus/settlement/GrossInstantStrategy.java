package com.sepanexus.settlement;

import com.sepanexus.ledger.GrossInstantLedgerPort;
import com.sepanexus.modules.GrossInstantPaymentFinalityPort;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Source §5's gross-instant order executed as one physical PostgreSQL transaction under ADR-N11:
 * ledger reserve/post, settlement ON_LEDGER_POST finality, then payment's finality projection and
 * source-backed status-report fact. No table SQL is issued by settlement.
 */
@Service
public class GrossInstantStrategy {

    private final GrossInstantLedgerPort ledgerPort;
    private final GrossInstantSettlementCommandPort settlementPort;
    private final GrossInstantPaymentFinalityPort paymentPort;
    private final GrossInstantCoordinatorTenantContext tenantContext;
    private final GrossInstantFailureInjector failureInjector;

    public GrossInstantStrategy(@Qualifier("grossInstantLedgerPort") GrossInstantLedgerPort ledgerPort,
            GrossInstantSettlementCommandPort settlementPort,
            @Qualifier("grossInstantPaymentFinalityPort") GrossInstantPaymentFinalityPort paymentPort,
            GrossInstantCoordinatorTenantContext tenantContext, GrossInstantFailureInjector failureInjector) {
        this.ledgerPort = ledgerPort;
        this.settlementPort = settlementPort;
        this.paymentPort = paymentPort;
        this.tenantContext = tenantContext;
        this.failureInjector = failureInjector;
    }

    @Transactional(transactionManager = "grossInstantTransactionManager")
    public GrossInstantOutcome execute(GrossInstantCommand command) {
        requireValid(command);
        tenantContext.apply(command.tenantId());
        failureInjector.at(GrossInstantFailureInjector.Phase.BEFORE_LEDGER);
        GrossInstantLedgerPort.GrossInstantResult ledger = ledgerPort.reserveAndPost(new GrossInstantLedgerPort.GrossInstantCommand(
                command.tenantId(), command.attemptId(), command.paymentId(), command.debtorAccountId(),
                command.creditorAccountId(), command.amountMinor(), command.currency(), command.commandId(),
                command.occurredAt()));
        failureInjector.at(GrossInstantFailureInjector.Phase.AFTER_LEDGER);
        if (ledger instanceof GrossInstantLedgerPort.InsufficientLiquidity insufficient) {
            failureInjector.at(GrossInstantFailureInjector.Phase.BEFORE_SETTLEMENT);
            GrossInstantSettlementCommandPort.RejectedAttemptResult attempt = settlementPort.recordInsufficientLiquidity(
                    command.tenantId(), command.attemptId(), command.paymentId(), command.commandId(), command.amountMinor(),
                    command.currency(), command.occurredAt());
            failureInjector.at(GrossInstantFailureInjector.Phase.AFTER_SETTLEMENT);
            failureInjector.at(GrossInstantFailureInjector.Phase.BEFORE_PAYMENT);
            GrossInstantPaymentFinalityPort.RejectionResult payment = paymentPort.recordGrossInstantInsufficientLiquidity(
                    command.tenantId(), command.paymentId(), command.commandId(), command.occurredAt());
            failureInjector.at(GrossInstantFailureInjector.Phase.AFTER_PAYMENT);
            return new InsufficientLiquidity(attempt.replayed(), payment);
        }
        GrossInstantLedgerPort.Posted posted = (GrossInstantLedgerPort.Posted) ledger;
        failureInjector.at(GrossInstantFailureInjector.Phase.BEFORE_SETTLEMENT);
        GrossInstantSettlementCommandPort.FinalityResult finality = settlementPort.recordPosted(command.tenantId(),
                command.attemptId(), command.paymentId(), command.commandId(), posted.reservationId(), posted.postEntryId(),
                command.amountMinor(), command.currency(), posted.occurredAt(), command.evidenceHash());
        failureInjector.at(GrossInstantFailureInjector.Phase.AFTER_SETTLEMENT);
        failureInjector.at(GrossInstantFailureInjector.Phase.BEFORE_PAYMENT);
        GrossInstantPaymentFinalityPort.ProjectionResult projection = paymentPort.projectGrossInstant(command.tenantId(),
                command.paymentId(), command.commandId(), finality.finalityRecordId(), finality.finalityAt(),
                command.occurredAt());
        failureInjector.at(GrossInstantFailureInjector.Phase.AFTER_PAYMENT);
        return new Settled(posted, finality, projection);
    }

    private static void requireValid(GrossInstantCommand command) {
        if (command == null || command.tenantId() == null || command.attemptId() == null || command.paymentId() == null
                || command.debtorAccountId() == null || command.creditorAccountId() == null || command.commandId() == null
                || command.amountMinor() <= 0 || !"EUR".equals(command.currency()) || command.occurredAt() == null
                || command.evidenceHash() == null || command.evidenceHash().length == 0) {
            throw new IllegalArgumentException("gross-instant command is incomplete or invalid");
        }
    }

    public record GrossInstantCommand(UUID tenantId, UUID attemptId, UUID paymentId, UUID debtorAccountId,
            UUID creditorAccountId, long amountMinor, String currency, UUID commandId, Instant occurredAt,
            byte[] evidenceHash) {
        public GrossInstantCommand {
            evidenceHash = evidenceHash == null ? null : Arrays.copyOf(evidenceHash, evidenceHash.length);
        }
        @Override public byte[] evidenceHash() {
            return evidenceHash == null ? null : Arrays.copyOf(evidenceHash, evidenceHash.length);
        }
    }

    public sealed interface GrossInstantOutcome permits Settled, InsufficientLiquidity { }
    public record Settled(GrossInstantLedgerPort.Posted posted, GrossInstantSettlementCommandPort.FinalityResult finality,
            GrossInstantPaymentFinalityPort.ProjectionResult projection) implements GrossInstantOutcome { }
    public record InsufficientLiquidity(boolean replayed, GrossInstantPaymentFinalityPort.RejectionResult payment)
            implements GrossInstantOutcome { }
}
