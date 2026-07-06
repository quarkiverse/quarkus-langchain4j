package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.watsonx.runtime.client.logger.WatsonxClientLogger;

public class WatsonxClientLoggerTest {

    private static String maskAuthorization(String value) throws Exception {
        Method m = WatsonxClientLogger.class.getDeclaredMethod("maskAuthorizationHeaderValue", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, value);
    }

    private static String formatBase64Image(String value) throws Exception {
        Method m = WatsonxClientLogger.class.getDeclaredMethod("formatBase64ImageForLogging", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, value);
    }

    @Test
    void mask_authorization_keeps_only_head_and_tail() throws Exception {
        String masked = maskAuthorization("Bearer abcdefghijklmnop");
        assertThat(masked).isEqualTo("Bearer abcd...mnop");
        assertThat(masked).doesNotContain("efghijkl");
    }

    @Test
    void mask_authorization_handles_jwt_like_tokens_with_non_word_chars() throws Exception {
        String token = "eyJhbGci.OiJkWt-9sd_fSDFghijkLMNO.pQRSztuv";
        String masked = maskAuthorization("Bearer " + token);

        assertThat(masked)
                .isNotEqualTo("Failed to mask the API key.")
                .startsWith("Bearer eyJh")
                .endsWith("tuv")
                .contains("...")
                .doesNotContain("OiJkWt-9sd_fSDFghijkLMNO");
    }

    @Test
    void mask_authorization_fully_masks_short_tokens() throws Exception {
        assertThat(maskAuthorization("Bearer short")).isEqualTo("Bearer ...");
    }

    @Test
    void base64_image_is_masked_and_surrounding_body_is_preserved() throws Exception {
        String body = "{\"image\":\"data:image/png;base64,"
                + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
                + "\",\"after\":\"keep\"}";

        String masked = formatBase64Image(body);

        assertThat(masked)
                .isNotEqualTo("Failed to format the base64 image value.")
                .contains("data:image/png;base64,")
                .contains("...")
                .doesNotContain("RU5ErkJggg==")
                .contains("\"after\":\"keep\"");
    }

    @Test
    void body_without_base64_is_returned_unchanged() throws Exception {
        String body = "{\"message\":\"hello world\"}";
        assertThat(formatBase64Image(body)).isEqualTo(body);
    }
}
