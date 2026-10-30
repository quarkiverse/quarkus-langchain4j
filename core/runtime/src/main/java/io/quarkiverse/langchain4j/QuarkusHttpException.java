package io.quarkiverse.langchain4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import dev.langchain4j.exception.HttpException;

/**
 * Carries the HTTP response headers along with the status code and body, so callers can read values such as
 * {@code Retry-After} from the cause of a {@code RateLimitException}.
 */
public class QuarkusHttpException extends HttpException {

    private final Map<String, List<String>> headers;

    public QuarkusHttpException(int statusCode, String message, Map<String, List<String>> headers) {
        super(statusCode, message);
        Map<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                copy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        this.headers = Collections.unmodifiableMap(copy);
    }

    public Map<String, List<String>> headers() {
        return headers;
    }

    public Optional<String> header(String name) {
        List<String> values = headers.get(name);
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(0));
    }
}
