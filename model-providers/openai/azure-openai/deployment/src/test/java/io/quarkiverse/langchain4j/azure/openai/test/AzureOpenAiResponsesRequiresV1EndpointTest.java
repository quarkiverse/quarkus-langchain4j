package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

/**
 * The Responses API only exists on the v1 endpoints, so the application refuses to start when the classic
 * deployment-based endpoint style is kept.
 */
public class AzureOpenAiResponsesRequiresV1EndpointTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.chat-model.provider", "azure-openai")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.chat-model.mode", "responses")
            .assertException(t -> assertThat(t)
                    .hasStackTraceContaining("quarkus.langchain4j.azure-openai.chat-model.mode=responses requires"
                            + " quarkus.langchain4j.azure-openai.endpoint-style=v1"));

    @Test
    void applicationDoesNotStart() {
        fail("The application should not have started");
    }
}
