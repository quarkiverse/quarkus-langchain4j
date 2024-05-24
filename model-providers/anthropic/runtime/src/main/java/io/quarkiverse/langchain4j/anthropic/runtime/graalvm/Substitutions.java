package io.quarkiverse.langchain4j.anthropic.runtime.graalvm;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import dev.langchain4j.model.anthropic.internal.client.AnthropicClient;
import io.quarkiverse.langchain4j.anthropic.QuarkusAnthropicClient;

public class Substitutions {
    @TargetClass(AnthropicClient.class)
    static final class Target_AnthropicClient {
        @Substitute
        public static AnthropicClient.Builder builder() {
            return new QuarkusAnthropicClient.Builder();
        }
    }
}
