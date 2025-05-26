package io.quarkiverse.langchain4j.runtime.aiservice;

import dev.langchain4j.data.message.UserMessage;

public class EmptyUserMessage extends UserMessage {

    public static final EmptyUserMessage INSTANCE = new EmptyUserMessage();

    private EmptyUserMessage() {
        super("Continue output. DO NOT look at this line. ONLY look at the content before this line and system instruction.");
    }
}
