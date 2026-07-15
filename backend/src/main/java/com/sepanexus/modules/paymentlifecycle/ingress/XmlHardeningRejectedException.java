package com.sepanexus.modules.paymentlifecycle.ingress;

/**
 * Raised when {@link HardenedXmlFactory} rejects the payload (XXE, entity expansion, malformed
 * XML). The rejection reason comes from the underlying {@link org.xml.sax.SAXException} message,
 * which describes the parser feature that fired (e.g. "DOCTYPE is disallowed") — never document
 * content — so it is safe to record, but {@code PaymentProblemHandler} still does not echo it back
 * to the caller verbatim (defense in depth: a controlled code only).
 */
public class XmlHardeningRejectedException extends RuntimeException {

    /** Shared with the RFC 7807 mapping and {@code iso.iso_message_parse_errors} evidence — the
     * underlying {@link HardenedXmlFactory} does not discriminate XXE/entity-expansion/malformed-XML
     * beyond a single accept/reject verdict, so one code covers every hardened-parser rejection. */
    public static final String ERROR_CODE = "MALFORMED_XML";

    public XmlHardeningRejectedException(String reason) {
        super("XML hardening rejected the payload: " + reason);
    }
}
