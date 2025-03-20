package io.quarkiverse.langchain4j.openai.common.runtime.graalvm;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import dev.langchain4j.model.openai.internal.OpenAiClient;
import io.quarkiverse.langchain4j.openai.common.QuarkusOpenAiClient;

public class Substitutions {

    @TargetClass(OpenAiClient.class)
    static final class Target_OpenAiClient {

        @Substitute
        public static OpenAiClient.Builder builder() {
            return new QuarkusOpenAiClient.Builder();
        }
    }
}
