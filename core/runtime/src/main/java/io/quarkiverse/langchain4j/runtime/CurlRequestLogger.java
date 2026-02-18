package io.quarkiverse.langchain4j.runtime;

import org.jboss.logging.Logger;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;

/**
 * Utility for generating cURL commands from Vert.x HTTP request data.
 * <p>
 * Headers are logged unmasked (unlike the standard {@code log-requests} output)
 * because the purpose is to produce a working command that can be copy-pasted
 * into a terminal.
 */
public final class CurlRequestLogger {

    private static final Logger log = Logger.getLogger(CurlRequestLogger.class);

    private CurlRequestLogger() {
    }

    /**
     * Builds a ready-to-use cURL command string from the given request and body.
     *
     * @param request the Vert.x HTTP client request
     * @param body the request body (may be {@code null})
     * @return a single-line cURL command string
     */
    public static String toCurl(HttpClientRequest request, Buffer body) {
        StringBuilder sb = new StringBuilder("curl");

        sb.append(" -X ").append(request.getMethod());

        sb.append(" '").append(request.absoluteURI()).append("'");

        MultiMap headers = request.headers();
        for (var entry : headers) {
            sb.append(" -H '")
                    .append(escapeForSingleQuote(entry.getKey()))
                    .append(": ")
                    .append(escapeForSingleQuote(entry.getValue()))
                    .append("'");
        }

        if (body != null && body.length() > 0) {
            String bodyStr = body.toString().replaceAll("\\R", " ");
            sb.append(" -d '")
                    .append(escapeForSingleQuote(bodyStr))
                    .append("'");
        }

        return sb.toString();
    }

    /**
     * Logs the cURL command at INFO level if logging is enabled.
     *
     * @param logger the logger to use
     * @param request the Vert.x HTTP client request
     * @param body the request body (may be {@code null})
     */
    public static void logCurl(Logger logger, HttpClientRequest request, Buffer body) {
        if (!logger.isInfoEnabled()) {
            return;
        }
        try {
            logger.infof("cURL:\n%s", toCurl(request, body));
        } catch (Exception e) {
            logger.warn("Failed to log cURL request", e);
        }
    }

    /**
     * Escapes a string for use inside single quotes in a shell command.
     * Replaces {@code '} with {@code '\''} (end quote, escaped quote, start quote).
     */
    static String escapeForSingleQuote(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("'", "'\\''");
    }
}
