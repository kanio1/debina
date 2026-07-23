package com.sepanexus.modules.paymentlifecycle.isoadapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.sepanexus.modules.paymentlifecycle.ingress.HardenedXmlFactory;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * EPIC-27 Story 27.1: extracts {@code OrgnlMsgId}/{@code OrgnlEndToEndId} from a pacs.002
 * (FIToFIPaymentStatusReport) document. Pure extraction only — no correlation (Story 27.2), no
 * DB write, no business decision; {@link Pacs002IdentifierExtractor} is pure (no Spring context
 * needed), every fixture is parsed through the same {@link HardenedXmlFactory} the real pipeline
 * uses, matching the {@code Pain001CanonicalMapperTest} convention.
 */
@org.junit.jupiter.api.Tag("fast")
class Pacs002IdentifierExtractionTest {

    private final HardenedXmlFactory hardenedXmlFactory = new HardenedXmlFactory();
    private final Pacs002IdentifierExtractor extractor = new Pacs002IdentifierExtractor();

    @Test
    void extractsOrgnlMsgIdAndOrgnlEndToEndIdFromSingleTransaction() {
        Pacs002IdentifierExtractionResult result = extract(singleTransaction());

        assertThat(result.success()).isTrue();
        assertThat(result.identifiers()).hasSize(1);
        Pacs002OriginalIdentifiers identifiers = result.identifiers().get(0);
        assertThat(identifiers.orgnlMsgId()).isEqualTo("MSG-0001");
        assertThat(identifiers.orgnlEndToEndId()).isEqualTo("E2E-0001");
    }

    @Test
    void bothIdentifiersPresentAndDistinctFromTheStatusMessagesOwnMsgId() {
        // GrpHdr/MsgId ("STS-MSG-0001") is this status message's OWN identifier -- never to be
        // confused with OrgnlGrpInfAndSts/OrgnlMsgId ("MSG-0001"), the id of the ORIGINAL message
        // being reported on. Story 27.1 task text explicitly names OrgnlMsgId/OrgnlEndToEndId.
        Pacs002IdentifierExtractionResult result = extract(singleTransaction());

        Pacs002OriginalIdentifiers identifiers = result.identifiers().get(0);
        assertThat(identifiers.orgnlMsgId())
                .isEqualTo("MSG-0001")
                .isNotEqualTo("STS-MSG-0001");
    }

    @Test
    void rejectsMissingOrgnlMsgId() {
        Pacs002IdentifierExtractionResult result =
                extract(withoutElement("<OrgnlMsgId>MSG-0001</OrgnlMsgId>"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.MISSING_REQUIRED_ELEMENT);
        assertThat(result.error().fieldPath()).isEqualTo("OrgnlGrpInfAndSts/OrgnlMsgId");
    }

    @Test
    void rejectsMissingOrgnlEndToEndId() {
        Pacs002IdentifierExtractionResult result =
                extract(withoutElement("<OrgnlEndToEndId>E2E-0001</OrgnlEndToEndId>"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.MISSING_REQUIRED_ELEMENT);
        assertThat(result.error().fieldPath()).isEqualTo("TxInfAndSts[0]/OrgnlEndToEndId");
    }

    @Test
    void extractsOneEntryPerTxInfAndStsSharingTheSameOrgnlMsgId() {
        Pacs002IdentifierExtractionResult result = extract(twoTransactions());

        assertThat(result.success()).isTrue();
        assertThat(result.identifiers()).hasSize(2);
        assertThat(result.identifiers())
                .extracting(Pacs002OriginalIdentifiers::orgnlEndToEndId)
                .containsExactly("E2E-0001", "E2E-0002");
        assertThat(result.identifiers())
                .extracting(Pacs002OriginalIdentifiers::orgnlMsgId)
                .containsOnly("MSG-0001");
    }

    @Test
    void rejectsMissingOrgnlEndToEndIdOnSecondOfTwoTransactions() {
        String withSecondTxMissingE2E = twoTransactions()
                .replace("<OrgnlEndToEndId>E2E-0002</OrgnlEndToEndId>", "");

        Pacs002IdentifierExtractionResult result = extract(withSecondTxMissingE2E);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.MISSING_REQUIRED_ELEMENT);
        assertThat(result.error().fieldPath()).isEqualTo("TxInfAndSts[1]/OrgnlEndToEndId");
    }

    @Test
    void rejectsDocumentWithNoTxInfAndSts() {
        String noTransactions = singleTransaction().replaceAll(
                "(?s)<TxInfAndSts>.*</TxInfAndSts>", "");

        Pacs002IdentifierExtractionResult result = extract(noTransactions);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.MISSING_REQUIRED_ELEMENT);
        assertThat(result.error().fieldPath()).isEqualTo("FIToFIPmtStsRpt/TxInfAndSts");
    }

    @Test
    void toleratesWhitespaceAroundIdentifierText() {
        String withWhitespace = singleTransaction()
                .replace("<OrgnlMsgId>MSG-0001</OrgnlMsgId>", "<OrgnlMsgId>\n   MSG-0001  \n</OrgnlMsgId>")
                .replace("<OrgnlEndToEndId>E2E-0001</OrgnlEndToEndId>",
                        "<OrgnlEndToEndId>  E2E-0001\t</OrgnlEndToEndId>");

        Pacs002IdentifierExtractionResult result = extract(withWhitespace);

        assertThat(result.success()).isTrue();
        assertThat(result.identifiers().get(0).orgnlMsgId()).isEqualTo("MSG-0001");
        assertThat(result.identifiers().get(0).orgnlEndToEndId()).isEqualTo("E2E-0001");
    }

    @Test
    void rejectsUnrecognizedMessageTypeGracefullyRatherThanThrowing() {
        Pacs002IdentifierExtractionResult result = extract("""
                <?xml version="1.0"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">
                  <CstmrCdtTrfInitn/>
                </Document>
                """);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.UNSUPPORTED_MESSAGE_TYPE);
    }

    @Test
    void rejectsUnsupportedVersion() {
        Pacs002IdentifierExtractionResult result =
                extract(singleTransaction().replace("pacs.002.001.10", "pacs.002.001.03"));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(MappingErrorCode.UNSUPPORTED_MESSAGE_VERSION);
    }

    @Test
    void extractionIsDeterministic() {
        String xml = twoTransactions();

        Pacs002IdentifierExtractionResult first = extract(xml);
        Pacs002IdentifierExtractionResult second = extract(xml);

        assertThat(first).isEqualTo(second);
    }

    private Pacs002IdentifierExtractionResult extract(String xml) {
        HardenedXmlFactory.HardenedParseResult parsed = hardenedXmlFactory.parse(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(parsed.accepted()).as("fixture must be well-formed XML").isTrue();
        return extractor.extract(parsed.document());
    }

    private static String withoutElement(String elementXml) {
        return singleTransaction().replace(elementXml, "");
    }

    private static String singleTransaction() {
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
                      <OrgnlEndToEndId>E2E-0001</OrgnlEndToEndId>
                      <TxSts>ACSC</TxSts>
                    </TxInfAndSts>
                  </FIToFIPmtStsRpt>
                </Document>
                """;
    }

    private static String twoTransactions() {
        String secondTransaction = """
                <TxInfAndSts>
                  <StsId>STS-0002</StsId>
                  <OrgnlEndToEndId>E2E-0002</OrgnlEndToEndId>
                  <TxSts>RJCT</TxSts>
                </TxInfAndSts>
                """;
        return singleTransaction().replace("</FIToFIPmtStsRpt>", secondTransaction + "</FIToFIPmtStsRpt>");
    }
}
