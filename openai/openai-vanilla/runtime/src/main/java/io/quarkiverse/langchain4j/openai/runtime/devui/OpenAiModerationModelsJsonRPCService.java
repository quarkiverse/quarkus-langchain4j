package io.quarkiverse.langchain4j.openai.runtime.devui;

import java.time.Duration;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.moderation.Categories;
import dev.ai4j.openai4j.moderation.CategoryScores;
import dev.ai4j.openai4j.moderation.ModerationRequest;
import dev.ai4j.openai4j.moderation.ModerationResponse;
import dev.ai4j.openai4j.moderation.ModerationResult;
import dev.langchain4j.internal.RetryUtils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class OpenAiModerationModelsJsonRPCService {

    @Inject
    @ConfigProperty(name = "quarkus.langchain4j.openai.base-url")
    String baseUrl;

    @Inject
    @ConfigProperty(name = "quarkus.langchain4j.openai.api-key")
    String apiKey;

    @Inject
    @ConfigProperty(name = "quarkus.langchain4j.openai.timeout")
    Duration timeout;

    @Inject
    @ConfigProperty(name = "quarkus.langchain4j.openai.image-model.user")
    Optional<String> user;

    @Inject
    @ConfigProperty(name = "quarkus.langchain4j.openai.max-retries")
    Integer maxRetries;

    OpenAiClient client;

    @PostConstruct
    public void init() {
        client = OpenAiClient.builder().openAiApiKey(apiKey).baseUrl(baseUrl)
                .callTimeout(timeout).connectTimeout(timeout)
                .readTimeout(timeout).writeTimeout(timeout).build();
    }

    @PreDestroy
    public void cleanup() {
        if (client != null) {
            client.shutdown();
        }
    }

    public JsonObject moderate(String modelName, String prompt) {
        ModerationRequest request = ModerationRequest.builder().model(modelName).input(prompt).build();
        ModerationResponse response = RetryUtils.withRetry(() -> client.moderation(request).execute(), this.maxRetries);
        ModerationResult moderationResult = response.results().get(0);
        CategoryScores categoryScores = moderationResult.categoryScores();
        Categories categoryFlags = moderationResult.categories();
        JsonObject result = new JsonObject();
        result.put("flagged", moderationResult.isFlagged());
        JsonArray categories = new JsonArray();
        addCategoryScore(categories, "sexual", categoryScores.sexual(), categoryFlags.sexual());
        addCategoryScore(categories, "hate", categoryScores.hate(), categoryFlags.hate());
        addCategoryScore(categories, "hate-threatening", categoryScores.hateThreatening(), categoryFlags.hateThreatening());
        addCategoryScore(categories, "self-harm", categoryScores.selfHarm(), categoryFlags.selfHarm());
        addCategoryScore(categories, "violence", categoryScores.violence(), categoryFlags.violence());
        addCategoryScore(categories, "violence-graphic", categoryScores.violenceGraphic(), categoryFlags.violenceGraphic());
        addCategoryScore(categories, "sexual-minors", categoryScores.sexualMinors(), categoryFlags.sexualMinors());
        result.put("categories", categories);
        return result;
    }

    private void addCategoryScore(JsonArray categories, String name, Double score, Boolean flagged) {
        if (score != null) {
            JsonObject categoryScore = new JsonObject();
            categoryScore.put("name", name);
            categoryScore.put("flagged", flagged);
            categoryScore.put("score", score);
            categories.add(categoryScore);
        }
    }

}
