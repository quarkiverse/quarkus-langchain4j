package io.quarkiverse.langchain4j.mockapi.internal.dialect.openai;

import static io.quarkiverse.langchain4j.mockapi.internal.dialect.openai.OpenAiTestRequests.match;
import static io.quarkiverse.langchain4j.mockapi.internal.dialect.openai.OpenAiTestRequests.req;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.ToolCall;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;

class OpenAiDialectChatTest {

    OpenAiDialect d = new OpenAiDialect();
    ObjectMapper m = MockApiJson.mapper();

    @Test
    void matchesChatCompletionsAndAzurePaths() throws Exception {
        assertThat(d.match("POST", "/v1/chat/completions", Map.of(), m.readTree("{\"stream\":true}")))
                .contains(new Dialect.Match(Operation.CHAT, true, "chat-completions"));
        assertThat(d.match("POST", "/openai/deployments/x/chat/completions", Map.of("api-version", List.of("2024-10-21")),
                null))
                .contains(new Dialect.Match(Operation.CHAT, false, "chat-completions"));
        assertThat(d.match("GET", "/v1/chat/completions", Map.of(), null)).isEmpty();
        assertThat(d.match("POST", "/messages", Map.of(), null)).isEmpty();
    }

    @Test
    void rendersTextReply() throws Exception {
        Rendered r = d.render(match(Operation.CHAT, false, "chat-completions"), ChatReply.text("hello").usage(14, 47).id("x1"),
                req("/v1/chat/completions", Operation.CHAT, "{\"model\":\"gpt-4o-mini\"}"));
        JsonNode j = m.readTree(r.body());
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.headers()).containsEntry("Content-Type", "application/json");
        assertThat(j.at("/id").asText()).isEqualTo("x1");
        assertThat(j.at("/model").asText()).isEqualTo("gpt-4o-mini");
        assertThat(j.at("/choices/0/message/content").asText()).isEqualTo("hello");
        assertThat(j.at("/choices/0/finish_reason").asText()).isEqualTo("stop");
        assertThat(j.at("/usage/total_tokens").asInt()).isEqualTo(61);
    }

    @Test
    void rendersToolCallWithStringArguments() throws Exception {
        Rendered r = d.render(match(Operation.CHAT, false, "chat-completions"),
                ChatReply.toolCall("getWeather", Map.of("city", "Paris")), req("/v1/chat/completions", Operation.CHAT, "{}"));
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/choices/0/message/content").isNull()).isTrue();
        assertThat(j.at("/choices/0/message/tool_calls/0/type").asText()).isEqualTo("function");
        assertThat(j.at("/choices/0/message/tool_calls/0/function/name").asText()).isEqualTo("getWeather");
        assertThat(j.at("/choices/0/message/tool_calls/0/function/arguments").isTextual()).isTrue();
        assertThat(j.at("/choices/0/message/tool_calls/0/function/arguments").asText()).isEqualTo("{\"city\":\"Paris\"}");
        assertThat(j.at("/choices/0/finish_reason").asText()).isEqualTo("tool_calls");
    }

    @Test
    void rendersError() throws Exception {
        Rendered r = d.renderError(match(Operation.CHAT, false, "chat-completions"), 429, "rate limited");
        assertThat(r.status()).isEqualTo(429);
        assertThat(m.readTree(r.body()).at("/error/message").asText()).isEqualTo("rate limited");
    }

    @Test
    void parsesRequest() throws Exception {
        String body = """
                {"model":"gpt-4o-mini","temperature":0.2,"max_tokens":100,"stream":false,
                 "messages":[{"role":"system","content":"be nice"},
                             {"role":"user","content":[{"type":"text","text":"hi "},{"type":"text","text":"there"}]},
                             {"role":"assistant","content":null,"tool_calls":[{"id":"c1","type":"function","function":{"name":"f","arguments":"{}"}}]},
                             {"role":"tool","tool_call_id":"c1","content":"42"}],
                 "tools":[{"type":"function","function":{"name":"f","description":"d","parameters":{"type":"object"}}}],
                 "tool_choice":"auto","response_format":{"type":"json_schema","json_schema":{"schema":{"type":"object"}}}}""";
        ChatRequestView v = d.parseChat(match(Operation.CHAT, false, "chat-completions"), m.readTree(body));
        assertThat(v.modelName()).isEqualTo("gpt-4o-mini");
        assertThat(v.temperature()).isEqualTo(0.2);
        assertThat(v.maxTokens()).isEqualTo(100);
        assertThat(v.messages()).extracting(ChatRequestView.Message::role).containsExactly("system", "user", "assistant",
                "tool");
        assertThat(v.messages().get(1).text()).isEqualTo("hi there");
        assertThat(v.messages().get(2).toolCalls()).singleElement().extracting(ToolCall::name).isEqualTo("f");
        assertThat(v.messages().get(3).toolCallId()).isEqualTo("c1");
        assertThat(v.tools()).singleElement().extracting(ChatRequestView.ToolDefinition::name).isEqualTo("f");
        assertThat(v.toolChoice()).isEqualTo("auto");
        assertThat(v.responseFormatType()).isEqualTo("json_schema");
        assertThat(v.responseFormatSchema().get("type").asText()).isEqualTo("object");
    }
}
