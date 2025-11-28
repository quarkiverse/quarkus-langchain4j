package io.quarkiverse.langchain4j.openai.common;

import java.util.function.Supplier;

public class QuarkusOpenAiTokenProvider {
    private QuarkusOpenAiTokenProvider() {
    }

    private static volatile String token = null;

    public static Supplier<String> tokenSupplier = () -> token;

    public static void setToken(String token) {
        QuarkusOpenAiTokenProvider.token = token;
    }

    public static String getToken() {
        return token;
    }
}
