package com.sepanexus.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Wave 11 RED/GREEN contract: the read-only transport must expose a dedicated, source-owned ISO
 * evidence field. Runtime/security behaviour belongs to the ISO owner once its public read port
 * exists; this test deliberately does not permit a repository shortcut in the GraphQL adapter.
 */
@org.junit.jupiter.api.Tag("fast")
class PaymentLineageGraphQLTest {

    @Test
    void schemaExposesPaymentIsoEvidenceAsAQueryOnlyReadModel() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/graphql/schema.graphqls"));

        assertThat(schema).contains("paymentIsoEvidence(paymentId: ID!): PaymentIsoEvidence");
        assertThat(schema).contains("type PaymentIsoEvidence");
        assertThat(schema).contains("type IsoMessageEvidence");
        assertThat(schema).contains("messageVersion: String");
        assertThat(schema).contains("enum IsoIdentifierType");
    }
}
