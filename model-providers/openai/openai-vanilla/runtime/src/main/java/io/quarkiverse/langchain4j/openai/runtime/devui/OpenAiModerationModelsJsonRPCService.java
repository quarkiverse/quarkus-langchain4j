package io.quarkiverse.langchain4j.openai.runtime.devui;

import java.time.Duration;

import jakarta.inject.Inject;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.moderation.Categories;
import dev.ai4j.openai4j.moderation.CategoryScores;
import dev.ai4j.openai4j.moderation.ModerationRequest;
import dev.ai4j.openai4j.moderation.ModerationResponse;
import dev.ai4j.openai4j.moderation.ModerationResult;
import dev.langchain4j.internal.RetryUtils;
import io.netty.util.internal.StringUtil;
import io.quarkiverse.langchain4j.openai.runtime.config.LangChain4jOpenAiConfig;
import io.quarkiverse.langchain4j.openai.runtime.utils.ValidationUtil;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class OpenAiModerationModelsJsonRPCService {

    @Inject
    LangChain4jOpenAiConfig config;

    public JsonObject moderate(String configuration, String modelName, String prompt) {
        ValidationUtil.isValidDefaultConfig(configuration, config);
        LangChain4jOpenAiConfig.OpenAiConfig clientConfig = NamedConfigUtil.isDefault(configuration) ? config.defaultConfig()
                : config.namedConfig().get(configuration);
        OpenAiClient client = OpenAiClient.builder().openAiApiKey(clientConfig.apiKey().orElse(StringUtil.EMPTY_STRING))
                .baseUrl(clientConfig.baseUrl())
                .callTimeout(clientConfig.timeout().orElse(Duration.ofSeconds(10)))
                .connectTimeout(clientConfig.timeout().orElse(Duration.ofSeconds(10)))
                .readTimeout(clientConfig.timeout().orElse(Duration.ofSeconds(10)))
                .writeTimeout(clientConfig.timeout().orElse(Duration.ofSeconds(10))).build();
        try {
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
        } finally {
            client.shutdown();
        }
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
