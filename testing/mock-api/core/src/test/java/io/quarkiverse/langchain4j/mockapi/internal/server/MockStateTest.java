package io.quarkiverse.langchain4j.mockapi.internal.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkiverse.langchain4j.mockapi.Reply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.CapturedRequestImpl;
import io.quarkiverse.langchain4j.mockapi.internal.model.RawStub;

class MockStateTest {
    MockState state = new MockState(Map.of(Operation.CHAT, ChatReply.text("default")));

    @Test
    void queueThenUserDefaultThenBuiltIn() {
        state.enqueue(Operation.CHAT, List.of(ChatReply.text("a"), ChatReply.text("b")));
        assertThat(((ChatReply) state.next(Operation.CHAT)).text()).isEqualTo("a");
        assertThat(((ChatReply) state.next(Operation.CHAT)).text()).isEqualTo("b");
        assertThat(((ChatReply) state.next(Operation.CHAT)).text()).isEqualTo("default");
        state.setDefault(Operation.CHAT, ChatReply.text("mine"));
        assertThat(((ChatReply) state.next(Operation.CHAT)).text()).isEqualTo("mine");
        assertThat(state.next(Operation.EMBEDDINGS)).isNull();
    }

    @Test
    void resetKeepsBuiltInDefault() {
        state.setDefault(Operation.CHAT, ChatReply.text("mine"));
        state.reset();
        assertThat(((ChatReply) state.next(Operation.CHAT)).text()).isEqualTo("default");
    }

    @Test
    void rawStubGlobAndBodyPredicate() throws Exception {
        state.addRawStub(new RawStub("post", "/ml/v1/text/extractions/*", "/status", "done",
                List.of(RawResponse.of(200, Map.of(), "{}"))));
        JsonNode done = MockApiJson.mapper().readTree("{\"status\":\"done\"}");
        assertThat(state.matchRaw("POST", "/ml/v1/text/extractions/42", done)).isPresent();
        assertThat(state.matchRaw("POST", "/ml/v1/text/extractions/42/x", done)).isEmpty();
        assertThat(state.matchRaw("POST", "/ml/v1/text/extractions/42", MockApiJson.mapper().readTree("{\"status\":\"no\"}")))
                .isEmpty();
        assertThat(state.matchRaw("GET", "/ml/v1/text/extractions/42", done)).isEmpty();
    }

    @Test
    void rawStubRepliesInOrderAndTheLastOneSticks() throws Exception {
        state.addRawStub(new RawStub("GET", "/jobs/*", null, null,
                List.of(RawResponse.json(202, "{\"s\":1}"), RawResponse.json(202, "{\"s\":2}"),
                        RawResponse.json(200, "{\"s\":3}"))));
        assertThat(IntStream.range(0, 5).mapToObj(i -> state.matchRaw("GET", "/jobs/1", null).orElseThrow().next().body()))
                .containsExactly("{\"s\":1}", "{\"s\":2}", "{\"s\":3}", "{\"s\":3}", "{\"s\":3}");
    }

    @Test
    void rawStubsWithBodyPredicatesKeepSeparateQueues() throws Exception {
        state.addRawStub(new RawStub("POST", "/x", "/id", "a",
                List.of(RawResponse.json(200, "a1"), RawResponse.json(200, "a2"))));
        state.addRawStub(new RawStub("POST", "/x", "/id", "b", List.of(RawResponse.json(200, "b1"))));
        JsonNode a = MockApiJson.mapper().readTree("{\"id\":\"a\"}");
        JsonNode b = MockApiJson.mapper().readTree("{\"id\":\"b\"}");
        assertThat(state.matchRaw("POST", "/x", a).orElseThrow().next().body()).isEqualTo("a1");
        assertThat(state.matchRaw("POST", "/x", b).orElseThrow().next().body()).isEqualTo("b1");
        assertThat(state.matchRaw("POST", "/x", a).orElseThrow().next().body()).isEqualTo("a2");
        assertThat(state.matchRaw("POST", "/x", null)).isEmpty();
    }

    @Test
    void rawStubWithoutResponsesIsRejected() {
        assertThatThrownBy(() -> state.addRawStub(new RawStub("GET", "/x", null, null, List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentNextConsumesDistinctRepliesAndJournalsInOrder() throws Exception {
        state.enqueue(Operation.CHAT, IntStream.range(0, 100).mapToObj(i -> (Reply) ChatReply.text("r" + i)).toList());
        Set<String> seen = ConcurrentHashMap.newKeySet();
        var pool = Executors.newFixedThreadPool(8);
        try {
            var futures = IntStream.range(0, 100).mapToObj(i -> pool.submit(() -> {
                seen.add(((ChatReply) state.next(Operation.CHAT)).text());
                long idx = state.nextIndex();
                state.record(
                        new CapturedRequestImpl(idx, "POST", "/p", null, Map.of(), "", Operation.CHAT, "openai", null,
                                null));
            })).toList();
            for (var f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(seen).hasSize(100);
        assertThat(state.requests(Operation.CHAT)).hasSize(100).extracting(CapturedRequest::index).isSorted();
    }
}
