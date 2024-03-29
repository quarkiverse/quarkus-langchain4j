package io.quarkiverse.langchain4j.runtime.devui.json;

import java.util.List;

// The response sent to the Dev UI frontend after executing a chat message.
// It contains EITHER the complete history of the chat OR an error.
public class ChatResultPojo {

    private List<ChatMessagePojo> history;

    private String error;

    public ChatResultPojo(List<ChatMessagePojo> history, String error) {
        this.history = history;
        this.error = error;
    }

    public List<ChatMessagePojo> getHistory() {
        return history;
    }

    public String getError() {
        return error;
    }
}
