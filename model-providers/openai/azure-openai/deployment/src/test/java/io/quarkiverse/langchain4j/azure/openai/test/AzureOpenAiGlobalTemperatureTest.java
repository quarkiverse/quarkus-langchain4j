package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

public class AzureOpenAiGlobalTemperatureTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.temperature", "0.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.api-key", "whatever");

    @Inject
    ChatModel chatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void globalTemperatureIsStillHonored() {
        mock.chat().reply("ok");

        chatModel.chat("hello");

        JsonNode requestBody = mock.chat().singleRequest().bodyAsJson();
        assertThat(requestBody.get("temperature").doubleValue()).isEqualTo(0.5);
        assertThat(requestBody.has("top_p")).isFalse();
    }
}
