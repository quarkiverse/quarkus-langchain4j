package org.acme.example.multiple;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiChatModel;
import io.quarkiverse.langchain4j.huggingface.QuarkusHuggingFaceChatModel;
import io.quarkiverse.langchain4j.openshiftai.OpenshiftAiChatModel;
import io.quarkiverse.langchain4j.watsonx.WatsonxChatModel;
import io.quarkiverse.langchain4j.watsonx.WatsonxGenerationModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class MultipleChatProvidersTest {

    @Inject
    ChatModel defaultModel;

    @Inject
    @ModelName("c1")
    ChatModel firstNamedModel;

    @Inject
    @ModelName("c2")
    ChatModel secondNamedModel;

    @Inject
    @ModelName("c3")
    ChatModel thirdNamedModel;

    @Inject
    @ModelName("c5")
    ChatModel fifthNamedModel;

    @Inject
    @ModelName("c6")
    ChatModel sixthNamedModel;

    @Inject
    @ModelName("c7")
    ChatModel seventhNamedModel;

    @Inject
    @ModelName("c8")
    ChatModel eighthNamedModel;

    @Inject
    @ModelName("c9")
    ChatModel ninthNamedModel;

    @Inject
    @Any
    Instance<ChatModel> anyModel;

    @Test
    void defaultModel() {
        assertThat(SubclassUtil.unwrap(defaultModel)).isInstanceOf(OpenAiChatModel.class);
    }

    @Test
    void firstNamedModel() {
        assertThat(SubclassUtil.unwrap(firstNamedModel)).isInstanceOf(OpenAiChatModel.class);
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
        assertThat(ClientProxy.unwrap(fifthNamedModel)).isInstanceOf(OllamaChatModel.class);
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

    @Test
    void ninthNamedModel() {
        assertThat(ClientProxy.unwrap(ninthNamedModel)).isInstanceOf(OllamaChatModel.class);
    }

    @Test
    void anyInstanceInjection() {
        // c10 model has no direct injection point; we only request it via Instance<T>
        assertThat(anyModel.handlesStream()
                .anyMatch(handle -> handle.getBean().getQualifiers().contains(ModelName.Literal.of("c10")))).isTrue();
    }
}
