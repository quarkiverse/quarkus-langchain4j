package devui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * Tests for the EmbeddingStoreJsonRPCService class that is used as the backend
 * called by the Dev UI.
 */
public class Langchain4jDevUIJsonRpcTest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .withEmptyApplication();

    public Langchain4jDevUIJsonRpcTest() {
        super("io.quarkiverse.langchain4j.quarkus-langchain4j-core");
    }

    @Test
    public void testAddAndSearchEmbedding() throws Exception {
        String id = executeJsonRPCMethod(String.class, "add", Map.of(
                "text", "Hello world",
                "metadata", "k1=v1,k2=v2"));
        assertNotNull(id);
        JsonNode relevantEmbeddings = executeJsonRPCMethod("findRelevant", Map.of(
                "text", "Hello world",
                "limit", "10"));
        assertEquals(1, relevantEmbeddings.size());
        assertEquals(id, relevantEmbeddings.get(0).get("embeddingId").asText());
        assertEquals("Hello world", relevantEmbeddings.get(0).get("embedded").asText());
        assertEquals(2, relevantEmbeddings.get(0).get("metadata").size());
    }

}
