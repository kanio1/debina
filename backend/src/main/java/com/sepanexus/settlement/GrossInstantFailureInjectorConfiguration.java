package com.sepanexus.settlement;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class GrossInstantFailureInjectorConfiguration {

    @Bean
    GrossInstantFailureInjector grossInstantFailureInjector() {
        return ignored -> { };
    }
}
