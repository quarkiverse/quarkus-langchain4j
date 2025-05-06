package org.acme.example.multiple;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiChatModel;
import io.quarkiverse.langchain4j.watsonx.WatsonxChatModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class MultipleTokenCountEstimatorProvidersTest {

    @Inject
    @ModelName("c2")
    ChatModel azureChat;

    @Inject
    @ModelName("c7")
    ChatModel watsonxChat;

    @Test
    void azureOpenAiTest() {
        assertThat(ClientProxy.unwrap(azureChat)).isInstanceOf(AzureOpenAiChatModel.class);
    }

    @Test
    void watsonxTest() {
        assertThat(ClientProxy.unwrap(watsonxChat)).isInstanceOf(WatsonxChatModel.class);
    }
}
