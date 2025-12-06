package io.quarkiverse.langchain4j.openai.common;

import java.util.List;
import java.util.ServiceLoader;

public final class RestApiFilterResolverLoader {
    private RestApiFilterResolverLoader() {
    }

    public static RestApiFilterResolver load() {
        ServiceLoader<RestApiFilterResolver> loader = ServiceLoader.load(RestApiFilterResolver.class);
        List<RestApiFilterResolver> resolvers = loader.stream().map(ServiceLoader.Provider::get).toList();

        if (resolvers.isEmpty())
            return new DefaultOpenAiRestApiFilterResolver();

        // return last registered resolver
        return resolvers.get(resolvers.size() - 1);
    }
}