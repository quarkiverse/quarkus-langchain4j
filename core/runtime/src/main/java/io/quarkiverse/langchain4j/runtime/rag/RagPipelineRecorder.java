package io.quarkiverse.langchain4j.runtime.rag;

import java.util.function.Function;

import dev.langchain4j.rag.RetrievalAugmentor;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class RagPipelineRecorder {

    public Function<SyntheticCreationalContext<RetrievalAugmentor>, RetrievalAugmentor> createStandaloneRagPipeline(
            RagPipelineCreateInfo info) {
        return ctx -> RagPipelineSupport.buildAugmentor(ctx, info);
    }
}
