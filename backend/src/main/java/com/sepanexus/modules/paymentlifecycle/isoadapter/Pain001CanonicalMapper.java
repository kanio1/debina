package com.sepanexus.modules.paymentlifecycle.isoadapter;

import static com.sepanexus.modules.paymentlifecycle.isoadapter.MappingErrorCode.INVALID_FIELD_FORMAT;
import static com.sepanexus.modules.paymentlifecycle.isoadapter.MappingErrorCode.MAPPING_FAILED;
import static com.sepanexus.modules.paymentlifecycle.isoadapter.MappingErrorCode.MISSING_REQUIRED_ELEMENT;
import static com.sepanexus.modules.paymentlifecycle.isoadapter.MappingErrorCode.UNSUPPORTED_MESSAGE_TYPE;
import static com.sepanexus.modules.paymentlifecycle.isoadapter.MappingErrorCode.UNSUPPORTED_MESSAGE_VERSION;
import static com.sepanexus.modules.paymentlifecycle.isoadapter.MappingErrorCode.UNSUPPORTED_TRANSACTION_COUNT;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Maps a single-transaction pain.001.001.09 {@code CstmrCdtTrfInitn} document to a
 * {@link CanonicalPaymentCommand}. Pinned to {@code pain.001.001.09} — the current ISO 20022 SCT
 * release (UETR-capable) — pragmatically, matching how this codebase already resolves an
 * unpinned blueprint detail (Ed25519 for Story 31.2): the blueprint names {@code pain.001} but
 * does not pin an exact SRU/version, so this is one {@code [OPEN-QUESTION]} resolved by picking a
 * concrete, real, current standard rather than inventing a synthetic one. Covers only the REST
 * single-payment channel (sepa-nexus-message-flow-and-data-blueprint.md §2.1 channel matrix:
 * {@code POST /api/v1/iso/pain001} is the single-payment REST channel; the multi-instruction batch
 * file rail is a separate channel, §2.3/EPIC-73, out of scope here) — exactly one {@code PmtInf}
 * block and exactly one {@code CdtTrfTxInf} inside it are required; anything else is a controlled
 * {@code UNSUPPORTED_TRANSACTION_COUNT} rejection, never a silent {@code .getFirst()}.
 */
@Component
public class Pain001CanonicalMapper implements CanonicalMapper {

    static final String SUPPORTED_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09";
    /** Exact version accepted by this channel; every persisted {@code pain.001} row passed this parser. */
    static final String MESSAGE_VERSION = "pain.001.001.09";
    private static final String PAIN001_NAMESPACE_PREFIX = "urn:iso:std:iso:20022:tech:xsd:pain.001.001.";

    @Override
    public CanonicalMappingResult map(Document document) {
        try {
            return doMap(document);
        } catch (RuntimeException exception) {
            return CanonicalMappingResult.failure(MAPPING_FAILED, null, exception.getClass().getSimpleName());
        }
    }

    private CanonicalMappingResult doMap(Document document) {
        Element root = document.getDocumentElement();
        String namespaceUri = root == null ? null : root.getNamespaceURI();
        if (root == null || !"Document".equals(root.getLocalName())
                || namespaceUri == null || !namespaceUri.startsWith(PAIN001_NAMESPACE_PREFIX)) {
            return CanonicalMappingResult.failure(UNSUPPORTED_MESSAGE_TYPE, "Document",
                    "root element is not a recognized pain.001 message");
        }
        if (!SUPPORTED_NAMESPACE.equals(namespaceUri)) {
            return CanonicalMappingResult.failure(UNSUPPORTED_MESSAGE_VERSION, "Document",
                    "unsupported pain.001 version: " + namespaceUri);
        }

        Element cstmrCdtTrfInitn = firstChildElement(root, "CstmrCdtTrfInitn");
        if (cstmrCdtTrfInitn == null) {
            return CanonicalMappingResult.failure(UNSUPPORTED_MESSAGE_TYPE, "Document/CstmrCdtTrfInitn",
                    "missing CstmrCdtTrfInitn");
        }

        Element grpHdr = firstChildElement(cstmrCdtTrfInitn, "GrpHdr");
        String msgId = textOf(firstChildElement(grpHdr, "MsgId"));
        if (isBlank(msgId)) {
            return CanonicalMappingResult.failure(MISSING_REQUIRED_ELEMENT, "GrpHdr/MsgId", "MsgId is required");
        }

        List<Element> pmtInfs = childElements(cstmrCdtTrfInitn, "PmtInf");
        if (pmtInfs.size() != 1) {
            return CanonicalMappingResult.failure(UNSUPPORTED_TRANSACTION_COUNT, "CstmrCdtTrfInitn/PmtInf",
                    "expected exactly one PmtInf block, found " + pmtInfs.size());
        }
        Element pmtInf = pmtInfs.get(0);

        String pmtInfId = textOf(firstChildElement(pmtInf, "PmtInfId"));
        if (isBlank(pmtInfId)) {
            return CanonicalMappingResult.failure(MISSING_REQUIRED_ELEMENT, "PmtInf/PmtInfId", "PmtInfId is required");
        }

        String debtorIban = ibanOf(firstChildElement(pmtInf, "DbtrAcct"));
        if (isBlank(debtorIban)) {
            return CanonicalMappingResult.failure(MISSING_REQUIRED_ELEMENT, "PmtInf/DbtrAcct/Id/IBAN",
                    "debtor IBAN is required");
        }

        List<Element> transactions = childElements(pmtInf, "CdtTrfTxInf");
        if (transactions.size() != 1) {
            return CanonicalMappingResult.failure(UNSUPPORTED_TRANSACTION_COUNT, "PmtInf/CdtTrfTxInf",
                    "expected exactly one CdtTrfTxInf, found " + transactions.size());
        }
        Element transaction = transactions.get(0);

        Element pmtId = firstChildElement(transaction, "PmtId");
        String endToEndId = textOf(firstChildElement(pmtId, "EndToEndId"));
        if (isBlank(endToEndId)) {
            return CanonicalMappingResult.failure(MISSING_REQUIRED_ELEMENT, "CdtTrfTxInf/PmtId/EndToEndId",
                    "EndToEndId is required");
        }
        String instrId = blankToNull(textOf(firstChildElement(pmtId, "InstrId")));
        String uetr = blankToNull(textOf(firstChildElement(pmtId, "UETR")));

        Element instdAmt = firstChildElement(firstChildElement(transaction, "Amt"), "InstdAmt");
        if (instdAmt == null) {
            return CanonicalMappingResult.failure(MISSING_REQUIRED_ELEMENT, "CdtTrfTxInf/Amt/InstdAmt",
                    "InstdAmt is required");
        }
        String currency = instdAmt.getAttribute("Ccy");
        if (isBlank(currency)) {
            return CanonicalMappingResult.failure(MISSING_REQUIRED_ELEMENT, "Amt/InstdAmt/@Ccy", "Ccy is required");
        }
        if (!currency.matches("[A-Z]{3}")) {
            return CanonicalMappingResult.failure(INVALID_FIELD_FORMAT, "Amt/InstdAmt/@Ccy",
                    "currency must be 3 uppercase letters");
        }
        BigDecimal amount = parseAmount(textOf(instdAmt));
        if (amount == null) {
            return CanonicalMappingResult.failure(INVALID_FIELD_FORMAT, "Amt/InstdAmt", "amount is not a valid decimal");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return CanonicalMappingResult.failure(INVALID_FIELD_FORMAT, "Amt/InstdAmt", "amount must be positive");
        }

        String creditorIban = ibanOf(firstChildElement(transaction, "CdtrAcct"));
        if (isBlank(creditorIban)) {
            return CanonicalMappingResult.failure(MISSING_REQUIRED_ELEMENT, "CdtTrfTxInf/CdtrAcct/Id/IBAN",
                    "creditor IBAN is required");
        }

        return CanonicalMappingResult.success(new CanonicalPaymentCommand(msgId, pmtInfId, instrId, endToEndId, uetr,
                amount, currency, debtorIban, creditorIban));
    }

    private static BigDecimal parseAmount(String text) {
        if (isBlank(text)) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String ibanOf(Element acctElement) {
        return textOf(firstChildElement(firstChildElement(acctElement, "Id"), "IBAN"));
    }

    private static Element firstChildElement(Element parent, String localName) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && localName.equals(element.getLocalName())) {
                return element;
            }
        }
        return null;
    }

    private static List<Element> childElements(Element parent, String localName) {
        if (parent == null) {
            return List.of();
        }
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && localName.equals(element.getLocalName())) {
                result.add(element);
            }
        }
        return result;
    }

    private static String textOf(Element element) {
        return element == null ? null : element.getTextContent().trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }
}
