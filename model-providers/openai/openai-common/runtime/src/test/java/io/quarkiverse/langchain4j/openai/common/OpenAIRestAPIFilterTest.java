package io.quarkiverse.langchain4j.openai.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

import jakarta.ws.rs.core.MultivaluedHashMap;

import org.jboss.resteasy.reactive.client.spi.ResteasyReactiveClientRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.auth.ModelAuthProvider;

public class OpenAIRestAPIFilterTest {

    private ModelAuthProvider authProvider;
    private ResteasyReactiveClientRequestContext context;
    private MultivaluedHashMap<Object, Object> headers;
    private OpenAiRestApi.OpenAIRestAPIFilter ollamaRestApiFilter;

    @BeforeEach
    void setUpFilter() {
        context = mock(ResteasyReactiveClientRequestContext.class);
        headers = new MultivaluedHashMap<>();
        doReturn(headers).when(context).getHeaders();

        authProvider = mock(ModelAuthProvider.class);

        ollamaRestApiFilter = new OpenAiRestApi.OpenAIRestAPIFilter(authProvider);
    }

    @Test
    void nullDoesNotSetAuthorization() {
        doReturn(null).when(authProvider).getAuthorization(any());

        ollamaRestApiFilter.filter(context);

        assertNull(headers.getFirst("Authorization"));
    }

    @Test
    void valueDoesSetAuthorization() {
        var token = "token";
        doReturn(token).when(authProvider).getAuthorization(any());

        ollamaRestApiFilter.filter(context);

        assertEquals(token, headers.getFirst("Authorization"));
    }

}
