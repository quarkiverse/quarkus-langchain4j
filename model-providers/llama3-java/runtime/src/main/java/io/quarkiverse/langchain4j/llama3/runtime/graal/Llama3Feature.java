package io.quarkiverse.langchain4j.llama3.runtime.graal;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

public class Llama3Feature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
            // needed to make the native image run at acceptable speed
            RuntimeClassInitialization.initializeAtBuildTime(
                    Class.forName("io.quarkiverse.langchain4j.llama3.copy.FloatTensor", false, Thread.currentThread()
                            .getContextClassLoader()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
