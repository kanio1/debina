package com.sepanexus.modules.paymentlifecycle.isoadapter;

import org.w3c.dom.Document;

/**
 * ISO 20022 XML → canonical payment command (sepa-nexus-message-flow-and-data-blueprint.md §3.1:
 * a {@code CanonicalMapper} port, "implemented by iso-adapter"; §3.8: {@code iso-adapter} owns
 * parsing/versioning/mapping and makes no business decision). Takes an already-hardened, already-
 * parsed {@link Document} ({@link com.sepanexus.modules.paymentlifecycle.ingress.HardenedXmlFactory}
 * runs first — XXE/entity-expansion is a parsing concern, not a mapping concern). Pure: no
 * database access, no repository, no HTTP, no clock, no random IDs, no logging of document
 * content — same input always produces the same output.
 */
public interface CanonicalMapper {

    CanonicalMappingResult map(Document document);
}
