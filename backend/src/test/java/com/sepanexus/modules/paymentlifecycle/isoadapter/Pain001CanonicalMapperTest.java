package com.sepanexus.modules.paymentlifecycle.isoadapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.sepanexus.modules.paymentlifecycle.ingress.HardenedXmlFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/**
 * EPIC-19 Story 19.4: {@link Pain001CanonicalMapper} is pure (no Spring context needed) — every
 * fixture is parsed through the same {@link HardenedXmlFactory} the real pipeline uses, so these
 * tests exercise the exact {@link Document} shape the mapper receives in production.
 */
@org.junit.jupiter.api.Tag("fast")
class Pain001CanonicalMapperTest {

    private final HardenedXmlFactory hardenedXmlFactory = new HardenedXmlFactory();
    private final Pain001CanonicalMapper mapper = new Pain001CanonicalMapper();

    @Test
    void mapsValidMinimalDocument() {
        CanonicalMappingResult result = map(minimal());

        assertThat(result.success()).isTrue();
        CanonicalPaymentCommand command = result.command();
        assertThat(command.msgId()).isEqualTo("MSG-0001");
        assertThat(command.pmtInfId()).isEqualTo("PMTINF-0001");
        assertThat(command.instrId()).isNull();
        assertThat(command.endToEndId()).isEqualTo("E2E-0001");
        assertThat(command.uetr()).isNull();
        assertThat(command.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(command.currency()).isEqualTo("EUR");
        assertThat(command.debtorIban()).isEqualTo("DE89370400440532013000");
        assertThat(command.creditorIban()).isEqualTo("FR7630006000011234567890189");
    }

    @Test
    void mapsValidRealisticDocumentWithOptionalFields() {
        CanonicalMappingResult result = map(realistic());

        assertThat(result.success()).isTrue();
        CanonicalPaymentCommand command = result.command();
        assertThat(command.instrId()).isEqualTo("INSTR-0001");
        assertThat(command.uetr()).isEqualTo("8a562c67-ca16-48ba-b074-65581be6f001");
        assertThat(command.amount()).isEqualByComparingTo(new BigDecimal("1250.50"));
        assertThat(command.currency()).isEqualTo("EUR");
    }

    @Test
    void mappingIsDeterministic() {
        String xml = realistic();

        CanonicalMappingResult first = map(xml);
        CanonicalMappingResult second = map(xml);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void rejectsMissingMsgId() {
        CanonicalMappingResult result = map(withoutElement("<MsgId>MSG-0001</MsgId>"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.MISSING_REQUIRED_ELEMENT);
        assertThat(result.error().fieldPath()).isEqualTo("GrpHdr/MsgId");
    }

    @Test
    void rejectsMissingEndToEndId() {
        CanonicalMappingResult result = map(withoutElement("<EndToEndId>E2E-0001</EndToEndId>"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.MISSING_REQUIRED_ELEMENT);
        assertThat(result.error().fieldPath()).isEqualTo("CdtTrfTxInf/PmtId/EndToEndId");
    }

    @Test
    void rejectsMissingAmount() {
        CanonicalMappingResult result = map(
                minimal().replace("<InstdAmt Ccy=\"EUR\">100.00</InstdAmt>", "<InstdAmt Ccy=\"EUR\"></InstdAmt>"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.INVALID_FIELD_FORMAT);
    }

    @Test
    void rejectsMissingCurrency() {
        CanonicalMappingResult result = map(
                minimal().replace("<InstdAmt Ccy=\"EUR\">100.00</InstdAmt>", "<InstdAmt>100.00</InstdAmt>"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.MISSING_REQUIRED_ELEMENT);
        assertThat(result.error().fieldPath()).isEqualTo("Amt/InstdAmt/@Ccy");
    }

    @Test
    void rejectsMissingDebtorIban() {
        CanonicalMappingResult result = map(withoutElement("<IBAN>DE89370400440532013000</IBAN>"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.MISSING_REQUIRED_ELEMENT);
        assertThat(result.error().fieldPath()).isEqualTo("PmtInf/DbtrAcct/Id/IBAN");
    }

    @Test
    void rejectsMissingCreditorIban() {
        CanonicalMappingResult result = map(withoutElement("<IBAN>FR7630006000011234567890189</IBAN>"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.MISSING_REQUIRED_ELEMENT);
        assertThat(result.error().fieldPath()).isEqualTo("CdtTrfTxInf/CdtrAcct/Id/IBAN");
    }

    @Test
    void rejectsUnsupportedVersion() {
        CanonicalMappingResult result = map(
                minimal().replace("pain.001.001.09", "pain.001.001.03"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.UNSUPPORTED_MESSAGE_VERSION);
    }

    @Test
    void rejectsUnrecognizedMessageType() {
        CanonicalMappingResult result = map("""
                <?xml version="1.0"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
                  <FIToFICstmrCdtTrf/>
                </Document>
                """);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.UNSUPPORTED_MESSAGE_TYPE);
    }

    @Test
    void rejectsMultipleTransactionsInOnePmtInf() {
        String secondTransaction = """
                <CdtTrfTxInf>
                  <PmtId><EndToEndId>E2E-0002</EndToEndId></PmtId>
                  <Amt><InstdAmt Ccy="EUR">5.00</InstdAmt></Amt>
                  <CdtrAcct><Id><IBAN>FR7630006000011234567890189</IBAN></Id></CdtrAcct>
                </CdtTrfTxInf>
                """;
        CanonicalMappingResult result = map(minimal().replace("</PmtInf>", secondTransaction + "</PmtInf>"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.UNSUPPORTED_TRANSACTION_COUNT);
        assertThat(result.error().fieldPath()).isEqualTo("PmtInf/CdtTrfTxInf");
    }

    @Test
    void rejectsMultiplePaymentInformationBlocks() {
        String secondPmtInf = minimal();
        String pmtInfBlock = secondPmtInf.substring(secondPmtInf.indexOf("<PmtInf>"), secondPmtInf.indexOf("</PmtInf>") + "</PmtInf>".length());
        CanonicalMappingResult result = map(minimal().replace("</PmtInf>", "</PmtInf>" + pmtInfBlock));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.UNSUPPORTED_TRANSACTION_COUNT);
        assertThat(result.error().fieldPath()).isEqualTo("CstmrCdtTrfInitn/PmtInf");
    }

    private CanonicalMappingResult map(String xml) {
        HardenedXmlFactory.HardenedParseResult parsed = hardenedXmlFactory.parse(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(parsed.accepted()).as("fixture must be well-formed XML").isTrue();
        return mapper.map(parsed.document());
    }

    private static String withoutElement(String elementXml) {
        return minimal().replace(elementXml, "");
    }

    private static String minimal() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">
                  <CstmrCdtTrfInitn>
                    <GrpHdr>
                      <MsgId>MSG-0001</MsgId>
                      <CreDtTm>2026-07-15T10:00:00</CreDtTm>
                      <NbOfTxs>1</NbOfTxs>
                    </GrpHdr>
                    <PmtInf>
                      <PmtInfId>PMTINF-0001</PmtInfId>
                      <DbtrAcct>
                        <Id>
                          <IBAN>DE89370400440532013000</IBAN>
                        </Id>
                      </DbtrAcct>
                      <CdtTrfTxInf>
                        <PmtId>
                          <EndToEndId>E2E-0001</EndToEndId>
                        </PmtId>
                        <Amt>
                          <InstdAmt Ccy="EUR">100.00</InstdAmt>
                        </Amt>
                        <CdtrAcct>
                          <Id>
                            <IBAN>FR7630006000011234567890189</IBAN>
                          </Id>
                        </CdtrAcct>
                      </CdtTrfTxInf>
                    </PmtInf>
                  </CstmrCdtTrfInitn>
                </Document>
                """;
    }

    private static String realistic() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">
                  <CstmrCdtTrfInitn>
                    <GrpHdr>
                      <MsgId>MSG-REAL-0001</MsgId>
                      <CreDtTm>2026-07-15T10:00:00</CreDtTm>
                      <NbOfTxs>1</NbOfTxs>
                      <InitgPty><Nm>ACME Corp</Nm></InitgPty>
                    </GrpHdr>
                    <PmtInf>
                      <PmtInfId>PMTINF-REAL-0001</PmtInfId>
                      <PmtMtd>TRF</PmtMtd>
                      <ReqdExctnDt><Dt>2026-07-16</Dt></ReqdExctnDt>
                      <Dbtr><Nm>ACME Corp</Nm></Dbtr>
                      <DbtrAcct>
                        <Id>
                          <IBAN>DE89370400440532013000</IBAN>
                        </Id>
                      </DbtrAcct>
                      <CdtTrfTxInf>
                        <PmtId>
                          <InstrId>INSTR-0001</InstrId>
                          <EndToEndId>E2E-REAL-0001</EndToEndId>
                          <UETR>8a562c67-ca16-48ba-b074-65581be6f001</UETR>
                        </PmtId>
                        <Amt>
                          <InstdAmt Ccy="EUR">1250.50</InstdAmt>
                        </Amt>
                        <Cdtr><Nm>Beneficiary GmbH</Nm></Cdtr>
                        <CdtrAcct>
                          <Id>
                            <IBAN>FR7630006000011234567890189</IBAN>
                          </Id>
                        </CdtrAcct>
                      </CdtTrfTxInf>
                    </PmtInf>
                  </CstmrCdtTrfInitn>
                </Document>
                """;
    }
}
