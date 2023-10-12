package io.quarkiverse.langchain4j;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ChatCompletionChoice {

    /**
     * This index of this completion in the returned list.
     */
    private Integer index;

    /**
     * The {@link ChatMessageRole#assistant} message or delta (when streaming) which was generated
     */
    @JsonAlias("delta")
    private ChatMessage message;

    /**
     * The reason why GPT stopped generating, for example "length".
     */
    @JsonProperty("finish_reason")
    private String finishReason;

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public ChatMessage getMessage() {
        return message;
    }

    public void setMessage(ChatMessage message) {
        this.message = message;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }
}
