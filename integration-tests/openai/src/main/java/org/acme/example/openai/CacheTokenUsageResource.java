package org.acme.example.openai;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import dev.langchain4j.model.openai.OpenAiTokenUsage;
import io.quarkiverse.langchain4j.cost.CacheTokenUsage;
import io.quarkiverse.langchain4j.cost.CacheTokenUsageResolver;

/**
 * Resolves an in-process {@link OpenAiTokenUsage} through the core {@link CacheTokenUsageResolver} so the OpenAI
 * {@code CacheTokenUsageExtractor} (bean discovery plus the super-type-token reflection in its base class) is
 * exercised in native image without any call to the OpenAI API.
 */
@Path("cache-token-usage")
public class CacheTokenUsageResource {

    private final CacheTokenUsageResolver resolver;

    public CacheTokenUsageResource(CacheTokenUsageResolver resolver) {
        this.resolver = resolver;
    }

    @GET
    @Path("openai")
    @Produces(MediaType.TEXT_PLAIN)
    public String openai() {
        OpenAiTokenUsage tokenUsage = OpenAiTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder().cachedTokens(8).build())
                .build();
        CacheTokenUsage cache = resolver.resolve(tokenUsage);
        return "read=" + cache.cacheReadInputTokens() + ";creation=" + cache.cacheCreationInputTokens();
    }
}
