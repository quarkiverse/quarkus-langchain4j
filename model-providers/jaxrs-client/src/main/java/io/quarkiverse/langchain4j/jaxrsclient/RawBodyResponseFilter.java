package io.quarkiverse.langchain4j.jaxrsclient;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.client.impl.MultiInvoker;
import org.jboss.resteasy.reactive.client.impl.PreservesThreadClientResponseFilter;

/**
 * Lets {@link JaxRsHttpClient#stream} receive a response body as raw bytes, whatever its content type.
 * <p>
 * {@link MultiInvoker} chooses how to split a body from its content type, and parses a {@code text/event-stream} or a
 * newline-delimited JSON body itself. For a request that carries an {@link OriginalContentType} under
 * {@link #PROPERTY}, this filter records the content type there and replaces it with {@code application/octet-stream},
 * so that the body is delivered as it was sent and the caller's parser can interpret it.
 */
final class RawBodyResponseFilter implements PreservesThreadClientResponseFilter {

    static final String PROPERTY = RawBodyResponseFilter.class.getName() + ".originalContentType";

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) {
        // there are no headers when the filter runs because the request failed before a response arrived
        if (requestContext.getProperty(PROPERTY) instanceof OriginalContentType originalContentType
                && responseContext.getHeaders() != null) {
            String contentType = responseContext.getHeaderString(HttpHeaders.CONTENT_TYPE);
            if (contentType != null) {
                originalContentType.value = contentType;
                responseContext.getHeaders().putSingle(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM);
            }
        }
    }

    static final class OriginalContentType {
        volatile String value;
    }
}
