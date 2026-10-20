package io.quarkiverse.langchain4j.mockapi.internal.dialect.openai;

import static io.quarkiverse.langchain4j.mockapi.internal.dialect.openai.OpenAiTestRequests.match;
import static io.quarkiverse.langchain4j.mockapi.internal.dialect.openai.OpenAiTestRequests.req;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;

class OpenAiDialectResponsesTest {

    OpenAiDialect d = new OpenAiDialect();
    ObjectMapper m = MockApiJson.mapper();

    @Test
    void parsesResponsesInput() throws Exception {
        String body = """
                {"model":"gpt-4o","instructions":"be nice","max_output_tokens":50,"stream":true,
                 "input":[{"role":"user","content":[{"type":"input_text","text":"hi "},{"type":"input_text","text":"there"}]},
                          {"role":"assistant","content":"ok"}],
                 "tools":[{"type":"function","name":"f","description":"d","parameters":{"type":"object"}}]}""";
        ChatRequestView v = d.parseChat(match(Operation.CHAT, true, "responses"), m.readTree(body));
        assertThat(v.modelName()).isEqualTo("gpt-4o");
        assertThat(v.stream()).isTrue();
        assertThat(v.maxTokens()).isEqualTo(50);
        assertThat(v.messages()).extracting(ChatRequestView.Message::role).containsExactly("system", "user", "assistant");
        assertThat(v.messages().get(0).text()).isEqualTo("be nice");
        assertThat(v.messages().get(1).text()).isEqualTo("hi there");
        assertThat(v.tools()).singleElement().extracting(ChatRequestView.ToolDefinition::name).isEqualTo("f");
        assertThat(v.tools().get(0).parametersSchema().get("type").asText()).isEqualTo("object");
    }

    @Test
    void parsesStringInput() throws Exception {
        ChatRequestView v = d.parseChat(match(Operation.CHAT, false, "responses"), m.readTree("{\"input\":\"hello\"}"));
        assertThat(v.messages()).singleElement().satisfies(msg -> {
            assertThat(msg.role()).isEqualTo("user");
            assertThat(msg.text()).isEqualTo("hello");
        });
    }

    @Test
    void rendersResponsesText() throws Exception {
        Rendered r = d.render(match(Operation.CHAT, false, "responses"), ChatReply.text("hi").usage(3, 4),
                req("/v1/responses", Operation.CHAT, "{\"model\":\"gpt-4o\"}"));
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/object").asText()).isEqualTo("response");
        assertThat(j.at("/status").asText()).isEqualTo("completed");
        assertThat(j.at("/model").asText()).isEqualTo("gpt-4o");
        assertThat(j.at("/output/0/type").asText()).isEqualTo("message");
        assertThat(j.at("/output/0/role").asText()).isEqualTo("assistant");
        assertThat(j.at("/output/0/status").asText()).isEqualTo("completed");
        assertThat(j.at("/output/0/content/0/type").asText()).isEqualTo("output_text");
        assertThat(j.at("/output/0/content/0/text").asText()).isEqualTo("hi");
        assertThat(j.at("/output/0/content/0/annotations").isArray()).isTrue();
        assertThat(j.at("/usage/input_tokens").asInt()).isEqualTo(3);
        assertThat(j.at("/usage/total_tokens").asInt()).isEqualTo(7);
    }

    @Test
    void rendersResponsesFunctionCall() throws Exception {
        Rendered r = d.render(match(Operation.CHAT, false, "responses"), ChatReply.toolCall("f", "{\"a\":1}"),
                req("/v1/responses", Operation.CHAT, "{}"));
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/output")).hasSize(1);
        assertThat(j.at("/output/0/type").asText()).isEqualTo("function_call");
        assertThat(j.at("/output/0/name").asText()).isEqualTo("f");
        assertThat(j.at("/output/0/call_id").asText()).isNotEmpty();
        assertThat(j.at("/output/0/arguments").asText()).isEqualTo("{\"a\":1}");
    }

    @Test
    void rendersThinkingAsReasoningItem() throws Exception {
        Rendered r = d.render(match(Operation.CHAT, false, "responses"), ChatReply.text("hi").thinking("hmm"),
                req("/v1/responses", Operation.CHAT, "{}"));
        JsonNode j = m.readTree(r.body());
        assertThat(j.at("/output/0/type").asText()).isEqualTo("reasoning");
        assertThat(j.at("/output/0/summary/0/type").asText()).isEqualTo("summary_text");
        assertThat(j.at("/output/0/summary/0/text").asText()).isEqualTo("hmm");
        assertThat(j.at("/output/1/type").asText()).isEqualTo("message");
    }

    @Test
    void streamsResponsesEvents() throws Exception {
        Rendered r = d.render(match(Operation.CHAT, true, "responses"), ChatReply.text("hi there").usage(1, 2),
                req("/v1/responses", Operation.CHAT, "{\"stream\":true}"));
        assertThat(r.streamFormat()).isEqualTo(Rendered.StreamFormat.SSE);
        List<String> names = r.events().stream().map(SseEvent::event).toList();
        assertThat(names).startsWith("response.created").endsWith("response.completed");
        assertThat(names).containsSubsequence("response.created", "response.output_item.added",
                "response.content_part.added", "response.output_text.delta", "response.output_text.done",
                "response.content_part.done", "response.output_item.done", "response.completed");
        StringBuilder text = new StringBuilder();
        int seq = 0;
        for (SseEvent e : r.events()) {
            JsonNode j = m.readTree(e.data());
            assertThat(j.at("/type").asText()).isEqualTo(e.event());
            assertThat(j.at("/sequence_number").asInt()).isEqualTo(seq++);
            if ("response.output_text.delta".equals(e.event())) {
                text.append(j.at("/delta").asText());
            }
        }
        assertThat(text.toString()).isEqualTo("hi there");
        JsonNode completed = m.readTree(r.events().get(r.events().size() - 1).data());
        assertThat(completed.at("/response/status").asText()).isEqualTo("completed");
        assertThat(completed.at("/response/output/0/content/0/text").asText()).isEqualTo("hi there");
        assertThat(completed.at("/response/usage/total_tokens").asInt()).isEqualTo(3);
    }

    @Test
    void streamsResponsesFunctionCall() throws Exception {
        Rendered r = d.render(match(Operation.CHAT, true, "responses"), ChatReply.toolCall("f", "{\"a\":1}"),
                req("/v1/responses", Operation.CHAT, "{\"stream\":true}"));
        List<String> names = r.events().stream().map(SseEvent::event).toList();
        assertThat(names).containsSubsequence("response.created", "response.output_item.added",
                "response.function_call_arguments.delta", "response.function_call_arguments.done",
                "response.output_item.done", "response.completed");
        SseEvent done = r.events().stream().filter(e -> e.event().equals("response.function_call_arguments.done")).findFirst()
                .orElseThrow();
        assertThat(m.readTree(done.data()).at("/arguments").asText()).isEqualTo("{\"a\":1}");
    }
}
