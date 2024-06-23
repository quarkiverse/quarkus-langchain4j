package io.quarkiverse.langchain4j.bam.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.bam.runtime.config.LangChain4jBamConfig;
import io.quarkus.test.QuarkusUnitTest;

public class ResponseSchemaOffTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.base-url", WireMockUtil.URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.api-key", WireMockUtil.API_KEY)
            .overrideConfigKey("quarkus.langchain4j.response-schema", "false")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @RegisterAiService
    @Singleton
    interface OnMethodAIService {
        String poem(@UserMessage String message, @V("topic") String topic);
    }

    @Inject
    OnMethodAIService onMethodAIService;

    @Inject
    LangChain4jBamConfig langchain4jBamConfig;

    @Test
    void on_method_ai_service() throws Exception {
        var ex = assertThrows(RuntimeException.class,
                () -> onMethodAIService.poem("{response_schema} Generate a poem about {topic}", "dog"));
        assertEquals(
                "The {response_schema} placeholder cannot be used if the property quarkus.langchain4j.response-schema is set to false. Found in: io.quarkiverse.langchain4j.bam.deployment.ResponseSchemaOffTest$OnMethodAIService",
                ex.getMessage());
    }

}
