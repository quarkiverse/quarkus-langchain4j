package io.quarkiverse.langchain4j.sample.chatbot;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.ModelProvider;
import io.quarkiverse.langchain4j.runtime.aiservice.SystemMessageProviderWithContext;

import java.util.Optional;

public class ModelAwareSystemMessageProvider implements SystemMessageProviderWithContext {

    @Override
    public Optional<String> getSystemMessage(InvocationContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                You are an AI named Bob answering questions.
                Your response must be polite, concise, use the same language as the question, and be relevant to the question.
                
                When you don't know, respond that you don't know the answer.
                """);
        if (context.modelProvider() == ModelProvider.OPEN_AI) {
            builder.append("Unless you asked to do otherwise, mention, that you're powered by OpenAI model.");
        }
        builder.append("Unless you asked to do otherwise, mention the following:\n");
        String modelName = context.defaultRequestParameters().modelName();
        String modelInfo = switch (context.modelProvider()) {
            case OPEN_AI -> "- you're powered by OpenAI model, named " + modelName;
            case WATSONX -> "- you're powered by IBM model, named " + modelName;
            case OTHER -> "- you are powered by this model: " + modelName;
            default -> "- you are powered by model %s from %s ".formatted(modelName, context.modelProvider());
        };
        builder.append(modelInfo);
        if (modelName.contains("nano") || modelName.contains("mini") || modelName.contains("micro")) {
            builder.append("- This is a resource-constrained model which may loose some context");
        }
        if (context.defaultRequestParameters().temperature() > 0) {
            builder.append("- Your answers may include hallucinations");
        }
        return Optional.of(builder.toString());
    }
}