package io.quarkiverse.langchain4j;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When placed on a {@link RegisterAiService} class, every method of that
 * service will be recorded by the {@link ChatHistoryStore} CDI bean.
 * <p>
 * Recording can also be enabled globally for all AI services via the
 * {@code quarkus.langchain4j.chat-history-enabled} build-time property.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RecordChatHistory {
}
