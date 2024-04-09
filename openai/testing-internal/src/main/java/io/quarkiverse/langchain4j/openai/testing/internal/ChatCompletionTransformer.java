package io.quarkiverse.langchain4j.openai.testing.internal;

import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

public class ChatCompletionTransformer implements ResponseDefinitionTransformerV2 {

    private static final String SYS_PROP_NAME = "quarkus.langchain4j.openai.chat-completion.message.content";

    @Override
    public ResponseDefinition transform(ServeEvent serveEvent) {
        var rd = serveEvent.getResponseDefinition();
        var chatCompletionMessageContent = System.getProperty(SYS_PROP_NAME);
        if (chatCompletionMessageContent != null) {
            Parameters transformerParameters = rd.getTransformerParameters();
            if (transformerParameters != null) {
                transformerParameters.put("ChatCompletionMessageContent", chatCompletionMessageContent);
            }
        }
        return rd;
    }

    @Override
    public String getName() {
        return "completion-transformer";
    }

    /**
     * TODO: this is a total necessary because this piece of code is run by neither the same thread
     * nor the same ClassLoader as {@link OpenAiBaseTest} so we use a system property in order to
     * provide an easy way to access the common state.
     * This is pretty terrible as it means we can't have multiple tests running in parallel.
     */
    public static void setContent(String content) {
        System.setProperty(SYS_PROP_NAME, content);
    }

    public static void clearContent() {
        System.clearProperty(SYS_PROP_NAME);
    }
}
