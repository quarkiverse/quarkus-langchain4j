package io.quarkiverse.langchain4j.compaction.strategies;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkiverse.langchain4j.compaction.CompactionException;
import io.quarkiverse.langchain4j.compaction.CompactionStrategy;
import io.quarkiverse.langchain4j.compaction.Summarizer;
import io.quarkiverse.langchain4j.compaction.ToolOutputArchive;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionConfig;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionModelsConfig;
import io.smallrye.common.annotation.Identifier;

/**
 * Creates the strategy of a compaction profile. Built-in strategies get their own instance, configured with the options
 * of the profile; other identifiers resolve to the application strategy bean with that {@link Identifier}, shared by
 * all profiles.
 */
@Singleton
public class StrategyFactory {

    private final Summarizer summarizer;
    private final ToolOutputArchive archive;
    private final CompactionModelsConfig models;
    private final Instance<EmbeddingModel> embeddingModels;
    private final Instance<CompactionStrategy> strategies;

    @Inject
    public StrategyFactory(Summarizer summarizer, ToolOutputArchive archive, CompactionModelsConfig models,
            @Any Instance<EmbeddingModel> embeddingModels, @Any Instance<CompactionStrategy> strategies) {
        this.summarizer = summarizer;
        this.archive = archive;
        this.models = models;
        this.embeddingModels = embeddingModels;
        this.strategies = strategies;
    }

    /**
     * @param id the strategy identifier
     * @param profile the profile providing the options of built-in strategies
     * @throws CompactionException if the identifier is unknown
     */
    public CompactionStrategy create(String id, CompactionConfig.Profile profile) {
        return switch (id) {
            case Strategies.MESSAGE_WINDOW -> new MessageWindowStrategy(profile);
            case Strategies.TOKEN_WINDOW -> new TokenWindowStrategy();
            case Strategies.TOOL_OUTPUT_PRUNING -> new ToolOutputPruningStrategy(profile, archive);
            case Strategies.DUPLICATE_COLLAPSE -> new DuplicateCollapseStrategy();
            case Strategies.ROLLING_SUMMARY -> new RollingSummaryStrategy(profile, summarizer);
            case Strategies.ANCHORED_SUMMARY -> new AnchoredSummaryStrategy(profile, summarizer);
            case Strategies.TIME_DECAY -> new TimeDecayStrategy(profile, summarizer);
            case Strategies.RELEVANCE -> new RelevanceStrategy(profile,
                    RelevanceStrategy.select(models, embeddingModels));
            case Strategies.PIPELINE -> new PipelineStrategy(buildPipelineSteps(profile));
            default -> {
                Instance<CompactionStrategy> selected = strategies.select(Identifier.Literal.of(id));
                if (!selected.isResolvable()) {
                    throw new CompactionException("Unknown compaction strategy '" + id + "'");
                }
                yield selected.get();
            }
        };
    }

    public Map<String, CompactionStrategy> buildPipelineSteps(CompactionConfig.Profile profile) {
        Map<String, CompactionStrategy> steps = new LinkedHashMap<>();
        for (String step : profile.pipeline().steps()) {
            if (Strategies.PIPELINE.equals(step)) {
                throw new CompactionException("A pipeline cannot contain itself");
            }
            steps.put(step, create(step, profile));
        }
        return steps;
    }
}
