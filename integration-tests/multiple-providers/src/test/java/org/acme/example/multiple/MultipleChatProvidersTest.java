package org.acme.example.multiple;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiChatModel;
import io.quarkiverse.langchain4j.huggingface.QuarkusHuggingFaceChatModel;
import io.quarkiverse.langchain4j.ollama.OllamaChatLanguageModel;
import io.quarkiverse.langchain4j.openshiftai.OpenshiftAiChatModel;
import io.quarkiverse.langchain4j.watsonx.WatsonxChatModel;
import io.quarkiverse.langchain4j.watsonx.WatsonxGenerationModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class MultipleChatProvidersTest {

    @Inject
    ChatLanguageModel defaultModel;

    @Inject
    @ModelName("c1")
    ChatLanguageModel firstNamedModel;

    @Inject
    @ModelName("c2")
    ChatLanguageModel secondNamedModel;

    @Inject
    @ModelName("c3")
    ChatLanguageModel thirdNamedModel;

    @Inject
    @ModelName("c5")
    ChatLanguageModel fifthNamedModel;

    @Inject
    @ModelName("c6")
    ChatLanguageModel sixthNamedModel;

    @Inject
    @ModelName("c7")
    ChatLanguageModel seventhNamedModel;

    @Inject
    @ModelName("c8")
    ChatLanguageModel eighthNamedModel;

    @Test
    void defaultModel() {
        assertThat(ClientProxy.unwrap(defaultModel)).isInstanceOf(OpenAiChatModel.class);
    }

    @Test
    void firstNamedModel() {
        assertThat(ClientProxy.unwrap(firstNamedModel)).isInstanceOf(OpenAiChatModel.class);
    }

    @Test
    void secondNamedModel() {
        assertThat(ClientProxy.unwrap(secondNamedModel)).isInstanceOf(AzureOpenAiChatModel.class);
    }

    @Test
    void thirdNamedModel() {
        assertThat(ClientProxy.unwrap(thirdNamedModel)).isInstanceOf(QuarkusHuggingFaceChatModel.class);
    }

    @Test
    void fifthNamedModel() {
        assertThat(ClientProxy.unwrap(fifthNamedModel)).isInstanceOf(OllamaChatLanguageModel.class);
    }

    @Test
    void sixthNamedModel() {
        assertThat(ClientProxy.unwrap(sixthNamedModel)).isInstanceOf(OpenshiftAiChatModel.class);
    }

    @Test
    void seventhNamedModel() {
        assertThat(ClientProxy.unwrap(seventhNamedModel)).isInstanceOf(WatsonxChatModel.class);
    }

    @Test
    void eighthNamedModel() {
        assertThat(ClientProxy.unwrap(eighthNamedModel)).isInstanceOf(WatsonxGenerationModel.class);
    }
}
