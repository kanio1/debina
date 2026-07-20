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

/**
 * ADR-N11's dedicated executor identity. The executor can only set transaction-local tenant
 * context and EXECUTE three module-owned functions; SQL grants provide no direct domain DML.
 */
@Configuration(proxyBeanMethods = false)
public class GrossInstantTransactionDataSourceConfiguration {

    @Bean
    @ConfigurationProperties("gross-instant.datasource")
    DataSourceProperties grossInstantDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("grossInstantDataSource")
    DataSource grossInstantDataSource(@Qualifier("grossInstantDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean("grossInstantJdbcTemplate")
    JdbcTemplate grossInstantJdbcTemplate(@Qualifier("grossInstantDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean("grossInstantTransactionManager")
    PlatformTransactionManager grossInstantTransactionManager(@Qualifier("grossInstantDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
