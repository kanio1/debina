package com.sepanexus.modules.paymentlifecycle.isoadapter;

import static com.sepanexus.modules.paymentlifecycle.isoadapter.MappingErrorCode.MAPPING_FAILED;
import static com.sepanexus.modules.paymentlifecycle.isoadapter.MappingErrorCode.MISSING_REQUIRED_ELEMENT;
import static com.sepanexus.modules.paymentlifecycle.isoadapter.MappingErrorCode.UNSUPPORTED_MESSAGE_TYPE;
import static com.sepanexus.modules.paymentlifecycle.isoadapter.MappingErrorCode.UNSUPPORTED_MESSAGE_VERSION;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * EPIC-27 Story 27.1: extracts {@code OrgnlMsgId}/{@code OrgnlEndToEndId} from a pacs.002
 * (FIToFIPaymentStatusReport) document. Pinned to {@code pacs.002.001.10} — a real, current
 * ISO 20022 FI-to-FI Payment Status Report release commonly paired with {@code pacs.008.001.08}/
 * {@code pain.001.001.09} — resolving the same kind of unpinned-version gap
 * {@link Pain001CanonicalMapper} already resolved for pain.001, per the same reasoning (a real,
 * current standard, not a synthetic one).
 *
 * <p>Pure extraction only: no correlation (which payment/message these identifiers refer to —
 * Story 27.2), no DB write, no business decision. {@code iso-adapter} correlates,
 * {@code payment-lifecycle} transitions (root {@code AGENTS.md} binding rule) — this class does
 * neither; it only reads what a pacs.002 document says.
 */
@Component
public class Pacs002IdentifierExtractor {

    static final String SUPPORTED_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:pacs.002.001.10";
    private static final String PACS002_NAMESPACE_PREFIX = "urn:iso:std:iso:20022:tech:xsd:pacs.002.001.";

    public Pacs002IdentifierExtractionResult extract(Document document) {
        try {
            return doExtract(document);
        } catch (RuntimeException exception) {
            return Pacs002IdentifierExtractionResult.failure(MAPPING_FAILED, null, exception.getClass().getSimpleName());
        }
    }

    private Pacs002IdentifierExtractionResult doExtract(Document document) {
        Element root = document.getDocumentElement();
        String namespaceUri = root == null ? null : root.getNamespaceURI();
        if (root == null || !"Document".equals(root.getLocalName())
                || namespaceUri == null || !namespaceUri.startsWith(PACS002_NAMESPACE_PREFIX)) {
            return Pacs002IdentifierExtractionResult.failure(UNSUPPORTED_MESSAGE_TYPE, "Document",
                    "root element is not a recognized pacs.002 message");
        }
        if (!SUPPORTED_NAMESPACE.equals(namespaceUri)) {
            return Pacs002IdentifierExtractionResult.failure(UNSUPPORTED_MESSAGE_VERSION, "Document",
                    "unsupported pacs.002 version: " + namespaceUri);
        }

        Element fiToFiPmtStsRpt = firstChildElement(root, "FIToFIPmtStsRpt");
        if (fiToFiPmtStsRpt == null) {
            return Pacs002IdentifierExtractionResult.failure(UNSUPPORTED_MESSAGE_TYPE, "Document/FIToFIPmtStsRpt",
                    "missing FIToFIPmtStsRpt");
        }

        Element orgnlGrpInfAndSts = firstChildElement(fiToFiPmtStsRpt, "OrgnlGrpInfAndSts");
        String orgnlMsgId = textOf(firstChildElement(orgnlGrpInfAndSts, "OrgnlMsgId"));
        if (isBlank(orgnlMsgId)) {
            return Pacs002IdentifierExtractionResult.failure(MISSING_REQUIRED_ELEMENT,
                    "OrgnlGrpInfAndSts/OrgnlMsgId", "OrgnlMsgId is required");
        }

        List<Element> txInfAndStsEntries = childElements(fiToFiPmtStsRpt, "TxInfAndSts");
        if (txInfAndStsEntries.isEmpty()) {
            return Pacs002IdentifierExtractionResult.failure(MISSING_REQUIRED_ELEMENT,
                    "FIToFIPmtStsRpt/TxInfAndSts", "expected at least one TxInfAndSts");
        }

        List<Pacs002OriginalIdentifiers> identifiers = new ArrayList<>();
        for (int i = 0; i < txInfAndStsEntries.size(); i++) {
            String orgnlEndToEndId = textOf(firstChildElement(txInfAndStsEntries.get(i), "OrgnlEndToEndId"));
            if (isBlank(orgnlEndToEndId)) {
                return Pacs002IdentifierExtractionResult.failure(MISSING_REQUIRED_ELEMENT,
                        "TxInfAndSts[" + i + "]/OrgnlEndToEndId", "OrgnlEndToEndId is required");
            }
            identifiers.add(new Pacs002OriginalIdentifiers(orgnlMsgId, orgnlEndToEndId));
        }

        return Pacs002IdentifierExtractionResult.success(List.copyOf(identifiers));
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
}
