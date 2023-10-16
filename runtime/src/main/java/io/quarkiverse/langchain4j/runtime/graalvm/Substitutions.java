package io.quarkiverse.langchain4j.runtime.graalvm;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import dev.ai4j.openai4j.OpenAiClient;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import dev.langchain4j.spi.prompt.PromptTemplateFactory;
import dev.langchain4j.spi.prompt.structured.StructuredPromptFactory;
import io.quarkiverse.langchain4j.QuarkusOpenAiClient;
import io.quarkiverse.langchain4j.QuarkusPromptTemplateFactory;
import io.quarkiverse.langchain4j.QuarkusStructuredPromptFactory;

public class Substitutions {

    @TargetClass(OpenAiClient.class)
    static final class Target_OpenAiClient {

        @Substitute
        public static OpenAiClient.Builder builder() {
            return new QuarkusOpenAiClient.Builder();
        }
    }

    @TargetClass(PromptTemplate.class)
    static final class Target_PromptTemplate {

        @Substitute
        private static PromptTemplateFactory factory() {
            return new QuarkusPromptTemplateFactory();
        }
    }

    @TargetClass(StructuredPromptProcessor.class)
    static final class Target_StructuredPromptProcessor {

        @Substitute
        private static StructuredPromptFactory factory() {
            return new QuarkusStructuredPromptFactory();
        }
    }
}
