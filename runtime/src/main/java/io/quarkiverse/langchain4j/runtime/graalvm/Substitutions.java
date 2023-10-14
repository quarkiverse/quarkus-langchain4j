package io.quarkiverse.langchain4j.runtime.graalvm;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import dev.ai4j.openai4j.OpenAiClient;
import io.quarkiverse.langchain4j.QuarkusOpenAiClient;

public class Substitutions {

    @TargetClass(OpenAiClient.class)
    static final class Target_OpenAiClient {

        @Substitute
        public static OpenAiClient.Builder builder() {
            return new QuarkusOpenAiClient.Builder();
        }
    }
}
