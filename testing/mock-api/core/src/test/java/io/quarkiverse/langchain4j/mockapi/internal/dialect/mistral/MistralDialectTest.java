package io.quarkiverse.langchain4j.mockapi.internal.dialect.mistral;

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
import io.quarkiverse.langchain4j.mockapi.ModerationReply;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.ScoringReply;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DialectRegistry;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.CapturedRequestImpl;

class MistralDialectTest {

    private static final Dialect.Match CHAT = new Dialect.Match(Operation.CHAT, false, "chat-completions");
    private static final Dialect.Match STREAM = new Dialect.Match(Operation.CHAT, true, "chat-completions");
    private static final Dialect.Match EMBEDDINGS = new Dialect.Match(Operation.EMBEDDINGS, false, "embeddings");
    private static final Dialect.Match MODERATION = new Dialect.Match(Operation.MODERATION, false, "moderations");

    MistralDialect d = new MistralDialect();
    ObjectMapper m = MockApiJson.mapper();

    static CapturedRequest req(String body) {
        return new CapturedRequestImpl(3, "POST", "/mistralai/v1/chat/completions", null, Map.of(), body,
                Operation.CHAT, "mistralai", null, null);
    }

    @Test
    void matchesItsOperationsUnderItsPrefix() throws Exception {
        assertThat(d.id()).isEqualTo("mistralai");
        assertThat(d.pathPrefix()).isEqualTo("/mistralai");
        assertThat(d.match("POST", "/v1/chat/completions", Map.of(), m.readTree("{\"stream\":true}"))).contains(STREAM);
        assertThat(d.match("POST", "/v1/chat/completions", Map.of(), null)).contains(CHAT);
        assertThat(d.match("POST", "/v1/embeddings", Map.of(), null)).contains(EMBEDDINGS);
        assertThat(d.match("POST", "/v1/moderations", Map.of(), null)).contains(MODERATION);
        assertThat(d.match("GET", "/v1/chat/completions", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/v1/fim/completions", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/v1/responses", Map.of(), null)).isEmpty();
    }

    @Test
    void mistralRequestsAreNotAttributedToOpenAi() {
        DialectRegistry registry = DialectRegistry.load();
        assertThat(registry.resolve("POST", "/mistralai/v1/chat/completions", Map.of(), null))
                .get().extracting(r -> r.dialect().id()).isEqualTo("mistralai");
        assertThat(registry.resolve("POST", "/v1/chat/completions", Map.of(), null))
                .get().extracting(r -> r.dialect().id()).isEqualTo("openai");
    }

    @Test
    void parsesRequest() throws Exception {
        String body = """
                {"model":"mistral-small","temperature":0.2,"top_p":0.7,"max_tokens":100,"stream":false,
                 "messages":[{"role":"system","content":"be nice"},
                             {"role":"user","content":[{"type":"text","text":"hi "},{"type":"text","text":"there"}]},
                             {"role":"assistant","content":null,"tool_calls":[{"id":"abc123def","type":"function",
                                "function":{"name":"getWeather","arguments":"{\\"city\\":\\"Paris\\"}"}}]},
                             {"role":"tool","content":"rainy","tool_call_id":"abc123def","name":"getWeather"}],
                 "tools":[{"type":"function","function":{"name":"getWeather","description":"weather",
                    "parameters":{"type":"object"}}}],
                 "tool_choice":"any",
                 "response_format":{"type":"json_schema","json_schema":{"name":"x","schema":{"type":"object"}}}}
                """;
        ChatRequestView v = d.parseChat(CHAT, m.readTree(body));
        assertThat(v.modelName()).isEqualTo("mistral-small");
        assertThat(v.temperature()).isEqualTo(0.2);
        assertThat(v.topP()).isEqualTo(0.7);
        assertThat(v.maxTokens()).isEqualTo(100);
        assertThat(v.stream()).isFalse();
        assertThat(v.toolChoice()).isEqualTo("any");
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
        assertThat(msgs.get(1).text()).isEqualTo("hi there");
        assertThat(msgs.get(2).text()).isNull();
        assertThat(msgs.get(2).toolCalls()).singleElement().satisfies(tc -> {
            assertThat(tc.id()).isEqualTo("abc123def");
            assertThat(tc.name()).isEqualTo("getWeather");
            assertThat(tc.argumentsJson()).isEqualTo("{\"city\":\"Paris\"}");
        });
        assertThat(msgs.get(3).toolCallId()).isEqualTo("abc123def");
        assertThat(msgs.get(3).name()).isEqualTo("getWeather");
        assertThat(msgs.get(3).text()).isEqualTo("rainy");
    }

    @Test
    void parsesObjectArgumentsAndNamedToolChoice() throws Exception {
        String body = """
                {"model":"m","stream":true,
                 "messages":[{"role":"assistant","tool_calls":[{"function":{"name":"f","arguments":{"a":1}}}]}],
                 "tool_choice":{"type":"function","function":{"name":"f"}}}
                """;
        ChatRequestView v = d.parseChat(STREAM, m.readTree(body));
        assertThat(v.stream()).isTrue();
        assertThat(v.toolChoice()).isEqualTo("f");
        assertThat(v.messages().get(0).toolCalls()).singleElement()
                .satisfies(tc -> assertThat(m.readTree(tc.argumentsJson())).isEqualTo(m.readTree("{\"a\":1}")));
        assertThat(v.responseFormatType()).isNull();
        assertThat(v.responseFormatSchema()).isNull();
    }

    @Test
    void rendersTextReply() throws Exception {
        Rendered r = d.render(CHAT, ChatReply.text("hello").usage(14, 47), req("{\"model\":\"mistral-small\"}"));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.headers()).containsEntry("Content-Type", "application/json");
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/id").asText()).isEqualTo("cmpl-mock-3");
        assertThat(j.at("/object").asText()).isEqualTo("chat.completion");
        assertThat(j.at("/created").isNumber()).isTrue();
        assertThat(j.at("/model").asText()).isEqualTo("mistral-small");
        assertThat(j.at("/choices")).hasSize(1);
        assertThat(j.at("/choices/0/index").asInt()).isZero();
        assertThat(j.at("/choices/0/message/role").asText()).isEqualTo("assistant");
        assertThat(j.at("/choices/0/message/content").asText()).isEqualTo("hello");
        assertThat(j.at("/choices/0/message/tool_calls").isNull()).isTrue();
        assertThat(j.at("/choices/0/finish_reason").asText()).isEqualTo("stop");
        assertThat(j.at("/usage/prompt_tokens").asInt()).isEqualTo(14);
        assertThat(j.at("/usage/completion_tokens").asInt()).isEqualTo(47);
        assertThat(j.at("/usage/total_tokens").asInt()).isEqualTo(61);
    }

    @Test
    void rendersToolCallsWithStringArguments() throws Exception {
        ChatReply reply = ChatReply.toolCall("getWeather", Map.of("city", "Paris")).modelName("x-model").id("c1");
        JsonNode j = m.readTree(d.render(CHAT, reply, req("{}")).body());
        assertThat(j.at("/id").asText()).isEqualTo("c1");
        assertThat(j.at("/model").asText()).isEqualTo("x-model");
        assertThat(j.at("/choices/0/message/content").isNull()).isTrue();
        JsonNode call = j.at("/choices/0/message/tool_calls/0");
        assertThat(call.at("/id").asText()).isEqualTo("call_mock_0");
        assertThat(call.at("/type").asText()).isEqualTo("function");
        assertThat(call.at("/index").asInt()).isZero();
        assertThat(call.at("/function/name").asText()).isEqualTo("getWeather");
        assertThat(call.at("/function/arguments").isTextual()).isTrue();
        assertThat(m.readTree(call.at("/function/arguments").asText())).isEqualTo(m.readTree("{\"city\":\"Paris\"}"));
        assertThat(j.at("/choices/0/finish_reason").asText()).isEqualTo("tool_calls");
    }

    @Test
    void mapsFinishReasonsAndFallsBackToMockModel() throws Exception {
        assertThat(finishReason(ChatReply.text("x").finishReason(FinishReason.LENGTH))).isEqualTo("length");
        assertThat(finishReason(ChatReply.text("x").finishReason(FinishReason.CONTENT_FILTER))).isEqualTo("error");
        assertThat(m.readTree(d.render(CHAT, ChatReply.text("x"), req("{}")).body()).at("/model").asText())
                .isEqualTo("mock-model");
    }

    private String finishReason(ChatReply reply) throws Exception {
        return m.readTree(d.render(CHAT, reply, req("{}")).body()).at("/choices/0/finish_reason").asText();
    }

    @Test
    void streamsTextChunksThenFinishWithUsageThenDone() throws Exception {
        Rendered r = d.render(STREAM, ChatReply.text("hi there").usage(5, 7), req("{\"model\":\"m\",\"stream\":true}"));
        assertThat(r.streamFormat()).isEqualTo(Rendered.StreamFormat.SSE);
        List<SseEvent> events = r.events();
        assertThat(events).hasSize(4);
        assertThat(events).allSatisfy(e -> assertThat(e.event()).isNull());
        JsonNode first = m.readTree(events.get(0).data());
        assertThat(first.at("/id").asText()).isEqualTo("cmpl-mock-3");
        assertThat(first.at("/object").asText()).isEqualTo("chat.completion.chunk");
        assertThat(first.at("/model").asText()).isEqualTo("m");
        assertThat(first.at("/choices/0/index").asInt()).isZero();
        assertThat(first.at("/choices/0/delta/role").asText()).isEqualTo("assistant");
        assertThat(first.at("/choices/0/delta/content").asText()).isEqualTo("hi");
        assertThat(first.at("/choices/0/finish_reason").isNull()).isTrue();
        assertThat(first.has("usage")).isFalse();
        JsonNode second = m.readTree(events.get(1).data());
        assertThat(second.at("/choices/0/delta/content").asText()).isEqualTo(" there");
        assertThat(second.at("/choices/0/delta").has("role")).isFalse();
        JsonNode last = m.readTree(events.get(2).data());
        assertThat(last.at("/choices/0/delta").isEmpty()).isTrue();
        assertThat(last.at("/choices/0/finish_reason").asText()).isEqualTo("stop");
        assertThat(last.at("/usage/prompt_tokens").asInt()).isEqualTo(5);
        assertThat(last.at("/usage/completion_tokens").asInt()).isEqualTo(7);
        assertThat(last.at("/usage/total_tokens").asInt()).isEqualTo(12);
        assertThat(events.get(3).data()).isEqualTo("[DONE]");
    }

    @Test
    void streamsToolCalls() throws Exception {
        ChatReply reply = ChatReply.toolCall("getWeather", "{\"city\":\"Paris\"}");
        List<SseEvent> events = d.render(STREAM, reply, req("{}")).events();
        assertThat(events).hasSize(3);
        JsonNode delta = m.readTree(events.get(0).data()).at("/choices/0/delta");
        assertThat(delta.at("/role").asText()).isEqualTo("assistant");
        assertThat(delta.has("content")).isFalse();
        assertThat(delta.at("/tool_calls/0/id").asText()).isEqualTo("call_mock_0");
        assertThat(delta.at("/tool_calls/0/index").asInt()).isZero();
        assertThat(delta.at("/tool_calls/0/function/name").asText()).isEqualTo("getWeather");
        assertThat(delta.at("/tool_calls/0/function/arguments").asText()).isEqualTo("{\"city\":\"Paris\"}");
        assertThat(m.readTree(events.get(1).data()).at("/choices/0/finish_reason").asText()).isEqualTo("tool_calls");
        assertThat(events.get(2).data()).isEqualTo("[DONE]");
    }

    @Test
    void streamsEmptyContentWhenReplyHasNothing() throws Exception {
        List<SseEvent> events = d.render(STREAM, ChatReply.text(""), req("{}")).events();
        assertThat(events).hasSize(3);
        assertThat(m.readTree(events.get(0).data()).at("/choices/0/delta/content").asText()).isEmpty();
    }

    @Test
    void rendersEmbeddings() throws Exception {
        CapturedRequest req = req("{\"model\":\"mistral-embed\",\"input\":[\"a\",\"b\"]}");
        JsonNode j = m.readTree(d.render(EMBEDDINGS, EmbeddingReply.of(new float[] { 1f, 2f }, new float[] { 3f, 4f }),
                req).body());
        assertThat(j.at("/id").asText()).isEqualTo("embd-mock-3");
        assertThat(j.at("/object").asText()).isEqualTo("list");
        assertThat(j.at("/model").asText()).isEqualTo("mistral-embed");
        assertThat(j.at("/data")).hasSize(2);
        assertThat(j.at("/data/1/object").asText()).isEqualTo("embedding");
        assertThat(j.at("/data/1/index").asInt()).isEqualTo(1);
        assertThat(j.at("/data/1/embedding/1").floatValue()).isEqualTo(4f);
        assertThat(j.at("/usage/prompt_tokens").asInt()).isEqualTo(2);
        assertThat(j.at("/usage/completion_tokens").asInt()).isZero();
        assertThat(j.at("/usage/total_tokens").asInt()).isEqualTo(2);
    }

    @Test
    void derivesEmbeddingsForASingleInputAndRejectsTooFewVectors() throws Exception {
        JsonNode j = m.readTree(d.render(EMBEDDINGS, EmbeddingReply.derived(8), req("{\"input\":\"a\"}")).body());
        assertThat(j.at("/data")).hasSize(1);
        assertThat(j.at("/data/0/embedding")).hasSize(8);
        assertThat(j.at("/model").asText()).isEqualTo("mock-embedding-model");

        assertThatThrownBy(
                () -> d.render(EMBEDDINGS, EmbeddingReply.of(new float[] { 1f }), req("{\"input\":[\"a\",\"b\"]}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("2 inputs");
    }

    @Test
    void rendersModerationResultPerInput() throws Exception {
        CapturedRequest req = req("{\"model\":\"mistral-moderation-latest\",\"input\":[\"a\",\"b\"]}");
        JsonNode j = m.readTree(d.render(MODERATION, ModerationReply.flagged("violence_and_threats", "custom"), req)
                .body());
        assertThat(j.at("/id").asText()).isEqualTo("mod-mock-3");
        assertThat(j.at("/model").asText()).isEqualTo("mistral-moderation-latest");
        assertThat(j.at("/results")).hasSize(2);
        JsonNode result = j.at("/results/1");
        assertThat(result.has("flagged")).isFalse();
        assertThat(result.at("/categories/violence_and_threats").asBoolean()).isTrue();
        assertThat(result.at("/categories/custom").asBoolean()).isTrue();
        assertThat(result.at("/categories/selfharm").asBoolean()).isFalse();
        assertThat(result.at("/categories/pii").asBoolean()).isFalse();
        assertThat(result.at("/category_scores/violence_and_threats").asDouble()).isEqualTo(1.0);
        assertThat(result.at("/category_scores/sexual").asDouble()).isZero();

        JsonNode clean = m.readTree(d.render(MODERATION, ModerationReply.notFlagged(), req("{\"input\":\"a\"}")).body());
        assertThat(clean.at("/results")).hasSize(1);
        assertThat(clean.at("/model").asText()).isEqualTo("mock-moderation-model");
        clean.at("/results/0/categories").forEach(c -> assertThat(c.asBoolean()).isFalse());
    }

    @Test
    void rendersValidationErrorEnvelope() throws Exception {
        Rendered r = d.renderError(CHAT, 429, "rate limited");
        assertThat(r.status()).isEqualTo(429);
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/detail")).hasSize(1);
        assertThat(j.at("/detail/0/msg").asText()).isEqualTo("rate limited");
        assertThat(j.at("/detail/0/type").asText()).isEqualTo("mock_error");
        assertThat(j.at("/detail/0/loc").isArray()).isTrue();
    }

    @Test
    void unsupportedReplyIsUnrenderable() {
        assertThatThrownBy(() -> d.render(CHAT, ScoringReply.equalScores(), req("{}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("not supported");
    }
}
