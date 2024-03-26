package io.quarkiverse.langchain4j.mistralai.runtime.graalvm;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import dev.langchain4j.model.mistralai.MistralAiClient;
import io.quarkiverse.langchain4j.mistralai.QuarkusMistralAiClient;

public class Substitutions {

    @TargetClass(MistralAiClient.class)
    static final class Target_MistralAiClient {

        @Substitute
        public static MistralAiClient.Builder builder() {
            return new QuarkusMistralAiClient.Builder();
        }
    }
}
