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

/** Dedicated scheduler identity: it can execute only payment.expire_due_approvals. */
@Configuration(proxyBeanMethods = false)
public class ApprovalExpiryDataSourceConfiguration {
    @Bean
    @ConfigurationProperties("approval-expiry.datasource")
    DataSourceProperties approvalExpiryDataSourceProperties() { return new DataSourceProperties(); }

    @Bean("approvalExpiryDataSource")
    DataSource approvalExpiryDataSource(@Qualifier("approvalExpiryDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean("approvalExpiryJdbcTemplate")
    JdbcTemplate approvalExpiryJdbcTemplate(@Qualifier("approvalExpiryDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean("approvalExpiryTransactionManager")
    PlatformTransactionManager approvalExpiryTransactionManager(@Qualifier("approvalExpiryDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
