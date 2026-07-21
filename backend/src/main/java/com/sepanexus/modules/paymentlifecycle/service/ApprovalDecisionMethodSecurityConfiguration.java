package com.sepanexus.modules.paymentlifecycle.service;

import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;

/** Payment-lifecycle owns the command's object authorization wiring as well as its rule. */
@Configuration(proxyBeanMethods = false)
class ApprovalDecisionMethodSecurityConfiguration {
    @Bean
    @Order(100)
    AuthorizationManagerBeforeMethodInterceptor paymentApprovalObjectAuthorization(
            PaymentApprovalAuthorizationManager manager) {
        StaticMethodMatcherPointcut pointcut = new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(java.lang.reflect.Method method, Class<?> targetClass) {
                return method.getName().equals("decide") && ApprovalDecisionService.class.isAssignableFrom(targetClass);
            }
        };
        return new AuthorizationManagerBeforeMethodInterceptor(pointcut, manager);
    }
}
