package com.sepanexus.modules.paymentlifecycle.ingress;

import java.io.IOException;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

/**
 * XML hardening (sepa-nexus-message-flow-and-data-blueprint.md §3.8, EPIC-19 Story 19.3): every
 * inbound XML channel parses through this factory — no DTDs, no external entities, no entity
 * expansion — before any ISO 20022 mapping runs. Rejects XXE (external entity disclosure) and
 * entity-expansion ("billion laughs") payloads at the parser level, not by pattern-matching input.
 */
@Component
public class HardenedXmlFactory {

    public DocumentBuilder newHardenedDocumentBuilder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder();
        } catch (ParserConfigurationException exception) {
            throw new IllegalStateException("Could not configure hardened XML parser", exception);
        }
    }

    public HardenedParseResult parse(byte[] xmlPayload) {
        try {
            newHardenedDocumentBuilder().parse(new java.io.ByteArrayInputStream(xmlPayload));
            return HardenedParseResult.ok();
        } catch (SAXException exception) {
            return HardenedParseResult.rejected(exception.getMessage());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read XML payload", exception);
        }
    }

    public record HardenedParseResult(boolean accepted, String rejectionReason) {
        static HardenedParseResult ok() {
            return new HardenedParseResult(true, null);
        }

        static HardenedParseResult rejected(String reason) {
            return new HardenedParseResult(false, reason);
        }
    }
}
