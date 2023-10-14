package io.quarkiverse.langchain4j;

import java.io.IOException;
import java.util.List;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;

import dev.ai4j.openai4j.chat.ChatCompletionRequest;

/**
 * The point of this is to properly set the {@code stream} value of the request
 * so users don't have to remember to set it manually
 */
public class OpenAiRestApiWriterInterceptor implements WriterInterceptor {
    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
        Object entity = context.getEntity();
        if (entity instanceof ChatCompletionRequest) {
            ChatCompletionRequest request = (ChatCompletionRequest) entity;
            MultivaluedMap<String, Object> headers = context.getHeaders();
            List<Object> acceptList = headers.get(HttpHeaders.ACCEPT);
            if ((acceptList != null) && (acceptList.size() == 1)) {
                String accept = (String) acceptList.get(0);
                if (MediaType.APPLICATION_JSON.equals(accept)) {
                    if (Boolean.TRUE.equals(request.stream())) {
                        context.setEntity(ChatCompletionRequest.builder().from(request).stream(null).build());
                    }
                } else if (MediaType.SERVER_SENT_EVENTS.equals(accept)) {
                    if (!Boolean.TRUE.equals(request.stream())) {
                        context.setEntity(ChatCompletionRequest.builder().from(request).stream(true).build());
                    }
                }
            }
        }
        context.proceed();
    }
}
