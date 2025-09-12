package io.quarkiverse.langchain4j.spi;

import java.util.function.Function;

/**
 * Quarkus extension can decide whether they need to provide a custom {@code ContentFilter} to be added to
 * {@code io.quarkus.qute.ParserHelper}
 */
public interface PromptTemplateFactoryContentFilterProvider {

    Function<String, String> getContentFilter();
}
