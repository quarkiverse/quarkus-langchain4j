package io.quarkiverse.langchain4j.ollama;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.auth.ModelAuthProvider;

class OllamaRestApiFilterTest {

    private ModelAuthProvider authProvider;
    private ClientRequestContext context;
    private MultivaluedHashMap<Object, Object> headers;
    private OllamaRestApi.OllamaRestAPIFilter ollamaRestApiFilter;

    @BeforeEach
    void setUpFilter() {
        context = mock(ClientRequestContext.class);
        headers = new MultivaluedHashMap<>();
        doReturn(headers).when(context).getHeaders();

        authProvider = mock(ModelAuthProvider.class);

        ollamaRestApiFilter = new OllamaRestApi.OllamaRestAPIFilter(authProvider);
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
