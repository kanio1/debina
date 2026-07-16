package com.sepanexus.modules.paymentlifecycle.isoadapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.sepanexus.modules.paymentlifecycle.ingress.HardenedXmlFactory;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * EPIC-27 Story 27.2A: {@link Pacs002IdentifierExtractor#extractCorrelationInputs} pulls the
 * full set of {@code Orgnl*} identifiers a pacs.002.001.10 {@code TxInfAndSts} entry can carry
 * (needed by the Story 27.2B ordered correlation policy), on top of Story 27.1's minimal
 * {@code OrgnlMsgId}/{@code OrgnlEndToEndId} extraction (untouched, still covered by
 * {@link Pacs002IdentifierExtractionTest}). Pure extraction only — no correlation, no DB write.
 */
class Pacs002CorrelationInputTest {

    private final HardenedXmlFactory hardenedXmlFactory = new HardenedXmlFactory();
    private final Pacs002IdentifierExtractor extractor = new Pacs002IdentifierExtractor();

    @Test
    void extractsAllOrgnlIdentifiersWhenPresent() {
        Pacs002CorrelationExtractionResult result = extract(transactionWithAllIdentifiers());

        assertThat(result.success()).isTrue();
        assertThat(result.inputs()).hasSize(1);
        Pacs002CorrelationInput input = result.inputs().get(0);
        assertThat(input.orgnlMsgId()).isEqualTo("MSG-0001");
        assertThat(input.orgnlTxId()).isEqualTo("TX-0001");
        assertThat(input.orgnlInstrId()).isEqualTo("INSTR-0001");
        assertThat(input.orgnlEndToEndId()).isEqualTo("E2E-0001");
        assertThat(input.orgnlUetr()).isEqualTo("11111111-1111-1111-1111-111111111111");
    }

    @Test
    void currentMessagesOwnMsgIdIsNeverConfusedWithOrgnlMsgId() {
        Pacs002CorrelationExtractionResult result = extract(transactionWithAllIdentifiers());

        assertThat(result.inputs().get(0).orgnlMsgId())
                .isEqualTo("MSG-0001")
                .isNotEqualTo("STS-MSG-0001");
    }

    @Test
    void orgnlTxIdIsNullWhenAbsent() {
        Pacs002CorrelationExtractionResult result =
                extract(withoutElement("<OrgnlTxId>TX-0001</OrgnlTxId>"));

        assertThat(result.success()).isTrue();
        assertThat(result.inputs().get(0).orgnlTxId()).isNull();
        assertThat(result.inputs().get(0).orgnlEndToEndId()).isEqualTo("E2E-0001");
    }

    @Test
    void orgnlInstrIdIsNullWhenAbsent() {
        Pacs002CorrelationExtractionResult result =
                extract(withoutElement("<OrgnlInstrId>INSTR-0001</OrgnlInstrId>"));

        assertThat(result.success()).isTrue();
        assertThat(result.inputs().get(0).orgnlInstrId()).isNull();
    }

    @Test
    void orgnlUetrIsNullWhenAbsent() {
        Pacs002CorrelationExtractionResult result = extract(withoutElement(
                "<OrgnlUETR>11111111-1111-1111-1111-111111111111</OrgnlUETR>"));

        assertThat(result.success()).isTrue();
        assertThat(result.inputs().get(0).orgnlUetr()).isNull();
    }

    @Test
    void rejectsMissingOrgnlMsgId() {
        Pacs002CorrelationExtractionResult result =
                extract(withoutElement("<OrgnlMsgId>MSG-0001</OrgnlMsgId>"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.MISSING_REQUIRED_ELEMENT);
        assertThat(result.error().fieldPath()).isEqualTo("OrgnlGrpInfAndSts/OrgnlMsgId");
    }

    @Test
    void rejectsMissingOrgnlEndToEndId() {
        Pacs002CorrelationExtractionResult result =
                extract(withoutElement("<OrgnlEndToEndId>E2E-0001</OrgnlEndToEndId>"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.MISSING_REQUIRED_ELEMENT);
        assertThat(result.error().fieldPath()).isEqualTo("TxInfAndSts[0]/OrgnlEndToEndId");
    }

    @Test
    void extractsOneInputPerTxInfAndStsSharingTheSameOrgnlMsgId() {
        String secondTransaction = """
                <TxInfAndSts>
                  <StsId>STS-0002</StsId>
                  <OrgnlInstrId>INSTR-0002</OrgnlInstrId>
                  <OrgnlEndToEndId>E2E-0002</OrgnlEndToEndId>
                  <OrgnlTxId>TX-0002</OrgnlTxId>
                  <TxSts>RJCT</TxSts>
                </TxInfAndSts>
                """;
        String twoTransactions = transactionWithAllIdentifiers()
                .replace("</FIToFIPmtStsRpt>", secondTransaction + "</FIToFIPmtStsRpt>");

        Pacs002CorrelationExtractionResult result = extract(twoTransactions);

        assertThat(result.success()).isTrue();
        assertThat(result.inputs()).hasSize(2);
        assertThat(result.inputs()).extracting(Pacs002CorrelationInput::orgnlMsgId).containsOnly("MSG-0001");
        assertThat(result.inputs())
                .extracting(Pacs002CorrelationInput::orgnlTxId)
                .containsExactly("TX-0001", "TX-0002");
        assertThat(result.inputs())
                .extracting(Pacs002CorrelationInput::orgnlInstrId)
                .containsExactly("INSTR-0001", "INSTR-0002");
    }

    @Test
    void rejectsDocumentWithNoTxInfAndSts() {
        String noTransactions = transactionWithAllIdentifiers().replaceAll(
                "(?s)<TxInfAndSts>.*</TxInfAndSts>", "");

        Pacs002CorrelationExtractionResult result = extract(noTransactions);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.MISSING_REQUIRED_ELEMENT);
        assertThat(result.error().fieldPath()).isEqualTo("FIToFIPmtStsRpt/TxInfAndSts");
    }

    @Test
    void rejectsUnrecognizedMessageTypeGracefullyRatherThanThrowing() {
        Pacs002CorrelationExtractionResult result = extract("""
                <?xml version="1.0"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">
                  <CstmrCdtTrfInitn/>
                </Document>
                """);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.UNSUPPORTED_MESSAGE_TYPE);
    }

    private Pacs002CorrelationExtractionResult extract(String xml) {
        HardenedXmlFactory.HardenedParseResult parsed = hardenedXmlFactory.parse(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(parsed.accepted()).as("fixture must be well-formed XML").isTrue();
        return extractor.extractCorrelationInputs(parsed.document());
    }

    private static String withoutElement(String elementXml) {
        return transactionWithAllIdentifiers().replace(elementXml, "");
    }

    private static String transactionWithAllIdentifiers() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.002.001.10">
                  <FIToFIPmtStsRpt>
                    <GrpHdr>
                      <MsgId>STS-MSG-0001</MsgId>
                      <CreDtTm>2026-07-16T10:00:00</CreDtTm>
                    </GrpHdr>
                    <OrgnlGrpInfAndSts>
                      <OrgnlMsgId>MSG-0001</OrgnlMsgId>
                      <OrgnlMsgNmId>pain.001.001.09</OrgnlMsgNmId>
                    </OrgnlGrpInfAndSts>
                    <TxInfAndSts>
                      <StsId>STS-0001</StsId>
                      <OrgnlInstrId>INSTR-0001</OrgnlInstrId>
                      <OrgnlEndToEndId>E2E-0001</OrgnlEndToEndId>
                      <OrgnlTxId>TX-0001</OrgnlTxId>
                      <OrgnlUETR>11111111-1111-1111-1111-111111111111</OrgnlUETR>
                      <TxSts>ACSC</TxSts>
                    </TxInfAndSts>
                  </FIToFIPmtStsRpt>
                </Document>
                """;
    }
}
