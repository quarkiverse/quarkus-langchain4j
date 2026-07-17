package io.quarkiverse.langchain4j;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks an AI Service method that continues a conversation from the messages
 * already present in chat memory, without adding a new user message.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface ResumeConversation {
}
