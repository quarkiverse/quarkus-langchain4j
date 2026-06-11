package io.quarkiverse.langchain4j.runtime.bedrock;

import java.lang.reflect.Method;

public final class BedrockTokenUsageReflection {

    private static final Method CACHE_WRITE_INPUT_TOKENS_METHOD;
    private static final Method CACHE_READ_INPUT_TOKENS_METHOD;

    static {
        Method cacheWriteMethod = null;
        Method cacheReadMethod = null;
        try {
            Class<?> bedrockTokenUsageClass = Class.forName("dev.langchain4j.model.bedrock.BedrockTokenUsage");
            cacheWriteMethod = bedrockTokenUsageClass.getMethod("cacheWriteInputTokens");
            cacheReadMethod = bedrockTokenUsageClass.getMethod("cacheReadInputTokens");
        } catch (ClassNotFoundException | NoSuchMethodException | NoClassDefFoundError e) {
            // Bedrock prompt cache tokens not available
        }
        CACHE_WRITE_INPUT_TOKENS_METHOD = cacheWriteMethod;
        CACHE_READ_INPUT_TOKENS_METHOD = cacheReadMethod;
    }

    private BedrockTokenUsageReflection() {
    }

    public static Integer getCacheWriteInputTokens(Object tokenUsage) {
        return invoke(CACHE_WRITE_INPUT_TOKENS_METHOD, tokenUsage);
    }

    public static Integer getCacheReadInputTokens(Object tokenUsage) {
        return invoke(CACHE_READ_INPUT_TOKENS_METHOD, tokenUsage);
    }

    private static Integer invoke(Method method, Object target) {
        if (method == null || target == null) {
            return null;
        }
        try {
            return (Integer) method.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isAvailable() {
        return CACHE_WRITE_INPUT_TOKENS_METHOD != null && CACHE_READ_INPUT_TOKENS_METHOD != null;
    }
}
