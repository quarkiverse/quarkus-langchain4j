package io.quarkiverse.langchain4j.mockapi.internal.dialect.openai;

import static io.quarkiverse.langchain4j.mockapi.internal.dialect.openai.OpenAiTestRequests.match;
import static io.quarkiverse.langchain4j.mockapi.internal.dialect.openai.OpenAiTestRequests.req;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.SseEvent;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Chunker;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;

class OpenAiDialectStreamingTest {

    OpenAiDialect d = new OpenAiDialect();
    ObjectMapper m = MockApiJson.mapper();

    @Test
    void chunksWordsWithLeadingSpace() {
        assertThat(Chunker.chunks(ChatReply.text("As an AI"))).containsExactly("As", " an", " AI");
        assertThat(Chunker.chunks(ChatReply.text(""))).isEmpty();
        assertThat(Chunker.chunks(ChatReply.text("a b").chunks(List.of("a b")))).containsExactly("a b");
    }

    @Test
    void streamsTextThenFinishThenDone() throws Exception {
        Rendered r = d.render(match(Operation.CHAT, true, "chat-completions"), ChatReply.text("hi there").usage(1, 2),
                req("/v1/chat/completions", Operation.CHAT, "{\"model\":\"m\",\"stream\":true}"));
        assertThat(r.streamFormat()).isEqualTo(Rendered.StreamFormat.SSE);
        List<String> data = r.events().stream().map(SseEvent::data).toList();
        assertThat(data).hasSize(4);
        assertThat(m.readTree(data.get(0)).at("/choices/0/delta/role").asText()).isEqualTo("assistant");
        assertThat(m.readTree(data.get(0)).at("/choices/0/delta/content").asText()).isEqualTo("hi");
        assertThat(m.readTree(data.get(0)).at("/object").asText()).isEqualTo("chat.completion.chunk");
        assertThat(m.readTree(data.get(1)).at("/choices/0/delta/role").isMissingNode()).isTrue();
        assertThat(m.readTree(data.get(1)).at("/choices/0/delta/content").asText()).isEqualTo(" there");
        assertThat(m.readTree(data.get(2)).at("/choices/0/finish_reason").asText()).isEqualTo("stop");
        assertThat(m.readTree(data.get(0)).at("/id").asText()).isEqualTo(m.readTree(data.get(2)).at("/id").asText());
        assertThat(data.get(3)).isEqualTo("[DONE]");
    }

    @Test
    void usageChunkOnlyWhenRequested() throws Exception {
        Rendered r = d.render(match(Operation.CHAT, true, "chat-completions"), ChatReply.text("x").usage(1, 2),
                req("/v1/chat/completions", Operation.CHAT, "{\"stream\":true,\"stream_options\":{\"include_usage\":true}}"));
        List<String> data = r.events().stream().map(SseEvent::data).toList();
        JsonNode usageChunk = m.readTree(data.get(data.size() - 2));
        assertThat(usageChunk.at("/choices").isEmpty()).isTrue();
        assertThat(usageChunk.at("/usage/completion_tokens").asInt()).isEqualTo(2);
        assertThat(data.get(data.size() - 1)).isEqualTo("[DONE]");

        Rendered without = d.render(match(Operation.CHAT, true, "chat-completions"), ChatReply.text("x").usage(1, 2),
                req("/v1/chat/completions", Operation.CHAT, "{\"stream\":true}"));
        assertThat(without.events()).hasSize(3);
    }

    @Test
    void streamsToolCall() throws Exception {
        Rendered r = d.render(match(Operation.CHAT, true, "chat-completions"), ChatReply.toolCall("f", "{}"),
                req("/v1/chat/completions", Operation.CHAT, "{\"stream\":true}"));
        JsonNode first = m.readTree(r.events().get(0).data());
        assertThat(first.at("/choices/0/delta/tool_calls/0/function/name").asText()).isEqualTo("f");
        assertThat(first.at("/choices/0/delta/tool_calls/0/index").asInt()).isZero();
        assertThat(first.at("/choices/0/delta/tool_calls/0/type").asText()).isEqualTo("function");
        assertThat(m.readTree(r.events().get(1).data()).at("/choices/0/finish_reason").asText()).isEqualTo("tool_calls");
    }
}
