package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.azure.openai.AzureOpenAiStreamingChatModel;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

public class AzureOpenAiStreamingChatModelTest {

    private static final String CHAT_COMPLETIONS_PATH = "/openai/deployments/mock-deployment/chat/completions";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.api-key", "api-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.log-responses", "true");

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    void test() throws Exception {
        assertThat(ClientProxy.unwrap(streamingChatModel))
                .isInstanceOf(AzureOpenAiStreamingChatModel.class);

        // generated from a true API call to Azure OpenAI; served raw because it carries the Azure-specific
        // prompt_filter_results chunk with empty choices and the content_filter_results of every chunk
        mock.raw().on("POST", CHAT_COMPLETIONS_PATH).replies(RawResponse.sse(200,
                Map.of("Content-Type", "text/event-stream"), List.of(
                        SseEvent.of(null,
                                "{\"choices\":[],\"created\":0,\"id\":\"\",\"model\":\"\",\"object\":\"\",\"prompt_filter_results\":[{\"prompt_index\":0,\"content_filter_results\":{\"hate\":{\"filtered\":false,\"severity\":\"safe\"},\"self_harm\":{\"filtered\":false,\"severity\":\"safe\"},\"sexual\":{\"filtered\":false,\"severity\":\"safe\"},\"violence\":{\"filtered\":false,\"severity\":\"safe\"}}}]}"),
                        SseEvent.of(null,
                                "{\"choices\":[{\"content_filter_results\":{},\"delta\":{\"content\":\"\",\"refusal\":null,\"role\":\"assistant\"},\"finish_reason\":null,\"index\":0,\"logprobs\":null}],\"created\":1750697159,\"id\":\"chatcmpl-BleatwEUn3D8iXsgXpDqeOPEMuW3j\",\"model\":\"gpt-4o-2024-08-06\",\"object\":\"chat.completion.chunk\",\"system_fingerprint\":\"fp_ee1d74bde0\"}"),
                        SseEvent.of(null,
                                "{\"choices\":[{\"content_filter_results\":{\"hate\":{\"filtered\":false,\"severity\":\"safe\"},\"self_harm\":{\"filtered\":false,\"severity\":\"safe\"},\"sexual\":{\"filtered\":false,\"severity\":\"safe\"},\"violence\":{\"filtered\":false,\"severity\":\"safe\"}},\"delta\":{\"content\":\"Hallo\"},\"finish_reason\":null,\"index\":0,\"logprobs\":null}],\"created\":1750697159,\"id\":\"chatcmpl-BleatwEUn3D8iXsgXpDqeOPEMuW3j\",\"model\":\"gpt-4o-2024-08-06\",\"object\":\"chat.completion.chunk\",\"system_fingerprint\":\"fp_ee1d74bde0\"}"),
                        SseEvent.of(null,
                                "{\"choices\":[{\"content_filter_results\":{\"hate\":{\"filtered\":false,\"severity\":\"safe\"},\"self_harm\":{\"filtered\":false,\"severity\":\"safe\"},\"sexual\":{\"filtered\":false,\"severity\":\"safe\"},\"violence\":{\"filtered\":false,\"severity\":\"safe\"}},\"delta\":{\"content\":\" Welt\"},\"finish_reason\":null,\"index\":0,\"logprobs\":null}],\"created\":1750697159,\"id\":\"chatcmpl-BleatwEUn3D8iXsgXpDqeOPEMuW3j\",\"model\":\"gpt-4o-2024-08-06\",\"object\":\"chat.completion.chunk\",\"system_fingerprint\":\"fp_ee1d74bde0\"}"),
                        SseEvent.of(null,
                                "{\"choices\":[{\"content_filter_results\":{\"hate\":{\"filtered\":false,\"severity\":\"safe\"},\"self_harm\":{\"filtered\":false,\"severity\":\"safe\"},\"sexual\":{\"filtered\":false,\"severity\":\"safe\"},\"violence\":{\"filtered\":false,\"severity\":\"safe\"}},\"delta\":{\"content\":\"!\"},\"finish_reason\":null,\"index\":0,\"logprobs\":null}],\"created\":1750697159,\"id\":\"chatcmpl-BleatwEUn3D8iXsgXpDqeOPEMuW3j\",\"model\":\"gpt-4o-2024-08-06\",\"object\":\"chat.completion.chunk\",\"system_fingerprint\":\"fp_ee1d74bde0\"}"),
                        SseEvent.of(null,
                                "{\"choices\":[{\"content_filter_results\":{},\"delta\":{},\"finish_reason\":\"stop\",\"index\":0,\"logprobs\":null}],\"created\":1750697159,\"id\":\"chatcmpl-BleatwEUn3D8iXsgXpDqeOPEMuW3j\",\"model\":\"gpt-4o-2024-08-06\",\"object\":\"chat.completion.chunk\",\"system_fingerprint\":\"fp_ee1d74bde0\"}"),
                        SseEvent.of(null,
                                "[DONE]"))));

        CompletableFuture<AiMessage> streamingResponse = new CompletableFuture<>();
        streamingChatModel.chat("Translate to German: Hello world!", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
            }

            @Override
            public void onError(Throwable error) {
                streamingResponse.completeExceptionally(error);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                streamingResponse.complete(response.aiMessage());
            }
        });

        assertThat(streamingResponse.get(1, TimeUnit.MINUTES).text())
                .isNotNull()
                .isEqualTo("Hallo Welt!");

        assertThat(mock.requests()).hasSize(1);
        CapturedRequest request = mock.requests().get(0);
        assertThat(request.path()).isEqualTo(CHAT_COMPLETIONS_PATH);
        assertThat(request.body())
                .doesNotContain("\"temperature\"")
                .doesNotContain("\"top_p\"");
    }
}
