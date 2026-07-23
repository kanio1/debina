package com.sepanexus;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("fast")
class TestClassificationCompletenessTest {

    private static final Pattern SUREFIRE_TEST_SOURCE =
            Pattern.compile("(Test.*|.*Test|.*Tests|.*TestCase)\\.java");
    private static final String FAST_TAG = "@org.junit.jupiter.api.Tag(\"fast\")";
    private static final String TESTCONTAINERS_TAG =
            "@org.junit.jupiter.api.Tag(\"testcontainers\")";

    @Test
    void everySurefireTestClassHasExactlyOneDurableClassification() throws IOException {
        Path testSources = Path.of("src", "test", "java");
        List<String> violations = new ArrayList<>();

        try (var paths = Files.walk(testSources)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> SUREFIRE_TEST_SOURCE.matcher(path.getFileName().toString()).matches())
                    .sorted()
                    .forEach(path -> verifyClassification(testSources, path, violations));
        }

        assertThat(violations)
                .as("Every Surefire test class must declare exactly one class-level fast/testcontainers tag")
                .isEmpty();
    }

    private static void verifyClassification(
            Path testSources, Path sourcePath, List<String> violations) {
        try {
            String source = Files.readString(sourcePath);
            int fastCount = occurrences(source, FAST_TAG);
            int testcontainersCount = occurrences(source, TESTCONTAINERS_TAG);
            if (fastCount + testcontainersCount != 1) {
                violations.add(testSources.relativize(sourcePath) + " fast=" + fastCount
                        + " testcontainers=" + testcontainersCount);
                return;
            }

            boolean isClassificationRegression =
                    sourcePath.getFileName().toString().equals("TestClassificationCompletenessTest.java");
            boolean requiresTestcontainers = !isClassificationRegression
                    && (source.contains("org.testcontainers.")
                    || source.matches("(?s).*extends\\s+(KafkaIntegrationSupport"
                            + "|IsoKafkaIntegrationSupport|TenantGucIntegrationTest)\\b.*")
                    || source.contains("KeycloakRealmTestSupport."));
            if (requiresTestcontainers != (testcontainersCount == 1)) {
                violations.add(testSources.relativize(sourcePath)
                        + " dependency classification mismatch: expected="
                        + (requiresTestcontainers ? "testcontainers" : "fast"));
            }
        } catch (IOException exception) {
            violations.add(testSources.relativize(sourcePath) + " unreadable: "
                    + exception.getMessage());
        }
    }

    private static int occurrences(String source, String marker) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(marker, offset)) >= 0) {
            count++;
            offset += marker.length();
        }
        return count;
    }
}
