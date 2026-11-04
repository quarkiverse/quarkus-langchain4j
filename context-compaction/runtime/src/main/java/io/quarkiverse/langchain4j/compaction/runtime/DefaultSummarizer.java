package io.quarkiverse.langchain4j.compaction.runtime;

import java.util.List;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.compaction.CompactionException;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.Summarizer;
import io.quarkus.arc.DefaultBean;

/**
 * Summarizes conversation spans with a chat model. The model is selected with
 * {@code quarkus.langchain4j.compaction.summary.model-name} (default model otherwise).
 */
@Singleton
@DefaultBean
public class DefaultSummarizer implements Summarizer {

    static final String INSTRUCTIONS = """
            You compress conversation history for an AI agent that will continue the conversation.
            Write a factual summary that preserves, in this order:
            1. The user's goals, constraints and preferences.
            2. Decisions made and answers already given.
            3. Facts obtained from tools: identifiers, numbers, dates, names, statuses. Keep them exact.
            4. Open questions and pending actions.
            Do not invent anything. Do not address the user. Use short bullet points.
            Stay under %d words.""";

    private final CompactionModelsConfig config;
    private final Instance<ChatModel> models;
    private volatile ChatModel model;

    @Inject
    public DefaultSummarizer(CompactionModelsConfig config, @Any Instance<ChatModel> models) {
        this.config = config;
        this.models = models;
    }

    @Override
    public String summarize(String previousSummary, List<ChatMessage> messages, int maxWords) {
        StringBuilder transcript = new StringBuilder();
        if (previousSummary != null) {
            transcript.append("Existing summary of the conversation so far:\n").append(previousSummary)
                    .append("\n\nNew messages to fold into the summary:\n");
        } else {
            transcript.append("Conversation to summarize:\n");
        }
        transcript.append(render(messages));
        String summary = model().chat(SystemMessage.from(INSTRUCTIONS.formatted(maxWords)),
                UserMessage.from(transcript.toString())).aiMessage().text();
        if (summary == null || summary.isBlank()) {
            throw new CompactionException("The summarization model returned an empty summary");
        }
        return summary.strip();
    }

    public static String render(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage message : messages) {
            String line;
            if (message instanceof UserMessage u && CompactionMessages.isSummary(u)) {
                line = "Earlier summary: " + CompactionMessages.text(u);
            } else if (message instanceof UserMessage u) {
                line = "User: " + CompactionMessages.text(u);
            } else if (message instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
                line = "Assistant (tool calls): " + CompactionMessages.text(ai);
            } else if (message instanceof AiMessage ai) {
                line = "Assistant: " + CompactionMessages.text(ai);
            } else if (message instanceof ToolExecutionResultMessage r) {
                line = "Tool " + r.toolName() + " returned: " + r.text();
            } else {
                line = null;
            }
            if (line != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private ChatModel model() {
        ChatModel m = model;
        if (m == null) {
            Instance<ChatModel> selected = config.summary().modelName()
                    .map(name -> models.select(ModelName.Literal.of(name)))
                    .orElseGet(() -> models.select(Default.Literal.INSTANCE));
            if (!selected.isResolvable()) {
                throw new CompactionException("No chat model available for summarization"
                        + config.summary().modelName().map(n -> " (model name '" + n + "')").orElse(""));
            }
            m = selected.get();
            model = m;
        }
        return m;
    }
}
