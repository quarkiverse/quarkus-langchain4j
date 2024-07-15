package org.acme.example.multiple;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiChatModel;
import io.quarkiverse.langchain4j.bam.BamChatModel;
import io.quarkiverse.langchain4j.watsonx.WatsonxChatModel;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class MultipleTokenCountEstimatorProvidersTest {

    @Inject
    @ModelName("c2")
    ChatLanguageModel azureChat;

    @Inject
    @ModelName("c2")
    TokenCountEstimator azureTokenizer;

    @Inject
    @ModelName("c4")
    ChatLanguageModel bamChat;

    @Inject
    @ModelName("c4")
    TokenCountEstimator bamTokenizer;

    @Inject
    @ModelName("c7")
    ChatLanguageModel watsonxChat;

    @Inject
    @ModelName("c7")
    TokenCountEstimator watsonxTokenizer;

    @Test
    void azureOpenAiTest() {
        assertThat(ClientProxy.unwrap(azureChat)).isInstanceOf(AzureOpenAiChatModel.class);
        assertThat(ClientProxy.unwrap(azureTokenizer)).isInstanceOf(AzureOpenAiChatModel.class);
    }

    @Test
    void bamTest() {
        assertThat(ClientProxy.unwrap(bamChat)).isInstanceOf(BamChatModel.class);
        assertThat(ClientProxy.unwrap(bamTokenizer)).isInstanceOf(BamChatModel.class);
    }

    @Test
    void watsonxTest() {
        assertThat(ClientProxy.unwrap(watsonxChat)).isInstanceOf(WatsonxChatModel.class);
        assertThat(ClientProxy.unwrap(watsonxTokenizer)).isInstanceOf(WatsonxChatModel.class);
    }
}
