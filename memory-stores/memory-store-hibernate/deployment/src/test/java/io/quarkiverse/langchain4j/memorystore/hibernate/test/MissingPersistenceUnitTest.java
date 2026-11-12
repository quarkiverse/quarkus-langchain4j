package io.quarkiverse.langchain4j.memorystore.hibernate.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class MissingPersistenceUnitTest {
    @RegisterExtension
    static final QuarkusUnitTest app = new QuarkusUnitTest()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:missing")
            .overrideConfigKey("quarkus.langchain4j.memorystore.hibernate.persistence-unit-name", "missing")
            .assertException(failure -> assertThat(failure).hasStackTraceContaining(
                    "No Hibernate ORM persistence unit 'missing' for quarkus.langchain4j.memorystore.hibernate.persistence-unit-name"));

    @Test
    void rejectsUnknownPersistenceUnit() {
    }
}
