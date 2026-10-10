package io.quarkiverse.langchain4j.runtime;

import java.util.List;
import java.util.Locale;

/**
 * Masks header values whose names suggest they carry a credential, for use in client log output.
 * <p>
 * Matching is performed on keywords rather than exact names, so custom credential headers passed
 * through configuration (for example {@code X-Gateway-Token} or {@code X-API-Gateway-Key}) are
 * masked as well. The keyword list follows langchain4j's {@code HttpRequestLogger}; {@code key} is
 * added so that gateway key headers are covered too.
 */
public final class SensitiveHeaderMasker {

    private static final List<String> SECRET_HEADER_NAME_KEYWORDS = List.of(
            "auth",
            "key",
            "token",
            "secret",
            "password",
            "credential",
            "cookie");

    private SensitiveHeaderMasker() {
    }

    /**
     * @return {@code true} if the header name contains one of the credential keywords, ignoring case
     */
    public static boolean isSensitive(String headerName) {
        if (headerName == null) {
            return false;
        }
        String lowercaseName = headerName.toLowerCase(Locale.ROOT);
        // Rate-limit headers (e.g. x-ratelimit-remaining-tokens) contain "token" but are not
        // credentials - they are exactly what you inspect when debugging a 429 - so they are
        // never masked.
        if (lowercaseName.contains("ratelimit")) {
            return false;
        }
        return SECRET_HEADER_NAME_KEYWORDS.stream().anyMatch(lowercaseName::contains);
    }

    /**
     * Masks a header value by keeping the first five and last two characters, if long enough.
     *
     * @return an empty or blank value unchanged, {@code ...} if the value is too short to be masked
     */
    public static String mask(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() < 7) {
            return "...";
        }
        return value.substring(0, 5) + "..." + value.substring(value.length() - 2);
    }
}
