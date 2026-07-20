package com.sepanexus.config;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Technical relay identity required by ADR-N5: it may claim and mark-published rows in module
 * outboxes, but is deliberately not a domain writer and is never the primary application
 * datasource.
 */
@Configuration(proxyBeanMethods = false)
public class OutboxRelayDataSourceConfiguration {

    /** Retains the existing domain-writer identity when the relay adds a second datasource. */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    DataSourceProperties domainDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("dataSource")
    @Primary
    DataSource domainDataSource(
            @Qualifier("domainDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean("jdbcTemplate")
    @Primary
    JdbcTemplate domainJdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /** The relay manager must not suppress the primary JPA transaction boundary used by domain code. */
    @Bean("transactionManager")
    @Primary
    PlatformTransactionManager domainTransactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    @ConfigurationProperties("outbox.datasource")
    DataSourceProperties outboxRelayDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("outboxRelayDataSource")
    DataSource outboxRelayDataSource(
            @Qualifier("outboxRelayDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean("outboxRelayJdbcTemplate")
    JdbcTemplate outboxRelayJdbcTemplate(@Qualifier("outboxRelayDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean("outboxRelayTransactionManager")
    PlatformTransactionManager outboxRelayTransactionManager(
            @Qualifier("outboxRelayDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /** Spring Kafka's default listener logs record values on failure; payment payloads must not leak. */
    @Bean
    ProducerListener<Object, Object> kafkaProducerListener() {
        return new ProducerListener<>() {
            @Override
            public void onError(org.apache.kafka.clients.producer.ProducerRecord<Object, Object> record,
                    org.apache.kafka.clients.producer.RecordMetadata metadata, Exception exception) {
                // Relay operational state records the category; this listener intentionally retains no payload.
            }
        };
    }
}
