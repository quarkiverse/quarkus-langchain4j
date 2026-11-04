package io.quarkiverse.langchain4j.compaction.strategies;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.compaction.CompactionContext;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.CompactionStrategy;
import io.quarkiverse.langchain4j.compaction.Conversation;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionConfig;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionModelsConfig;
import io.smallrye.common.annotation.Identifier;

/**
 * Keeps the last turns plus the older turns most relevant to the latest user message, in their original order.
 * <p>
 * Relevance is the cosine similarity of embeddings when an embedding model is available, and a lexical (Jaccard)
 * similarity otherwise. Turn embeddings are cached in the conversation state, so each turn is embedded once.
 */
@Singleton
@Identifier(Strategies.RELEVANCE)
public class RelevanceStrategy implements CompactionStrategy {

    private static final Logger LOG = Logger.getLogger(RelevanceStrategy.class);

    public record Options(int keepLastTurns, int topK) {
    }

    private final Options options;
    private final EmbeddingModel embeddingModel;

    @Inject
    public RelevanceStrategy(CompactionConfig config, CompactionModelsConfig models,
            @Any Instance<EmbeddingModel> embeddingModels) {
        this(config.defaultProfile(), select(models, embeddingModels));
    }

    public RelevanceStrategy(CompactionConfig.Profile profile, EmbeddingModel embeddingModel) {
        this(new Options(profile.relevance().keepLastTurns(), profile.relevance().topK()), embeddingModel);
    }

    public RelevanceStrategy(Options options, EmbeddingModel embeddingModel) {
        this.options = options;
        this.embeddingModel = embeddingModel;
    }

    /**
     * @return the embedding model configured for relevance scoring, {@code null} if none is available
     */
    public static EmbeddingModel select(CompactionModelsConfig config, Instance<EmbeddingModel> models) {
        Instance<EmbeddingModel> candidate = config.relevance().embeddingModelName()
                .map(name -> models.select(ModelName.Literal.of(name)))
                .orElseGet(() -> models.select(jakarta.enterprise.inject.Default.Literal.INSTANCE));
        return candidate.isResolvable() ? candidate.get() : null;
    }

    @Override
    public List<ChatMessage> compact(Conversation conversation, CompactionContext context) {
        List<Conversation.Turn> turns = conversation.turns();
        if (turns.size() <= options.keepLastTurns() + options.topK()) {
            return conversation.toMessages();
        }
        int split = turns.size() - options.keepLastTurns();
        List<Conversation.Turn> candidates = turns.subList(0, split);
        String query = latestUserText(turns);

        Scorer scorer = scorer(query, context);
        List<Conversation.Turn> ranked = new ArrayList<>(candidates);
        Map<Conversation.Turn, Double> scores = new HashMap<>();
        for (Conversation.Turn turn : candidates) {
            scores.put(turn, scorer.score(turn));
        }
        ranked.sort(Comparator.comparingDouble((Conversation.Turn t) -> scores.get(t)).reversed());
        Set<Integer> selected = new HashSet<>();
        ranked.stream().limit(options.topK()).forEach(t -> selected.add(t.index()));

        List<Conversation.Turn> kept = new ArrayList<>();
        for (Conversation.Turn turn : turns) {
            if (turn.index() >= split || selected.contains(turn.index())) {
                kept.add(turn);
            }
        }
        context.note(Strategies.RELEVANCE, "kept " + selected.size() + " of " + candidates.size()
                + " older turns by " + scorer.name() + " similarity, dropped " + (candidates.size() - selected.size()));
        return conversation.assembleTurns(kept);
    }

    private static String latestUserText(List<Conversation.Turn> turns) {
        for (int i = turns.size() - 1; i >= 0; i--) {
            ChatMessage first = turns.get(i).first();
            if (first instanceof UserMessage && !CompactionMessages.isDerived(first)) {
                return CompactionMessages.text(first);
            }
        }
        return "";
    }

    private interface Scorer {
        double score(Conversation.Turn turn);

        String name();
    }

    private Scorer scorer(String query, CompactionContext context) {
        if (embeddingModel != null) {
            try {
                Embedding q = embeddingModel.embed(query).content();
                Map<String, Object> cache = context.state(Strategies.RELEVANCE);
                return new Scorer() {
                    @Override
                    public double score(Conversation.Turn turn) {
                        String text = turn.text();
                        Embedding e = (Embedding) cache.computeIfAbsent(text,
                                k -> embeddingModel.embed(text).content());
                        return CosineSimilarity.between(q, e);
                    }

                    @Override
                    public String name() {
                        return "embedding";
                    }
                };
            } catch (RuntimeException e) {
                LOG.warnf(e, "Embedding failed, falling back to lexical similarity");
                context.note(Strategies.RELEVANCE, "embedding failed, used lexical similarity");
            }
        }
        Set<String> queryTerms = terms(query);
        return new Scorer() {
            @Override
            public double score(Conversation.Turn turn) {
                Set<String> turnTerms = terms(turn.text());
                if (queryTerms.isEmpty() || turnTerms.isEmpty()) {
                    return 0;
                }
                Set<String> intersection = new HashSet<>(queryTerms);
                intersection.retainAll(turnTerms);
                Set<String> union = new HashSet<>(queryTerms);
                union.addAll(turnTerms);
                return (double) intersection.size() / union.size();
            }

            @Override
            public String name() {
                return "lexical";
            }
        };
    }

    static Set<String> terms(String text) {
        Set<String> terms = new HashSet<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (token.length() > 2) {
                terms.add(token);
            }
        }
        return terms;
    }

    @Override
    public String description() {
        return "Keeps the last " + options.keepLastTurns() + " turns and the " + options.topK()
                + " older turns most relevant to the latest question";
    }
}
