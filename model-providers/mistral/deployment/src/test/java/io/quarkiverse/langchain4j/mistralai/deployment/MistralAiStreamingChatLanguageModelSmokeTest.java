package io.quarkiverse.langchain4j.mistralai.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

class MistralAiStreamingChatLanguageModelSmokeTest extends WiremockAware {

    private static final String API_KEY = "somekey";
    private static final String CHAT_MODEL_ID = "mistral-tiny";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(WeatherAssistant.class, WeatherTools.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.mistralai.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.mistralai.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mistralai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Singleton
    @RegisterAiService(tools = WeatherTools.class)
    interface WeatherAssistant {
        Multi<String> chat(String message);
    }

    @ApplicationScoped
    public static class WeatherTools {

        static final List<String> invocations = new CopyOnWriteArrayList<>();

        @Tool
        public String getWeather(String name) {
            invocations.add("getWeather(" + name + ")");
            return "The weather in " + name + " is sunny";
        }

        @Tool
        public String getTime(String zone) {
            invocations.add("getTime(" + zone + ")");
            return "The time in " + zone + " is 12:00";
        }
    }

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    WeatherAssistant assistant;

    @BeforeEach
    void setup() {
        WeatherTools.invocations.clear();
        resetRequests();
    }

    @Test
    void streaming() throws InterruptedException {
        assertThat(ClientProxy.unwrap(streamingChatModel))
                .isInstanceOf(MistralAiStreamingChatModel.class);

        // Mistral emits a tool-call delta with "content": null. Before the null-guard
        // fix,
        // QuarkusMistralAiClient iterated getDelta().getContent() and threw a
        // NullPointerException,
        // breaking streaming + function calling. This stream reproduces that exact
        // shape.
        String eventStream = """
                data: {"id":"cmpl-1","object":"chat.completion.chunk","created":1711442725,"model":"mistral-tiny","choices":[{"index":0,"delta":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"get_weather","arguments":"{}"}}]},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":19,"total_tokens":27,"completion_tokens":8}}

                data: [DONE]

                """;

        wiremock().register(
                post(urlEqualTo("/v1/chat/completions"))
                        .withHeader("Authorization", equalTo("Bearer " + API_KEY))
                        .willReturn(okForContentType(MediaType.SERVER_SENT_EVENTS, eventStream)));

        AtomicReference<ChatResponse> response = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        streamingChatModel.chat("What is the weather?", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                response.set(completeResponse);
                latch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
                latch.countDown();
            }
        });

        assertThat(latch.await(30, TimeUnit.SECONDS))
                .as("streaming did not complete within the timeout")
                .isTrue();

        // The regression guard: a null-content delta must not blow up the stream.
        if (error.get() != null) {
            fail("streaming failed: %s".formatted(error.get().getMessage()), error.get());
        }

        // The stream completed without onError -> the null-content delta no longer
        // NPEs.
        assertThat(response.get()).isNotNull();
        assertThat(response.get().aiMessage()).isNotNull();

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("User-Agent")).isEqualTo("Quarkus REST Client");
        String requestBody = new String(loggedRequest.getBody());
        assertThat(requestBody).contains(CHAT_MODEL_ID).contains("stream");
    }

    @Test
    void streamingToolCalling() {
        // Step 1: the model answers with two tool calls. getWeather's arguments are
        // split across
        // two chunks (the second delta has no tool-call id) to exercise the
        // ToolCallBuilder
        // argument aggregation in QuarkusMistralAiClient. All deltas carry "content":
        // null,
        // which also covers the original NPE regression.
        String toolCallStream = """
                data: {"id":"cmpl-1","object":"chat.completion.chunk","created":1711442725,"model":"mistral-tiny","choices":[{"index":0,"delta":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"getWeather","arguments":"{\\"name\\": \\"Pa"}}]},"finish_reason":null}]}

                data: {"id":"cmpl-1","object":"chat.completion.chunk","created":1711442725,"model":"mistral-tiny","choices":[{"index":0,"delta":{"content":null,"tool_calls":[{"function":{"arguments":"ris\\"}"}}]},"finish_reason":null}]}

                data: {"id":"cmpl-1","object":"chat.completion.chunk","created":1711442725,"model":"mistral-tiny","choices":[{"index":0,"delta":{"content":null,"tool_calls":[{"id":"call_2","type":"function","function":{"name":"getTime","arguments":"{\\"zone\\": \\"Europe/Paris\\"}"}}]},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":19,"total_tokens":27,"completion_tokens":8}}

                data: [DONE]

                """;

        // Step 2: after the tools have been executed, the model streams the final
        // answer.
        String finalAnswerStream = """
                data: {"id":"cmpl-2","object":"chat.completion.chunk","created":1711442726,"model":"mistral-tiny","choices":[{"index":0,"delta":{"role":"assistant","content":"The weather in Paris is sunny"},"finish_reason":null}]}

                data: {"id":"cmpl-2","object":"chat.completion.chunk","created":1711442726,"model":"mistral-tiny","choices":[{"index":0,"delta":{"content":" and it is 12:00."},"finish_reason":"stop"}],"usage":{"prompt_tokens":40,"total_tokens":60,"completion_tokens":20}}

                data: [DONE]

                """;

        wiremock().register(
                post(urlEqualTo("/v1/chat/completions"))
                        .inScenario("tool-calling")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willSetStateTo("tools-executed")
                        .withHeader("Authorization", equalTo("Bearer " + API_KEY))
                        .willReturn(okForContentType(MediaType.SERVER_SENT_EVENTS, toolCallStream)));
        wiremock().register(
                post(urlEqualTo("/v1/chat/completions"))
                        .inScenario("tool-calling")
                        .whenScenarioStateIs("tools-executed")
                        .withHeader("Authorization", equalTo("Bearer " + API_KEY))
                        .willReturn(okForContentType(MediaType.SERVER_SENT_EVENTS, finalAnswerStream)));

        List<String> tokens = assistant.chat("What is the weather and time in Paris?")
                .collect().asList()
                .await().atMost(Duration.ofSeconds(30));

        // the streamed answer is the one from the second (post-tools) response
        assertThat(String.join("", tokens)).isEqualTo("The weather in Paris is sunny and it is 12:00.");

        // both tools were actually executed, with the arguments parsed from the stream
        assertThat(WeatherTools.invocations)
                .containsExactlyInAnyOrder("getWeather(Paris)", "getTime(Europe/Paris)");

        List<LoggedRequest> requests = wiremock().find(postRequestedFor(urlEqualTo("/v1/chat/completions")));
        assertThat(requests).hasSize(2);
        List<String> bodies = requests.stream().map(r -> new String(r.getBody())).toList();

        // the tools were declared to the model on the initial streaming request
        assertThat(bodies).anySatisfy(body -> assertThat(body)
                .contains("\"tools\"")
                .contains("getWeather")
                .contains("getTime")
                .doesNotContain("call_1"));

        // the follow-up request carried the tool execution results back to the model
        assertThat(bodies).anySatisfy(body -> assertThat(body)
                .contains("call_1")
                .contains("call_2")
                .contains("The weather in Paris is sunny")
                .contains("The time in Europe/Paris is 12:00"));
    }
}
