package com.sepanexus.modules.paymentlifecycle.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * EPIC-15 Story 15.2: every topic this module actually produces onto must be a real,
 * unmodified entry of the §3.7 v2 catalog (ADR-N8) — generated, not hand-authored, into
 * {@code infra/asyncapi/asyncapi.yaml} by {@code infra/asyncapi/generate.py}. A topic
 * name or declared producer-owner drifting from that file is exactly the
 * "[EVENT-RISK] documentation defect" the source blueprint calls out.
 */
@org.junit.jupiter.api.Tag("fast")
class KafkaTopicContractTest {

    private static final Path ASYNCAPI_FILE = Path.of("../infra/asyncapi/asyncapi.yaml");
    private static final Pattern ADDRESS = Pattern.compile("^\\s{4}address:\\s*(\\S+)\\s*$");
    private static final Pattern PRODUCER_OWNER = Pattern.compile("^\\s{4}x-producer-owner:\\s*(\\S+)\\s*$");

    @Test
    void controlledPaymentFactsResolveOnlyToCatalogTopicsWithTheirDeclaredOwners() throws IOException {
        Map<String, String> producerOwnerByTopic = parseTopicCatalog();

        assertThat(producerOwnerByTopic).containsEntry(PaymentLifecycleTopicConfig.RECEIVED_TOPIC, "ingress");
        assertThat(producerOwnerByTopic).containsEntry(PaymentLifecycleTopicConfig.VALIDATED_TOPIC, "payment-lifecycle");
    }

    private static Map<String, String> parseTopicCatalog() throws IOException {
        Map<String, String> producerOwnerByTopic = new LinkedHashMap<>();
        String address = null;
        for (String line : Files.readAllLines(ASYNCAPI_FILE)) {
            Matcher addressMatcher = ADDRESS.matcher(line);
            if (addressMatcher.matches()) {
                address = addressMatcher.group(1);
                continue;
            }
            Matcher ownerMatcher = PRODUCER_OWNER.matcher(line);
            if (ownerMatcher.matches() && address != null) {
                producerOwnerByTopic.put(address, ownerMatcher.group(1));
                address = null;
            }
        }
        return producerOwnerByTopic;
    }
}
