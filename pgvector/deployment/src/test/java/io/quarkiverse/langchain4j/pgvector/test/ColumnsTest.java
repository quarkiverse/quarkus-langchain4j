package io.quarkiverse.langchain4j.pgvector.test;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(ColumnsTest.TestProfile.class)
class ColumnsTest extends LangChain4jPgvectorBaseTest {

    public static class TestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.langchain4j.pgvector.metadata.type", "COLUMNS",
                    "quarkus.langchain4j.pgvector.metadata.definition", "key text NULL, name text NULL, " +
                            "age float NULL, city varchar null, country varchar null",
                    "quarkus.langchain4j.pgvector.metadata.indexes", "key, name, age");
        }
    }

    @Test
    // do not test parent method to avoid defining all the metadata fields
    void should_add_embedding_with_segment_with_metadata() {
    }

}
