package io.quarkiverse.langchain4j.mockapi.internal.dialect.anthropic;

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
import io.quarkiverse.langchain4j.mockapi.FinishReason;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.CapturedRequestImpl;

class AnthropicDialectTest {

    private static final Dialect.Match CHAT = new Dialect.Match(Operation.CHAT, false, "messages");
    private static final Dialect.Match STREAM = new Dialect.Match(Operation.CHAT, true, "messages");

    AnthropicDialect d = new AnthropicDialect();
    ObjectMapper m = MockApiJson.mapper();

    static CapturedRequest req(String body) {
        return new CapturedRequestImpl(3, "POST", "/anthropic/v1/messages", null, Map.of(), body, Operation.CHAT,
                "anthropic", null, null);
    }

    @Test
    void matchesMessagesUnderItsPrefix() throws Exception {
        assertThat(d.id()).isEqualTo("anthropic");
        assertThat(d.pathPrefix()).isEqualTo("/anthropic");
        assertThat(d.match("POST", "/v1/messages", Map.of(), m.readTree("{\"stream\":true}"))).contains(STREAM);
        assertThat(d.match("POST", "/v1/messages", Map.of(), null)).contains(CHAT);
        assertThat(d.match("GET", "/v1/messages", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/v1/chat/completions", Map.of(), null)).isEmpty();
    }

    @Test
    void parsesRequest() throws Exception {
        String body = """
                {"model":"claude-sonnet-4-5","max_tokens":100,"temperature":0.2,"top_p":0.7,"stream":false,
                 "system":[{"type":"text","text":"be "},{"type":"text","text":"nice"}],
                 "messages":[{"role":"user","content":"hi"},
                             {"role":"assistant","content":[{"type":"text","text":"let me check"},
                                {"type":"tool_use","id":"toolu_1","name":"getWeather","input":{"city":"Paris"}}]},
                             {"role":"user","content":[{"type":"tool_result","tool_use_id":"toolu_1","content":"rainy"},
                                {"type":"text","text":"thanks"}]}],
                 "tools":[{"name":"getWeather","description":"weather","input_schema":{"type":"object"}}],
                 "tool_choice":{"type":"tool","name":"getWeather"}}
                """;
        ChatRequestView v = d.parseChat(CHAT, m.readTree(body));
        assertThat(v.modelName()).isEqualTo("claude-sonnet-4-5");
        assertThat(v.maxTokens()).isEqualTo(100);
        assertThat(v.temperature()).isEqualTo(0.2);
        assertThat(v.topP()).isEqualTo(0.7);
        assertThat(v.stream()).isFalse();
        assertThat(v.toolChoice()).isEqualTo("getWeather");
        assertThat(v.tools()).singleElement().satisfies(t -> {
            assertThat(t.name()).isEqualTo("getWeather");
            assertThat(t.description()).isEqualTo("weather");
            assertThat(t.parametersSchema().path("type").asText()).isEqualTo("object");
        });
        List<ChatRequestView.Message> msgs = v.messages();
        assertThat(msgs).extracting(ChatRequestView.Message::role)
                .containsExactly("system", "user", "assistant", "tool", "user");
        assertThat(msgs.get(0).text()).isEqualTo("be nice");
        assertThat(msgs.get(1).text()).isEqualTo("hi");
        assertThat(msgs.get(2).text()).isEqualTo("let me check");
        assertThat(msgs.get(2).toolCalls()).singleElement().satisfies(tc -> {
            assertThat(tc.id()).isEqualTo("toolu_1");
            assertThat(tc.name()).isEqualTo("getWeather");
            assertThat(m.readTree(tc.argumentsJson())).isEqualTo(m.readTree("{\"city\":\"Paris\"}"));
        });
        assertThat(msgs.get(3).toolCallId()).isEqualTo("toolu_1");
        assertThat(msgs.get(3).text()).isEqualTo("rainy");
        assertThat(msgs.get(4).text()).isEqualTo("thanks");
    }

    @Test
    void parsesStringSystemToolResultBlocksAndSimpleToolChoice() throws Exception {
        String body = """
                {"model":"m","system":"sys","stream":true,
                 "messages":[{"role":"user","content":[{"type":"tool_result","tool_use_id":"t",
                    "content":[{"type":"text","text":"a"},{"type":"text","text":"b"}]}]}],
                 "tool_choice":{"type":"any"}}
                """;
        ChatRequestView v = d.parseChat(STREAM, m.readTree(body));
        assertThat(v.stream()).isTrue();
        assertThat(v.toolChoice()).isEqualTo("any");
        assertThat(v.messages()).extracting(ChatRequestView.Message::role).containsExactly("system", "tool");
        assertThat(v.messages().get(0).text()).isEqualTo("sys");
        assertThat(v.messages().get(1).text()).isEqualTo("ab");
        assertThat(v.tools()).isEmpty();
    }

    @Test
    void rendersTextReply() throws Exception {
        Rendered r = d.render(CHAT, ChatReply.text("hello").usage(14, 47),
                req("{\"model\":\"claude-sonnet-4-5\"}"));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.headers()).containsEntry("Content-Type", "application/json");
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/id").asText()).isEqualTo("msg_mock_3");
        assertThat(j.at("/type").asText()).isEqualTo("message");
        assertThat(j.at("/role").asText()).isEqualTo("assistant");
        assertThat(j.at("/model").asText()).isEqualTo("claude-sonnet-4-5");
        assertThat(j.at("/content")).hasSize(1);
        assertThat(j.at("/content/0/type").asText()).isEqualTo("text");
        assertThat(j.at("/content/0/text").asText()).isEqualTo("hello");
        assertThat(j.at("/stop_reason").asText()).isEqualTo("end_turn");
        assertThat(j.at("/stop_sequence").isNull()).isTrue();
        assertThat(j.at("/usage/input_tokens").asInt()).isEqualTo(14);
        assertThat(j.at("/usage/output_tokens").asInt()).isEqualTo(47);
    }

    @Test
    void mapsFinishReasons() throws Exception {
        assertThat(stopReason(ChatReply.text("x").finishReason(FinishReason.LENGTH))).isEqualTo("max_tokens");
        assertThat(stopReason(ChatReply.text("x").finishReason(FinishReason.CONTENT_FILTER))).isEqualTo("refusal");
        assertThat(stopReason(ChatReply.toolCall("f", "{}"))).isEqualTo("tool_use");
    }

    private String stopReason(ChatReply reply) throws Exception {
        return m.readTree(d.render(CHAT, reply, req("{}")).body()).at("/stop_reason").asText();
    }

    @Test
    void rendersToolUseWithObjectInputAndThinking() throws Exception {
        ChatReply reply = ChatReply.toolCall("getWeather", Map.of("city", "Paris")).thinking("hmm").modelName("x-model");
        JsonNode j = m.readTree(d.render(CHAT, reply, req("{\"model\":\"m\"}")).body());
        assertThat(j.at("/model").asText()).isEqualTo("x-model");
        assertThat(j.at("/content")).hasSize(2);
        assertThat(j.at("/content/0/type").asText()).isEqualTo("thinking");
        assertThat(j.at("/content/0/thinking").asText()).isEqualTo("hmm");
        assertThat(j.at("/content/0/signature").isTextual()).isTrue();
        assertThat(j.at("/content/1/type").asText()).isEqualTo("tool_use");
        assertThat(j.at("/content/1/id").asText()).isEqualTo("toolu_mock_0");
        assertThat(j.at("/content/1/name").asText()).isEqualTo("getWeather");
        assertThat(j.at("/content/1/input").isObject()).isTrue();
        assertThat(j.at("/content/1/input/city").asText()).isEqualTo("Paris");
        assertThat(j.at("/stop_reason").asText()).isEqualTo("tool_use");
    }

    @Test
    void keepsExplicitIdAndFallsBackToMockModel() throws Exception {
        ChatReply reply = ChatReply.text("t").id("msg_1");
        JsonNode j = m.readTree(d.render(CHAT, reply, req("{}")).body());
        assertThat(j.at("/id").asText()).isEqualTo("msg_1");
        assertThat(j.at("/model").asText()).isEqualTo("mock-model");
    }

    @Test
    void rendersErrorEnvelope() throws Exception {
        Rendered r = d.renderError(CHAT, 429, "rate limited");
        assertThat(r.status()).isEqualTo(429);
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/type").asText()).isEqualTo("error");
        assertThat(j.at("/error/type").asText()).isEqualTo("rate_limit_error");
        assertThat(j.at("/error/message").asText()).isEqualTo("rate limited");

        assertThat(errorType(400)).isEqualTo("invalid_request_error");
        assertThat(errorType(401)).isEqualTo("authentication_error");
        assertThat(errorType(404)).isEqualTo("not_found_error");
        assertThat(errorType(418)).isEqualTo("invalid_request_error");
        assertThat(errorType(500)).isEqualTo("api_error");
        assertThat(errorType(503)).isEqualTo("api_error");
        assertThat(errorType(529)).isEqualTo("overloaded_error");
    }

    private String errorType(int status) throws Exception {
        return m.readTree(d.renderError(CHAT, status, "x").body()).at("/error/type").asText();
    }

    @Test
    void streamsTextAsNamedEvents() throws Exception {
        Rendered r = d.render(STREAM, ChatReply.text("hi there").usage(5, 7), req("{\"model\":\"m\",\"stream\":true}"));
        assertThat(r.streamFormat()).isEqualTo(Rendered.StreamFormat.SSE);
        List<SseEvent> events = r.events();
        assertThat(events).extracting(SseEvent::event).containsExactly("message_start", "content_block_start",
                "content_block_delta", "content_block_delta", "content_block_stop", "message_delta", "message_stop");
        for (SseEvent e : events) {
            assertThat(m.readTree(e.data()).path("type").asText()).isEqualTo(e.event());
        }
        JsonNode start = m.readTree(events.get(0).data());
        assertThat(start.at("/message/id").asText()).isEqualTo("msg_mock_3");
        assertThat(start.at("/message/content").isEmpty()).isTrue();
        assertThat(start.at("/message/model").asText()).isEqualTo("m");
        assertThat(start.at("/message/stop_reason").isNull()).isTrue();
        assertThat(start.at("/message/usage/input_tokens").asInt()).isEqualTo(5);
        JsonNode blockStart = m.readTree(events.get(1).data());
        assertThat(blockStart.at("/index").asInt()).isZero();
        assertThat(blockStart.at("/content_block/type").asText()).isEqualTo("text");
        assertThat(blockStart.at("/content_block/text").asText()).isEmpty();
        assertThat(m.readTree(events.get(2).data()).at("/delta/type").asText()).isEqualTo("text_delta");
        assertThat(m.readTree(events.get(2).data()).at("/delta/text").asText()).isEqualTo("hi");
        assertThat(m.readTree(events.get(3).data()).at("/delta/text").asText()).isEqualTo(" there");
        assertThat(m.readTree(events.get(4).data()).at("/index").asInt()).isZero();
        JsonNode delta = m.readTree(events.get(5).data());
        assertThat(delta.at("/delta/stop_reason").asText()).isEqualTo("end_turn");
        assertThat(delta.at("/delta/stop_sequence").isNull()).isTrue();
        assertThat(delta.at("/usage/output_tokens").asInt()).isEqualTo(7);
    }

    @Test
    void streamsToolUseWithInputJsonDelta() throws Exception {
        ChatReply reply = ChatReply.text("ok").addToolCall("getWeather", "{\"city\":\"Paris\"}");
        List<SseEvent> events = d.render(STREAM, reply, req("{\"stream\":true}")).events();
        assertThat(events).extracting(SseEvent::event).containsExactly("message_start",
                "content_block_start", "content_block_delta", "content_block_stop",
                "content_block_start", "content_block_delta", "content_block_stop",
                "message_delta", "message_stop");
        JsonNode toolStart = m.readTree(events.get(4).data());
        assertThat(toolStart.at("/index").asInt()).isEqualTo(1);
        assertThat(toolStart.at("/content_block/type").asText()).isEqualTo("tool_use");
        assertThat(toolStart.at("/content_block/id").asText()).isEqualTo("toolu_mock_0");
        assertThat(toolStart.at("/content_block/name").asText()).isEqualTo("getWeather");
        assertThat(toolStart.at("/content_block/input").isObject()).isTrue();
        assertThat(toolStart.at("/content_block/input").isEmpty()).isTrue();
        JsonNode toolDelta = m.readTree(events.get(5).data());
        assertThat(toolDelta.at("/index").asInt()).isEqualTo(1);
        assertThat(toolDelta.at("/delta/type").asText()).isEqualTo("input_json_delta");
        assertThat(toolDelta.at("/delta/partial_json").asText()).isEqualTo("{\"city\":\"Paris\"}");
        assertThat(m.readTree(events.get(7).data()).at("/delta/stop_reason").asText()).isEqualTo("tool_use");
    }

    @Test
    void streamsThinkingBeforeText() throws Exception {
        List<SseEvent> events = d.render(STREAM, ChatReply.text("a").thinking("hmm"), req("{}")).events();
        JsonNode start = m.readTree(events.get(1).data());
        assertThat(start.at("/content_block/type").asText()).isEqualTo("thinking");
        assertThat(m.readTree(events.get(2).data()).at("/delta/type").asText()).isEqualTo("thinking_delta");
        assertThat(m.readTree(events.get(2).data()).at("/delta/thinking").asText()).isEqualTo("hmm");
        assertThat(m.readTree(events.get(3).data()).at("/delta/type").asText()).isEqualTo("signature_delta");
        assertThat(events.get(4).event()).isEqualTo("content_block_stop");
        assertThat(m.readTree(events.get(5).data()).at("/content_block/type").asText()).isEqualTo("text");
        assertThat(m.readTree(events.get(5).data()).at("/index").asInt()).isEqualTo(1);
    }

    @Test
    void streamsEmptyTextBlockWhenReplyHasNoContent() {
        List<SseEvent> events = d.render(STREAM, ChatReply.text(""), req("{}")).events();
        assertThat(events).extracting(SseEvent::event).containsExactly("message_start", "content_block_start",
                "content_block_delta", "content_block_stop", "message_delta", "message_stop");
    }

    @Test
    void invalidToolCallArgumentsAreUnrenderableNamingTheTool() {
        assertThatThrownBy(() -> d.render(CHAT, ChatReply.toolCall("getWeather", "{not json"), req("{}")))
                .isInstanceOf(UnrenderableReply.class).hasMessageContaining("getWeather");
    }
}
