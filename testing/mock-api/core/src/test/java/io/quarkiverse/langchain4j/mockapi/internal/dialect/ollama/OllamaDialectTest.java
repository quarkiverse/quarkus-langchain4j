package io.quarkiverse.langchain4j.mockapi.internal.dialect.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.FinishReason;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DialectRegistry;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.CapturedRequestImpl;

class OllamaDialectTest {

    private static final Dialect.Match CHAT = new Dialect.Match(Operation.CHAT, false, "chat");
    private static final Dialect.Match STREAM = new Dialect.Match(Operation.CHAT, true, "chat");
    private static final Dialect.Match EMBEDDINGS = new Dialect.Match(Operation.EMBEDDINGS, false, "embed");

    OllamaDialect d = new OllamaDialect();
    ObjectMapper m = MockApiJson.mapper();

    static CapturedRequest req(String body) {
        return new CapturedRequestImpl(3, "POST", "/ollama/api/chat", null, Map.of(), body, Operation.CHAT, "ollama",
                null, null);
    }

    @Test
    void matchesItsOperationsUnderItsPrefix() throws Exception {
        assertThat(d.id()).isEqualTo("ollama");
        assertThat(d.pathPrefix()).isEqualTo("/ollama");
        assertThat(d.match("POST", "/api/chat", Map.of(), m.readTree("{\"stream\":false}"))).contains(CHAT);
        assertThat(d.match("POST", "/api/chat", Map.of(), m.readTree("{\"stream\":true}"))).contains(STREAM);
        // stream defaults to true
        assertThat(d.match("POST", "/api/chat", Map.of(), m.readTree("{\"model\":\"m\"}"))).contains(STREAM);
        assertThat(d.match("POST", "/api/chat", Map.of(), m.readTree("{\"stream\":null}"))).contains(STREAM);
        assertThat(d.match("POST", "/api/embed", Map.of(), null)).contains(EMBEDDINGS);
        assertThat(d.match("GET", "/api/chat", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/api/generate", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/api/embeddings", Map.of(), null)).isEmpty();
    }

    @Test
    void ollamaRequestsAreResolvedByPrefix() {
        DialectRegistry registry = DialectRegistry.load();
        assertThat(registry.resolve("POST", "/ollama/api/chat", Map.of(), null))
                .get().extracting(r -> r.dialect().id()).isEqualTo("ollama");
        assertThat(registry.resolve("POST", "/ollama/api/embed", Map.of(), null))
                .get().extracting(r -> r.dialect().id()).isEqualTo("ollama");
    }

    @Test
    void parsesRequest() throws Exception {
        String body = """
                {"model":"llama3.2","stream":false,"keep_alive":"5m",
                 "options":{"temperature":0.2,"top_p":0.7,"num_predict":100,"top_k":40},
                 "messages":[{"role":"system","content":"be nice"},
                             {"role":"user","content":"hi","images":["aGk="]},
                             {"role":"assistant","content":"","tool_calls":[{"function":{"name":"getWeather",
                                "arguments":{"city":"Paris"}}}]},
                             {"role":"tool","content":"rainy","tool_name":"getWeather"}],
                 "tools":[{"type":"function","function":{"name":"getWeather","description":"weather",
                    "parameters":{"type":"object"}}}],
                 "format":{"type":"object"}}
                """;
        ChatRequestView v = d.parseChat(CHAT, m.readTree(body));
        assertThat(v.modelName()).isEqualTo("llama3.2");
        assertThat(v.stream()).isFalse();
        assertThat(v.temperature()).isEqualTo(0.2);
        assertThat(v.topP()).isEqualTo(0.7);
        assertThat(v.maxTokens()).isEqualTo(100);
        assertThat(v.toolChoice()).isNull();
        assertThat(v.responseFormatType()).isEqualTo("json_schema");
        assertThat(v.responseFormatSchema().path("type").asText()).isEqualTo("object");
        assertThat(v.tools()).singleElement().satisfies(t -> {
            assertThat(t.name()).isEqualTo("getWeather");
            assertThat(t.description()).isEqualTo("weather");
            assertThat(t.parametersSchema().path("type").asText()).isEqualTo("object");
        });
        List<ChatRequestView.Message> msgs = v.messages();
        assertThat(msgs).extracting(ChatRequestView.Message::role).containsExactly("system", "user", "assistant", "tool");
        assertThat(msgs.get(0).text()).isEqualTo("be nice");
        assertThat(msgs.get(1).text()).isEqualTo("hi");
        assertThat(msgs.get(2).toolCalls()).singleElement().satisfies(tc -> {
            assertThat(tc.id()).isNull();
            assertThat(tc.name()).isEqualTo("getWeather");
            assertThat(m.readTree(tc.argumentsJson())).isEqualTo(m.readTree("{\"city\":\"Paris\"}"));
        });
        assertThat(msgs.get(3).text()).isEqualTo("rainy");
        assertThat(msgs.get(3).name()).isEqualTo("getWeather");
        assertThat(msgs.get(3).toolCallId()).isNull();
    }

    @Test
    void parsesJsonModeAndDefaultStream() throws Exception {
        ChatRequestView v = d.parseChat(STREAM, m.readTree("{\"model\":\"m\",\"messages\":[],\"format\":\"json\"}"));
        assertThat(v.stream()).isTrue();
        assertThat(v.responseFormatType()).isEqualTo("json");
        assertThat(v.responseFormatSchema()).isNull();
        assertThat(v.temperature()).isNull();
        assertThat(v.maxTokens()).isNull();

        ChatRequestView plain = d.parseChat(CHAT, m.readTree("{\"model\":\"m\",\"stream\":false,\"messages\":[]}"));
        assertThat(plain.responseFormatType()).isNull();
        assertThat(plain.responseFormatSchema()).isNull();
    }

    @Test
    void rendersTextReply() throws Exception {
        Rendered r = d.render(CHAT, ChatReply.text("hello").usage(14, 47), req("{\"model\":\"llama3.2\"}"));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.streamFormat()).isEqualTo(Rendered.StreamFormat.NONE);
        assertThat(r.headers()).containsEntry("Content-Type", "application/json");
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/model").asText()).isEqualTo("llama3.2");
        assertThat(j.at("/created_at").asText()).isNotBlank();
        assertThat(j.at("/message/role").asText()).isEqualTo("assistant");
        assertThat(j.at("/message/content").asText()).isEqualTo("hello");
        assertThat(j.at("/message").has("tool_calls")).isFalse();
        assertThat(j.at("/message").has("thinking")).isFalse();
        assertThat(j.at("/done").asBoolean()).isTrue();
        assertThat(j.at("/done_reason").asText()).isEqualTo("stop");
        assertThat(j.at("/prompt_eval_count").asInt()).isEqualTo(14);
        assertThat(j.at("/eval_count").asInt()).isEqualTo(47);
        assertThat(j.at("/total_duration").isIntegralNumber()).isTrue();
        assertThat(j.at("/load_duration").isIntegralNumber()).isTrue();
        assertThat(j.at("/prompt_eval_duration").isIntegralNumber()).isTrue();
        assertThat(j.at("/eval_duration").isIntegralNumber()).isTrue();
    }

    @Test
    void rendersToolCallsWithObjectArguments() throws Exception {
        ChatReply reply = ChatReply.toolCall("getWeather", Map.of("city", "Paris")).modelName("x-model");
        JsonNode j = m.readTree(d.render(CHAT, reply, req("{}")).body());
        assertThat(j.at("/model").asText()).isEqualTo("x-model");
        assertThat(j.at("/message/content").asText()).isEmpty();
        JsonNode call = j.at("/message/tool_calls/0");
        assertThat(call.has("id")).isFalse();
        assertThat(call.at("/function/name").asText()).isEqualTo("getWeather");
        assertThat(call.at("/function/arguments").isObject()).isTrue();
        assertThat(call.at("/function/arguments")).isEqualTo(m.readTree("{\"city\":\"Paris\"}"));
        // the documented tool call examples finish with "stop"
        assertThat(j.at("/done_reason").asText()).isEqualTo("stop");
    }

    @Test
    void rejectsToolCallArgumentsThatAreNotAJsonObject() {
        assertThatThrownBy(() -> d.render(CHAT, ChatReply.toolCall("f", "[1]"), req("{}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("JSON object")
                .hasMessageContaining("Tool call f ");
    }

    @Test
    void rendersThinking() throws Exception {
        JsonNode j = m.readTree(d.render(CHAT, ChatReply.text("4").thinking("2+2"), req("{}")).body());
        assertThat(j.at("/message/thinking").asText()).isEqualTo("2+2");
    }

    @Test
    void mapsFinishReasonsAndFallsBackToMockModel() throws Exception {
        assertThat(doneReason(ChatReply.text("x").finishReason(FinishReason.LENGTH))).isEqualTo("length");
        assertThat(doneReason(ChatReply.text("x").finishReason(FinishReason.CONTENT_FILTER))).isEqualTo("content_filter");
        JsonNode j = m.readTree(d.render(CHAT, ChatReply.text("x"), req("{}")).body());
        assertThat(j.at("/model").asText()).isEqualTo("mock-model");
        assertThat(j.at("/prompt_eval_count").asInt()).isZero();
        assertThat(j.at("/eval_count").asInt()).isZero();
    }

    private String doneReason(ChatReply reply) throws Exception {
        return m.readTree(d.render(CHAT, reply, req("{}")).body()).at("/done_reason").asText();
    }

    @Test
    void streamsTextObjectsThenADoneObjectWithCounts() throws Exception {
        Rendered r = d.render(STREAM, ChatReply.text("hi there").usage(5, 7), req("{\"model\":\"m\"}"));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.streamFormat()).isEqualTo(Rendered.StreamFormat.NDJSON);
        assertThat(r.streamFormat().contentType()).isEqualTo("application/x-ndjson");
        List<SseEvent> events = r.events();
        assertThat(events).hasSize(3);
        assertThat(events).allSatisfy(e -> {
            assertThat(e.event()).isNull();
            assertThat(e.data()).doesNotContain("\n");
        });
        JsonNode first = m.readTree(events.get(0).data());
        assertThat(first.at("/model").asText()).isEqualTo("m");
        assertThat(first.at("/created_at").asText()).isNotBlank();
        assertThat(first.at("/message/role").asText()).isEqualTo("assistant");
        assertThat(first.at("/message/content").asText()).isEqualTo("hi");
        assertThat(first.at("/done").asBoolean()).isFalse();
        assertThat(first.has("done_reason")).isFalse();
        assertThat(first.has("eval_count")).isFalse();
        assertThat(m.readTree(events.get(1).data()).at("/message/content").asText()).isEqualTo(" there");
        JsonNode last = m.readTree(events.get(2).data());
        assertThat(last.at("/message/role").asText()).isEqualTo("assistant");
        assertThat(last.at("/message/content").asText()).isEmpty();
        assertThat(last.at("/done").asBoolean()).isTrue();
        assertThat(last.at("/done_reason").asText()).isEqualTo("stop");
        assertThat(last.at("/prompt_eval_count").asInt()).isEqualTo(5);
        assertThat(last.at("/eval_count").asInt()).isEqualTo(7);
        assertThat(last.at("/total_duration").isIntegralNumber()).isTrue();
    }

    @Test
    void streamsToolCallsInOneObject() throws Exception {
        ChatReply reply = ChatReply.toolCall("getWeather", "{\"city\":\"Paris\"}");
        List<SseEvent> events = d.render(STREAM, reply, req("{}")).events();
        assertThat(events).hasSize(2);
        JsonNode first = m.readTree(events.get(0).data());
        assertThat(first.at("/done").asBoolean()).isFalse();
        assertThat(first.at("/message/content").asText()).isEmpty();
        assertThat(first.at("/message/tool_calls/0/function/name").asText()).isEqualTo("getWeather");
        assertThat(first.at("/message/tool_calls/0/function/arguments/city").asText()).isEqualTo("Paris");
        JsonNode last = m.readTree(events.get(1).data());
        assertThat(last.at("/done").asBoolean()).isTrue();
        assertThat(last.at("/message").has("tool_calls")).isFalse();
    }

    @Test
    void streamsThinkingBeforeText() throws Exception {
        List<SseEvent> events = d.render(STREAM, ChatReply.text("4").thinking("2+2"), req("{}")).events();
        assertThat(events).hasSize(3);
        JsonNode thinking = m.readTree(events.get(0).data());
        assertThat(thinking.at("/message/thinking").asText()).isEqualTo("2+2");
        assertThat(thinking.at("/message/content").asText()).isEmpty();
        assertThat(m.readTree(events.get(1).data()).at("/message/content").asText()).isEqualTo("4");
    }

    @Test
    void streamsOnlyTheDoneObjectWhenReplyHasNothing() throws Exception {
        List<SseEvent> events = d.render(STREAM, ChatReply.text(""), req("{}")).events();
        assertThat(events).singleElement()
                .satisfies(e -> assertThat(m.readTree(e.data()).at("/done").asBoolean()).isTrue());
    }

    @Test
    void rendersEmbeddingsForAStringAndAnArrayInput() throws Exception {
        JsonNode one = m.readTree(d.render(EMBEDDINGS, EmbeddingReply.of(new float[] { 1f, 2f }),
                req("{\"model\":\"all-minilm\",\"input\":\"a\"}")).body());
        assertThat(one.at("/model").asText()).isEqualTo("all-minilm");
        assertThat(one.at("/embeddings")).hasSize(1);
        assertThat(one.at("/embeddings/0/1").floatValue()).isEqualTo(2f);
        assertThat(one.at("/prompt_eval_count").asInt()).isEqualTo(1);
        assertThat(one.at("/total_duration").isIntegralNumber()).isTrue();
        assertThat(one.at("/load_duration").isIntegralNumber()).isTrue();

        JsonNode two = m.readTree(d.render(EMBEDDINGS, EmbeddingReply.of(new float[] { 1f }, new float[] { 3f }),
                req("{\"input\":[\"a\",\"b\"]}")).body());
        assertThat(two.at("/embeddings")).hasSize(2);
        assertThat(two.at("/embeddings/1/0").floatValue()).isEqualTo(3f);
        assertThat(two.at("/model").asText()).isEqualTo("mock-embedding-model");
    }

    @Test
    void derivesEmbeddingsAndRejectsTooFewVectors() throws Exception {
        JsonNode j = m.readTree(d.render(EMBEDDINGS, EmbeddingReply.derived(8), req("{\"input\":\"a\"}")).body());
        assertThat(j.at("/embeddings")).hasSize(1);
        assertThat(j.at("/embeddings/0")).hasSize(8);

        assertThatThrownBy(
                () -> d.render(EMBEDDINGS, EmbeddingReply.of(new float[] { 1f }), req("{\"input\":[\"a\",\"b\"]}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("2 inputs");
    }

    @Test
    void rendersErrorEnvelope() throws Exception {
        Rendered r = d.renderError(STREAM, 404, "model not found");
        assertThat(r.status()).isEqualTo(404);
        assertThat(r.streamFormat()).isEqualTo(Rendered.StreamFormat.NONE);
        assertThat(r.headers()).containsEntry("Content-Type", "application/json");
        JsonNode j = m.readTree(r.body());
        assertThat(j.size()).isEqualTo(1);
        assertThat(j.at("/error").asText()).isEqualTo("model not found");
    }
}
