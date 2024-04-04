package io.quarkiverse.langchain4j.pgvector.test;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(JSONBMultiIndexTest.TestProfile.class)
public class JSONBMultiIndexTest extends LangChain4jPgVectorBaseTest {

    public static class TestProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.langchain4j.pgvector.metadata.type", "JSONB",
                    "quarkus.langchain4j.pgvector.metadata.definition", "metadata_b JSONB NULL",
                    "quarkus.langchain4j.pgvector.metadata.indexes",
                    "(metadata_b->'key'), (metadata_b->'name'), (metadata_b->'age')",
                    "quarkus.langchain4j.pgvector.metadata.index-type", "GIN");
        }
    }

}
