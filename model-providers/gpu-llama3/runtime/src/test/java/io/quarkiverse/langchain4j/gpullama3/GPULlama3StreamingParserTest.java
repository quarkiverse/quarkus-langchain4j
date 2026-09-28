package io.quarkiverse.langchain4j.gpullama3;

import static org.assertj.core.api.Assertions.assertThat;

import org.beehive.jitllm.api.GenerationEvent;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

/**
 * What reaches the client while tokens stream. Markers arrive whole or split across events
 * depending on the tokenizer, and both must keep tool-call JSON out of the visible stream.
 */
class GPULlama3StreamingParserTest {

    static final class Recorder implements StreamingChatResponseHandler {
        final StringBuilder response = new StringBuilder();
        final StringBuilder thinking = new StringBuilder();

        @Override
        public void onPartialResponse(String partialResponse) {
            response.append(partialResponse);
        }

        @Override
        public void onPartialThinking(PartialThinking partialThinking) {
            thinking.append(partialThinking.text());
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
        }

        @Override
        public void onError(Throwable error) {
        }
    }

    private static Recorder stream(String... pieces) {
        return stream(false, false, pieces);
    }

    /** As the streaming bean drives it: finish, then release held text unless the engine found a call. */
    private static Recorder stream(boolean toolsOffered, boolean engineFoundCall, String... pieces) {
        Recorder recorder = new Recorder();
        GPULlama3ResponseParser.StreamingParser parser = GPULlama3ResponseParser.createStreamingParser(recorder,
                toolsOffered);
        for (String piece : pieces) {
            parser.onEvent(new GenerationEvent(0, piece));
        }
        parser.finish();
        if (!engineFoundCall) {
            parser.releaseHeldText();
        }
        return recorder;
    }

    @Test
    void aGemma4ToolCallKeepsTheCallOutOfTheStream() {
        Recorder recorder = stream("<|tool_call>", "call:getWeather{city:<|\"|>Berlin<|\"|>}", "<tool_call|>");

        assertThat(recorder.response.toString()).isEmpty();
    }

    @Test
    void aSplitGemma4ToolCallMarkerKeepsTheCallOutOfTheStream() {
        Recorder recorder = stream("<|", "tool", "_call>call:getWeather{}<tool", "_call|>", " ok");

        assertThat(recorder.response.toString()).isEqualTo(" ok");
    }

    @Test
    void aBareJsonCallIsHeldWhenToolsAreOfferedAndTheEngineFoundACall() {
        Recorder recorder = stream(true, true, "{\"name\": ", "\"getWeather\", \"parameters\": {}}");

        assertThat(recorder.response.toString()).isEmpty();
    }

    @Test
    void aBareJsonResponseIsStreamedAfterAllWhenTheEngineFoundNoCall() {
        Recorder recorder = stream(true, false, "{\"a\": 1}");

        assertThat(recorder.response.toString()).isEqualTo("{\"a\": 1}");
    }

    @Test
    void jsonIsStreamedAsTextWhenNoToolsWereOffered() {
        Recorder recorder = stream(false, false, "{\"a\": 1}");

        assertThat(recorder.response.toString()).isEqualTo("{\"a\": 1}");
    }

    @Test
    void aBraceLaterInAToolTurnAnswerIsStreamedUnchanged() {
        Recorder recorder = stream(true, false, "Use ", "{x}");

        assertThat(recorder.response.toString()).isEqualTo("Use {x}");
    }

    @Test
    void aWholeToolCallMarkerKeepsTheCallOutOfTheStream() {
        Recorder recorder = stream("<tool_call>", "\n{\"name\": \"getWeather\"}\n", "</tool_call>");

        assertThat(recorder.response.toString()).isEmpty();
    }

    @Test
    void aToolCallMarkerSplitAcrossEventsKeepsTheCallOutOfTheStream() {
        Recorder recorder = stream("<", "tool", "_call", ">\n{\"name\": ", "\"getWeather\"}\n</", "tool_call", ">");

        assertThat(recorder.response.toString()).isEmpty();
    }

    @Test
    void textAfterASplitCloseMarkerStreamsAgain() {
        Recorder recorder = stream("<tool_call>{}</tool", "_call>", " done");

        assertThat(recorder.response.toString()).isEqualTo(" done");
    }

    @Test
    void textThatOnlyLooksLikeTheStartOfAMarkerIsStreamedUnchanged() {
        Recorder recorder = stream("a <", "b and <to", "p");

        assertThat(recorder.response.toString()).isEqualTo("a <b and <top");
    }

    @Test
    void aHeldBackPrefixIsFlushedWhenGenerationEnds() {
        Recorder recorder = stream("x <tool");

        assertThat(recorder.response.toString()).isEqualTo("x <tool");
    }

    @Test
    void aSplitThinkMarkerRoutesTheReasoningToThinking() {
        Recorder recorder = stream("<th", "ink>plan</th", "ink>answer");

        assertThat(recorder.thinking.toString()).isEqualTo("<think>plan</think>");
        assertThat(recorder.response.toString()).isEqualTo("answer");
    }
}
