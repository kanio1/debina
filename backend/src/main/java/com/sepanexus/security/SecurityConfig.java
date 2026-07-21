package com.sepanexus.security;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import com.sepanexus.modules.paymentlifecycle.service.ApprovalDecisionService;
import com.sepanexus.modules.paymentlifecycle.service.PaymentApprovalAuthorizationManager;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    Converter<org.springframework.security.oauth2.jwt.Jwt,
            org.springframework.security.authentication.AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            var normalized = normalizeOrganizationClaims(jwt);
            return new JwtAuthenticationToken(normalized, realmRoles(normalized));
        };
    }

    @Bean
    AuthorizationManager<MethodInvocation> paymentLifecycleAuthorizationManager() {
        // Deliberately deny if this future Iteration 1 policy is accidentally wired early.
        return (authentication, invocation) -> new AuthorizationDecision(false);
    }

    @Bean
    AuthorizationManagerBeforeMethodInterceptor paymentApprovalObjectAuthorization(
            PaymentApprovalAuthorizationManager manager) {
        StaticMethodMatcherPointcut pointcut = new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(java.lang.reflect.Method method, Class<?> targetClass) {
                return method.getName().equals("decide") && ApprovalDecisionService.class.isAssignableFrom(targetClass);
            }
        };
        AuthorizationManagerBeforeMethodInterceptor interceptor = new AuthorizationManagerBeforeMethodInterceptor(pointcut, manager);
        interceptor.setOrder(100);
        return interceptor;
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> realmRoles(org.springframework.security.oauth2.jwt.Jwt jwt) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> access)) {
            return List.of();
        }
        Object roles = access.get("roles");
        if (!(roles instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    /**
     * Keycloak's supported Organization Membership mapper emits the Organization id and its
     * attributes below {@code organization.<alias>}. The frozen realm model keeps the stable
     * tenant id on that Organization, while existing module entry points consume the normalized
     * {@code tenant_id}/{@code organization_id} claims. Only the one-Organization MVP shape is
     * flattened; an absent or ambiguous Organization claim deliberately produces no tenant claim.
     */
    private org.springframework.security.oauth2.jwt.Jwt normalizeOrganizationClaims(
            org.springframework.security.oauth2.jwt.Jwt jwt) {
        if (jwt.hasClaim("tenant_id")) return jwt;
        Object organizations = jwt.getClaim("organization");
        if (!(organizations instanceof Map<?, ?> values) || values.size() != 1) return jwt;
        Object organization = values.values().iterator().next();
        if (!(organization instanceof Map<?, ?> details)) return jwt;

        String tenantId = firstString(details.get("tenant_id"));
        if (tenantId == null) return jwt;

        Map<String, Object> claims = new LinkedHashMap<>(jwt.getClaims());
        claims.put("tenant_id", tenantId);
        String organizationId = firstString(details.get("id"));
        if (organizationId != null) claims.put("organization_id", organizationId);
        return new org.springframework.security.oauth2.jwt.Jwt(
                jwt.getTokenValue(), jwt.getIssuedAt(), jwt.getExpiresAt(), jwt.getHeaders(), claims);
    }

    private static String firstString(Object value) {
        if (value instanceof String text && !text.isBlank()) return text;
        if (value instanceof Collection<?> values) {
            return values.stream().filter(String.class::isInstance).map(String.class::cast)
                    .filter(text -> !text.isBlank()).findFirst().orElse(null);
        }
        return null;
    }
}
