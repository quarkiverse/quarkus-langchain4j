package io.quarkiverse.langchain4j.openai.runtime.devui;

import java.time.Duration;

import jakarta.inject.Inject;

import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.moderation.Categories;
import dev.langchain4j.model.openai.internal.moderation.CategoryScores;
import dev.langchain4j.model.openai.internal.moderation.ModerationRequest;
import dev.langchain4j.model.openai.internal.moderation.ModerationResponse;
import dev.langchain4j.model.openai.internal.moderation.ModerationResult;
import io.quarkiverse.langchain4j.openai.common.QuarkusOpenAiClient;
import io.quarkiverse.langchain4j.openai.runtime.config.LangChain4jOpenAiConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class OpenAiModerationModelsJsonRPCService {

    @Inject
    LangChain4jOpenAiConfig config;

    public JsonObject moderate(String configuration, String modelName, String prompt) {
        if (NamedConfigUtil.isDefault(configuration) && config.defaultConfig().apiKey().equals("dummy")) {
            // for non-default configurations, we assume that Quarkus has verified by now that the api key is set
            throw new RuntimeException("OpenAI API key is not configured. " +
                    "Please specify the key in the `quarkus.langchain4j.openai.api-key` configuration property.");
        }
        LangChain4jOpenAiConfig.OpenAiConfig clientConfig = NamedConfigUtil.isDefault(configuration) ? config.defaultConfig()
                : config.namedConfig().get(configuration);
        OpenAiClient client = QuarkusOpenAiClient.builder().openAiApiKey(clientConfig.apiKey()).baseUrl(clientConfig.baseUrl())
                .callTimeout(clientConfig.timeout().orElse(Duration.ofSeconds(10)))
                .connectTimeout(clientConfig.timeout().orElse(Duration.ofSeconds(10)))
                .readTimeout(clientConfig.timeout().orElse(Duration.ofSeconds(10)))
                .writeTimeout(clientConfig.timeout().orElse(Duration.ofSeconds(10))).build();

        ModerationRequest request = ModerationRequest.builder().model(modelName).input(prompt).build();
        ModerationResponse response = RetryUtils.withRetry(() -> client.moderation(request).execute(),
                clientConfig.maxRetries());
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
