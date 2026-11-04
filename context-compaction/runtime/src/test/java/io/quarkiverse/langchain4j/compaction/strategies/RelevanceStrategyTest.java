package io.quarkiverse.langchain4j.compaction.strategies;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.compaction.CompactionInvariants;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.Fixtures;

class RelevanceStrategyTest {

    private static final String[] TOPICS = { "invoice refund for the blue kettle", "password reset",
            "delivery address change", "loyalty points balance", "store opening hours", "warranty of the toaster" };

    private static List<ChatMessage> history(String latestQuestion) {
        List<ChatMessage> history = new ArrayList<>();
        history.add(Fixtures.SYSTEM);
        for (String topic : TOPICS) {
            history.add(UserMessage.from("I have a question about " + topic));
            history.add(AiMessage.from("Here is the answer about " + topic));
        }
        history.add(UserMessage.from(latestQuestion));
        return history;
    }

    @Test
    void keepsTheMatchingOldTurnLexically() {
        List<ChatMessage> history = history("Back to the refund of the blue kettle invoice: when do I get it?");
        Fixtures.TestContext context = Fixtures.context(10_000);
        List<ChatMessage> view = new RelevanceStrategy(new RelevanceStrategy.Options(1, 1), null)
                .compact(history, context);
        assertThat(view).extracting(CompactionMessages::text)
                .contains("I have a question about invoice refund for the blue kettle")
                .doesNotContain("I have a question about password reset");
        assertThat(context.notes).anyMatch(n -> n.contains("lexical"));
    }

    @Test
    void usesEmbeddingsAndKeepsTheOriginalOrder() {
        // the embedding model knows that a "teapot" is a "kettle" and "opening" relates to "hours": no shared words
        KeywordEmbeddings embeddings = new KeywordEmbeddings();
        List<ChatMessage> history = history("My teapot reimbursement, and when do you open?");
        Fixtures.TestContext context = Fixtures.context(10_000);
        List<ChatMessage> view = new RelevanceStrategy(new RelevanceStrategy.Options(1, 2), embeddings)
                .compact(history, context);

        assertThat(view).extracting(CompactionMessages::text).containsSubsequence(
                "I have a question about invoice refund for the blue kettle",
                "I have a question about store opening hours",
                "My teapot reimbursement, and when do you open?");
        assertThat(view).extracting(CompactionMessages::text).doesNotContain("I have a question about password reset");
        assertThat(context.notes).anyMatch(n -> n.contains("embedding"));
        assertThat(CompactionInvariants.violations(history, view)).isEmpty();
    }

    @Test
    void embedsEachTurnOnce() {
        KeywordEmbeddings embeddings = new KeywordEmbeddings();
        RelevanceStrategy strategy = new RelevanceStrategy(new RelevanceStrategy.Options(1, 2), embeddings);
        Fixtures.TestContext context = Fixtures.context(10_000);
        List<ChatMessage> history = history("My teapot reimbursement?");

        strategy.compact(history, context);
        int first = embeddings.calls;
        assertThat(first).isEqualTo(1 + TOPICS.length); // the question and each older turn
        strategy.compact(history, context);
        assertThat(embeddings.calls - first).isEqualTo(1); // only the question again
    }

    @Test
    void fallsBackToLexicalSimilarityWhenEmbeddingFails() {
        EmbeddingModel failing = new EmbeddingModel() {
            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
                throw new IllegalStateException("embedding service down");
            }

            @Override
            public Response<Embedding> embed(String text) {
                throw new IllegalStateException("embedding service down");
            }
        };
        Fixtures.TestContext context = Fixtures.context(10_000);
        List<ChatMessage> view = new RelevanceStrategy(new RelevanceStrategy.Options(1, 1), failing)
                .compact(history("the blue kettle refund?"), context);
        assertThat(view).extracting(CompactionMessages::text)
                .contains("I have a question about invoice refund for the blue kettle");
        assertThat(context.notes).anyMatch(n -> n.contains("embedding failed"));
    }

    @Test
    void neverUsesASummaryAsTheQuestion() {
        List<ChatMessage> history = history("password reset again please");
        history.add(CompactionMessages.summary("the customer talked about loyalty points"));
        history.add(AiMessage.from("noted"));
        List<ChatMessage> view = new RelevanceStrategy(new RelevanceStrategy.Options(2, 1), null)
                .compact(history, Fixtures.context(10_000));
        assertThat(view).extracting(CompactionMessages::text).contains("I have a question about password reset");
    }

    /**
     * Embeds texts on a few concepts, each known by several words, so similar meanings without shared words are close.
     */
    static final class KeywordEmbeddings implements EmbeddingModel {

        static final String[][] CONCEPTS = { { "kettle", "teapot" }, { "refund", "reimbursement" },
                { "opening", "open", "hours" }, { "password" }, { "delivery", "address" }, { "loyalty", "points" },
                { "warranty", "toaster" } };

        int calls;

        @Override
        public Response<Embedding> embed(String text) {
            calls++;
            String lower = text.toLowerCase(Locale.ROOT);
            float[] vector = new float[CONCEPTS.length + 1];
            vector[CONCEPTS.length] = 0.01f; // never a zero vector
            for (int i = 0; i < CONCEPTS.length; i++) {
                for (String word : CONCEPTS[i]) {
                    if (lower.contains(word)) {
                        vector[i] = 1;
                    }
                }
            }
            return Response.from(Embedding.from(vector));
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            return Response.from(segments.stream().map(s -> embed(s.text()).content()).toList());
        }
    }
}
