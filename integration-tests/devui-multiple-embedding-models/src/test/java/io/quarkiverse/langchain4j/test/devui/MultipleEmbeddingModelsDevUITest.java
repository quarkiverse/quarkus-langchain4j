package io.quarkiverse.langchain4j.test.devui;

import java.util.Map;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.devui.tests.DevUIJsonRPCTest;
import io.quarkus.test.QuarkusDevModeTest;

/**
 * Test that when there are two embedding models in an app,
 * the Dev UI will deploy the EmbeddingStoreJsonRPCService pick the 'default' embedding model.
 */
public class MultipleEmbeddingModelsDevUITest extends DevUIJsonRPCTest {

    @RegisterExtension
    static final QuarkusDevModeTest config = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(Dummy.class)
                    .addAsResource(new StringAsset(
                            "quarkus.langchain4j.model1.embedding-model.provider=openai\n" +
                                    "quarkus.langchain4j.embedding-model.provider=" +
                                    "dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel\n"
                                    +
                                    "quarkus.langchain4j.openai.model1.api-key=WRONG\n" +
                                    "quarkus.langchain4j.openai.model1.base-url=http://blabla\n"),
                            "application.properties"));

    public MultipleEmbeddingModelsDevUITest() {
        super("io.quarkiverse.langchain4j.quarkus-langchain4j-core");
    }

    @Test
    public void test() throws Exception {
        // make sure the AllMiniLmL6V2QuantizedEmbeddingModel is chosen as the embedding model, not OpenAI
        // if it was OpenAI, the request would fail
        String id = executeJsonRPCMethod(String.class, "add", Map.of(
                "text", "Hello world",
                "metadata", "k1=v1,k2=v2"));
        Assertions.assertNotNull(id);
    }

}
