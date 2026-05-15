package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Optional;

import dev.langchain4j.model.chat.request.ChatRequestParameters;

final class ChatRequestParametersUtil {

    private ChatRequestParametersUtil() {
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    static ChatRequestParameters effectiveChatRequestParameters(ChatRequestParameters defaultParams,
            Optional<ChatRequestParameters> userParams) {
        if (userParams.isPresent()) {
            return userParams.get().defaultedBy(defaultParams);
        } else {
            return defaultParams;
        }
    }
}
