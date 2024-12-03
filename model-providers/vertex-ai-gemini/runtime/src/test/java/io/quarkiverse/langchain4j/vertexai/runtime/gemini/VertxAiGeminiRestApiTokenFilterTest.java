package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MultivaluedHashMap;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.resteasy.reactive.client.spi.ResteasyReactiveClientRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class VertxAiGeminiRestApiTokenFilterTest {

    @InjectMock
    MyModelAuthProvider authProvider;

    private ResteasyReactiveClientRequestContext context;
    private MultivaluedHashMap<Object, Object> headers;
    private VertxAiGeminiRestApi.TokenFilter ollamaRestApiFilter;

    @BeforeEach
    void setUpFilter() {
        context = mock(ResteasyReactiveClientRequestContext.class);
        headers = new MultivaluedHashMap<>();
        doReturn(headers).when(context).getHeaders();

        ollamaRestApiFilter = new VertxAiGeminiRestApi.TokenFilter(mockSyncExecutor());
    }

    private static ManagedExecutor mockSyncExecutor() {
        ManagedExecutor executor = mock(ManagedExecutor.class);
        // execute the runnable immediately, to avoid any async issues
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).submit(any(Runnable.class));
        return executor;
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

    @ApplicationScoped
    static class MyModelAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            fail("should never be called");
            return null;
        }
    }

}
