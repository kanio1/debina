package com.sepanexus.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Mutation guards for ADR-N10's non-negotiable row-lock and active-transition predicates. */
class LedgerPortReservationProtocolContractTest {

    private static final Path WRITER = Path.of("src/main/java/com/sepanexus/ledger/JdbcLedgerPort.java");

    @Test
    void reservationConsumptionLocksTheReservationRow() throws Exception {
        assertThat(Files.readString(WRITER)).contains("WHERE id = ? FOR UPDATE");
    }

    @Test
    void terminalTransitionStillRequiresAnActiveReservation() throws Exception {
        assertThat(Files.readString(WRITER)).contains("WHERE id = ? AND state = 'ACTIVE'");
    }

    @Test
    void terminalCommandReplayProtectionIsPresent() throws Exception {
        assertThat(Files.readString(WRITER)).contains("commandId.equals(reservation.terminalCommandId())");
    }
}
