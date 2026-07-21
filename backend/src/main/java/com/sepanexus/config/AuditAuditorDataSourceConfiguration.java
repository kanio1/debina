package com.sepanexus.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/** Dedicated read-only identity for RLS-enforced auditor cross-tenant queries. */
@Configuration(proxyBeanMethods = false)
public class AuditAuditorDataSourceConfiguration {
    @Bean
    @ConfigurationProperties("audit-auditor.datasource")
    DataSourceProperties auditAuditorDataSourceProperties() { return new DataSourceProperties(); }

    @Bean("auditAuditorDataSource")
    DataSource auditAuditorDataSource(@Qualifier("auditAuditorDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean("auditAuditorJdbcTemplate")
    JdbcTemplate auditAuditorJdbcTemplate(@Qualifier("auditAuditorDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean("auditAuditorTransactionManager")
    PlatformTransactionManager auditAuditorTransactionManager(@Qualifier("auditAuditorDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
