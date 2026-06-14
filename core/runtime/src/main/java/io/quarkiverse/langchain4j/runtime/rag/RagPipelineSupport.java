package io.quarkiverse.langchain4j.runtime.rag;

import java.util.List;

import org.eclipse.microprofile.context.ManagedExecutor;

import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import io.quarkiverse.langchain4j.runtime.aiservice.ComponentResolutionMode;
import io.quarkus.arc.SyntheticCreationalContext;

public final class RagPipelineSupport {

    private RagPipelineSupport() {
    }

    public static RetrievalAugmentor buildAugmentor(
            SyntheticCreationalContext<?> ctx, RagPipelineCreateInfo info) {

        if (info.augmentor().mode() == ComponentResolutionMode.EXPLICIT) {
            return (RetrievalAugmentor) ctx.getInjectedReference(loadClass(info.augmentor().className()));
        }

        DefaultRetrievalAugmentor.DefaultRetrievalAugmentorBuilder builder = DefaultRetrievalAugmentor.builder();

        if (!info.retrieverClassNames().isEmpty()) {
            List<ContentRetriever> retrievers = info.retrieverClassNames().stream()
                    .map(name -> (ContentRetriever) ctx.getInjectedReference(loadClass(name)))
                    .toList();
            builder.queryRouter(new DefaultQueryRouter(retrievers));
        }

        if (info.router().mode() == ComponentResolutionMode.EXPLICIT) {
            builder.queryRouter((QueryRouter) ctx.getInjectedReference(loadClass(info.router().className())));
        }

        if (info.transformer().mode() == ComponentResolutionMode.EXPLICIT) {
            builder.queryTransformer(
                    (QueryTransformer) ctx.getInjectedReference(loadClass(info.transformer().className())));
        }

        if (info.aggregator().mode() == ComponentResolutionMode.EXPLICIT) {
            builder.contentAggregator(
                    (ContentAggregator) ctx.getInjectedReference(loadClass(info.aggregator().className())));
        }

        if (info.injector().mode() == ComponentResolutionMode.EXPLICIT) {
            builder.contentInjector(
                    (ContentInjector) ctx.getInjectedReference(loadClass(info.injector().className())));
        }

        builder.executor(ctx.getInjectedReference(ManagedExecutor.class));

        return builder.build();
    }

    private static Class<?> loadClass(String className) {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load class: " + className, e);
        }
    }
}
