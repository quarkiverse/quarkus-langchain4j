package io.quarkiverse.langchain4j.llama3.runtime.graal;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import io.quarkiverse.langchain4j.llama3.copy.AOT;
import io.quarkiverse.langchain4j.llama3.copy.GGMLType;
import io.quarkiverse.langchain4j.llama3.copy.GGUF;
import io.quarkiverse.langchain4j.llama3.copy.Llama;
import io.quarkiverse.langchain4j.llama3.copy.Pair;
import io.quarkiverse.langchain4j.llama3.copy.Tokenizer;
import io.quarkiverse.langchain4j.llama3.copy.Vocabulary;

public class Llama3Feature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
            // needed to make the native image run at acceptable speed
            RuntimeClassInitialization.initializeAtBuildTime(
                    Class.forName("io.quarkiverse.langchain4j.llama3.copy.FloatTensor", false, Thread.currentThread()
                            .getContextClassLoader()));

            // needed to make preload feature work correctly
            RuntimeClassInitialization.initializeAtBuildTime(AOT.PartialModel.class,
                    Llama.class, Llama.Configuration.class,
                    Tokenizer.class,
                    Vocabulary.class,
                    Pair.class,
                    GGUF.GGUFTensorInfo.class,
                    GGMLType.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
