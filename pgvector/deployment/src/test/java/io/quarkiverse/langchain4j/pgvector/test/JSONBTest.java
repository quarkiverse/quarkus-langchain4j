package io.quarkiverse.langchain4j.pgvector.test;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(JSONBTest.TestProfile.class)
public class JSONBTest extends LangChain4jPgvectorBaseTest {

    public static class TestProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.langchain4j.pgvector.metadata.type", "JSONB",
                    "quarkus.langchain4j.pgvector.metadata.definition", "metadata JSONB NULL",
                    "quarkus.langchain4j.pgvector.metadata.indexes", "metadata");
        }
    }

}
