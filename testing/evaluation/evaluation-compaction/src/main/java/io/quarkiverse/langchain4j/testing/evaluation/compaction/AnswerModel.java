package io.quarkiverse.langchain4j.testing.evaluation.compaction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the {@code ChatModel} field of a test class answering the recall questions, with the full history, the
 * compacted views and no history alike. Without it, the default chat model answers.
 * <p>
 * Not a CDI qualifier: the field injects the model as usual, for instance with {@code @Inject @ModelName("gpt")}, which
 * also makes Quarkus create it; the evaluation looks the model up by the {@code @ModelName} of the field.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AnswerModel {
}
