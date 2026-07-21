package com.sepanexus.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Wave 9's first structural boundary: GraphQL must be an explicit, source-controlled schema,
 * never an accidental controller convention.
 */
class GraphQLReadOnlyStructureTest {

    @Test
    void readOnlySchemaIsPresent() {
        assertThat(new ClassPathResource("graphql/schema.graphqls").exists())
                .as("the read-only GraphQL boundary must be explicitly defined")
                .isTrue();
    }

    @Test
    void schemaHasQueryAndNoMutationOrSubscriptionRoots() throws Exception {
        TypeDefinitionRegistry schema = new SchemaParser().parse(
                new ClassPathResource("graphql/schema.graphqls").getInputStream());

        assertThat(schema.getType("Query")).isPresent();
        assertThat(schema.getType("Mutation")).isEmpty();
        assertThat(schema.getType("Subscription")).isEmpty();
    }
}
