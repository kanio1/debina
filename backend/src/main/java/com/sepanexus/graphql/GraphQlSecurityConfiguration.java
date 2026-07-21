package com.sepanexus.graphql;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import java.util.List;
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Bounded read queries are a server invariant, independent of BFF/UI behaviour. */
@Configuration
class GraphQlSecurityConfiguration {
    static final int MAX_DEPTH = 10;
    static final int MAX_COMPLEXITY = 100;

    @Bean
    GraphQlSourceBuilderCustomizer boundedGraphQlQueries() {
        return builder -> builder.instrumentation(List.of(
                new MaxQueryDepthInstrumentation(MAX_DEPTH),
                new MaxQueryComplexityInstrumentation(MAX_COMPLEXITY)));
    }
}
