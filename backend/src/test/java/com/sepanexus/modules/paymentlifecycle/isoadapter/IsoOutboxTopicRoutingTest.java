package com.sepanexus.modules.paymentlifecycle.isoadapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * EPIC-27 Story 27.4: proves {@link IsoOutboxDispatcher}'s real routing behavior against a real
 * Kafka broker (see {@code sepa-nexus-database-testing} skill — broker semantics genuinely matter
 * here: which topic a message actually lands on, and whether {@code published_at} is only ever
 * set after a real broker acknowledgement). {@link IsoOutboxDispatcher} is called explicitly in
 * every test, never relying on the {@code @Scheduled} trigger firing on its own.
 */
class IsoOutboxTopicRoutingTest extends IsoKafkaIntegrationSupport {

    @Autowired
    private IsoOutboxDispatcher dispatcher;

    @Test
    void correlatedEventIsPublishedOnlyToTheCorrelatedTopic() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        insertOutboxRow(eventId, paymentId, IsoOutboxEventType.MESSAGE_CORRELATED.eventType(),
                "{\"eventId\":\"%s\",\"paymentId\":\"%s\"}".formatted(eventId, paymentId));

        dispatcher.dispatch();
        eventually(() -> assertThat(publishedAt(eventId)).isNotNull());

        assertThat(consumeOne(IsoOutboxEventType.MESSAGE_CORRELATED.topic(), eventId)).isPresent();
        assertThat(consumeOne(IsoOutboxEventType.MESSAGE_ORPHANED.topic(), eventId)).isEmpty();
    }

    @Test
    void orphanedEventIsPublishedOnlyToTheOrphanedTopicKeyedByIsoMessageId() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID isoMessageId = UUID.randomUUID();
        insertOutboxRow(eventId, isoMessageId, IsoOutboxEventType.MESSAGE_ORPHANED.eventType(),
                "{\"eventId\":\"%s\",\"isoMessageId\":\"%s\"}".formatted(eventId, isoMessageId));

        dispatcher.dispatch();
        eventually(() -> assertThat(publishedAt(eventId)).isNotNull());

        var record = consumeOne(IsoOutboxEventType.MESSAGE_ORPHANED.topic(), eventId);
        assertThat(record).isPresent();
        assertThat(record.get().key()).isEqualTo(isoMessageId.toString());
        assertThat(consumeOne(IsoOutboxEventType.MESSAGE_CORRELATED.topic(), eventId)).isEmpty();
    }

    @Test
    void unknownEventTypeIsNeverPublishedAndStaysUnpublished() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        insertOutboxRow(eventId, aggregateId, "iso.message.something-else.v1",
                "{\"eventId\":\"%s\"}".formatted(eventId));

        dispatcher.dispatch();
        Thread.sleep(1000);

        assertThat(publishedAt(eventId)).isNull();
        assertThat(consumeOne(IsoOutboxEventType.MESSAGE_CORRELATED.topic(), eventId)).isEmpty();
        assertThat(consumeOne(IsoOutboxEventType.MESSAGE_ORPHANED.topic(), eventId)).isEmpty();
    }

    /**
     * Deliberately does NOT use the shared {@code @SpringBootTest} context's own {@code
     * @Scheduled} {@link IsoOutboxDispatcher} bean (wired to a healthy broker) — that bean polls
     * every 2s and would race this test's manually-inserted row to the real, reachable Kafka
     * container, publishing it before this test's own broken-broker dispatcher gets a chance and
     * making the "stays unpublished" assertion false by construction. A dedicated, isolated
     * Postgres Testcontainer (no Spring context, so no live scheduled bean anywhere near it) is
     * the minimal isolation this failure scenario needs — see Story 27.4's own guidance on not
     * depending on the scheduler's timing and not changing production dispatcher semantics.
     */
    @Test
    void brokerPublicationFailureLeavesTheEventUnpublishedForRetry() throws Exception {
        PostgreSQLContainer<?> isolatedPostgres = new PostgreSQLContainer<>("postgres:18")
                .withDatabaseName("sepa_nexus").withUsername("test_admin").withPassword("test_admin");
        isolatedPostgres.start();
        DefaultKafkaProducerFactory<String, String> unreachableBrokerProducerFactory = unreachableBrokerProducerFactory();
        try {
            migrate(isolatedPostgres);

            UUID eventId = UUID.randomUUID();
            UUID aggregateId = UUID.randomUUID();
            insertOutboxRow(isolatedPostgres, eventId, aggregateId, IsoOutboxEventType.MESSAGE_ORPHANED.eventType(),
                    "{\"eventId\":\"%s\"}".formatted(eventId));

            JdbcTemplate jdbcTemplate = new JdbcTemplate(appDataSource(isolatedPostgres));
            IsoOutboxDispatcher unreachableBrokerDispatcher = new IsoOutboxDispatcher(jdbcTemplate,
                    new KafkaTemplate<>(unreachableBrokerProducerFactory), Instant::now);

            unreachableBrokerDispatcher.dispatch();

            assertThat(publishedAt(isolatedPostgres, eventId)).isNull();
        } finally {
            unreachableBrokerProducerFactory.destroy();
            isolatedPostgres.stop();
        }
    }

    // -- helpers -------------------------------------------------------------------------------

    private static DriverManagerDataSource appDataSource() {
        return appDataSource(POSTGRES);
    }

    private static DriverManagerDataSource appDataSource(PostgreSQLContainer<?> postgres) {
        return new DriverManagerDataSource(postgres.getJdbcUrl(), "sepa_app", "dev-only-app");
    }

    private void insertOutboxRow(UUID id, UUID aggregateId, String eventType, String payloadJson) {
        insertOutboxRow(POSTGRES, id, aggregateId, eventType, payloadJson);
    }

    private void insertOutboxRow(PostgreSQLContainer<?> postgres, UUID id, UUID aggregateId, String eventType,
            String payloadJson) {
        try (Connection connection = adminConnection(postgres); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO iso.outbox_events (id, aggregate_id, event_type, payload, correlation_id, created_at)
                VALUES (?, ?, ?, CAST(? AS jsonb), ?, now())
                """)) {
            statement.setObject(1, id);
            statement.setObject(2, aggregateId);
            statement.setString(3, eventType);
            statement.setString(4, payloadJson);
            statement.setObject(5, UUID.randomUUID());
            statement.executeUpdate();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Object publishedAt(UUID eventId) {
        return publishedAt(POSTGRES, eventId);
    }

    private static Object publishedAt(PostgreSQLContainer<?> postgres, UUID eventId) {
        try (Connection connection = adminConnection(postgres); PreparedStatement statement = connection.prepareStatement(
                "SELECT published_at FROM iso.outbox_events WHERE id = ?")) {
            statement.setObject(1, eventId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getObject(1);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Connection adminConnection(PostgreSQLContainer<?> postgres) throws SQLException {
        return java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), "test_admin", "test_admin");
    }

    private static void migrate(PostgreSQLContainer<?> postgres) throws SQLException {
        try (Connection connection = adminConnection(postgres);
                java.sql.Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE sepa_migration LOGIN SUPERUSER PASSWORD 'dev-only-migration'");
        }
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), "sepa_migration", "dev-only-migration")
                .locations("filesystem:src/main/resources/db/migration")
                .load()
                .migrate();
    }

    private java.util.Optional<ConsumedRecord> consumeOne(String topic, UUID eventId) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "iso-outbox-routing-verify-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + 3_000_000_000L;
            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
                for (var record : records.records(topic)) {
                    if (record.value() != null && record.value().contains(eventId.toString())) {
                        return java.util.Optional.of(new ConsumedRecord(record.key(), record.value()));
                    }
                }
            }
            return java.util.Optional.empty();
        }
    }

    /**
     * A producer factory pointed at an address nothing listens on — simulates a broker publication
     * failure. Returned un-wrapped (not as a {@link KafkaTemplate}) so the caller can {@code
     * destroy()} it after use — otherwise its background reconnect thread would keep retrying and
     * logging for the remainder of the JVM's life.
     */
    private static DefaultKafkaProducerFactory<String, String> unreachableBrokerProducerFactory() {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:1");
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "2000");
        producerProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "2000");
        producerProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "3000");
        return new DefaultKafkaProducerFactory<>(producerProps);
    }

    private record ConsumedRecord(String key, String value) {
    }
}
