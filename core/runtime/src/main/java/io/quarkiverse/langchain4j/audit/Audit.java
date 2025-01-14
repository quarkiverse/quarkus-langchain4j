package io.quarkiverse.langchain4j.audit;

import java.util.List;
import java.util.Optional;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;

/**
 * Abstract class to be implemented in order to keep track of whatever information is useful for the application auditing.
 * <p>
 *
 * @deprecated All the auditing stuff is going to be re-written, so don't use any of this stuff
 * @see <a href=
 *      "https://github.com/quarkiverse/quarkus-langchain4j/issues/1217">https://github.com/quarkiverse/quarkus-langchain4j/issues/1217</a>
 *      </p>
 */
@Deprecated(forRemoval = true)
public abstract class Audit {

    /**
     * Information about the AiService that is being audited
     */
    public record CreateInfo(String interfaceName, String methodName, Object[] parameters,
            Optional<Integer> memoryIDParamPosition) {

    }

    private final CreateInfo createInfo;
    private Optional<SystemMessage> systemMessage;
    private UserMessage userMessage;

    /**
     * Invoked by {@link AuditService} when an AiService is invoked
     */
    public Audit(CreateInfo createInfo) {
        this.createInfo = createInfo;
    }

    /**
     * @return information about the AiService that is being audited
     */
    public CreateInfo getCreateInfo() {
        return createInfo;
    }

    /**
     * Invoked when the original user and system messages have been created
     */
    public void initialMessages(Optional<SystemMessage> systemMessage, UserMessage userMessage) {

    }

    /**
     * Invoked if a relevant document was added to the messages to be sent to the LLM
     */
    public void addRelevantDocument(List<TextSegment> segments, UserMessage userMessage) {

    }

    /**
     * Invoked with a response from an LLM. It is important to note that this can be invoked multiple times
     * when tools exist.
     */
    public void addLLMToApplicationMessage(Response<AiMessage> response) {

    }

    /**
     * Invoked with a response from an LLM. It is important to note that this can be invoked multiple times
     * when tools exist.
     */
    public void addApplicationToLLMMessage(ToolExecutionResultMessage toolExecutionResultMessage) {

    }

    /**
     * Invoked when the final result of the AiService method has been computed
     */
    public void onCompletion(Object result) {

    }

    /**
     * Invoked when there was an exception computing the result of the AiService method
     */
    public void onFailure(Exception e) {

    }
}
